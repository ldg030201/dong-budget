package com.dong.budget.ui.card

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Test

class CardPerformanceViewModelTest {
    @Test
    fun `그리는 화면이 없어도 화면 모델이 사는 동안 새 값을 받는다`() {
        // 전에는 그리는 동안만 구독해서(5초 유예) 편집 화면에 오래 있다 돌아오면 옛 시작일로 센 기간을 먼저 그렸다
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val startDay = MutableStateFlow(1)
        val state = startDay.map { "매달 ${it}일" }.stateWhileAlive(scope, "읽는 중")
        assertEquals("매달 1일", state.value)
        startDay.value = 15
        assertEquals("매달 15일", state.value)
        scope.cancel()
    }
}
