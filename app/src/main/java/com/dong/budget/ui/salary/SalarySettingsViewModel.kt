package com.dong.budget.ui.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.launchWrite
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.ui.components.appendAmountDigit
import com.dong.budget.ui.components.deleteAmountDigit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 월급 설정. 바꾸는 대로 바로 저장한다(따로 저장 버튼이 없다).
 * 화면이 그리는 값은 여기 있는 것이 기준이다. 저장소에서 다시 읽은 값을 기다리면 키패드를 빨리 누를 때 숫자가 빠진다.
 * 저장은 누른 차례대로 하고, 화면을 떠나도 끝까지 한다(마지막으로 누른 숫자가 빠지지 않게, [launchWrite]).
 */
class SalarySettingsViewModel(
    private val repository: SalaryRepository,
    /** 월급 설정과 월급날 알림 기록·떠 있는 알림을 지운다(AppContainer.clearSalary) */
    private val clearSalary: suspend () -> Unit = { repository.clear() },
) : ViewModel() {
    /** 지금 설정. 저장소를 다 읽기 전에는 null 이다. */
    private val _settings = MutableStateFlow<SalarySettings?>(null)
    val settings: StateFlow<SalarySettings?> = _settings.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** 한 번 띄우고 마는 알림(토스트). 저장 공간이 모자라 월급 설정을 저장하지 못했을 때 알린다. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** 저장하지 못하면 앱을 죽이지 않고 로그에 남긴 뒤 알린다. 화면에는 고친 값이 남지만 다음에 열면 저장된 값으로 돌아간다. */
    private fun saveFailed(e: IOException) {
        DevLog.warn(LogTag.SALARY, SALARY_SAVE_FAILED_MESSAGE, e)
        _messages.trySend(SALARY_SAVE_FAILED_MESSAGE)
    }

    /** 화면을 열 때의 설정. 닫을 때 바뀌었으면 로그에 한 번 남긴다. */
    private var opened: SalarySettings? = null

    init {
        viewModelScope.launch {
            val saved = repository.settings.first()
            opened = saved
            if (_settings.value == null) _settings.value = saved
        }
    }

    fun update(next: SalarySettings) {
        if (_settings.value == null) return
        _settings.value = next
        viewModelScope.launchWrite(::saveFailed) { repository.save(next) }
    }

    /** 금액 키패드. 앞의 0 은 떼고, 12자리를 넘으면 받지 않는다. [takeHome] 이면 실수령, 아니면 세전 금액을 고친다. */
    fun appendDigit(takeHome: Boolean, digit: String) {
        val current = _settings.value ?: return
        val next = appendAmountDigit(if (takeHome) current.takeHome else current.amount, digit) ?: return
        setAmount(current, takeHome, next)
    }

    fun deleteDigit(takeHome: Boolean) {
        val current = _settings.value ?: return
        setAmount(current, takeHome, deleteAmountDigit(if (takeHome) current.takeHome else current.amount))
    }

    fun clearAmount(takeHome: Boolean) {
        val current = _settings.value ?: return
        setAmount(current, takeHome, 0)
    }

    private fun setAmount(current: SalarySettings, takeHome: Boolean, value: Long) {
        update(if (takeHome) current.copy(takeHome = value) else current.copy(amount = value))
    }

    /**
     * 월급 설정만 처음대로 돌린다. 가계부 거래는 그대로다. 화면은 그대로 두고 기본값을 보여 줘서 바로 다시 적게 한다.
     * 다 지운 뒤에 화면을 떠나도 끝까지 한다(설정만 지우고 알림 기록이 남지 않게).
     */
    fun reset() {
        if (_settings.value == null) return
        _settings.value = SalarySettings()
        // 고친 값 저장과 같은 차례에 둔다(초기화 뒤에 누른 값이 먼저 쓰였다가 지워지지 않게)
        viewModelScope.launchWrite(::saveFailed) {
            clearSalary()
            DevLog.info(LogTag.SALARY, "월급 설정을 처음대로 돌렸어요")
        }
    }

    override fun onCleared() {
        val last = _settings.value
        if (last != null && last != opened) DevLog.info(LogTag.SALARY, "월급 설정을 바꿨어요 · ${repository.describe(last)}")
    }
}

/** 월급 설정을 파일에 쓰지 못했을 때 */
internal const val SALARY_SAVE_FAILED_MESSAGE = "월급 설정을 저장하지 못했어요"
