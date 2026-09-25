package com.dong.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.BuildConfig
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.InstallEvent
import com.dong.budget.data.update.InstallEvents
import com.dong.budget.data.update.UpdateChecker
import com.dong.budget.data.update.UpdateRepository
import com.dong.budget.data.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 업데이트 화면이 보여줄 상태 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Available(val version: String, val notes: String, val sizeBytes: Long) : UpdateUiState

    data class Downloading(val version: String, val progress: Float) : UpdateUiState

    /** 내려받기가 끝나고 시스템 설치 확인창을 기다리는 중 */
    data object AwaitingInstall : UpdateUiState

    /**
     * @property detail 시스템이 알려준 원래 사유. 없으면 null
     * @property canTryOtherWays 받은 파일이나 브라우저로 다시 설치해 볼 만한 실패인지
     */
    data class Failed(val reason: String, val detail: String? = null, val canTryOtherWays: Boolean = true) : UpdateUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val apkInstaller: ApkInstaller,
    private val updateChecker: UpdateChecker,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        settingsRepository.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ThemeMode.SYSTEM,
        )

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    /**
     * 최신 버전의 배포 페이지. 앱 안 설치가 기기 설치기에 막힐 때 브라우저로 설치 파일을 직접 받게 한다.
     * 처음 설치할 때 썼던 것과 같은 길이라 기기와 상관없이 된다.
     */
    val releasePageUrl: String = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    private var pendingDownloadUrl: String? = null
    private var pendingSize: Long = 0

    /**
     * 받아두었지만 아직 설치되지 않은 새 버전. 앱이 꺼졌다 켜져도 파일이 남아 있으면 다시 설치할 수 있다.
     * 이 파일로 '받은 파일로 직접 설치' 를 한다.
     */
    private val downloadedApk = MutableStateFlow(apkInstaller.latestDownloaded(currentVersion))
    val downloadedVersion: StateFlow<String?> =
        downloadedApk
            .map { it?.version }
            .stateIn(viewModelScope, SharingStarted.Eagerly, downloadedApk.value?.version)

    init {
        // 앱을 열 때 자동 확인에서 이미 찾은 새 버전이 있으면 바로 보여준다.
        // 홈 배너를 눌러 들어왔을 때 확인 버튼을 한 번 더 누르지 않아도 되게 하기 위함이다.
        updateChecker.available.value?.let { update ->
            showAvailable(update)
            // 다른 버전으로 받아둔 파일이 남아 있으면 그 버전을 설치하라고 권하게 된다. 지금 새 버전 것만 남긴다.
            forgetDownloadsExcept(update.version)
        }

        // 설치 결과는 시스템이 브로드캐스트로 알려준다. 그걸 화면 상태로 옮긴다.
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                _updateState.value =
                    when (event) {
                        is InstallEvent.Succeeded -> UpdateUiState.UpToDate
                        is InstallEvent.Failed -> UpdateUiState.Failed(event.reason, event.detail, event.canTryOtherWays)
                    }
            }
        }
    }

    fun selectThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val checked = updateRepository.check()
            // 홈 배너도 이 결과에 맞춘다
            updateChecker.record(checked)
            _updateState.value =
                when (val status = checked) {
                    is UpdateStatus.UpToDate -> {
                        // 최신이면 받아둔 새 버전 파일은 필요 없다. 배포를 내린 버전일 수도 있다.
                        forgetDownloadsExcept(null)
                        UpdateUiState.UpToDate
                    }

                    is UpdateStatus.Failed -> UpdateUiState.Failed(status.reason)

                    is UpdateStatus.Available -> {
                        forgetDownloadsExcept(status.version)
                        availableState(status)
                    }
                }
        }
    }

    /** 설치 파일을 받아(이미 받아뒀으면 건너뛰고) 앱 안 설치를 시작한다. */
    fun downloadAndInstall() {
        val url = pendingDownloadUrl ?: return
        val version = (_updateState.value as? UpdateUiState.Available)?.version ?: return
        // 지난 설치 결과가 남아 있으면 새 시도의 상태를 덮어쓴다.
        InstallEvents.clear()
        // 이미 받아둔 파일이면 내려받을 게 없다. 진행 막대를 다 찬 상태로 두어 '설치 준비 중' 으로 보이게 한다.
        val cached = downloadedApk.value?.version == version
        _updateState.value = UpdateUiState.Downloading(version, if (cached) 1f else 0f)
        viewModelScope.launch {
            val apk =
                apkInstaller
                    .download(url, version, pendingSize) { fraction ->
                        _updateState.update { current ->
                            if (current is UpdateUiState.Downloading) current.copy(progress = fraction.coerceIn(0f, 1f)) else current
                        }
                    }.getOrElse {
                        _updateState.value = UpdateUiState.Failed(it.message ?: "내려받기에 실패했어요")
                        return@launch
                    }
            downloadedApk.value = apkInstaller.latestDownloaded(currentVersion)
            _updateState.value =
                apkInstaller.installWithSession(apk).fold(
                    onSuccess = { UpdateUiState.AwaitingInstall },
                    onFailure = { UpdateUiState.Failed("설치를 시작하지 못했어요", it.message) },
                )
        }
    }

    private fun showAvailable(status: UpdateStatus.Available) {
        _updateState.value = availableState(status)
    }

    private fun availableState(status: UpdateStatus.Available): UpdateUiState.Available {
        pendingDownloadUrl = status.downloadUrl
        pendingSize = status.sizeBytes
        return UpdateUiState.Available(version = status.version, notes = status.notes, sizeBytes = status.sizeBytes)
    }

    private fun forgetDownloadsExcept(version: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            apkInstaller.keepOnly(version)
            downloadedApk.value = apkInstaller.latestDownloaded(currentVersion)
        }
    }

    /**
     * 받아둔 파일을 시스템 설치기로 직접 연다. 앱 안 설치가 제조사 설치기에 막힐 때 쓰는 길이다.
     * 파일이 캐시 정리로 지워졌으면 다시 받아야 한다고 알린다.
     */
    fun installDownloadedManually() {
        val apk = apkInstaller.latestDownloaded(currentVersion)
        downloadedApk.value = apk
        if (apk == null) {
            _updateState.value = UpdateUiState.Failed("받아둔 설치 파일이 없어요. 업데이트를 다시 확인해 주세요")
            return
        }
        apkInstaller.openWithSystemInstaller(apk.file).onFailure {
            _updateState.value = UpdateUiState.Failed("설치 화면을 열지 못했어요", it.message)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
