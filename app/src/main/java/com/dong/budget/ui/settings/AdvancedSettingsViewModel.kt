package com.dong.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.backup.BackupExporter
import com.dong.budget.data.backup.BackupRepository
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 초기화 전에 묻는 창 */
sealed interface ResetPrompt {
    /** 테마와 자동 기능을 처음대로 */
    data object Settings : ResetPrompt

    /**
     * 거래·분류·결제수단·월급 설정·알림 목록을 처음 설치한 상태로. [transactionCount] 건이 지워진다고 알린다.
     * @property autoBackup 지우기 전에 지금 데이터를 파일로 저장하는지(자동 백업 스위치가 켜져 있고 지울 것이 있을 때)
     */
    data class Data(val transactionCount: Int, val autoBackup: Boolean = false) : ResetPrompt
}

/** 고급 설정. 자동 기능 스위치를 켜고 끄고, 설정·데이터를 처음대로 돌린다. 스위치 값은 앱 전체가 따라가는 값(AppContainer.autoSettings)을 받아 그린다. */
class AdvancedSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val paymentCapture: PaymentCapture,
    /** 데이터 초기화 전 자동 백업 */
    private val backupExporter: BackupExporter,
    /** 월급 설정과 월급날 알림 기록·떠 있는 알림을 지운다(AppContainer.clearSalary) */
    private val clearSalary: suspend () -> Unit = {},
) : ViewModel() {
    private val _prompt = MutableStateFlow<ResetPrompt?>(null)
    val prompt: StateFlow<ResetPrompt?> = _prompt.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** 한 번 띄우고 마는 알림(토스트). 초기화 결과를 알린다. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** 지우는 중. 두 번 눌러 두 번 지우지 않게 줄을 막는다. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setAuto(option: AutoOption, on: Boolean) {
        viewModelScope.launch { settingsRepository.setAuto(option, on) }
    }

    fun askResetSettings() {
        _prompt.value = ResetPrompt.Settings
    }

    /** 몇 건이 지워지는지 세어 본 뒤 묻는다 */
    fun askResetData() {
        if (_busy.value) return
        viewModelScope.launch {
            val count = backupRepository.transactionCount()
            _prompt.value = ResetPrompt.Data(count, shouldBackupBeforeReplace(settingsRepository, backupExporter, count))
        }
    }

    fun dismissReset() {
        _prompt.value = null
    }

    fun confirmReset() {
        val prompt = _prompt.value ?: return
        _prompt.value = null
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            // 거래를 다 지운 뒤에 멈추면 알림 목록·월급 설정만 남는다. 화면을 떠나 뷰모델이 치워져도 끝까지 한다.
            var dataCleared = false
            try {
                val done =
                    withContext(NonCancellable) {
                        when (prompt) {
                            ResetPrompt.Settings -> {
                                settingsRepository.resetAll()
                                RESET_SETTINGS_DONE
                            }

                            // 지우기 전에 지금 데이터를 파일로 남긴다. 남기지 못하면 지우지 않는다.
                            is ResetPrompt.Data ->
                                backupExporter.replaceWithBackup(prompt.autoBackup, AutoBackupReason.RESET, AUTO_BACKUP_FAILED_RESET) {
                                    backupRepository.resetToDefaults()
                                    dataCleared = true
                                    // 알림 목록도 비운다. 남겨 두면 지운 거래의 결제가 '등록 안 함' 으로 되살아나 보인다.
                                    paymentCapture.clearInbox()
                                    // 처음 설치한 상태라 월급 설정도 지운다(월급날 알림 기록과 떠 있는 알림도)
                                    clearSalary()
                                    RESET_DATA_DONE
                                }
                        }
                    }
                _messages.send(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure =
                    when {
                        prompt !is ResetPrompt.Data -> RESET_SETTINGS_FAILED

                        // 거래는 이미 지웠다. '그대로예요' 라고 하면 틀린 말이다.
                        dataCleared -> RESET_DATA_PARTLY_FAILED

                        else -> RESET_DATA_FAILED
                    }
                DevLog.warn(LogTag.BACKUP, failure, e)
                _messages.send(failure)
            } finally {
                _busy.value = false
            }
        }
    }
}
