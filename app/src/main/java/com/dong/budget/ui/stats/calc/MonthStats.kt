package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.dailyTotals
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.CumulativeFlow
import com.dong.budget.ui.stats.MonthPoint
import com.dong.budget.ui.stats.Pace
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.TrendAverage
import com.dong.budget.ui.stats.YearToDate
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 월별 탭: 이번 달 흐름, 속도, 최근 6개월, 큰 지출, 올해 모아 보기.
// 달별 거래는 Map<YearMonth, 거래> 로 받는다. 이체가 섞여 있어도 Totals 가 빼고 센다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 누적 지출. 그날까지의 순지출(환불을 뺀 지출)을 차례로 더한다.
 *
 * 이번 달은 오늘까지만 센다(오늘 뒤 날짜로 미리 적은 거래는 아직 쓴 돈이 아니다). 지나간 달은 말일까지다.
 * 지난달 선은 1일부터 그 달 말일까지이고, 차트가 같은 '일' 에 맞춰 겹친다.
 *
 * @return 아직 오지 않은 달이면 null
 */
fun cumulativeFlow(
    month: YearMonth,
    today: LocalDate,
    current: List<TransactionListItem>,
    previous: List<TransactionListItem>,
): CumulativeFlow? {
    val lastDay =
        when (periodOf(month, today)) {
            Period.FUTURE -> return null
            Period.CURRENT -> today.dayOfMonth
            Period.PAST -> month.lengthOfMonth()
        }
    val previousLine = previous.takeIf { rows -> rows.any { it.isRecord } }?.let { cumulative(it, month.minusMonths(1).lengthOfMonth()) }
    return CumulativeFlow(month, cumulative(current, lastDay), previousLine)
}

/** 1일부터 [lastDay] 일까지 하루 순지출을 누적한다. 날짜는 홈 달력 칸(dailyTotals)과 같게 나눈다. */
private fun cumulative(rows: List<TransactionListItem>, lastDay: Int): List<Long> {
    val byDay = dailyTotals(rows).mapKeys { (date, _) -> date.dayOfMonth }
    return (1..lastDay).map { byDay[it]?.expense ?: 0L }.runningReduce(Long::plus)
}

/**
 * 이번 달 속도: 지난달만큼 쓰려면 남은 날 하루에 얼마까지 쓸 수 있는지.
 * 예측이 아니라 지난달이라는 기준에 견준 값이다. 오늘 뒤 날짜로 미리 적은 거래는 아직 쓴 돈에 넣지 않는다.
 *
 * @return 이번 달이 아니거나, 지난달 기록이 없거나, 지난달 지출이 0 이하면 null. 견줄 기준이 없다.
 */
fun pace(month: YearMonth, today: LocalDate, current: List<TransactionListItem>, previous: List<TransactionListItem>): Pace? {
    if (periodOf(month, today) != Period.CURRENT || previous.none { it.isRecord }) return null
    val lastMonthTotal = previous.totals().expense
    if (lastMonthTotal <= 0) return null
    val spent = current.filter { !it.localDate().isAfter(today) }.totals().expense
    // 오늘도 아직 쓸 수 있는 날이라 남은 날에 넣는다
    val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
    val remaining = lastMonthTotal - spent
    return Pace(remaining = remaining, daysLeft = daysLeft, dailyAllowance = if (remaining > 0) divRound(remaining, daysLeft) else null)
}

/** 고른 달까지 최근 [TREND_MONTHS] 달. 오래된 달이 앞이다. 해를 넘어도 그대로 이어진다(2027년 3월이면 2026년 10월부터). */
fun monthTrend(month: YearMonth, rowsByMonth: Map<YearMonth, List<TransactionListItem>>, firstRecord: LocalDate?): List<MonthPoint> =
    (TREND_MONTHS - 1 downTo 0).map { back ->
        val m = month.minusMonths(back.toLong())
        val rows = rowsByMonth[m].orEmpty()
        MonthPoint(
            month = m,
            totals = rows.totals(),
            hasRecord = rows.any { it.isRecord },
            beforeFirstRecord = firstRecord == null || m.isBefore(YearMonth.from(firstRecord)),
        )
    }

