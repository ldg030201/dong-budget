package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.WEEK_ORDER
import com.dong.budget.ui.stats.DayPeak
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

class DailyStatsTest {
    private val september = YearMonth.of(2026, 9)

    private fun stacks(today: String, vararg rows: TransactionListItem) =
        dayStacks(september, day(today), rows.toList(), breakdown(rows.toList(), Measure.EXPENSE, Grouping.CATEGORY, null).series)

    // ── 쌓은 막대 ───────────────────────────────────────────────────────

    @Test
    fun `하루치는 1일부터 말일까지 있고 이체는 없다`() {
        val days = stacks("2026-09-27", tx("2026-09-03", 1_000, categoryId = 1), tx("2026-09-03", 70_000, TRANSFER))
        assertEquals(30, days.size)
        assertEquals((1..30).toList(), days.map { it.date.dayOfMonth })
        assertEquals(1, days[2].items.size)
        assertEquals(1_000L, days[2].expense)
    }

    @Test
    fun `조각은 계열 순서이고 접힌 분류는 그 외 조각에 모인다`() {
        // 식비·교통·쇼핑·문화·의료 5개와 분류 없음·통신(6위) → 5개 + 그 외 2개
        val rows = listOf(
            tx("2026-09-01", 60_000, categoryId = 1),
            tx("2026-09-01", 50_000, categoryId = 2),
            tx("2026-09-01", 40_000, categoryId = 3),
            tx("2026-09-01", 30_000, categoryId = 4),
            tx("2026-09-01", 20_000, categoryId = 5),
            tx("2026-09-03", 10_000, categoryId = 6),
            tx("2026-09-03", 7_000),
            tx("2026-09-03", 2_000, REFUND, categoryId = 1),
        )
        val series = breakdown(rows, Measure.EXPENSE, Grouping.CATEGORY, null).series
        val days = dayStacks(september, day("2026-09-27"), rows, series)
        assertEquals(listOf(60_000L, 50_000L, 40_000L, 30_000L, 20_000L, 0L), days[0].segments)
        // 3일: 식비는 환불뿐이라 0, 그 외 = 통신 10,000 + 분류 없음 7,000
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 17_000L), days[2].segments)
        assertEquals(15_000L, days[2].expense)
        assertEquals(2_000L, days[2].refund)
        // 접힌 분류도 제 이름으로 남고 계열 번호는 그 외(5)다
        assertEquals(listOf("분류6", "분류 없음", "분류1"), days[2].details.map { it.name })
        assertEquals(listOf(5, 5, 0), days[2].details.map { it.seriesIndex })
        assertEquals(listOf(10_000L, 7_000L, -2_000L), days[2].details.map { it.amount })
    }

    @Test
    fun `계열에 없는 분류(환불이 더 많은 분류)는 조각이 없고 계열 번호가 null 이다`() {
        val days = stacks(
            "2026-09-27",
            tx("2026-09-01", 10_000, categoryId = 1),
            tx("2026-09-02", 3_000, categoryId = 2),
            tx("2026-09-05", 9_000, REFUND, categoryId = 2),
        )
        assertEquals(listOf(0L), days[1].segments)
        assertNull(days[1].details.single().seriesIndex)
        assertEquals(3_000L, days[1].expense)
    }

    @Test
    fun `그날 거래는 늦은 시각이 먼저다`() {
        val early = tx("2026-09-03T08:00", 1_000)
        val late = tx("2026-09-03T21:00", 2_000)
        assertEquals(listOf(late, early), stacks("2026-09-27", early, late)[2].items)
    }

    @Test
    fun `오늘 뒤의 날은 미래로 표시하고 미리 적은 거래도 막대에 넣는다`() {
        val days = stacks("2026-09-10", tx("2026-09-20", 500_000, categoryId = 1))
        assertTrue(days[19].isFuture)
        assertFalse(days[9].isFuture)
        assertEquals(500_000L, days[19].expense)
        assertEquals(listOf(500_000L), days[19].segments)
    }

    // ── 하루 기록 ───────────────────────────────────────────────────────

    @Test
    fun `미래 날짜 거래는 하루 평균, 가장 많이 쓴 날, 돈 안 쓴 날에서 빠진다`() {
        val days = stacks("2026-09-10", tx("2026-09-03", 10_000), tx("2026-09-20", 500_000))
        val records = dayRecords(countedDays(september, day("2026-09-10"), day("2026-09-01")), days)
        assertEquals(1_000L, records.average)
        assertEquals(DayPeak(day("2026-09-03"), 10_000), records.peak)
        assertEquals(9, records.noSpendDays)
        assertEquals(1, records.spentDays)
        assertEquals(10_000L, records.spentDayAverage)
    }

    @Test
    fun `돈 안 쓴 날은 환불만 있거나 수입만 있는 날도 세고 최장 연속을 낸다`() {
        val days = stacks(
            "2026-09-10",
            tx("2026-09-01", 1_000),
            tx("2026-09-03", 5_000, REFUND),
            tx("2026-09-04", 90_000, INCOME),
            tx("2026-09-06", 2_000),
            tx("2026-09-10", 3_000),
        )
        val records = dayRecords(1..10, days)
        // 2·3·4·5 와 7·8·9
        assertEquals(7, records.noSpendDays)
        assertEquals(4, records.longestNoSpend)
        assertEquals(3, records.spentDays)
    }

    @Test
    fun `기록 전 날과 오늘 뒤 날은 세지 않는다`() {
        val days = stacks("2026-09-10", tx("2026-09-05", 6_000), tx("2026-09-15", 1_000))
        val counted = countedDays(september, day("2026-09-10"), day("2026-09-05"))
        val records = dayRecords(counted, days)
        assertEquals(5..10, counted)
        assertEquals(5, records.noSpendDays)
        assertEquals(1_000L, records.average)
        assertEquals(5, records.longestNoSpend)
    }

    @Test
    fun `가장 많이 쓴 날이 같으면 이른 날이고 쓴 날이 없으면 없다`() {
        val tie = stacks("2026-09-27", tx("2026-09-08", 5_000), tx("2026-09-04", 5_000))
        assertEquals(day("2026-09-04"), dayRecords(1..27, tie).peak?.date)
        val none = stacks("2026-09-27", tx("2026-09-08", 5_000, REFUND))
        assertNull(dayRecords(1..27, none).peak)
    }

    @Test
    fun `환불이 더 많으면 하루 평균과 쓴 날 평균이 없다`() {
        val days = stacks("2026-09-10", tx("2026-09-02", 1_000), tx("2026-09-03", 50_000, REFUND))
        val records = dayRecords(1..10, days)
        assertNull(records.average)
        assertNull(records.spentDayAverage)
        assertEquals(1, records.spentDays)
    }

    @Test
    fun `센 날이 없으면 평균과 돈 안 쓴 날이 없다`() {
        val records = dayRecords(IntRange.EMPTY, stacks("2026-09-10"))
        assertNull(records.average)
        assertNull(records.noSpendDays)
        assertEquals(0, records.longestNoSpend)
    }

    // ── 처음 고를 날 ────────────────────────────────────────────────────

    @Test
    fun `처음 고를 날은 이번 달이면 오늘, 지나간 달이면 가장 많이 쓴 날, 쓴 날이 없으면 1일이다`() {
        val days = stacks("2026-09-27", tx("2026-09-08", 5_000))
        assertEquals(27, defaultDay(Period.CURRENT, day("2026-09-27"), DayPeak(day("2026-09-08"), 5_000), days))
        assertEquals(8, defaultDay(Period.PAST, day("2026-10-02"), DayPeak(day("2026-09-08"), 5_000), days))
        assertEquals(1, defaultDay(Period.PAST, day("2026-10-02"), null, stacks("2026-10-02")))
    }

    @Test
    fun `오지 않은 달은 기록이 있는 첫날을 고르고 없으면 1일이다`() {
        val days = stacks("2026-08-20", tx("2026-09-12", 100_000, INCOME), tx("2026-09-25", 5_000))
        assertEquals(12, defaultDay(Period.FUTURE, day("2026-08-20"), null, days))
        assertEquals(1, defaultDay(Period.FUTURE, day("2026-08-20"), null, stacks("2026-08-20")))
    }

    // ── 요일별 ──────────────────────────────────────────────────────────

    @Test
    fun `오지 않은 달은 요일 통계가 없다`() {
        assertNull(weekdayStats(YearMonth.of(2026, 10), day("2026-09-27"), day("2025-01-01"), emptyList()))
    }

    @Test
    fun `요일 창이 13일이면 없고 14일이면 있다`() {
        // 9월 1일에 시작. 13일이면 13일치, 14일이면 14일치
        assertNull(weekdayStats(september, day("2026-09-13"), day("2026-09-01"), emptyList()))
        assertNotNull(weekdayStats(september, day("2026-09-14"), day("2026-09-01"), emptyList()))
        assertNull(weekdayStats(september, day("2026-09-27"), null, emptyList()))
    }

    @Test
    fun `요일 창은 고른 달의 두 달 전 1일부터 오늘까지다`() {
        val stats = weekdayStats(september, day("2026-09-20"), day("2025-01-01"), emptyList())!!
        assertEquals(day("2026-07-01"), stats.from)
        assertEquals(day("2026-09-20"), stats.to)
        // 지나간 달이면 말일까지
        assertEquals(day("2026-08-31"), weekdayStats(YearMonth.of(2026, 8), day("2026-09-20"), day("2025-01-01"), emptyList())!!.to)
    }

    @Test
    fun `요일 평균은 0원인 날도 분모에 넣고 창 밖과 오늘 뒤 거래는 넣지 않는다`() {
        // 9월 1일(화)~14일(월): 화요일은 1일과 8일 이틀
        val rows = listOf(
            tx("2026-09-01", 30_000),
            tx("2026-08-25", 999_000), // 기록 시작(9월 1일) 전이라 창 밖
            tx("2026-09-15", 999_000), // 오늘 뒤
            tx("2026-09-08", 5_000, REFUND),
        )
        val stats = weekdayStats(september, day("2026-09-14"), day("2026-09-01"), rows)!!
        assertEquals(WEEK_ORDER, stats.averages.map { it.day })
        val tuesday = stats.averages.first { it.day == DayOfWeek.TUESDAY }
        assertEquals(2, tuesday.days)
        assertEquals(12_500L, tuesday.average)
        assertEquals(DayOfWeek.TUESDAY, stats.top)
        assertEquals(14, stats.averages.sumOf { it.days })
    }

    @Test
    fun `모든 요일이 0 이면 가장 많이 쓴 요일이 없고, 같으면 앞선 요일이다`() {
        assertNull(weekdayStats(september, day("2026-09-14"), day("2026-09-01"), emptyList())!!.top)
        // 일요일(6일, 13일)과 토요일(5일, 12일)이 같다
        val rows = listOf(tx("2026-09-05", 10_000), tx("2026-09-06", 10_000))
        assertEquals(DayOfWeek.SUNDAY, weekdayStats(september, day("2026-09-14"), day("2026-09-01"), rows)!!.top)
    }

    /** 9월 1일(화)~14일(월): 주말 4일(5·6·12·13일), 평일 10일 */
    private fun weekdayRatio(weekendTotal: Long, weekdayTotal: Long): Double? {
        val rows = listOf(tx("2026-09-05", weekendTotal), tx("2026-09-01", weekdayTotal))
        return weekdayStats(september, day("2026-09-14"), day("2026-09-01"), rows)!!.weekendRatio
    }

    @Test
    fun `주말 평일 비는 합과 날 수로 한 번에 나눠서 문턱에서 흔들리지 않는다`() {
        // (52,000 ÷ 4) ÷ (100,000 ÷ 10) = 1.3
        val exact = weekdayRatio(52_000, 100_000)!!
        assertEquals(1.3, exact, 0.0)
        assertEquals(true, weekPattern(weekdayStatsWith(exact))?.weekendHigher)
        assertNull(weekPattern(weekdayStatsWith(weekdayRatio(51_999, 100_000)!!)))
        // (40,000 ÷ 4) ÷ (130,000 ÷ 10) = 1 ÷ 1.3
        val low = weekPattern(weekdayStatsWith(weekdayRatio(40_000, 130_000)!!))!!
        assertFalse(low.weekendHigher)
        assertEquals(1.3, low.ratio, 1e-9)
    }

    @Test
    fun `주말이나 평일 한쪽에 쓴 돈이 없으면 비가 없다`() {
        assertNull(weekdayRatio(0, 100_000))
        assertNull(weekdayRatio(10_000, 0))
    }

    private fun weekdayStatsWith(ratio: Double) =
        weekdayStats(september, day("2026-09-14"), day("2026-09-01"), emptyList())!!.copy(weekendRatio = ratio)

    @Test
    fun `계열 키로 분류를 찾는다`() {
        assertEquals(GroupKey.Id(3), tx("2026-09-01", 1, categoryId = 3).groupKey(Grouping.CATEGORY))
        assertEquals(GroupKey.None, tx("2026-09-01", 1, categoryId = 3).groupKey(Grouping.PAYMENT_METHOD))
    }
}
