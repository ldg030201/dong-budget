package com.dong.budget.ui.detail

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionDetailTextTest {
    private val start = samePlaceStart(day("2026-09-28"))

    private fun placeOf(vararg rows: TransactionListItem) = requireNotNull(samePlace(rows.first(), rows.toList(), start))

    @Test
    fun `여러 번이면 모두와 한 번에 평균을 적는다`() {
        val place =
            placeOf(
                tx("2026-09-20", 5_000, merchant = "스타벅스"),
                tx("2026-09-10", 6_000, merchant = "스타벅스"),
                tx("2026-08-10", 7_000, merchant = "스타벅스"),
            )
        assertEquals("최근 1년 · 3번 · 모두 18,000원 · 한 번에 평균 6,000원", samePlaceSummary(place))
    }

    @Test
    fun `한 번이면 금액만 적는다`() {
        val place = placeOf(tx("2026-09-20", 5_800, merchant = "스타벅스"))
        assertEquals("최근 1년 · 1번 · 5,800원", samePlaceSummary(place))
    }

    @Test
    fun `환불만 남으면 돌려받은 금액을 +로 적는다`() {
        val place = placeOf(tx("2026-09-20", 3_000, REFUND, merchant = "쿠팡"))
        assertEquals("최근 1년 · +3,000원", samePlaceSummary(place))
    }

    @Test
    fun `수입은 한 번에 평균 없이 모두 받은 금액을 적는다`() {
        val place =
            placeOf(
                tx("2026-09-25", 3_000_000, INCOME, merchant = "회사"),
                tx("2026-08-25", 3_000_000, INCOME, merchant = "회사"),
            )
        assertEquals("최근 1년 · 2번 · 모두 6,000,000원", samePlaceSummary(place))
    }

    @Test
    fun `1년 안에 내역이 없으면 요약 대신 안내를 쓴다`() {
        val place = placeOf(tx("2024-05-01", 5_000, merchant = "스타벅스"))
        assertNull(samePlaceSummary(place))
        assertEquals("최근 1년 동안 같은 곳에서 쓴 내역이 없어요", samePlaceEmptyText(place))
    }
}
