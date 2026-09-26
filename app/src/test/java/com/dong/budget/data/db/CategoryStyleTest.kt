package com.dong.budget.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryStyleTest {
    @Test
    fun `새로 만드는 색은 안 쓴 색 중 첫 번째이고 회색은 고르지 않는다`() {
        // 기본 결제수단(현금 초록, 체크카드 파랑, 신용카드 보라, 계좌이체 청록)만 있으면 빨강이 된다
        assertEquals("red", CategoryStyle.firstUnusedColor(setOf("green", "blue", "purple", "teal")))
        assertEquals("orange", CategoryStyle.firstUnusedColor(setOf("red")))
    }

    @Test
    fun `다 쓰였으면 회색이다`() {
        assertEquals(CategoryStyle.FALLBACK_COLOR, CategoryStyle.firstUnusedColor(CategoryStyle.COLORS.toSet()))
    }
}
