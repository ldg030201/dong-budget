package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

class BuildStatisticsTest {
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `거래가 하나도 없으면 기록이 없는 빈 달이다`() {
        val state = buildStatistics(september, day("2026-09-27"), emptyList(), firstRecord = null)
        assertTrue(state.loaded)
        assertFalse(state.hasAnyRecord)
        assertTrue(state.monthIsEmpty)
        assertEquals(Period.CURRENT, state.period)
        assertEquals(30, state.daily.days.size)
    }

    @Test
    fun `이체뿐인 달은 빈 달이고 이체는 목록 어디에도 없다`() {
        val rows = listOf(
            tx("2026-08-10", 10_000, categoryId = 1),
            tx("2026-09-05", 70_000, TRANSFER, categoryId = 1, paymentId = 1, merchant = "토스"),
        )
        val state = buildStatistics(september, day("2026-09-27"), rows, day("2026-08-10"))
        assertTrue(state.monthIsEmpty)
        assertEquals(Totals(), state.monthly.totals)
        assertTrue(state.monthly.largest.isEmpty())
        assertTrue(state.monthly.insights.isEmpty())
        assertTrue(state.merchants.isEmpty())
        assertTrue(state.expenseByCategory.entries.isEmpty())
        assertTrue(state.expenseByPayment.entries.isEmpty())
        assertTrue(state.daily.days.all { it.items.isEmpty() })
    }

    @Test
    fun `미래 날짜 거래는 합계와 막대에는 들어가고 평균과 누적과 요일에서는 빠진다`() {
        val rows = listOf(
            tx("2026-08-01", 100_000, categoryId = 1),
            tx("2026-09-03", 10_000, categoryId = 1),
            tx("2026-09-25", 500_000, categoryId = 2),
        )
        val state = buildStatistics(september, day("2026-09-10"), rows, day("2026-08-01"))
        assertEquals(510_000L, state.monthly.totals.expense)
        assertEquals(1, state.monthly.futureCount)
        assertEquals(510_000L, state.expenseByCategory.total)
        assertEquals(500_000L, state.daily.days[24].expense)
        assertEquals(500_000L, state.daily.stackMax)
        assertEquals(1_000L, state.daily.average)
        assertEquals(day("2026-09-03"), state.daily.peak?.date)
        assertEquals(9, state.daily.noSpendDays)
        assertEquals(10_000L, state.monthly.flow!!.thisMonth.last())
        assertEquals(10, state.monthly.flow!!.thisMonth.size)
        // 요일 창은 8월 1일~9월 10일이라 25일(금) 거래가 없다
        val weekday = state.daily.weekday!!
        assertEquals(day("2026-09-10"), weekday.to)
        assertEquals(0L, weekday.averages.first { it.day == DayOfWeek.FRIDAY }.average)
        // 토요일 6번(8월 1일 100,000원)
        assertEquals(16_667L, weekday.averages.first { it.day == DayOfWeek.SATURDAY }.average)
    }

    @Test
    fun `일별 계열은 분류 탭 도넛과 같은 계열이다`() {
        val rows =
            listOf(
                tx("2026-09-03", 10_000, categoryId = 1),
                tx("2026-09-04", 20_000, categoryId = 2),
                tx("2026-09-04", 5_000, categoryId = 1),
            )
        val state = buildStatistics(september, day("2026-09-27"), rows, day("2026-09-03"))
        assertEquals(state.expenseByCategory.series, state.daily.series)
        // 계열 순서: 분류2(20,000), 분류1(15,000)
        assertEquals(listOf(20_000L, 10_000L), state.daily.seriesMax)
        assertEquals(25_000L, state.daily.stackMax)
    }

    @Test
    fun `기록 시작일은 보이는 거래보다 늦을 수 없다`() {
        val rows = listOf(tx("2026-09-03", 10_000))
        // 거래는 들어왔는데 시작일 조회가 아직 안 따라온 순간
        val state = buildStatistics(september, day("2026-09-27"), rows, firstRecord = null)
        assertTrue(state.hasAnyRecord)
        assertEquals(day("2026-09-03"), state.firstRecord)
        assertEquals(3..27, state.daily.counted)
        assertTrue(state.daily.startsLate)
        // 창 밖에 더 이른 기록이 있으면 조회한 시작일을 쓴다
        assertEquals(day("2025-01-10"), buildStatistics(september, day("2026-09-27"), rows, day("2025-01-10")).firstRecord)
    }

    @Test
    fun `지나간 달은 가장 많이 쓴 날을 처음 고르고 월별 숫자를 모두 채운다`() {
        val rows = listOf(
            tx("2026-07-01", 200_000, categoryId = 1),
            tx("2026-07-02", 3_000_000, INCOME, categoryId = 9),
            tx("2026-08-05", 30_000, categoryId = 1, merchant = "마트"),
            tx("2026-08-20", 90_000, categoryId = 2, merchant = "주유소"),
            tx("2026-08-21", 2_500_000, INCOME, categoryId = 9),
        )
        val august = YearMonth.of(2026, 8)
        val state = buildStatistics(august, day("2026-09-27"), rows, day("2026-07-01"))
        assertEquals(Period.PAST, state.period)
        assertEquals(20, state.daily.defaultDay)
        assertEquals(1..31, state.daily.counted)
        assertEquals(5, spendRatioPercent(state.monthly.totals))
        assertEquals(5, state.monthly.spendRatioPercent)
        assertNotNull(state.monthly.comparison)
        assertNull(state.monthly.pace)
        assertEquals(31, state.monthly.flow!!.thisMonth.size)
        assertEquals(listOf("주유소", "마트"), state.merchants.map { it.name })
        assertEquals(listOf(90_000L, 30_000L), state.monthly.largest.map { it.amount })
        assertEquals(2_500_000L, state.incomeByCategory.total)
        assertEquals(YearMonth.of(2026, 7), state.monthly.yearToDate?.startMonth)
        assertEquals(6, state.monthly.trend.size)
        assertEquals(august, state.monthly.trend.last().month)
    }

    @Test
    fun `지난달이 이체뿐이어도 홈처럼 지난달과 견준다`() {
        // 홈은 그 달 거래 전부(이체 포함)를 넘겨 비교 여부를 정한다. 통계도 같은 문장을 보여야 한다.
        val rows = listOf(tx("2026-08-05", 1_000, TRANSFER), tx("2026-09-03", 10_000, categoryId = 1))
        val state = buildStatistics(september, day("2026-09-27"), rows, day("2026-09-03"))
        assertEquals(10_000L, state.monthly.comparison?.difference)
        // 누적과 속도는 지난달 기록(이체 말고)이 없어서 지난달 쪽이 없다
        assertNull(state.monthly.flow!!.previous)
        assertNull(state.monthly.pace)
    }
}
