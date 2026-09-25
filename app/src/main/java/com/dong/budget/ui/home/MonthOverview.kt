package com.dong.budget.ui.home

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.format.formatCompactWon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 홈 화면에 보여줄 숫자를 거래 목록에서 계산한다.
// 화면과 떼어 둔 이유는 단위 테스트로 계산을 확인하기 위해서다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 수입과 지출의 합.
 *
 * 이체는 내 돈이 자리만 옮긴 것이라 어디에도 넣지 않는다.
 * 환불은 쓴 돈을 되돌려 받은 것이라 지출에서 뺀다. 그래서 지출이 음수가 될 수도 있다.
 */
data class Totals(val expense: Long = 0, val income: Long = 0) {
    operator fun plus(item: TransactionListItem): Totals = when (item.type) {
        TransactionType.EXPENSE -> copy(expense = expense + item.amount)
        TransactionType.REFUND -> copy(expense = expense - item.amount)
        TransactionType.INCOME -> copy(income = income + item.amount)
        TransactionType.TRANSFER -> this
    }
}

fun Iterable<TransactionListItem>.totals(): Totals = fold(Totals()) { acc, item -> acc + item }

/** 같은 날 거래 묶음. 목록에서 날짜 구분선 하나와 그 아래 거래들이 된다. */
data class DayGroup(val date: LocalDate, val items: List<TransactionListItem>)

internal fun TransactionListItem.localDate(): LocalDate = BudgetTime.toLocalDate(occurredAt)

/**
 * 날짜별로 묶는다. 날짜는 최신 → 오래된 순, 같은 날 안에서도 늦은 시각이 먼저다.
 * 조회 쿼리가 이미 이 순서로 주지만, 여기서 다시 정렬해 두 곳의 약속이 어긋나도 목록이 흐트러지지 않게 한다.
 */
fun groupByDay(items: List<TransactionListItem>): List<DayGroup> = items
    .sortedWith(compareByDescending<TransactionListItem> { it.occurredAt }.thenByDescending { it.id })
    .groupBy { it.localDate() }
    .map { (date, dayItems) -> DayGroup(date, dayItems) }

fun dailyTotals(items: List<TransactionListItem>): Map<LocalDate, Totals> = items
    .groupBy { it.localDate() }
    .mapValues { (_, dayItems) -> dayItems.totals() }

/**
 * 달력 한 달치. 한 줄이 한 주(일요일 시작)이고, 이 달이 아닌 칸은 null 이다.
 * 마지막 주도 7칸을 채워서 모든 줄의 칸 너비가 같게 한다.
 */
fun calendarWeeks(month: YearMonth): List<List<LocalDate?>> {
    // DayOfWeek 는 월요일이 1, 일요일이 7 이다. 일요일을 0 칸으로 맞춘다.
    val leading = month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val trailing = (DAYS_IN_WEEK - cells.size % DAYS_IN_WEEK) % DAYS_IN_WEEK
    return (cells + List(trailing) { null }).chunked(DAYS_IN_WEEK)
}

/** [date] 가 들어 있는 주(일요일 시작)의 7일. 달이 바뀌는 주라도 이 달 칸만 남기고 나머지는 null 이다. */
fun weekOf(date: LocalDate, month: YearMonth): List<LocalDate?> {
    val sunday = date.minusDays((date.dayOfWeek.value % DAYS_IN_WEEK).toLong())
    return (0 until DAYS_IN_WEEK).map { offset ->
        sunday.plusDays(offset.toLong()).takeIf { YearMonth.from(it) == month }
    }
}

/** 비교 기준. 문구가 달라진다. */
enum class ComparisonScope {
    /** 이번 달: 오늘까지 쓴 돈과 지난달 1일부터 같은 날까지 쓴 돈 */
    SAME_DAY,

    /** 지나간 달: 한 달 전체끼리 */
    WHOLE_MONTH,
}

/** @property difference 이번 지출 − 지난 지출. 음수면 덜 쓴 것이다. */
data class SpendingComparison(val difference: Long, val scope: ComparisonScope)

/**
 * 지난달과 지출을 비교한다.
 *
 * 이번 달은 '지난달 같은 날까지' 와 비교한다. 지난달 전체와 비교하면
 * 월초에는 늘 크게 덜 쓴 것으로 나와서 쓸모가 없다.
 * 지난달이 이번 달보다 짧으면(예: 3월 31일 ↔ 2월) 지난달 마지막 날까지로 맞춘다.
 *
 * @return 비교할 수 없으면 null. 아직 오지 않은 달이거나, 지난달 기록이 하나도 없을 때다.
 *   지난달이 비어 있는데 비교하면 이번 달 지출 전체가 '더 쓴 돈' 으로 나와 오해를 부른다.
 */
fun compareSpending(
    month: YearMonth,
    today: LocalDate,
    current: List<TransactionListItem>,
    previous: List<TransactionListItem>,
): SpendingComparison? {
    val thisMonth = YearMonth.from(today)
    if (month.isAfter(thisMonth) || previous.isEmpty()) return null

    if (month.isBefore(thisMonth)) {
        return SpendingComparison(current.totals().expense - previous.totals().expense, ComparisonScope.WHOLE_MONTH)
    }

    val cutoff = minOf(today.dayOfMonth, month.minusMonths(1).lengthOfMonth())
    // 날짜를 바꿔 미래에 적어둔 거래는 아직 쓴 돈이 아니므로 뺀다
    val spentSoFar = current.filter { !it.localDate().isAfter(today) }.totals().expense
    val spentBefore = previous.filter { it.localDate().dayOfMonth <= cutoff }.totals().expense
    return SpendingComparison(spentSoFar - spentBefore, ComparisonScope.SAME_DAY)
}

/** 지난달 비교 문장. 금액 부분에만 색을 입힐 수 있게 셋으로 나눈다. [amount] 가 null 이면 금액이 없는 문장이다. */
data class ComparisonSentence(val prefix: String, val amount: String?, val suffix: String)

fun SpendingComparison.sentence(): ComparisonSentence {
    val base =
        when (scope) {
            ComparisonScope.SAME_DAY -> "지난달 이맘때"
            ComparisonScope.WHOLE_MONTH -> "지난달"
        }
    return when {
        difference == 0L -> ComparisonSentence("${base}만큼 썼어요", null, "")
        difference < 0 -> ComparisonSentence("${base}보다 ", formatCompactWon(difference), " 덜 썼어요")
        else -> ComparisonSentence("${base}보다 ", formatCompactWon(difference), " 더 썼어요")
    }
}

private const val DAYS_IN_WEEK = 7

/** 일요일부터 시작하는 요일 순서. 달력 머리줄에 쓴다. */
val WEEK_ORDER: List<DayOfWeek> =
    listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
    )
