package com.dong.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.BuildConfig
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.DownloadedApk
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
import kotlinx.coroutines.withContext

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
     * @property suggestGalaxySecurity 갤럭시 '보안 위험 자동 차단' 설정으로 가는 버튼을 보여줄지
     */
    data class Failed(
        val reason: String,
        val detail: String? = null,
        val canTryOtherWays: Boolean = true,
        val suggestGalaxySecurity: Boolean = false,
    ) : UpdateUiState
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

    /** 지금 보여주는 새 버전. 설치 버튼이 이 주소와 크기로 받는다. */
    private var shownUpdate: UpdateStatus.Available? = null

    /**
     * 이 화면이 시작한 설치의 결과를 기다리는 중인지.
     * 설치 결과 흐름(InstallEvents)에는 지난 시도의 결과가 남아 있다. 새로 연 화면이 그걸 받아
     * 지금 상태(설치 버튼)를 옛 실패 문구로 덮지 않도록, 자기가 설치를 시작한 경우에만 결과를 반영한다.
     */
    private var awaitingResult = false

    /**
     * 받아두었지만 아직 설치되지 않은 새 버전. 앱이 꺼졌다 켜져도 파일이 남아 있으면 다시 설치할 수 있다.
     * 이 파일로 '받은 파일로 설치' 를 한다. 디스크를 읽으므로 화면을 만들 때가 아니라 뒤에서 채운다.
     */
    private val downloadedApk = MutableStateFlow<DownloadedApk?>(null)
    val downloadedVersion: StateFlow<String?> =
        downloadedApk
            .map { it?.version }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // 새 버전 확인 결과를 따라간다. 이 화면을 연 뒤에 앱을 열 때의 자동 확인이나 패치노트의 배포 목록이
        // 새 결과를 기록해도 바로 맞춘다. 결과가 바뀔 때 받아둔 파일도 다시 읽는다(기록 전에 이미 정리돼 있다).
        // 홈 배너를 눌러 들어왔을 때 확인 버튼을 한 번 더 누르지 않아도 새 버전이 바로 보인다.
        viewModelScope.launch {
            updateChecker.available.collect { update ->
                refreshDownloaded()
                reconcile(update)
            }
        }

        // 설치 결과는 시스템이 브로드캐스트로 알려준다. 이 화면이 시작한 설치의 결과만 화면 상태로 옮긴다.
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                if (!awaitingResult) return@collect
                awaitingResult = false
                _updateState.value =
                    when (event) {
                        is InstallEvent.Succeeded -> UpdateUiState.UpToDate

                        is InstallEvent.Failed ->
                            UpdateUiState.Failed(event.reason, event.detail, event.canTryOtherWays, event.suggestGalaxySecurity)
                    }
            }
        }
    }

    /** 확인 결과가 바뀌면 화면을 맞춘다. 확인·내려받기·설치가 진행 중이면 그 화면을 덮지 않는다. */
    private fun reconcile(update: UpdateStatus.Available?) {
        val state = _updateState.value
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading || state is UpdateUiState.AwaitingInstall) return
        if (update != null) {
            shownUpdate = update
            _updateState.value = UpdateUiState.Available(version = update.version, notes = update.notes, sizeBytes = update.sizeBytes)
        } else if (state is UpdateUiState.Available) {
            // 보여주던 새 버전이 사라졌다(배포를 내렸거나 이미 설치됨)
            shownUpdate = null
            _updateState.value = UpdateUiState.UpToDate
        }
    }

    private suspend fun refreshDownloaded() {
        downloadedApk.value = withContext(Dispatchers.IO) { apkInstaller.latestDownloaded(currentVersion) }
    }

    fun selectThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun checkForUpdate() {
        val current = _updateState.value
        if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val checked = updateRepository.check()
            // 받아둔 파일 정리와 홈 배너까지 한 곳(UpdateChecker)에서 맞춘다
            updateChecker.apply(checked)
            refreshDownloaded()
            _updateState.value =
                when (checked) {
                    is UpdateStatus.UpToDate -> {
                        shownUpdate = null
                        UpdateUiState.UpToDate
                    }

                    // 확인에 실패한 것이지 설치에 실패한 게 아니다. 다른 설치 방법을 권하지 않는다.
                    is UpdateStatus.Failed -> UpdateUiState.Failed(checked.reason, canTryOtherWays = false)

                    is UpdateStatus.Available -> {
                        shownUpdate = checked
                        UpdateUiState.Available(version = checked.version, notes = checked.notes, sizeBytes = checked.sizeBytes)
                    }
                }
        }
    }

    /** 설치 파일을 받아(이미 받아뒀으면 건너뛰고) 앱 안 설치를 시작한다. */
    fun downloadAndInstall() {
        val update = shownUpdate ?: return
        if (_updateState.value !is UpdateUiState.Available) return
        // 지난 설치 결과와 띄우지 못한 확인창을 비운다
        InstallEvents.clear()
        // 이미 받아둔 파일이면 내려받을 게 없다. 진행 막대를 다 찬 상태로 두어 '설치 준비 중' 으로 보이게 한다.
        val cached = downloadedApk.value?.version == update.version
        _updateState.value = UpdateUiState.Downloading(update.version, if (cached) 1f else 0f)
        viewModelScope.launch {
            val apk =
                apkInstaller
                    .download(update.downloadUrl, update.version, update.sizeBytes) { fraction ->
                        _updateState.update { current ->
                            if (current is UpdateUiState.Downloading) current.copy(progress = fraction.coerceIn(0f, 1f)) else current
                        }
                    }.getOrElse {
                        _updateState.value = UpdateUiState.Failed(UpdateRepository.describeNetworkError(it), it.message)
                        return@launch
                    }
            refreshDownloaded()
            // 설치를 넘기기 전에 기다린다고 적는다. 결과가 설치 함수가 돌아오기 전에 도착할 수도 있다.
            awaitingResult = true
            apkInstaller.installWithSession(apk).fold(
                // 그사이 결과가 먼저 와서 반영됐으면 덮지 않는다
                onSuccess = { if (awaitingResult) _updateState.value = UpdateUiState.AwaitingInstall },
                onFailure = {
                    awaitingResult = false
                    _updateState.value = UpdateUiState.Failed("설치를 시작하지 못했어요", it.message)
                },
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
            val apk = downloadedApk.value
            if (apk == null) {
                _updateState.value = UpdateUiState.Failed("받아둔 설치 파일이 없어요. 업데이트를 다시 확인해 주세요", canTryOtherWays = false)
                return@launch
            }
            apkInstaller.openWithSystemInstaller(apk.file).onFailure {
                _updateState.value = UpdateUiState.Failed("설치 화면을 열지 못했어요", it.message)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
