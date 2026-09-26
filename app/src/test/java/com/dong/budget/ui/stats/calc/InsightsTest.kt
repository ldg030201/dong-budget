package com.dong.budget.ui.stats.calc

import com.dong.budget.testing.day
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.WeekdayStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private fun entry(id: Long, amount: Long, previous: Long? = null) = BreakdownEntry(
        key = GroupKey.Id(id),
        name = "분류$id",
        icon = null,
        color = null,
        amount = amount,
        count = 1,
        share = null,
        averageTicket = null,
        change = previous?.let { EntryChange(ComparisonScope.WHOLE_MONTH, previous = it, current = amount) },
    )

    private fun expense(positiveTotal: Long, vararg entries: BreakdownEntry) =
        Breakdown(total = positiveTotal, positiveTotal = positiveTotal, entries = entries.toList(), series = emptyList(), negativeCount = 0)

    private fun weekday(ratio: Double?) = WeekdayStats(day("2026-09-01"), day("2026-09-27"), emptyList(), null, ratio)

    private val none = expense(0)

    /** 1위 비율이 20% 에 못 미치게 분모를 크게 둔다. 증감만 보려는 것이다. */
    private fun changesOnly(vararg entries: BreakdownEntry) = expense(10_000_000, *entries)

    @Test
    fun `1위 분류가 딱 20% 면 말하고 그보다 작으면 말하지 않는다`() {
        val exact = insights(expense(100_000, entry(1, 20_000)), null, IntRange.EMPTY, null)
        assertEquals(listOf(Insight.TopShare(entry(1, 20_000), 20)), exact)
        assertTrue(insights(expense(100_000, entry(1, 19_999)), null, IntRange.EMPTY, null).isEmpty())
    }

    @Test
    fun `1위 비율은 반올림한 정수 % 다`() {
        val top = insights(expense(300_000, entry(1, 128_500)), null, IntRange.EMPTY, null).single() as Insight.TopShare
        assertEquals(43, top.percent)
    }

    @Test
    fun `가장 크게 달라진 분류는 3만원 이상이면서 지난 값의 30% 이상이어야 한다`() {
        fun change(amount: Long, previous: Long) = insights(changesOnly(entry(1, amount, previous)), null, IntRange.EMPTY, null)
        // 3만원, 30% 딱 맞으면 말한다
        assertEquals(1, change(130_000, 100_000).size)
        // 금액이 모자라다
        assertTrue(change(29_999 + 50_000, 50_000).isEmpty())
        // 비율이 모자라다(30,000 ÷ 100,001)
        assertTrue(change(130_001, 100_001).isEmpty())
        // 줄어든 것도 같은 문턱이다
        assertEquals(1, change(70_000, 100_000).size)
    }

    @Test
    fun `지난 값이 0 이면 금액 조건만 본다`() {
        val result = insights(changesOnly(entry(1, 30_000, previous = 0)), null, IntRange.EMPTY, null)
        assertEquals(
            listOf<Insight>(Insight.CategoryChange(entry(1, 30_000, 0), EntryChange(ComparisonScope.WHOLE_MONTH, 0, 30_000))),
            result,
        )
        assertTrue(insights(changesOnly(entry(1, 29_999, previous = 0)), null, IntRange.EMPTY, null).isEmpty())
    }

    @Test
    fun `차이가 가장 큰 분류 하나만 고른다(줄어든 것 포함)`() {
        val grew = entry(1, 140_000, previous = 100_000)
        val shrank = entry(2, 50_000, previous = 100_000)
        val result = insights(changesOnly(grew, shrank), null, IntRange.EMPTY, null).single() as Insight.CategoryChange
        assertEquals(GroupKey.Id(2), result.entry.key)
    }

    @Test
    fun `주말과 평일의 하루 평균이 문턱만큼 벌어져야 말한다`() {
        assertEquals(listOf<Insight>(Insight.WeekPattern(true, 1.3)), insights(none, weekday(1.3), IntRange.EMPTY, null))
        assertTrue(insights(none, weekday(1.29), IntRange.EMPTY, null).isEmpty())
        assertTrue(insights(none, weekday(0.8), IntRange.EMPTY, null).isEmpty())
        val weekdayHigher = insights(none, weekday(0.5), IntRange.EMPTY, null).single() as Insight.WeekPattern
        assertEquals(false, weekdayHigher.weekendHigher)
        assertEquals(2.0, weekdayHigher.ratio, 1e-12)
        // 요일 창이 짧거나 비를 낼 수 없으면 말하지 않는다
        assertTrue(insights(none, null, IntRange.EMPTY, null).isEmpty())
        assertTrue(insights(none, weekday(null), IntRange.EMPTY, null).isEmpty())
    }

    @Test
    fun `돈 안 쓴 날은 센 날이 일주일 이상이고 하루라도 있을 때 말한다`() {
        assertEquals(listOf<Insight>(Insight.NoSpendDays(1)), insights(none, null, 1..7, 1))
        assertTrue(insights(none, null, 1..6, 1).isEmpty())
        assertTrue(insights(none, null, 1..7, 0).isEmpty())
        assertTrue(insights(none, null, IntRange.EMPTY, null).isEmpty())
    }

    @Test
    fun `네 가지가 모두 맞으면 순서대로 3개까지만 말한다`() {
        val result = insights(expense(100_000, entry(1, 60_000, previous = 10_000)), weekday(2.0), 1..27, 5)
        assertEquals(3, result.size)
        assertTrue(result[0] is Insight.TopShare)
        assertTrue(result[1] is Insight.CategoryChange)
        assertTrue(result[2] is Insight.WeekPattern)
    }

    @Test
    fun `앞의 것이 빠지면 뒤의 것이 올라온다`() {
        val result = insights(expense(100_000, entry(1, 10_000), entry(2, 5_000)), weekday(1.0), 1..27, 5)
        assertEquals(listOf<Insight>(Insight.NoSpendDays(5)), result)
    }
}
