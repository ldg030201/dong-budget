package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.Pace
import com.dong.budget.ui.stats.TrendAverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthStatsTest {
    private val september = YearMonth.of(2026, 9)

    private fun byMonth(vararg rows: TransactionListItem) = rows.groupBy { YearMonth.from(it.localDate()) }

    // ── 속도 ────────────────────────────────────────────────────────────

    private val lastMonth = listOf(tx("2026-08-05", 300_000))

    @Test
    fun `지난달보다 덜 썼으면 남은 날(오늘 포함) 하루에 쓸 수 있는 돈을 낸다`() {
        // 9월은 30일. 10일이면 오늘을 넣어 21일이 남았다
        val pace = pace(september, day("2026-09-10"), listOf(tx("2026-09-03", 90_000)), lastMonth, firstRecord = null)
        assertEquals(Pace(remaining = 210_000, scheduled = 0, daysLeft = 21, dailyAllowance = 10_000), pace)
    }

    @Test
    fun `하루 허용액은 반올림한다`() {
        val pace = pace(september, day("2026-09-10"), listOf(tx("2026-09-03", 200_000)), lastMonth, firstRecord = null)
        // 100,000 ÷ 21 = 4,761.9…
        assertEquals(4_762L, pace?.dailyAllowance)
    }

    @Test
    fun `마지막 날에는 남은 날이 오늘 하루다`() {
        assertEquals(1, pace(september, day("2026-09-30"), emptyList(), lastMonth, firstRecord = null)?.daysLeft)
    }

    @Test
    fun `지난달만큼 썼거나 더 썼으면 허용액이 없다`() {
        assertEquals(
            Pace(0, 0, 21, null),
            pace(september, day("2026-09-10"), listOf(tx("2026-09-03", 300_000)), lastMonth, firstRecord = null),
        )
        assertEquals(
            Pace(-50_000, 0, 21, null),
            pace(september, day("2026-09-10"), listOf(tx("2026-09-03", 350_000)), lastMonth, firstRecord = null),
        )
    }

    @Test
    fun `오늘 뒤 날짜로 미리 적은 지출은 남은 날 안에 나갈 돈이라 쓸 수 있는 돈에서 뺀다`() {
        val current = listOf(tx("2026-09-03", 90_000), tx("2026-09-20", 1_000_000))
        assertEquals(
            Pace(remaining = -790_000, scheduled = 1_000_000, daysLeft = 21, dailyAllowance = null),
            pace(september, day("2026-09-10"), current, lastMonth, firstRecord = null),
        )
        // 작은 예정 지출은 허용액만 줄인다: (300,000 − 90,000 − 21,000) ÷ 21 = 9,000
        val small = listOf(tx("2026-09-03", 90_000), tx("2026-09-20", 21_000))
        assertEquals(9_000L, pace(september, day("2026-09-10"), small, lastMonth, firstRecord = null)?.dailyAllowance)
    }

    @Test
    fun `지난달이 기록을 달 중간에 시작한 달이면 속도를 내지 않는다`() {
        assertNull(pace(september, day("2026-09-10"), emptyList(), lastMonth, firstRecord = day("2026-08-05")))
    }

    @Test
    fun `지난달 1일에 기록을 시작했으면 속도를 낸다`() {
        val pace = pace(september, day("2026-09-10"), emptyList(), listOf(tx("2026-08-01", 300_000)), firstRecord = day("2026-08-01"))
        assertEquals(21, pace?.daysLeft)
    }

    @Test
    fun `지난달 지출이 0 이하이거나 기록이 없으면 속도를 내지 않는다`() {
        val today = day("2026-09-10")
        assertNull(pace(september, today, emptyList(), emptyList(), firstRecord = null))
        assertNull(pace(september, today, emptyList(), listOf(tx("2026-08-05", 1_000_000, INCOME)), firstRecord = null))
        assertNull(pace(september, today, emptyList(), listOf(tx("2026-08-05", 5_000, REFUND)), firstRecord = null))
        assertNull(pace(september, today, emptyList(), listOf(tx("2026-08-05", 5_000, TRANSFER)), firstRecord = null))
    }

    @Test
    fun `이번 달이 아니면 속도를 내지 않는다`() {
        assertNull(pace(YearMonth.of(2026, 8), day("2026-09-10"), emptyList(), lastMonth, firstRecord = null))
    }

    // ── 누적 흐름 ───────────────────────────────────────────────────────

    @Test
    fun `이번 달 누적은 오늘까지이고 환불을 빼며 미래 날짜 거래는 넣지 않는다`() {
        val current = listOf(
            tx("2026-09-01", 10_000),
            tx("2026-09-03", 5_000),
            tx("2026-09-03", 2_000, REFUND),
            tx("2026-09-04", 50_000, INCOME),
            tx("2026-09-20", 999_000),
        )
        val flow = cumulativeFlow(september, day("2026-09-05"), current, lastMonth)!!
        assertEquals(listOf(10_000L, 10_000L, 13_000L, 13_000L, 13_000L), flow.thisMonth)
        // 지난달은 말일(31일)까지
        assertEquals(31, flow.previous!!.size)
        assertEquals(300_000L, flow.previous!!.last())
        assertEquals(0L, flow.previous!![3])
    }

    @Test
    fun `지나간 달 누적은 말일까지다`() {
        val flow = cumulativeFlow(YearMonth.of(2026, 2), day("2026-09-05"), listOf(tx("2026-02-28", 1_000)), emptyList())!!
        assertEquals(28, flow.thisMonth.size)
        assertEquals(1_000L, flow.thisMonth.last())
    }

    @Test
    fun `지난달 기록이 이체뿐이면 지난달 선이 없다`() {
        val flow = cumulativeFlow(september, day("2026-09-05"), emptyList(), listOf(tx("2026-08-05", 1_000, TRANSFER)))!!
        assertNull(flow.previous)
    }

    @Test
    fun `아직 오지 않은 달은 누적 흐름이 없다`() {
        assertNull(cumulativeFlow(YearMonth.of(2026, 10), day("2026-09-05"), listOf(tx("2026-10-01", 1_000)), emptyList()))
    }

    // ── 최근 6개월 ──────────────────────────────────────────────────────

    @Test
    fun `최근 6개월은 해를 넘어 이어지고 오래된 달이 앞이다`() {
        val trend = monthTrend(
            YearMonth.of(2027, 3),
            byMonth(tx("2026-12-05", 10_000), tx("2027-01-10", 20_000, INCOME), tx("2027-02-01", 3_000, TRANSFER)),
            firstRecord = day("2026-12-05"),
        )
        assertEquals((10..12).map { YearMonth.of(2026, it) } + (1..3).map { YearMonth.of(2027, it) }, trend.map { it.month })
        assertEquals(listOf(true, true, false, false, false, false), trend.map { it.beforeFirstRecord })
        // 이체뿐인 달은 기록이 없는 달이다
        assertEquals(listOf(false, false, true, true, false, false), trend.map { it.hasRecord })
        assertEquals(Totals(expense = 10_000), trend[2].totals)
        assertEquals(Totals(income = 20_000), trend[3].totals)
    }

    @Test
    fun `앞선 달 평균에서 고른 달, 달 중간에 시작한 달, 빈 달은 빠진다`() {
        val rows = byMonth(
            tx("2026-05-20", 999_999), // 5월 15일에 시작해서 5월은 덜 센 달이다
            tx("2026-06-10", 100_000),
            tx("2026-06-25", 1_000_000, INCOME),
            tx("2026-08-03", 300_000), // 7월은 비었다
            tx("2026-09-05", 5_000_000), // 고른 달
        )
        val first = day("2026-05-15")
        val trend = monthTrend(september, rows, first)
        assertEquals(
            TrendAverage(months = 2, expense = 200_000, income = 500_000),
            trendAverage(trend, september, day("2026-09-27"), first),
        )
    }

    @Test
    fun `이번 달과 그 뒤 달은 끝나지 않아 앞선 달 평균에서 빠진다`() {
        val rows = byMonth(tx("2026-08-03", 300_000), tx("2026-09-05", 5_000_000), tx("2026-10-05", 7_000_000))
        val november = YearMonth.of(2026, 11)
        val trend = monthTrend(november, rows, day("2026-08-01"))
        assertEquals(TrendAverage(1, 300_000, 0), trendAverage(trend, november, day("2026-09-27"), day("2026-08-01")))
    }

    @Test
    fun `1일에 시작한 달은 앞선 달 평균에 들어간다`() {
        val rows = byMonth(tx("2026-08-01", 300_000))
        val trend = monthTrend(september, rows, day("2026-08-01"))
        assertEquals(1, trendAverage(trend, september, day("2026-09-27"), day("2026-08-01"))?.months)
    }

    @Test
    fun `넣을 달이 없으면 앞선 달 평균이 없다`() {
        val trend = monthTrend(september, byMonth(tx("2026-09-05", 1_000)), day("2026-09-05"))
        assertNull(trendAverage(trend, september, day("2026-09-27"), day("2026-09-05")))
    }

    // ── 큰 지출 ─────────────────────────────────────────────────────────

    @Test
    fun `큰 지출은 지출만 금액순으로 5건이고 같은 금액이면 늦은 시각, 그다음 나중에 적은 것이 먼저다`() {
        val early = tx("2026-09-01T09:00", 50_000)
        val late = tx("2026-09-01T21:00", 50_000)
        val sameTimeFirst = tx("2026-09-02T10:00", 10_000)
        val sameTimeSecond = tx("2026-09-02T10:00", 10_000)
        val rows = listOf(
            early,
            tx("2026-09-03", 900_000, INCOME),
            tx("2026-09-03", 800_000, REFUND),
            sameTimeFirst,
            tx("2026-09-04", 70_000),
            late,
            sameTimeSecond,
            tx("2026-09-05", 1_000),
        )
        val largest = largestExpenses(rows)
        assertEquals(listOf(70_000L, 50_000L, 50_000L, 10_000L, 10_000L), largest.map { it.amount })
        assertEquals(listOf(late, early), largest.subList(1, 3))
        assertEquals(listOf(sameTimeSecond, sameTimeFirst), largest.subList(3, 5))
    }

    // ── 올해 모아 보기 ──────────────────────────────────────────────────

    @Test
    fun `기록을 9월에 시작하면 올해 모아 보기는 9월부터다`() {
        val first = day("2026-09-03")
        val rows = byMonth(
            tx("2026-09-03", 100_000),
            tx("2026-10-10", 200_000),
            tx("2026-10-11", 1_000_000, INCOME),
            tx("2026-11-10", 400_000),
            tx("2026-11-30", 5_000, TRANSFER),
        )
        val ytd = yearToDate(YearMonth.of(2026, 11), day("2026-12-15"), rows, first)!!
        assertEquals(YearMonth.of(2026, 9), ytd.startMonth)
        assertEquals(YearMonth.of(2026, 11), ytd.endMonth)
        assertTrue(ytd.startsLate)
        assertEquals(Totals(expense = 700_000, income = 1_000_000), ytd.totals)
        // 9월은 3일에 시작해서 빼고, 10월과 11월의 평균
        assertEquals(300_000L, ytd.monthlyAverageExpense)
    }

    @Test
    fun `시작 달이 고른 달이면 이번 달 요약과 같아서 올해 모아 보기가 없다`() {
        val rows = byMonth(tx("2026-09-03", 100_000))
        assertNull(yearToDate(september, day("2026-09-27"), rows, day("2026-09-03")))
        assertNull(yearToDate(YearMonth.of(2026, 1), day("2026-09-27"), rows, day("2025-03-01")))
    }

    @Test
    fun `지난해에 시작했으면 올해 1월부터 모으고 오늘 뒤 거래도 넣는다`() {
        val rows = byMonth(tx("2026-01-10", 10_000), tx("2026-03-31", 20_000), tx("2026-09-30", 7_000))
        val ytd = yearToDate(september, day("2026-09-27"), rows, day("2025-11-01"))!!
        assertEquals(YearMonth.of(2026, 1), ytd.startMonth)
        assertFalse(ytd.startsLate)
        assertEquals(37_000L, ytd.totals.expense)
        // 기록이 있는 끝난 달(1월, 3월)만 평균에 넣는다. 이번 달(9월)은 끝나지 않았다
        assertEquals(15_000L, ytd.monthlyAverageExpense)
    }

    @Test
    fun `고른 달까지 기록이 없으면 올해 모아 보기가 없다`() {
        assertNull(yearToDate(YearMonth.of(2026, 5), day("2026-09-27"), emptyMap(), day("2026-09-03")))
        assertNull(yearToDate(september, day("2026-09-27"), emptyMap(), null))
    }

    @Test
    fun `평균 낼 달이 없으면 한 달 평균 지출이 없다`() {
        val rows = byMonth(tx("2026-08-10", 100_000), tx("2026-09-10", 100_000))
        val ytd = yearToDate(september, day("2026-09-27"), rows, day("2026-08-10"))!!
        assertNull(ytd.monthlyAverageExpense)
    }
}