/**
 * 앞선 달들의 평균. 고른 달 앞 5달 중 다 끝났고, 기록이 있고, 1일부터 기록한 달만 넣는다.
 * 이번 달과 기록을 달 중간에 시작한 달은 덜 센 달이라 평균을 낮춘다.
 * @return 넣을 달이 없으면 null
 */
fun trendAverage(trend: List<MonthPoint>, month: YearMonth, today: LocalDate, firstRecord: LocalDate?): TrendAverage? {
    val thisMonth = YearMonth.from(today)
    val used = trend.filter { point ->
        point.month.isBefore(month) && point.month.isBefore(thisMonth) && point.hasRecord && !startsMidMonth(point.month, firstRecord)
    }
    if (used.isEmpty()) return null
    return TrendAverage(
        months = used.size,
        expense = divRound(used.sumOf { it.totals.expense }, used.size),
        income = divRound(used.sumOf { it.totals.income }, used.size),
    )
}

/** 기록을 이 달 1일이 아닌 날에 시작했는지. 그런 달은 한 달을 다 센 것이 아니다. */
private fun startsMidMonth(month: YearMonth, firstRecord: LocalDate?): Boolean =
    firstRecord != null && YearMonth.from(firstRecord) == month && firstRecord.dayOfMonth != 1

/** 이 달 지출 중 큰 것 [limit] 건. 금액이 같으면 늦게 쓴 것 → 나중에 적은 것(큰 id)이 먼저다. */
fun largestExpenses(rows: List<TransactionListItem>, limit: Int = LARGEST_COUNT): List<TransactionListItem> = rows
    .filter { it.type == TransactionType.EXPENSE }
    .sortedWith(compareByDescending<TransactionListItem> { it.amount }.thenByDescending { it.occurredAt }.thenByDescending { it.id })
    .take(limit)

/**
 * 올해 모아 보기. 1월(올해 기록을 시작했으면 그 달)부터 고른 달까지 모은다.
 * 오늘 뒤 날짜로 미리 적은 거래도 넣는다. 홈의 달 합계를 더한 것과 같게 하기 위함이다.
 *
 * 한 달 평균 지출은 범위 안에서 다 끝났고, 1일부터 기록했고, 기록이 있는 달로만 낸다(앞선 달 평균과 같은 규칙).
 *
 * @return 기록이 없거나, 고른 달까지 기록이 없거나, 시작 달이 고른 달이면 null. 마지막 경우는 이번 달 요약과 같아서 뺀다.
 */
fun yearToDate(
    month: YearMonth,
    today: LocalDate,
    rowsByMonth: Map<YearMonth, List<TransactionListItem>>,
    firstRecord: LocalDate?,
): YearToDate? {
    firstRecord ?: return null
    val firstMonth = YearMonth.from(firstRecord)
    val start = if (firstRecord.year == month.year) firstMonth else YearMonth.of(month.year, 1)
    if (firstMonth.isAfter(month) || !start.isBefore(month)) return null

    val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(month) }.toList()
    val totals = months.flatMap { rowsByMonth[it].orEmpty() }.totals()
    val thisMonth = YearMonth.from(today)
    val averaged = months.filter { m ->
        m.isBefore(thisMonth) && !startsMidMonth(m, firstRecord) && rowsByMonth[m].orEmpty().any { it.isRecord }
    }
    return YearToDate(
        year = month.year,
        startMonth = start,
        endMonth = month,
        totals = totals,
        spendRatioPercent = spendRatioPercent(totals),
        monthlyAverageExpense = averaged.takeIf { it.isNotEmpty() }?.let { used ->
            divRound(used.sumOf { rowsByMonth[it].orEmpty().totals().expense }, used.size)
        },
        startsLate = start.monthValue > 1,
    )
}

/** 최근 몇 달을 견주는지. 360dp 에서 묶음 막대가 읽히는 개수다. */
const val TREND_MONTHS = 6

/** 큰 지출 몇 건을 보이는지 */
const val LARGEST_COUNT = 5
