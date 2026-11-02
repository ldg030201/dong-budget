package com.dong.budget.ui.savings

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.isSavings
import com.dong.budget.ui.history.historyMonths
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.calc.Grouping
import com.dong.budget.ui.stats.calc.Measure
import com.dong.budget.ui.stats.calc.breakdown
import com.dong.budget.ui.stats.calc.topMerchants
import com.dong.budget.ui.stats.calc.trendMonths
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 저축 화면의 숫자를 '저축' 분류 거래에서 계산한다. 화면과 떼어 둔 이유는 단위 테스트로 계산을 확인하기 위해서다.
// 금액은 홈 합계(Totals.savings)로 잰다. 저축은 +, 꺼낸 돈(저축 분류의 환불)은 − 이다.
// 통계 계산(breakdown · topMerchants)은 Measure.SAVINGS 로 그대로 쓴다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 저축 화면 전체.
 * @param month 통계 탭에서 고른 달
 * @param rows '저축' 분류 거래(지출 · 환불) 전부. 다른 거래가 섞여 와도 저축([isSavings])만 센다.
 */
fun buildSavings(month: YearMonth, today: LocalDate, hasCategory: Boolean, rows: List<TransactionListItem>): SavingsUiState {
    val savings = rows.filter { it.isSavings }
    val byMonth = savings.groupBy { YearMonth.from(it.localDate()) }
    return SavingsUiState(
        loaded = true,
        month = month,
        today = today,
        hasCategory = hasCategory,
        history = savingsHistory(savings, byMonth, today),
        stats = savingsStats(month, today, byMonth),
    )
}

/** 내역 탭. [byMonth] 는 [rows] 를 달로 묶은 것이다. */
internal fun savingsHistory(
    rows: List<TransactionListItem>,
    byMonth: Map<YearMonth, List<TransactionListItem>>,
    today: LocalDate,
): SavingsHistory = SavingsHistory(
    total = rows.totals().savings,
    thisMonth = byMonth[YearMonth.from(today)].orEmpty().totals().savings,
    months = historyMonths(rows),
)

/** 통계 탭. [byMonth] 는 저축 거래를 달로 묶은 것이다(달마다 처음부터 다시 거르지 않게). */
internal fun savingsStats(month: YearMonth, today: LocalDate, byMonth: Map<YearMonth, List<TransactionListItem>>): SavingsStats {
    val rows = byMonth[month].orEmpty()
    val firstMonth = byMonth.keys.minOrNull()
    fun savedIn(months: (YearMonth) -> Boolean): Long = byMonth.filterKeys(months).values.sumOf { it.totals().savings }
    return SavingsStats(
        total = rows.totals().savings,
        count = rows.size,
        comparison = savingsComparison(month, today, rows, byMonth[month.minusMonths(1)].orEmpty()),
        trend =
        trendMonths(month).map { m ->
            val items = byMonth[m].orEmpty()
            SavingsMonth(month = m, amount = items.totals().savings, count = items.size, beforeFirst = firstMonth == null || m < firstMonth)
        },
        year =
        if (firstMonth != null && firstMonth.year < month.year && month.monthValue > 1) {
            YearSavings(month.year, month, savedIn { it.year == month.year && it <= month })
        } else {
            null
        },
        cumulative = if (firstMonth != null && firstMonth < month) savedIn { it <= month } else null,
        merchants = topMerchants(rows, measure = Measure.SAVINGS),
        byPayment = breakdown(rows, Measure.SAVINGS, Grouping.PAYMENT_METHOD, window = null),
    )
}

/**
 * 지난달 대비. 홈의 지난달 비교(comparisonWindow)와 같은 범위다. 이번 달은 오늘까지 모은 돈을 지난달 같은 날까지와 견준다
 * (지난달이 짧으면 지난달 마지막 날까지). 지난달 전체와 견주면 달 초에는 늘 덜 모은 것으로 나온다.
 * 날짜를 앞으로 적어 둔 저축은 아직 모은 돈이 아니라 뺀다.
 *
 * 홈의 비교는 저축뿐인 지난달을 빈 달로 봐서 그대로 쓸 수 없다(저축만 넘기면 늘 비교하지 않는다).
 *
 * @return 오지 않은 달이거나 지난달에 저축이 없으면 null. 지난달이 비었는데 견주면 이번 달 저축 전체가 '더 모은 돈' 으로 나온다.
 */
internal fun savingsComparison(
    month: YearMonth,
    today: LocalDate,
    current: List<TransactionListItem>,
    previous: List<TransactionListItem>,
): SavingsComparison? {
    val thisMonth = YearMonth.from(today)
    if (month.isAfter(thisMonth) || previous.isEmpty()) return null
    if (month.isBefore(thisMonth)) {
        return SavingsComparison(current.totals().savings - previous.totals().savings, ComparisonScope.WHOLE_MONTH)
    }
    val cutoff = minOf(today.dayOfMonth, month.minusMonths(1).lengthOfMonth())
    val now = current.filter { !it.localDate().isAfter(today) }.totals().savings
    val before = previous.filter { it.localDate().dayOfMonth <= cutoff }.totals().savings
    return SavingsComparison(now - before, ComparisonScope.SAME_DAY)
}
