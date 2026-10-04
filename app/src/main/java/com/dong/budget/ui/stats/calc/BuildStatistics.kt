package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.ui.home.ComparisonWindow
import com.dong.budget.ui.home.compareSpending
import com.dong.budget.ui.home.comparisonWindow
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.BreakdownEntry
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
    // 홈과 같은 비교 창이되, 지난달을 1일부터 기록하지 않았으면 분류별 증감을 내지 않는다
    val window = breakdownWindow(month, today, current, byMonth[month.minusMonths(1)].orEmpty(), first)
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
        monthly = monthlyStats(
            month,
            today,
            byMonth,
            first,
            expenseByCategory,
            goneCategories(expenseByCategory, window),
            daily,
            monthIsEmpty,
        ),
        daily = daily,
        expenseByCategory = expenseByCategory,
        incomeByCategory = breakdown(current, Measure.INCOME, Grouping.CATEGORY, window),
        expenseByPayment = breakdown(current, Measure.EXPENSE, Grouping.PAYMENT_METHOD, window),
        merchants = topMerchants(current),
    )
}

/**
 * 분류·결제수단 증감에 쓰는 비교 창. 홈과 같은 [comparisonWindow] 인데, 지난달을 1일부터 기록하지 않았으면 null 이다.
 * 기록 전 날이 0원으로 들어가면 모든 줄이 '없었어요'·'늘었어요' 가 되고 눈에 띄는 점이 '더 썼어요' 로 나오기 때문이다.
 * (앞선 달 평균이 기록을 달 중간에 시작한 달을 빼는 것과 같은 규칙. 통계 탭 요약 머리의 비교 문장은 홈과 같게 compareSpending 을 쓴다)
 */
internal fun breakdownWindow(
    month: YearMonth,
    today: LocalDate,
    current: List<TransactionListItem>,
    previous: List<TransactionListItem>,
    firstRecord: LocalDate?,
): ComparisonWindow? {
    if (firstRecord == null || firstRecord.isAfter(month.minusMonths(1).atDay(1))) return null
    return comparisonWindow(month, today, current, previous)
}

/** 비교 창의 지난 쪽에만 있던 지출 분류(이 달엔 0 이라 목록에 없다). 눈에 띄는 점이 가장 크게 줄어든 분류도 말할 수 있게 한다. */
private fun goneCategories(expenseByCategory: Breakdown, window: ComparisonWindow?): List<BreakdownEntry> {
    window ?: return emptyList()
    val shown = expenseByCategory.entries.mapTo(HashSet()) { it.key }
    return breakdown(window.previous, Measure.EXPENSE, Grouping.CATEGORY, window).entries.filter { it.key !in shown }
}

/** 기록 시작일. [rows] 중 기록(이체 말고)의 가장 이른 날과 조회한 시작일([firstRecord])로 [effectiveFirstDate] 를 고른다. */
internal fun effectiveFirstRecord(rows: List<TransactionListItem>, firstRecord: LocalDate?): LocalDate? =
    effectiveFirstDate(firstRecord, rows.filter { it.isRecord }.minOfOrNull { it.localDate() })

/**
 * 처음 기록한 날. 시작일 조회([queried])와 거래 목록은 따로 방출되어 잠깐 어긋날 수 있다(첫 거래를 막 등록한 직후 등).
 * 보이는 거래(가장 이른 날 [earliestSeen])보다 늦은 시작일은 있을 수 없으므로 둘 중 이른 날을 쓴다.
 * 통계의 기록 시작일과 카드실적의 카드를 처음 쓴 날이 같이 쓴다.
 */
internal fun effectiveFirstDate(queried: LocalDate?, earliestSeen: LocalDate?): LocalDate? =
    listOfNotNull(queried, earliestSeen).minOrNull()

private fun monthlyStats(
    month: YearMonth,
    today: LocalDate,
    byMonth: Map<YearMonth, List<TransactionListItem>>,
    firstRecord: LocalDate?,
    expenseByCategory: Breakdown,
    goneCategories: List<BreakdownEntry>,
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
        insights =
        if (monthIsEmpty) emptyList() else insights(expenseByCategory, daily.weekday, daily.counted, daily.noSpendDays, goneCategories),
        flow = cumulativeFlow(month, today, current, previous),
        pace = pace(month, today, current, previous, firstRecord),
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
    // 분류 탭 도넛과 같은 계열을 분류 순서로 쌓는다
    val series = seriesOf(expenseByCategory.entries, expenseByCategory.positiveTotal, CATEGORY_ORDER)
    val days = dayStacks(month, today, current, series)
    val counted = countedDays(month, today, firstRecord)
    val records = dayRecords(counted, days)
    return DailyStats(
        series = series,
        days = days,
        stackMax = days.maxOfOrNull { it.segments.sum() } ?: 0L,
        defaultDay = defaultDay(period, today, records.peak, days),
        counted = counted,
        average = records.average,
        spentDays = records.spentDays,
        spentDayAverage = records.spentDayAverage,
        peak = records.peak,
        noSpendDays = records.noSpendDays,
        longestNoSpend = records.longestNoSpend,
        weekday = weekdayStats(month, today, firstRecord, rows),
    )
}
