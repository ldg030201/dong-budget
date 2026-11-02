package com.dong.budget.ui.home

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.isSavings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MonthOverviewTest {
    private var nextId = 1L

    private fun tx(
        at: String,
        amount: Long,
        type: TransactionType = TransactionType.EXPENSE,
        categoryCode: String? = null,
    ): TransactionListItem {
        val instant = LocalDateTime.parse(at).atZone(BudgetTime.ZONE).toInstant()
        return TransactionListItem(
            id = nextId++,
            type = type,
            amount = amount,
            occurredAt = instant,
            occurredDate = BudgetTime.toDateKey(instant),
            merchant = null,
            memo = null,
            categoryName = null,
            categoryIcon = null,
            categoryColor = null,
            paymentMethodName = null,
            categoryCode = categoryCode,
        )
    }

    @Test
    fun `합계는 이체를 빼고 환불을 지출에서 뺀다`() {
        val totals =
            listOf(
                tx("2026-09-01T10:00", 10_000),
                tx("2026-09-01T11:00", 3_000, TransactionType.REFUND),
                tx("2026-09-02T09:00", 50_000, TransactionType.INCOME),
                tx("2026-09-02T09:30", 70_000, TransactionType.TRANSFER),
            ).totals()
        assertEquals(Totals(expense = 7_000, income = 50_000), totals)
    }

    @Test
    fun `저축 분류로 적은 돈은 지출에 넣지 않고 저축에 따로 모은다`() {
        val totals =
            listOf(
                tx("2026-09-01T10:00", 10_000),
                tx("2026-09-01T11:00", 300_000, categoryCode = SAVINGS_CATEGORY_CODE),
                // 저축 환불(백업으로만 들어온다)은 꺼낸 돈이라 저축에서 뺀다
                tx("2026-09-02T09:00", 50_000, TransactionType.REFUND, categoryCode = SAVINGS_CATEGORY_CODE),
                // 다른 기본 분류 코드는 그대로 지출이다
                tx("2026-09-03T09:00", 20_000, categoryCode = FIXED_CATEGORY_CODE),
            ).totals()
        assertEquals(Totals(expense = 30_000, savings = 250_000), totals)
    }

    @Test
    fun `저축 분류 코드라도 수입과 이체는 저축이 아니다`() {
        // 저축 코드는 지출 분류에만 있지만, 종류가 지출·환불이 아니면 저축으로 세지 않는다
        val income = tx("2026-09-01T10:00", 10_000, TransactionType.INCOME, categoryCode = SAVINGS_CATEGORY_CODE)
        val transfer = tx("2026-09-01T11:00", 20_000, TransactionType.TRANSFER, categoryCode = SAVINGS_CATEGORY_CODE)
        assertFalse(income.isSavings)
        assertFalse(transfer.isSavings)
        assertTrue(tx("2026-09-01T12:00", 1_000, categoryCode = SAVINGS_CATEGORY_CODE).isSavings)
        assertEquals(Totals(income = 10_000), listOf(income, transfer).totals())
    }

    @Test
    fun `날짜별 묶음은 최신 날짜가 먼저이고 같은 날은 늦은 시각이 먼저다`() {
        val early = tx("2026-09-20T08:00", 1_000)
        val late = tx("2026-09-20T21:00", 2_000)
        val newest = tx("2026-09-25T12:00", 3_000)
        val oldest = tx("2026-09-03T12:00", 4_000)

        // 일부러 뒤섞어 넣는다
        val groups = groupByDay(listOf(early, oldest, newest, late))

        assertEquals(
            listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 3)),
            groups.map { it.date },
        )
        assertEquals(listOf(late, early), groups[1].items)
    }

    @Test
    fun `자정 직전 거래는 한국 시간 기준 그날로 묶인다`() {
        // 한국 23:30 은 UTC 로는 같은 날 14:30 이지만, 한국 00:30 은 UTC 로 전날이다
        val groups = groupByDay(listOf(tx("2026-09-21T00:30", 1_000), tx("2026-09-20T23:30", 1_000)))
        assertEquals(listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 20)), groups.map { it.date })
    }

    @Test
    fun `날짜별 합계`() {
        val days =
            dailyTotals(
                listOf(
                    tx("2026-09-20T08:00", 1_000),
                    tx("2026-09-20T09:00", 5_000, TransactionType.INCOME),
                    tx("2026-09-21T09:00", 2_000),
                ),
            )
        assertEquals(Totals(expense = 1_000, income = 5_000), days[LocalDate.of(2026, 9, 20)])
        assertEquals(Totals(expense = 2_000), days[LocalDate.of(2026, 9, 21)])
        assertNull(days[LocalDate.of(2026, 9, 22)])
    }

    @Test
    fun `날짜별 합계도 저축을 지출에서 뺀다`() {
        val days =
            dailyTotals(
                listOf(
                    tx("2026-09-20T08:00", 1_000),
                    tx("2026-09-20T09:00", 100_000, categoryCode = SAVINGS_CATEGORY_CODE),
                    tx("2026-09-21T09:00", 50_000, categoryCode = SAVINGS_CATEGORY_CODE),
                ),
            )
        assertEquals(Totals(expense = 1_000, savings = 100_000), days[LocalDate.of(2026, 9, 20)])
        // 저축만 있는 날은 쓴 돈이 0 이다
        assertEquals(Totals(savings = 50_000), days[LocalDate.of(2026, 9, 21)])
    }

    @Test
    fun `달력은 일요일에 시작하고 모든 줄이 7칸이다`() {
        // 2026년 9월 1일은 화요일, 30일은 수요일
        val weeks = calendarWeeks(YearMonth.of(2026, 9))
        assertEquals(5, weeks.size)
        weeks.forEach { assertEquals(7, it.size) }
        assertEquals(listOf(null, null, LocalDate.of(2026, 9, 1)), weeks.first().take(3))
        assertEquals(LocalDate.of(2026, 9, 30), weeks.last()[3])
        assertEquals(listOf(null, null, null), weeks.last().takeLast(3))
        assertEquals(30, weeks.flatten().filterNotNull().size)
    }

    @Test
    fun `일요일에 시작하는 달은 앞 빈칸이 없다`() {
        // 2026년 2월 1일은 일요일, 28일은 토요일 → 딱 4줄
        val weeks = calendarWeeks(YearMonth.of(2026, 2))
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2026, 2, 1), weeks.first().first())
        assertEquals(LocalDate.of(2026, 2, 28), weeks.last().last())
    }

    @Test
    fun `달이 걸친 주는 이 달 칸만 남긴다`() {
        // 2026-09-30(수) 가 든 주: 27 28 29 30 | 10/1 10/2 10/3
        val week = weekOf(LocalDate.of(2026, 9, 30), YearMonth.of(2026, 9))
        assertEquals(
            listOf(27, 28, 29, 30, null, null, null),
            week.map { it?.dayOfMonth },
        )
        // 일요일 당일이 주의 첫 칸이다
        assertEquals(LocalDate.of(2026, 9, 20), weekOf(LocalDate.of(2026, 9, 20), YearMonth.of(2026, 9)).first())
    }

    @Test
    fun `월요일에 시작하면 달력·한 주 줄·요일 순서가 월요일부터다`() {
        val monday = DayOfWeek.MONDAY
        // 2026년 9월 1일(화)은 둘째 칸, 30일(수)은 마지막 줄 셋째 칸
        val weeks = calendarWeeks(YearMonth.of(2026, 9), monday)
        assertEquals(5, weeks.size)
        weeks.forEach { assertEquals(7, it.size) }
        assertEquals(listOf(null, LocalDate.of(2026, 9, 1)), weeks.first().take(2))
        assertEquals(LocalDate.of(2026, 9, 30), weeks.last()[2])
        // 2026년 2월 1일(일)은 첫 줄 마지막 칸이라 한 줄이 늘어난다
        val february = calendarWeeks(YearMonth.of(2026, 2), monday)
        assertEquals(5, february.size)
        assertEquals(LocalDate.of(2026, 2, 1), february.first().last())

        // 2026-09-30(수) 가 든 주: 28 29 30 | 10/1 ~ 10/4
        assertEquals(
            listOf(28, 29, 30, null, null, null, null),
            weekOf(LocalDate.of(2026, 9, 30), YearMonth.of(2026, 9), monday).map {
                it?.dayOfMonth
            },
        )
        // 일요일은 그 주의 마지막 칸이다
        assertEquals(LocalDate.of(2026, 9, 20), weekOf(LocalDate.of(2026, 9, 20), YearMonth.of(2026, 9), monday).last())

        assertEquals(DayOfWeek.entries.toList(), weekOrder(monday))
        assertEquals(WEEK_ORDER, weekOrder(DayOfWeek.SUNDAY))
    }

    @Test
    fun `이번 달은 지난달 같은 날까지와 비교한다`() {
        val today = LocalDate.of(2026, 9, 25)
        val current = listOf(tx("2026-09-10T12:00", 50_000), tx("2026-09-25T20:00", 10_000))
        val previous =
            listOf(
                tx("2026-08-05T12:00", 100_000),
                tx("2026-08-25T23:59", 20_000), // 같은 날까지라서 들어간다
                tx("2026-08-26T00:00", 500_000), // 같은 날 이후라서 빠진다
            )
        val comparison = compareSpending(YearMonth.of(2026, 9), today, current, previous)
        assertEquals(SpendingComparison(60_000 - 120_000, ComparisonScope.SAME_DAY), comparison)
    }

    @Test
    fun `미래 날짜로 적어둔 거래는 이번 달 비교에서 뺀다`() {
        val today = LocalDate.of(2026, 9, 25)
        val current = listOf(tx("2026-09-10T12:00", 50_000), tx("2026-09-28T12:00", 999_000))
        val previous = listOf(tx("2026-08-05T12:00", 50_000))
        assertEquals(0L, compareSpending(YearMonth.of(2026, 9), today, current, previous)?.difference)
    }

    @Test
    fun `지난달이 더 짧으면 지난달 마지막 날까지 비교한다`() {
        // 3월 31일 기준 → 2월은 28일까지 전부
        val today = LocalDate.of(2026, 3, 31)
        val previous = listOf(tx("2026-02-28T22:00", 30_000))
        val comparison = compareSpending(YearMonth.of(2026, 3), today, listOf(tx("2026-03-01T12:00", 10_000)), previous)
        assertEquals(-20_000L, comparison?.difference)
    }

    @Test
    fun `지나간 달은 한 달 전체끼리 비교한다`() {
        val today = LocalDate.of(2026, 9, 3)
        val current = listOf(tx("2026-08-30T12:00", 80_000))
        val previous = listOf(tx("2026-07-31T12:00", 30_000))
        assertEquals(
            SpendingComparison(50_000, ComparisonScope.WHOLE_MONTH),
            compareSpending(YearMonth.of(2026, 8), today, current, previous),
        )
    }

    @Test
    fun `지난달 기록이 없거나 아직 오지 않은 달이면 비교하지 않는다`() {
        val today = LocalDate.of(2026, 9, 25)
        assertNull(compareSpending(YearMonth.of(2026, 9), today, listOf(tx("2026-09-10T12:00", 1_000)), emptyList()))
        assertNull(
            compareSpending(YearMonth.of(2026, 10), today, emptyList(), listOf(tx("2026-09-10T12:00", 1_000))),
        )
    }

    @Test
    fun `지난달이 저축뿐이면 비교하지 않고 이번 달 저축은 비교에 넣지 않는다`() {
        val today = LocalDate.of(2026, 9, 25)
        val current = listOf(tx("2026-09-10T12:00", 10_000), tx("2026-09-11T12:00", 500_000, categoryCode = SAVINGS_CATEGORY_CODE))
        val onlySavings = listOf(tx("2026-08-10T12:00", 500_000, categoryCode = SAVINGS_CATEGORY_CODE))
        assertNull(compareSpending(YearMonth.of(2026, 9), today, current, onlySavings))
        // 지난달에 저축 말고도 기록이 있으면 비교하고, 두 달의 저축은 빼고 견준다
        val previous = onlySavings + tx("2026-08-12T12:00", 30_000)
        assertEquals(
            SpendingComparison(10_000 - 30_000, ComparisonScope.SAME_DAY),
            compareSpending(YearMonth.of(2026, 9), today, current, previous),
        )
    }

    @Test
    fun `비교 문장`() {
        assertEquals(
            ComparisonSentence("지난달 이맘때보다 ", "9만원", " 덜 썼어요"),
            SpendingComparison(-90_400, ComparisonScope.SAME_DAY).sentence(),
        )
        assertEquals(
            ComparisonSentence("지난달보다 ", "3,000원", " 더 썼어요"),
            SpendingComparison(3_000, ComparisonScope.WHOLE_MONTH).sentence(),
        )
        assertEquals(
            ComparisonSentence("지난달 이맘때만큼 썼어요", null, ""),
            SpendingComparison(0, ComparisonScope.SAME_DAY).sentence(),
        )
    }
}
