package com.dong.budget.ui.stats.calc

import com.dong.budget.testing.day
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class StatsPeriodTest {
    private val today = day("2026-09-27")

    @Test
    fun `고른 달이 오늘에 견줘 지나갔는지 이번 달인지 오지 않았는지`() {
        assertEquals(Period.PAST, periodOf(YearMonth.of(2026, 8), today))
        assertEquals(Period.PAST, periodOf(YearMonth.of(2025, 12), today))
        assertEquals(Period.CURRENT, periodOf(YearMonth.of(2026, 9), today))
        assertEquals(Period.FUTURE, periodOf(YearMonth.of(2026, 10), today))
    }

    @Test
    fun `지나간 달은 말일까지, 이번 달은 오늘까지 센다`() {
        assertEquals(1..31, countedDays(YearMonth.of(2026, 8), today, day("2026-07-01")))
        assertEquals(1..27, countedDays(YearMonth.of(2026, 9), today, day("2026-08-01")))
    }

    @Test
    fun `아직 오지 않은 달은 셀 날이 없다`() {
        assertTrue(countedDays(YearMonth.of(2026, 10), today, day("2026-01-01")).isEmpty())
    }

    @Test
    fun `기록을 달 중간에 시작했으면 그날부터 센다`() {
        assertEquals(12..27, countedDays(YearMonth.of(2026, 9), today, day("2026-09-12")))
        // 시작일 당일도 센다
        assertEquals(27..27, countedDays(YearMonth.of(2026, 9), today, day("2026-09-27")))
    }

    @Test
    fun `기록을 다음 달에 시작했거나 기록이 없으면 셀 날이 없다`() {
        assertTrue(countedDays(YearMonth.of(2026, 8), today, day("2026-09-05")).isEmpty())
        assertTrue(countedDays(YearMonth.of(2026, 9), today, null).isEmpty())
    }

    @Test
    fun `윤년 2월은 29일까지 센다`() {
        assertEquals(1..29, countedDays(YearMonth.of(2028, 2), day("2028-03-10"), day("2028-01-01")))
        assertEquals(1..28, countedDays(YearMonth.of(2026, 2), today, day("2026-01-01")))
    }

    @Test
    fun `반올림은 0 에서 먼 쪽으로 올린다`() {
        assertEquals(3L, divRound(5, 2))
        assertEquals(2L, divRound(4, 2))
        assertEquals(2L, divRound(7, 3))
        assertEquals(1L, divRound(2, 3))
        assertEquals(0L, divRound(1, 3))
        assertEquals(0L, divRound(0, 5))
        assertEquals(-3L, divRound(-5, 2))
        assertEquals(-2L, divRound(-7, 3))
    }

    @Test
    fun `수입 대비 지출은 수입이 있을 때만 내고 100 을 넘을 수 있다`() {
        assertEquals(56, spendRatioPercent(Totals(expense = 560_000, income = 1_000_000)))
        assertEquals(150, spendRatioPercent(Totals(expense = 1_500_000, income = 1_000_000)))
        assertEquals(33, spendRatioPercent(Totals(expense = 1, income = 3)))
        assertEquals(67, spendRatioPercent(Totals(expense = 2, income = 3)))
        assertNull(spendRatioPercent(Totals(expense = 10_000, income = 0)))
    }

    @Test
    fun `환불이 더 많아 지출이 음수면 수입 대비 0% 다`() {
        assertEquals(0, spendRatioPercent(Totals(expense = -12_000, income = 100_000)))
    }
}
