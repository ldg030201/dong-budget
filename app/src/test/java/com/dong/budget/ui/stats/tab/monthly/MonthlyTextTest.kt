package com.dong.budget.ui.stats.tab.monthly

import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.testing.day
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.MonthPoint
import com.dong.budget.ui.stats.calc.SpendRatioSentence
import com.dong.budget.ui.stats.calc.TREND_MONTHS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthlyTextTest {
    private val today = day("2026-09-27")
    private val september = YearMonth.of(2026, 9)

    private fun point(month: YearMonth, expense: Long = 0, income: Long = 0, hasRecord: Boolean = true, before: Boolean = false) =
        MonthPoint(month, Totals(expense, income), hasRecord, before)

    private fun entry(key: GroupKey) = BreakdownEntry(key, "식비", null, null, 523_000, 12, 0.42, 20_000, change = null)

    @Test
    fun `최근 몇 달 제목은 막대 칸 수를 따른다`() {
        // 전에는 '최근 6개월' 로 박혀 있어 칸 수(TREND_MONTHS)를 바꾸면 제목만 남았다
        assertEquals("최근 ${TREND_MONTHS}개월", TREND_TITLE)
        assertTrue(TREND_CHART_DESCRIPTION.startsWith(TREND_TITLE))
    }

    @Test
    fun `쓴 돈은 - 를 붙이고, 환불이 더 많으면 돌아온 돈으로 적는다`() {
        assertEquals("-1,234,560원", formatNetExpense(1_234_560))
        assertEquals("0원", formatNetExpense(0))
        assertEquals("+12,000원", formatNetExpense(-12_000))
    }

    @Test
    fun `눈에 띄는 점 줄은 누르면 갈 곳을 화면 읽기에 알린다`() {
        assertEquals("분류 상세 보기", insightActionLabel(Insight.TopShare(entry(GroupKey.Id(1)), 42)))
        assertEquals("요일별 하루 평균 보기", insightActionLabel(Insight.WeekPattern(weekendHigher = true, ratio = 1.6)))
        assertEquals("하루 기록 보기", insightActionLabel(Insight.NoSpendDays(8)))
    }

    @Test
    fun `섹션 제목은 달 이름을 따른다`() {
        assertEquals("이번 달 흐름", flowTitle(september, today))
        assertEquals("8월 흐름", flowTitle(YearMonth.of(2026, 8), today))
        assertEquals("2025년 12월 흐름", flowTitle(YearMonth.of(2025, 12), today))
        assertEquals("이번 달 큰 지출", largestTitle(september, today))
        assertEquals("8월 큰 지출", largestTitle(YearMonth.of(2026, 8), today))
    }

    @Test
    fun `분류 이야기는 그 분류 상세로, 요일과 돈 안 쓴 날은 상세가 없다`() {
        assertEquals(
            StatsDetailKey(StatsDimension.EXPENSE_CATEGORY, 7, 2026, 9),
            Insight.TopShare(entry(GroupKey.Id(7)), 42).detailKey(september),
        )
        // '분류 없음' 은 id 가 null 인 상세
        val change = EntryChange(ComparisonScope.SAME_DAY, previous = 10_000, current = 80_000)
        assertEquals(
            StatsDetailKey(StatsDimension.EXPENSE_CATEGORY, null, 2026, 9),
            Insight.CategoryChange(entry(GroupKey.None), change).detailKey(september),
        )
        assertNull(Insight.WeekPattern(weekendHigher = true, ratio = 1.5).detailKey(september))
        assertNull(Insight.NoSpendDays(3).detailKey(september))
    }

    @Test
    fun `기록한 달이 두 달은 되어야 달마다 비교한다`() {
        val one = listOf(point(YearMonth.of(2026, 8), hasRecord = false), point(september, 1_000))
        val two = listOf(point(YearMonth.of(2026, 8), 5_000), point(september, 1_000))
        assertFalse(canCompareMonths(one))
        assertTrue(canCompareMonths(two))
    }

    @Test
    fun `표는 기록을 시작한 달부터다`() {
        val trend =
            listOf(
                point(YearMonth.of(2026, 6), hasRecord = false, before = true),
                point(YearMonth.of(2026, 7), hasRecord = false, before = true),
                point(YearMonth.of(2026, 8), 5_000),
                point(september, hasRecord = false),
            )
        assertEquals(listOf(YearMonth.of(2026, 8), september), trendTableRows(trend).map { it.month })
        // 기록을 시작하기 전 달만 있으면 표가 비어 섹션을 숨긴다
        assertTrue(trendTableRows(trend.take(2)).isEmpty())
    }

    @Test
    fun `표 칸은 단위 없이 지출은 -, 수입은 + 를 붙이고 음수 지출은 돌아온 돈으로 적는다`() {
        assertEquals(listOf("8월", "-1,234,000", "+2,000,000", "+766,000"), trendCells(point(YearMonth.of(2026, 8), 1_234_000, 2_000_000)))
        assertEquals(listOf("9월", "+12,000", "0", "+12,000"), trendCells(point(september, -12_000)))
        assertEquals(listOf("9월", "-50,000", "0", "-50,000"), trendCells(point(september, 50_000)))
    }

    @Test
    fun `표 줄은 연도와 열 이름을 붙여 읽고 지금 보는 달을 알린다`() {
        val august = point(YearMonth.of(2026, 8), 1_234_000, 2_000_000)
        assertEquals("2026년 8월, 지출 1,234,000원, 수입 2,000,000원, 남은 돈 +766,000원", trendRowDescription(august, isShownMonth = false))
        assertEquals(
            "2026년 9월, 지출 +12,000원, 수입 0원, 남은 돈 +12,000원, 지금 보는 달",
            trendRowDescription(point(september, -12_000), isShownMonth = true),
        )
    }

    @Test
    fun `올해 문장은 수입이 있고 지출이 0 이상일 때만 둔다`() {
        assertEquals(
            SpendRatioSentence("수입의 62%를 썼어요", null, overspent = false),
            yearRatioSentence(Totals(expense = 6_200_000, income = 10_000_000)),
        )
        assertEquals(
            SpendRatioSentence("수입보다 30만원 더 썼어요", null, overspent = true),
            yearRatioSentence(Totals(expense = 1_300_000, income = 1_000_000)),
        )
        // 월별 문장('이 달에는…')은 한 해에 맞지 않아 두지 않는다
        assertNull(yearRatioSentence(Totals(expense = 500_000, income = 0)))
        assertNull(yearRatioSentence(Totals(expense = -10_000, income = 1_000_000)))
    }
}
