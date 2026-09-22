package com.dong.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.BuildConfig
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.UpdateRepository
import com.dong.budget.data.update.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    data class Failed(val reason: String) : UpdateUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val apkInstaller: ApkInstaller,
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

    private var pendingDownloadUrl: String? = null
    private var pendingSize: Long = 0

    fun selectThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value =
                when (val status = updateRepository.check()) {
                    is UpdateStatus.UpToDate -> UpdateUiState.UpToDate

                    is UpdateStatus.Failed -> UpdateUiState.Failed(status.reason)

                    is UpdateStatus.Available -> {
                        pendingDownloadUrl = status.downloadUrl
                        pendingSize = status.sizeBytes
                        UpdateUiState.Available(
                            version = status.version,
                            notes = status.notes,
                            sizeBytes = status.sizeBytes,
                        )
                    }
                }
        }
    }

    fun downloadAndInstall() {
        val url = pendingDownloadUrl ?: return
        val version = (_updateState.value as? UpdateUiState.Available)?.version ?: return
        _updateState.value = UpdateUiState.Downloading(version, 0f)
        viewModelScope.launch {
            val result =
                apkInstaller.downloadAndInstall(url, pendingSize) { fraction ->
                    _updateState.update { current ->
                        if (current is UpdateUiState.Downloading) {
                            current.copy(progress = fraction.coerceIn(0f, 1f))
                        } else {
                            current
                        }
                    }
                }
            _updateState.value =
                result.fold(
                    onSuccess = { UpdateUiState.AwaitingInstall },
                    onFailure = { UpdateUiState.Failed(it.message ?: "내려받기에 실패했어요") },
                )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
