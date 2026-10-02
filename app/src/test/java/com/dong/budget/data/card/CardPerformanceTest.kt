package com.dong.budget.data.card

import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPerformanceTest {
    @Test
    fun `저장된 구간 글을 오름차순 금액 목록으로 읽는다`() {
        assertEquals(listOf(300_000L, 700_000L), parsePerformanceTiers("300000,700000"))
        // 순서가 섞이거나 겹치거나 빈칸이 있어도 정리해서 읽는다
        assertEquals(listOf(300_000L, 700_000L), parsePerformanceTiers(" 700000, 300000 ,300000"))
        assertEquals(emptyList<Long>(), parsePerformanceTiers(null))
        assertEquals(emptyList<Long>(), parsePerformanceTiers(""))
    }

    @Test
    fun `망가진 조각과 범위 밖 금액은 버린다`() {
        assertEquals(listOf(500L), parsePerformanceTiers("abc,,0,-5,1000000000000,500,1.5"))
        // 12자리 끝까지는 받는다
        assertEquals(listOf(999_999_999_999L), parsePerformanceTiers("999999999999"))
        assertEquals(999_999_999_999L, MAX_PERFORMANCE_AMOUNT)
    }

    @Test
    fun `구간은 작은 것부터 다섯 개까지만 둔다`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), parsePerformanceTiers("6,5,4,3,2,1"))
        assertEquals(MAX_PERFORMANCE_TIERS, normalizePerformanceTiers((1L..9L).toList()).size)
    }

    @Test
    fun `구간 목록을 정리해서 저장할 글로 만들고, 남는 게 없으면 실적을 안 적은 것이다`() {
        assertEquals("300000,700000", encodePerformanceTiers(listOf(700_000, 300_000, 300_000)))
        assertNull(encodePerformanceTiers(emptyList()))
        assertNull(encodePerformanceTiers(listOf(0, -1)))
        // 쓴 글을 다시 읽으면 같은 목록이다
        val tiers = listOf(300_000L, 500_000L, 1_000_000L)
        assertEquals(tiers, parsePerformanceTiers(encodePerformanceTiers(tiers)))
    }

    @Test
    fun `실적 시작일은 1일부터 31일까지로 맞춘다`() {
        assertEquals(1, normalizePerformanceStartDay(0))
        assertEquals(1, normalizePerformanceStartDay(-3))
        assertEquals(15, normalizePerformanceStartDay(15))
        assertEquals(31, normalizePerformanceStartDay(31))
        assertEquals(MAX_PERFORMANCE_START_DAY, normalizePerformanceStartDay(40))
    }

    @Test
    fun `결제수단의 실적 구간을 바로 읽는다`() {
        val card = PaymentMethodEntity(id = 1, uuid = "u1", name = "하나카드", type = PaymentMethodType.OTHER)
        // 새로 만든 결제수단은 실적이 없고 1일부터 센다
        assertEquals(emptyList<Long>(), card.performanceTierList)
        assertEquals(1, card.performanceStartDay)
        assertEquals(listOf(300_000L, 700_000L), card.copy(performanceTiers = "700000,300000").performanceTierList)
    }
}
