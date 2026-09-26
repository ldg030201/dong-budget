package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.tx
import com.dong.budget.ui.stats.MerchantStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantsTest {
    @Test
    fun `가게 열쇠는 공백을 모두 빼고 소문자로 만든다`() {
        assertEquals("스타벅스", merchantKey(" 스타 벅스 "))
        assertEquals(merchantKey("GS25"), merchantKey("gs25"))
        assertNull(merchantKey(null))
        assertNull(merchantKey("   "))
    }

    @Test
    fun `공백과 대소문자만 다른 가게는 한 곳으로 묶고 이름은 가장 최근 원문이다`() {
        val result = topMerchants(
            listOf(
                tx("2026-09-01", 5_000, merchant = "스타벅스"),
                tx("2026-09-03", 6_000, merchant = "스타 벅스 "),
                tx("2026-09-02", 1_000, merchant = "스타벅스 강남점"),
            ),
        )
        assertEquals(listOf(MerchantStat("스타 벅스", 11_000, 2, 5_500), MerchantStat("스타벅스 강남점", 1_000, 1, 1_000)), result)
    }

    @Test
    fun `환불은 그 가게에서 빼고 한 번에 평균은 환불 전 금액이다`() {
        val result = topMerchants(
            listOf(
                tx("2026-09-01", 10_000, merchant = "쿠팡"),
                tx("2026-09-02", 20_000, merchant = "쿠팡"),
                tx("2026-09-05", 12_000, REFUND, merchant = "쿠팡"),
            ),
        ).single()
        assertEquals(18_000L, result.amount)
        assertEquals(2, result.count)
        assertEquals(15_000L, result.averageTicket)
    }

    @Test
    fun `이름 없는 거래, 수입, 이체, 환불이 더 많은 가게는 빠진다`() {
        val result = topMerchants(
            listOf(
                tx("2026-09-01", 10_000),
                tx("2026-09-01", 10_000, merchant = " "),
                tx("2026-09-01", 900_000, INCOME, merchant = "회사"),
                tx("2026-09-01", 50_000, TRANSFER, merchant = "토스"),
                tx("2026-09-01", 3_000, merchant = "무신사"),
                tx("2026-09-02", 5_000, REFUND, merchant = "무신사"),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `많이 쓴 순서로 10곳까지고 같은 금액이면 이름순이다`() {
        val rows = (1..12).map { tx("2026-09-01", it * 1_000L, merchant = "가게%02d".format(it)) } +
            tx("2026-09-01", 12_000, merchant = "가게00")
        val result = topMerchants(rows)
        assertEquals(10, result.size)
        assertEquals(listOf("가게00", "가게12", "가게11"), result.take(3).map { it.name })
        assertEquals("가게04", result.last().name)
    }
}
