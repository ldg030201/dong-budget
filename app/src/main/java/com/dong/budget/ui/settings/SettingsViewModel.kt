package com.dong.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.BuildConfig
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.InstallEvent
import com.dong.budget.data.update.InstallEvents
import com.dong.budget.data.update.NewerRelease
import com.dong.budget.data.update.UpdateChecker
import com.dong.budget.data.update.UpdateFailure
import com.dong.budget.data.update.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 업데이트 화면이 보여줄 상태 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Available(val release: NewerRelease) : UpdateUiState

    data class Downloading(val version: String, val progress: Float) : UpdateUiState

    /** 내려받기가 끝나고 시스템 설치 확인창을 기다리는 중 */
    data object AwaitingInstall : UpdateUiState

    data class Failed(val failure: UpdateFailure) : UpdateUiState
}

/** 확인 중이거나 내려받는 중. 이때는 다시 확인하지 않는다. 내려받던 화면이 확인 결과로 덮이면 진행 상황이 사라진다. */
val UpdateUiState.isBusy: Boolean get() = this is UpdateUiState.Checking || this is UpdateUiState.Downloading

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
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

    /**
     * 가장 새 버전을 받아둔 파일이 있으면 그 버전. 앱이 꺼졌다 켜져도 파일이 남아 있으면 다시 받지 않고 설치할 수 있다.
     * 이 파일로 '받은 파일로 설치' 를 한다. 디스크를 보므로 화면을 만들 때가 아니라 뒤에서 채운다.
     */
    private val _downloadedVersion = MutableStateFlow<String?>(null)
    val downloadedVersion: StateFlow<String?> = _downloadedVersion.asStateFlow()

    init {
        // 새 버전 확인 결과를 따라간다. 이 화면을 연 뒤에 앱을 열 때의 자동 확인이 새 결과를 기록해도 바로 맞춘다.
        // 홈 배너를 눌러 들어왔을 때 확인 버튼을 한 번 더 누르지 않아도 새 버전이 바로 보인다.
        viewModelScope.launch {
            updateChecker.newer.collect { releases ->
                refreshDownloaded()
                reconcile(releases.firstOrNull())
            }
        }

        // 설치 결과는 시스템이 브로드캐스트로 알려준다. 이 화면이 시작한 설치를 기다리는 중일 때만 반영한다.
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                val state = _updateState.value
                if (state !is UpdateUiState.Downloading && state !is UpdateUiState.AwaitingInstall) return@collect
                _updateState.value =
                    when (event) {
                        is InstallEvent.Succeeded -> UpdateUiState.UpToDate
                        is InstallEvent.Failed -> UpdateUiState.Failed(event.failure)
                    }
            }
        }
    }

    /** 확인 결과가 바뀌면 화면을 맞춘다. 확인·내려받기·설치가 진행 중이면 그 화면을 덮지 않는다. */
    private fun reconcile(latest: NewerRelease?) {
        val state = _updateState.value
        if (state.isBusy || state is UpdateUiState.AwaitingInstall) return
        if (latest != null) {
            _updateState.value = UpdateUiState.Available(latest)
        } else if (state is UpdateUiState.Available) {
            // 보여주던 새 버전이 사라졌다(배포를 내렸거나 이미 설치됨)
            _updateState.value = UpdateUiState.UpToDate
        }
    }

    private suspend fun refreshDownloaded() {
        val latest = updateChecker.newer.value.firstOrNull()?.version
        _downloadedVersion.value = latest?.takeIf { withContext(Dispatchers.IO) { apkInstaller.downloaded(it) } != null }
    }

    fun selectThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun checkForUpdate() {
        if (_updateState.value.isBusy) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            // 확인하는 동안은 reconcile 이 화면을 덮지 않으므로 결과는 여기서 맞춘다
            val checked = updateChecker.checkNow()
            refreshDownloaded()
            _updateState.value =
                checked.fold(
                    onSuccess = { releases -> releases.firstOrNull()?.let(UpdateUiState::Available) ?: UpdateUiState.UpToDate },
                    // 확인에 실패한 것이지 설치에 실패한 게 아니다. 다른 설치 방법을 권하지 않는다.
                    onFailure = { UpdateUiState.Failed(UpdateFailure(UpdateRepository.describeNetworkError(it), canTryOtherWays = false)) },
                )
        }
    }

    /** 설치 파일을 받아(이미 받아뒀으면 건너뛰고) 앱 안 설치를 시작한다. */
    fun downloadAndInstall() {
        val update = (_updateState.value as? UpdateUiState.Available)?.release ?: return
        InstallEvents.beginAttempt()
        // 이미 받아둔 파일이면 내려받을 게 없다. 진행 막대를 다 찬 상태로 두어 '설치 준비 중' 으로 보이게 한다.
        val cached = _downloadedVersion.value == update.version
        _updateState.value = UpdateUiState.Downloading(update.version, if (cached) 1f else 0f)
        viewModelScope.launch {
            val apk =
                apkInstaller
                    .download(update.downloadUrl, update.version, update.sizeBytes) { fraction ->
                        _updateState.update { current ->
                            if (current is UpdateUiState.Downloading) current.copy(progress = fraction.coerceIn(0f, 1f)) else current
                        }
                    }.getOrElse {
                        _updateState.value = UpdateUiState.Failed(UpdateFailure(UpdateRepository.describeNetworkError(it), it.message))
                        return@launch
                    }
            refreshDownloaded()
            apkInstaller.installWithSession(apk).fold(
                // 결과가 설치 함수가 돌아오기 전에 먼저 와서 반영됐으면 덮지 않는다
                onSuccess = { _updateState.update { if (it is UpdateUiState.Downloading) UpdateUiState.AwaitingInstall else it } },
                onFailure = { _updateState.value = UpdateUiState.Failed(UpdateFailure("설치를 시작하지 못했어요", it.message)) },
            )
        }
    }

    /**
     * 받아둔 파일을 시스템 설치기로 직접 연다. 앱 안 설치가 제조사 설치기에 막힐 때 쓰는 길이다.
     * 파일이 캐시 정리로 지워졌으면 다시 받아야 한다고 알린다.
     */
    fun installDownloadedManually() {
        viewModelScope.launch {
            refreshDownloaded()
            val apk = _downloadedVersion.value?.let { withContext(Dispatchers.IO) { apkInstaller.downloaded(it) } }
            if (apk == null) {
                _updateState.value =
                    UpdateUiState.Failed(UpdateFailure("받아둔 설치 파일이 없어요. 업데이트를 다시 확인해 주세요", canTryOtherWays = false))
                return@launch
            }
            apkInstaller.openWithSystemInstaller(apk).onFailure {
                _updateState.value = UpdateUiState.Failed(UpdateFailure("설치 화면을 열지 못했어요", it.message))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
