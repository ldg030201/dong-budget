package com.dong.budget.ui.stats.tab.daily

import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.StatSeries
import com.dong.budget.ui.stats.WeekdayAverage
import com.dong.budget.ui.stats.calc.Grouping
import com.dong.budget.ui.stats.calc.Measure
import com.dong.budget.ui.stats.calc.breakdown
import com.dong.budget.ui.stats.calc.dayStacks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

class DailyTextTest {
    private val today = day("2026-09-10")

    private val days = run {
        val rows = listOf(
            tx("2026-09-03", 20_000, categoryId = 1, categoryName = "식비"),
            tx("2026-09-03", 12_000, categoryId = 2, categoryName = "교통/차량"),
            tx("2026-09-05", 3_000, REFUND, categoryId = 1, categoryName = "식비"),
            tx("2026-09-05", 300_000, INCOME),
            tx("2026-09-20", 50_000, categoryId = 1, categoryName = "식비"),
        )
        dayStacks(YearMonth.of(2026, 9), today, rows, breakdown(rows, Measure.EXPENSE, Grouping.CATEGORY, null).series)
    }

    @Test
    fun `읽기 판 날짜 줄은 오늘이면 오늘을 붙인다`() {
        assertEquals("9월 3일 목요일", dayTitle(day("2026-09-03"), today))
        assertEquals("9월 10일 목요일 · 오늘", dayTitle(today, today))
    }

    @Test
    fun `칸 설명은 그날 지출과 분류별 금액을 전체 금액으로 읽는다`() {
        assertEquals("9월 3일 목요일, 지출 32,000원, 식비 20,000원, 교통/차량 12,000원", daySlotDescription(days[2], today))
    }

    @Test
    fun `지출이 없는 날, 오늘, 오지 않은 날`() {
        // 5일은 환불과 수입만 있다. 환불이 더 많은 분류는 들어온 돈이라 + 로 읽는다.
        assertEquals("9월 5일 토요일, 지출 없음, 식비 +3,000원", daySlotDescription(days[4], today))
        assertEquals("9월 10일 목요일, 오늘, 지출 없음", daySlotDescription(days[9], today))
        assertEquals("9월 11일 금요일, 아직 오지 않은 날", daySlotDescription(days[10], today))
        assertEquals(
            "9월 20일 일요일, 아직 오지 않은 날, 미리 적은 지출 50,000원, 식비 50,000원",
            daySlotDescription(days[19], today),
        )
    }

    @Test
    fun `하루 기록 값과 캡션`() {
        assertEquals("-12,345원", averageValue(12_345))
        assertEquals("—", averageValue(null))
        assertEquals("27일 기준", countedCaption(1..27))
        assertEquals("돈을 쓴 12일 기준", spentDaysCaption(12))
        assertEquals("+2,000원", formatNetExpense(-2_000))
        assertEquals("0원", formatNetExpense(0))
    }

    @Test
    fun `필터 칩과 더 보기 문구`() {
        val food = StatSeries(GroupKey.Id(1), "식비", "red", 20_000, 0.5, listOf(GroupKey.Id(1)))
        assertEquals("식비만 보기", filterChipDescription(food))
        assertEquals("모든 분류 보기", filterChipDescription(null))
        assertEquals("2건 더 보기", moreItemsText(2))
    }

    @Test
    fun `하나만 보기 키는 분류를 가리키고 여러 분류를 묶은 그 외는 하나다`() {
        val food = StatSeries(GroupKey.Id(1), "식비", "red", 20_000, 0.5, listOf(GroupKey.Id(1)))
        val none = StatSeries(GroupKey.None, "분류 없음", null, 5_000, 0.1, listOf(GroupKey.None))
        val others = StatSeries(null, "그 외 3개", null, 9_000, 0.2, listOf(GroupKey.Id(7), GroupKey.Id(8), GroupKey.None))
        assertEquals("id:1", food.focusKey())
        assertEquals("none", none.focusKey())
        assertEquals("other", others.focusKey())
    }

    @Test
    fun `요일 막대 한 줄`() {
        val saturday = weekdayBar(WeekdayAverage(DayOfWeek.SATURDAY, 32_000, 12), top = true)
        assertEquals("토", saturday.label)
        // 화면에는 지출 부호를 붙이고, 화면 읽기는 '하루 평균' 이 방향을 말해 부호 없이 읽는다
        assertEquals("-32,000원", saturday.valueText)
        assertEquals("토요일, 하루 평균 32,000원, 가장 많음", saturday.description)
        val monday = weekdayBar(WeekdayAverage(DayOfWeek.MONDAY, -1_000, 13), top = false)
        assertEquals("+1,000원", monday.valueText)
        assertEquals("월요일, 하루 평균 +1,000원", monday.description)
    }
}
