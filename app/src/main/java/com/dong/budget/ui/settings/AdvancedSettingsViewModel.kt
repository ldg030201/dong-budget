package com.dong.budget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.backup.BackupRepository
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 초기화 전에 묻는 창 */
sealed interface ResetPrompt {
    /** 테마와 자동 기능을 처음대로 */
    data object Settings : ResetPrompt

    /** 거래·분류·결제수단·월급 설정·알림 목록을 처음 설치한 상태로. [transactionCount] 건이 지워진다고 알린다. */
    data class Data(val transactionCount: Int) : ResetPrompt
}

/** 고급 설정. 자동 기능 스위치를 켜고 끄고, 설정·데이터를 처음대로 돌린다. 스위치 값은 앱 전체가 따라가는 값(AppContainer.autoSettings)을 받아 그린다. */
class AdvancedSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val paymentCapture: PaymentCapture,
    private val salaryRepository: SalaryRepository,
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
        viewModelScope.launch { _prompt.value = ResetPrompt.Data(backupRepository.transactionCount()) }
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
            try {
                when (prompt) {
                    ResetPrompt.Settings -> {
                        settingsRepository.resetAll()
                        _messages.send(RESET_SETTINGS_DONE)
                    }

                    is ResetPrompt.Data -> {
                        backupRepository.resetToDefaults()
                        // 알림 목록도 비운다. 남겨 두면 지운 거래의 결제가 '등록 안 함' 으로 되살아나 보인다.
                        paymentCapture.clearInbox()
                        // 처음 설치한 상태라 월급 설정도 지운다(월급날 알림도 따라서 떨어진다)
                        salaryRepository.clear()
                        _messages.send(RESET_DATA_DONE)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = if (prompt is ResetPrompt.Data) RESET_DATA_FAILED else RESET_SETTINGS_FAILED
                DevLog.warn(LogTag.BACKUP, failure, e)
                _messages.send(failure)
            } finally {
                _busy.value = false
            }
        }
    }
}
