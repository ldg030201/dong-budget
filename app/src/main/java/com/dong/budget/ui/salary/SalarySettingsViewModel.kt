package com.dong.budget.ui.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.salary.SalarySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 월급 설정. 바꾸는 대로 바로 저장한다(따로 저장 버튼이 없다).
 * 화면이 그리는 값은 여기 있는 것이 기준이다. 저장소에서 다시 읽은 값을 기다리면 키패드를 빨리 누를 때 숫자가 빠진다.
 */
class SalarySettingsViewModel(private val repository: SalaryRepository) : ViewModel() {
    /** 지금 설정. 저장소를 다 읽기 전에는 null 이다. */
    private val _settings = MutableStateFlow<SalarySettings?>(null)
    val settings: StateFlow<SalarySettings?> = _settings.asStateFlow()

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
        // 저장은 부른 순서대로 끝난다(DataStore 가 쓰기를 차례로 처리한다)
        viewModelScope.launch { repository.save(next) }
    }

    /** 금액 키패드. 앞의 0 은 떼고, 12자리를 넘으면 받지 않는다. [takeHome] 이면 실수령, 아니면 세전 금액을 고친다. */
    fun appendDigit(takeHome: Boolean, digit: String) {
        val current = _settings.value ?: return
        val now = if (takeHome) current.takeHome else current.amount
        val digits = (now.takeIf { it > 0 }?.toString().orEmpty() + digit).trimStart('0')
        if (digits.length > MAX_AMOUNT_DIGITS) return
        setAmount(current, takeHome, digits.toLongOrNull() ?: 0)
    }

    fun deleteDigit(takeHome: Boolean) {
        val current = _settings.value ?: return
        setAmount(current, takeHome, (if (takeHome) current.takeHome else current.amount) / DECIMAL)
    }

    fun clearAmount(takeHome: Boolean) {
        val current = _settings.value ?: return
        setAmount(current, takeHome, 0)
    }

    private fun setAmount(current: SalarySettings, takeHome: Boolean, value: Long) {
        update(if (takeHome) current.copy(takeHome = value) else current.copy(amount = value))
    }

    override fun onCleared() {
        val last = _settings.value
        if (last != null && last != opened) DevLog.info(LogTag.SALARY, "월급 설정을 바꿨어요 · ${repository.describe(last)}")
    }

    private companion object {
        const val DECIMAL = 10L
    }
}
