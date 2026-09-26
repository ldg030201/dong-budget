package com.dong.budget.ui.stats.calc

import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.WeekdayStats
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 월별 '눈에 띄는 점'. 조건을 순서대로 검사해 맞는 것만 최대 3개 고른다.
// 문턱값은 '말할 만한 차이' 만 남기려는 것이다. 작은 차이까지 말하면 매달 같은 말이 나와 아무도 읽지 않는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * @param expense 이 달 지출 분류별
 * @param weekday 요일별 하루 평균(창이 2주보다 짧으면 null)
 * @param counted 평균에 넣는 날 범위
 * @param noSpendDays counted 안에서 돈 안 쓴 날 수
 */
fun insights(expense: Breakdown, weekday: WeekdayStats?, counted: IntRange, noSpendDays: Int?): List<Insight> = listOfNotNull(
    topShare(expense),
    categoryChange(expense),
    weekPattern(weekday),
    noSpend(counted, noSpendDays),
).take(MAX_INSIGHTS)

/** a. 1위 지출 분류가 지출의 20% 이상 */
private fun topShare(expense: Breakdown): Insight.TopShare? {
    val top = expense.entries.firstOrNull()?.takeIf { it.amount > 0 && expense.positiveTotal > 0 } ?: return null
    // 비율을 소수로 견주지 않고 정수로 곱해 견준다. 딱 20% 가 문턱에서 흔들리지 않게.
    if (top.amount * PERCENT < TOP_SHARE_PERCENT * expense.positiveTotal) return null
    return Insight.TopShare(top, divRound(top.amount * PERCENT, expense.positiveTotal).toInt())
}

/**
 * b. 지난달 대비 가장 크게 달라진 분류. 차이가 3만원 이상이면서 지난 값의 30% 이상이어야 한다.
 * 지난 값이 0 이면 비율을 낼 수 없어 금액 조건만 본다. 같은 차이면 목록에서 앞선(금액이 큰) 분류다.
 */
private fun categoryChange(expense: Breakdown): Insight.CategoryChange? {
    val candidate = expense.entries
        .mapNotNull { entry -> entry.change?.let { entry to it } }
        .maxByOrNull { (_, change) -> abs(change.current - change.previous) }
        ?: return null
    val (entry, change) = candidate
    val delta = abs(change.current - change.previous)
    val previous = abs(change.previous)
    if (delta < CHANGE_MIN_AMOUNT) return null
    if (previous > 0 && delta * PERCENT < CHANGE_MIN_PERCENT * previous) return null
    return Insight.CategoryChange(entry, change)
}

/**
 * c. 주말과 평일의 하루 평균이 1.3배 넘게 벌어졌는지. 요일 창이 2주보다 짧으면 보지 않는다.
 * 요일 섹션의 주말·평일 문장도 이 결과를 쓴다. null 이면 '비슷하게 써요' 다.
 */
fun weekPattern(weekday: WeekdayStats?): Insight.WeekPattern? {
    val ratio = weekday?.weekendRatio ?: return null
    return when {
        ratio >= WEEK_PATTERN_RATIO - RATIO_TOLERANCE -> Insight.WeekPattern(weekendHigher = true, ratio = ratio)
        ratio * WEEK_PATTERN_RATIO <= 1 + RATIO_TOLERANCE -> Insight.WeekPattern(weekendHigher = false, ratio = 1 / ratio)
        else -> null
    }
}

/** d. 센 날이 일주일 이상이고 돈 안 쓴 날이 있을 때 */
private fun noSpend(counted: IntRange, noSpendDays: Int?): Insight.NoSpendDays? {
    if (counted.count() < NO_SPEND_MIN_DAYS || noSpendDays == null || noSpendDays < 1) return null
    return Insight.NoSpendDays(noSpendDays)
}

/** 눈에 띄는 점을 몇 개까지 보이는지 */
const val MAX_INSIGHTS = 3

private const val TOP_SHARE_PERCENT = 20L
private const val CHANGE_MIN_AMOUNT = 30_000L
private const val CHANGE_MIN_PERCENT = 30L
private const val WEEK_PATTERN_RATIO = 1.3

/** 배수는 나눗셈 결과(소수)라 딱 1.3배가 1.2999… 로 나올 수 있다. 문턱에서만 이만큼 봐준다. */
private const val RATIO_TOLERANCE = 1e-9
private const val NO_SPEND_MIN_DAYS = 7
