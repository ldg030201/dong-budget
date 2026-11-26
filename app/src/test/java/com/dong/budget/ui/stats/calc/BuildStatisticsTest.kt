package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.compareSpending
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.Insight
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
    fun `저축뿐인 달은 빈 달이고 저축은 금액에도 목록에도 없다`() {
        val rows = listOf(
            tx("2026-08-10", 10_000, categoryId = 1),
            tx("2026-09-05", 300_000, categoryId = 7, paymentId = 1, merchant = "적금", categoryCode = SAVINGS_CATEGORY_CODE),
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
        // 저축만 한 날도 돈 안 쓴 날이다
        assertEquals(0, state.daily.records.spentDays)
        assertEquals(27, state.daily.records.noSpendDays)
    }

    @Test
    fun `저축이 섞인 달은 저축을 빼고 센다`() {
        val rows = listOf(
            tx("2026-09-03", 10_000, categoryId = 1, paymentId = 1, merchant = "마트"),
            tx("2026-09-05", 300_000, categoryId = 7, paymentId = 1, merchant = "적금", categoryCode = SAVINGS_CATEGORY_CODE),
        )
        val state = buildStatistics(september, day("2026-09-27"), rows, day("2026-09-03"))
        assertFalse(state.monthIsEmpty)
        assertEquals(Totals(expense = 10_000), state.monthly.totals)
        assertEquals(listOf(10_000L), state.monthly.largest.map { it.amount })
        assertEquals(listOf("마트"), state.merchants.map { it.name })
        assertEquals(listOf(1), state.expenseByPayment.entries.map { it.count })
        assertEquals(1, state.daily.records.spentDays)
        assertTrue(state.daily.days[4].items.isEmpty())
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
        assertEquals(1_000L, state.daily.records.average)
        assertEquals(day("2026-09-03"), state.daily.records.peak?.date)
        assertEquals(9, state.daily.records.noSpendDays)
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
    fun `일별 계열은 분류 탭 도넛과 같은 계열을 분류 순서로 늘어놓는다`() {
        val rows =
            listOf(
                tx("2026-09-03", 10_000, categoryId = 1),
                tx("2026-09-04", 20_000, categoryId = 2),
                tx("2026-09-04", 5_000, categoryId = 1),
            )
        val state = buildStatistics(september, day("2026-09-27"), rows, day("2026-09-03"))
        assertEquals(state.expenseByCategory.series.toSet(), state.daily.series.toSet())
        // 도넛은 금액 순서(분류2 20,000 → 분류1 15,000), 일별은 분류 순서
        assertEquals(listOf("분류2", "분류1"), state.expenseByCategory.series.map { it.name })
        assertEquals(listOf("분류1", "분류2"), state.daily.series.map { it.name })
        assertEquals(25_000L, state.daily.stackMax)
    }

    @Test
    fun `기록 시작일은 보이는 거래보다 늦을 수 없다`() {
        val rows = listOf(tx("2026-09-03", 10_000))
        // 거래는 들어왔는데 시작일 조회가 아직 안 따라온 순간
        val state = buildStatistics(september, day("2026-09-27"), rows, firstRecord = null)
        assertTrue(state.hasAnyRecord)
        assertEquals(day("2026-09-03"), effectiveFirstRecord(rows, firstRecord = null))
        assertEquals(3..27, state.daily.counted)
        // 창 밖에 더 이른 기록이 있으면 조회한 시작일을 쓴다
        assertEquals(day("2025-01-10"), effectiveFirstRecord(rows, day("2025-01-10")))
        // 날짜만으로도 같은 규칙이다(카드실적의 카드를 처음 쓴 날이 쓴다)
        assertEquals(day("2026-09-20"), effectiveFirstDate(null, day("2026-09-20")))
        assertEquals(day("2026-09-20"), effectiveFirstDate(day("2026-10-02"), day("2026-09-20")))
        assertEquals(day("2026-05-01"), effectiveFirstDate(day("2026-05-01"), day("2026-09-20")))
        assertNull(effectiveFirstDate(null, null))
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

    @Test
    fun `지난달이 저축뿐이면 이체와 달리 지난달과 견주지 않는다`() {
        // 저축은 통계 입구에서 걸러져 지난달이 빈 달이다. 홈(comparisonWindow)도 저축뿐인 지난달은 비교하지 않아 같은 답이다.
        val rows = listOf(
            tx("2026-08-05", 300_000, categoryId = 7, categoryCode = SAVINGS_CATEGORY_CODE),
            tx("2026-09-03", 10_000, categoryId = 1),
        )
        // 기록 시작일 조회도 저축을 빼서 9월 3일이다
        val state = buildStatistics(september, day("2026-09-27"), rows, day("2026-09-03"))
        assertNull(state.monthly.comparison)
        val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
        assertNull(compareSpending(september, day("2026-09-27"), byMonth[september].orEmpty(), byMonth[YearMonth.of(2026, 8)].orEmpty()))
    }

    @Test
    fun `저축은 기록 시작일을 당기지 않는다`() {
        // 8월 1일에 적금만 적고 쓴 돈은 8월 20일부터 적었다. 기록 시작일(조회도 저축을 뺀다)은 8월 20일이라
        // 8월을 1일부터 기록한 달로 보지 않는다. 그래야 9월 증감이 기록 전 날을 0원으로 견주지 않고 8월이 평균을 낮추지 않는다.
        val rows = listOf(
            tx("2026-08-01", 300_000, categoryId = 7, categoryCode = SAVINGS_CATEGORY_CODE),
            tx("2026-08-20", 50_000, categoryId = 1),
            tx("2026-09-03", 150_000, categoryId = 1),
        )
        val state = buildStatistics(september, day("2026-09-25"), rows, day("2026-08-20"))
        assertTrue(state.hasAnyRecord)
        assertTrue(state.expenseByCategory.entries.all { it.change == null })
        assertNull(state.monthly.trendAverage)
        // 저축만 적었으면 아직 기록이 없는 것과 같다
        val onlySavings = buildStatistics(september, day("2026-09-25"), rows.take(1), firstRecord = null)
        assertFalse(onlySavings.hasAnyRecord)
    }

    @Test
    fun `지난달을 달 중간부터 기록했으면 분류별 증감을 내지 않고 요약 비교는 홈과 같다`() {
        // 9월 20일에 기록을 시작했다. 지난 쪽 창(9월 1~10일)은 기록 전이라 0원으로 들어가면 모두 '늘었어요' 가 된다.
        val rows = listOf(tx("2026-09-20", 50_000, categoryId = 1), tx("2026-10-03", 150_000, categoryId = 1))
        val october = YearMonth.of(2026, 10)
        val today = day("2026-10-10")
        val state = buildStatistics(october, today, rows, day("2026-09-20"))
        assertTrue(state.expenseByCategory.entries.all { it.change == null })
        assertTrue(state.monthly.insights.none { it is Insight.CategoryChange })
        val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
        assertEquals(compareSpending(october, today, byMonth[october].orEmpty(), byMonth[september].orEmpty()), state.monthly.comparison)
    }
}
