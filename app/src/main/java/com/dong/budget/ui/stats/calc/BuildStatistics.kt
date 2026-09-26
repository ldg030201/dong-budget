package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.ui.home.compareSpending
import com.dong.budget.ui.home.comparisonWindow
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.DailyStats
import com.dong.budget.ui.stats.MonthlyStats
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.StatsUiState
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 화면 전체를 계산하는 입구.
 *
 * 숫자는 홈과 한 몸이다. 달 합계는 Totals, 하루 금액은 dailyTotals, 지난달 비교는 compareSpending 을 그대로 쓴다.
 *
 * @param rows [month] 가 들어 있는 조회 창(StatsViewModel 이 읽는 여러 달)의 거래 전부. 이체도 섞여 온다.
 *   창은 [min(M−5, 그해 1월), M] 이라 최근 6개월, 요일별 3달, 올해 모아 보기를 모두 덮는다.
 * @param firstRecord 기록 시작일. 거래가 없으면 null
 */
fun buildStatistics(month: YearMonth, today: LocalDate, rows: List<TransactionListItem>, firstRecord: LocalDate?): StatsUiState {
    val first = effectiveFirstRecord(rows, firstRecord)
    val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
    val current = byMonth[month].orEmpty()
    // 홈과 같은 비교가 되도록 홈이 넘기는 것(그 달 거래 전부)을 그대로 넘긴다
    val window = comparisonWindow(month, today, current, byMonth[month.minusMonths(1)].orEmpty())
    val period = periodOf(month, today)
    val expenseByCategory = breakdown(current, Measure.EXPENSE, Grouping.CATEGORY, window)
    val monthIsEmpty = current.none { it.isRecord }
    val daily = dailyStats(month, today, period, current, rows, first, expenseByCategory)

    return StatsUiState(
        loaded = true,
        month = month,
        today = today,
        period = period,
        hasAnyRecord = first != null,
        monthIsEmpty = monthIsEmpty,
        firstRecord = first,
        monthly = monthlyStats(month, today, byMonth, first, expenseByCategory, daily, monthIsEmpty),
        daily = daily,
        expenseByCategory = expenseByCategory,
        incomeByCategory = breakdown(current, Measure.INCOME, Grouping.CATEGORY, window),
        expenseByPayment = breakdown(current, Measure.EXPENSE, Grouping.PAYMENT_METHOD, window),
        merchants = topMerchants(current),
    )
}

/**
 * 기록 시작일. 거래 목록과 시작일 조회는 따로 방출되어 잠깐 어긋날 수 있다(첫 거래를 막 등록한 직후 등).
 * 보이는 거래보다 늦은 시작일은 있을 수 없으므로 둘 중 이른 날을 쓴다.
 */
internal fun effectiveFirstRecord(rows: List<TransactionListItem>, firstRecord: LocalDate?): LocalDate? {
    val earliestRow = rows.filter { it.isRecord }.minOfOrNull { it.localDate() }
    return listOfNotNull(firstRecord, earliestRow).minOrNull()
}

private fun monthlyStats(
    month: YearMonth,
    today: LocalDate,
    byMonth: Map<YearMonth, List<TransactionListItem>>,
    firstRecord: LocalDate?,
    expenseByCategory: Breakdown,
    daily: DailyStats,
    monthIsEmpty: Boolean,
): MonthlyStats {
    val current = byMonth[month].orEmpty()
    val previous = byMonth[month.minusMonths(1)].orEmpty()
    val totals = current.totals()
    val trend = monthTrend(month, byMonth, firstRecord)
    val futureCount =
        if (periodOf(month, today) == Period.CURRENT) current.count { it.isRecord && it.localDate().isAfter(today) } else 0
    return MonthlyStats(
        totals = totals,
        comparison = compareSpending(month, today, current, previous),
        futureCount = futureCount,
        spendRatioPercent = spendRatioPercent(totals),
        insights = if (monthIsEmpty) emptyList() else insights(expenseByCategory, daily.weekday, daily.counted, daily.noSpendDays),
        flow = cumulativeFlow(month, today, current, previous),
        pace = pace(month, today, current, previous),
        trend = trend,
        trendAverage = trendAverage(trend, month, today, firstRecord),
        largest = largestExpenses(current),
        yearToDate = yearToDate(month, today, byMonth, firstRecord),
    )
}

private fun dailyStats(
    month: YearMonth,
    today: LocalDate,
    period: Period,
    current: List<TransactionListItem>,
    rows: List<TransactionListItem>,
    firstRecord: LocalDate?,
    expenseByCategory: Breakdown,
): DailyStats {
    val series = expenseByCategory.series
    val days = dayStacks(month, today, current, series)
    val counted = countedDays(month, today, firstRecord)
    val records = dayRecords(counted, days)
    return DailyStats(
        series = series,
        days = days,
        seriesMax = series.indices.map { index -> days.maxOf { it.segments[index] } },
        stackMax = days.maxOfOrNull { it.segments.sum() } ?: 0L,
        defaultDay = defaultDay(period, today, records.peak, days),
        counted = counted,
        startsLate = !counted.isEmpty() && counted.first > 1,
        average = records.average,
        spentDays = records.spentDays,
        spentDayAverage = records.spentDayAverage,
        peak = records.peak,
        noSpendDays = records.noSpendDays,
        longestNoSpend = records.longestNoSpend,
        weekday = weekdayStats(month, today, firstRecord, rows),
    )
}
