package com.dong.budget.data

import com.dong.budget.data.card.normalizePerformanceTiers
import com.dong.budget.ui.components.appendAmountDigit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 금액 상한은 MAX_AMOUNT 하나다. 전에는 백업 검사·카드 실적·미리 채우기마다 같은 식의 복사본이 있었다.
class AmountLimitTest {
    @Test
    fun `금액 상한은 등록 화면 자릿수를 9로 채운 값이고, 키패드가 받는 가장 큰 금액과 같다`() {
        assertEquals("9".repeat(MAX_AMOUNT_DIGITS).toLong(), MAX_AMOUNT)
        assertEquals(MAX_AMOUNT, appendAmountDigit(MAX_AMOUNT / 10, "9"))
        assertNull(appendAmountDigit(MAX_AMOUNT, "0"))
    }

    @Test
    fun `카드 실적 구간은 상한까지 받고 그 위는 버린다`() {
        assertEquals(listOf(MAX_AMOUNT), normalizePerformanceTiers(listOf(MAX_AMOUNT + 1, MAX_AMOUNT)))
    }
}
