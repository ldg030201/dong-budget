package com.dong.budget.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 등록창·월급 설정·카드 실적 편집이 함께 쓰는 금액 키패드 규칙. 전에는 세 곳에 따로 있었다.
class AmountDigitsTest {
    @Test
    fun `금액 키패드는 앞의 0 을 떼고 12자리까지만 받는다`() {
        assertEquals(3L, appendAmountDigit(0, "3"))
        assertEquals(300L, appendAmountDigit(3, "00"))
        assertEquals(0L, appendAmountDigit(0, "0"))
        assertEquals(0L, appendAmountDigit(0, "00"))
        assertEquals(999_999_999_999L, appendAmountDigit(99_999_999_999, "9"))
        assertNull(appendAmountDigit(999_999_999_999, "9"))
        // '00' 은 한 자리만 들어갈 자리에서는 통째로 받지 않는다
        assertNull(appendAmountDigit(99_999_999_999, "00"))
    }

    @Test
    fun `지우기는 맨 끝 자리 하나를 뗀다`() {
        assertEquals(1_234L, deleteAmountDigit(12_345))
        assertEquals(0L, deleteAmountDigit(7))
        assertEquals(0L, deleteAmountDigit(0))
    }
}
