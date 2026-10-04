package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.holiday.KoreanHolidays
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 고정지출 한 가게의 일정 짐작. 한 달에 몇 번 · 며칠에 · 몇 달마다 내는지를 한 가지 원리로 고른다:
// 결제마다 가장 가까운 차례의 '낼 날' 과 며칠 떨어졌는지를 더해 가장 덜 떨어지는 일정이다.
// 낼 날은 평소 날짜부터 그날이 쉬는 날(주말 · 공휴일, KoreanHolidays)이면 다음 영업일까지다(카드는 그날, 자동이체는 다음 영업일에 나간다).
// 그래서 주말 · 연휴로 밀린 결제도, 31일이 없는 달의 말일도 따로 가리지 않고 같은 셈으로 들어온다.
// ─────────────────────────────────────────────────────────────────────

/** 고정지출 결제 한 건. 서울 날짜는 처음에 한 번만 구해 들고 다닌다. */
internal class Paid(val row: TransactionListItem, val date: LocalDate) {
    val month: YearMonth = YearMonth.from(date)
    val id: Long get() = row.id
    val amount: Long get() = row.amount
}

/** [month] 의 [day] 일. 그 달에 없는 날(2월 30일 등)이면 그 달 말일이다. */
internal fun usualDateOf(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

/**
 * 평소 [day] 일에 내는 것을 [month] 에 낼 날. 평소 날짜([usualDateOf])가 쉬는 날이면 다음 영업일이다([KoreanHolidays.nextBusinessDay]).
 * 매달 25일 것은 2026년 9월엔 추석 연휴(24~26일)와 일요일을 지나 28일이 낼 날이고, 말일 것이 다음 달 초로 밀려도 앞 달의 낼 날이다.
 */
internal fun dueDateOf(month: YearMonth, day: Int): LocalDate = KoreanHolidays.nextBusinessDay(usualDateOf(month, day))

/** 달 번호. 달 수 차이를 뺄셈 한 번으로 센다. */
internal fun YearMonth.index(): Int = year * MONTHS_IN_YEAR + monthValue - 1

internal fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()

/** 가운데 값. 짝수 개면 가운데 둘 중 작은 쪽이다. */
internal fun <T : Comparable<T>> lowerMedian(values: List<T>): T = values.sorted()[(values.size - 1) / 2]

/** [amount] 가 [reference] 와 ±[SIMILAR_AMOUNT_PERCENT]% 안인지(17,000원에 18,000원은 비슷하고 50,000원에 30,000원은 아니다) */
internal fun isNear(amount: Long, reference: Long): Boolean = abs(amount - reference) * PERCENT <= reference * SIMILAR_AMOUNT_PERCENT

/**
 * 따로 낸 결제(연간 결제 · 한 번 산 것)인지. 최근 금액([sample], 낸 차례) 가운데 [EXTRA_RATIO] 배 안쪽으로 비슷한 것이 [EXTRA_SHARE] 분의 1이
 * 안 되는 금액이다. 다만 마지막 두 결제가 비슷한 금액이면 그 금액은 값을 바꾼 것이라 따로 낸 것이 아니다(5,500원 → 17,000원).
 * 가운데 값 하나와 견주면 금액이 크게 다른 두 차례(월세 500,000원 · 관리비 100,000원)의 한쪽이 모두 따로 낸 것이 되고, 절반과 견주면
 * 최근 12건에 두 차례가 7건 · 5건으로 걸린 날 적은 쪽이 따로 낸 것이 된다.
 */
internal fun isExtra(amount: Long, sample: List<Long>): Boolean {
    val settled = sample.takeLast(2).takeIf { it.size == 2 && isNear(it[1], it[0]) }?.last()
    if (settled != null && isNear(amount, settled)) return false
    return sample.count { it <= amount * EXTRA_RATIO && amount <= it * EXTRA_RATIO } * EXTRA_SHARE < sample.size
}

/** 한 가게의 낼 날 표. (달, 평소 날)마다 낼 날을 한 번만 구한다. 짐작 · 짝짓기가 같은 달의 낼 날을 수십 번 묻는다. */
internal class DueDates {
    private val cache = HashMap<Int, LocalDate>()

    fun due(month: YearMonth, day: Int): LocalDate = cache.getOrPut(month.index() * DAY_KEYS + day) { dueDateOf(month, day) }

    /**
     * [date] 가 [month] 의 [day] 일 차례와 며칠 떨어졌는지. 평소 날짜(카드는 쉬는 날에도 그날 나간다)와 낼 날(자동이체는 다음 영업일에 나간다)
     * 가운데 가까운 쪽으로 센다.
     */
    fun distance(date: LocalDate, month: YearMonth, day: Int): Int =
        minOf(abs(daysBetween(date, usualDateOf(month, day))), abs(daysBetween(date, due(month, day))))

    /** [date] 에 가장 가까운 차례의 달(낸 달과 그 앞뒤 달 가운데, 평소 날 [days] 가운데 어느 것이든). 같으면 낸 달이다. */
    fun nearestMonth(date: LocalDate, days: List<Int>): YearMonth {
        val own = YearMonth.from(date)
        return listOf(own, own.minusMonths(1), own.plusMonths(1)).minBy { month -> days.minOf { distance(date, month, it) } }
    }
}

/**
 * 한 가게의 일정.
 * @property days 차례마다 평소 날(1~31, 31은 말일), 이른 차례부터. 한 달에 한 번 내면 하나, 서로 다른 두 때에 내면 둘(3일 · 28일)
 * @property slotAmounts 두 차례 금액이 서로 달라 금액으로 차례를 가를 수 있으면 차례마다 평소 금액(50,000원 · 30,000원). 아니면 null
 * @property cadence 몇 달마다. 1, 2~[MAX_EVERY_MONTHS], [YEARLY]
 * @property phase 몇 달마다 내면 달 번호([index])를 [cadence] 로 나눈 나머지가 이것인 달이 차례 달이다(매달이면 0)
 * @property sample 따로 낸 결제([isExtra])를 가를 때 견주는 최근 금액들
 * @property timesPerMonth 자주 내는 가게([frequentTimes])면 한 달에 보통 몇 번 내는지. 차례 없이 달력 달로 본다(그 밖에는 null).
 */
internal class FixedSchedule(
    val days: List<Int>,
    val slotAmounts: List<Long>?,
    val cadence: Int,
    val phase: Int,
    private val sample: List<Long>,
    val timesPerMonth: Int? = null,
) {
    fun isSlotMonth(month: YearMonth): Boolean = Math.floorMod(month.index() - phase, cadence) == 0

    fun isExtra(amount: Long): Boolean = isExtra(amount, sample)

    /** 금액으로 본 차례(0 · 1). 한 차례 가게이거나 어느 쪽과도(또는 두 쪽 다) 비슷하면 null */
    fun slotByAmount(amount: Long): Int? {
        val amounts = slotAmounts ?: return null
        val near = amounts.map { isNear(amount, it) }
        return if (near[0] == near[1]) null else near.indexOf(true)
    }
}

/**
 * [estimation] 결제(같은 날 본 모든 화면이 같은 것을 넘긴다)로 짐작한 일정. 따로 낸 결제([isExtra])는 빼고 센다.
 * 1. 평소 날: 낸 날(같은 날은 한 번)마다 앞뒤 달 차례 가운데 가장 가까운 것과 며칠 떨어졌는지([DueDates.distance])를 보아,
 *    딱 맞으면 0, 아니면 [MISSED] 에 [NEAR_DAYS] 일까지 센 거리를 더한 값의 합이 가장 작은 날이다(한 차례면 [singleDay], 두 차례면 최근
 *    [DAY_SAMPLES] × [MAX_SLOTS] 번으로 두 날). 딱 맞는 결제가 가장 많은 날을 고르는 셈이라 가끔 늦게 낸 것이나 결제일을 바꾸기 전 결제가
 *    평소 날을 사이 날로 끌지 않고, 말일 자동이체를 하루 차이인 1일 납부로 보지 않는다. 같으면 거리를 끝까지 더한 합,
 *    평소 날짜와 그대로 견준 합이 작은 쪽, 그래도 같으면 마지막 결제일에서 볼 때 이른 날이다(늦게 알리는 것보다 일찍 알리는 게 낫다).
 * 2. 차례 수: 두 날이 [SLOT_GAP_DAYS] 일 넘게(달 경계를 돌아서도) 떨어져 있고, 두 날로 보면 한 날로 볼 때보다 딱 맞지 않는 결제가 [CHANGE_MARGIN] 넘게
 *    줄고, 최근 달마다 두 때에 냈으면([paysTwice]) 두 차례다. 말일 것이 쉬는 날로 다음 달 초에 밀려 한 달력 달에 두 번 낸 달이 생겨도
 *    (한 날로 다 맞는다), 말일 것을 가끔 이틀 늦게 내도(두 날이 붙어 있고 달마다 한 번이다) 한 차례다.
 * 3. 주기: 결제마다 가장 가까운 차례의 달을 모아 최근 [CADENCE_GAPS] 간격의 가운데 값(짝수 개면 짧은 쪽, 늦게 알리는 것보다 일찍 알리는 게 낫다).
 * 4. 몇 달마다면 차례 달: 최근 결제가 가장 덜 떨어지는 달들(같으면 마지막 결제의 달을 지나는 쪽)이다.
 * 자주 내는 가게([frequentTimes], 평일마다 내는 돌봄 · 주 3회 PT)는 차례를 짐작하지 않고 매달 내는 것으로 둔다.
 */
internal fun estimateSchedule(estimation: List<Paid>, base: YearMonth, dues: DueDates): FixedSchedule {
    // 같은 날 낸 것도 적은 차례로 줄 세워 최근 금액의 끝이 읽은 차례(조회는 최신순)에 따라 달라지지 않게 한다
    val sorted = estimation.sortedWith(paidByTime)
    val sample = sorted.takeLast(EXTRA_SAMPLES).map { it.amount }
    frequentTimes(sorted, base)?.let { times ->
        return FixedSchedule(listOf(1), slotAmounts = null, cadence = 1, phase = 0, sample = sample, timesPerMonth = times)
    }
    val regular = sorted.filter { !isExtra(it.amount, sample) }.ifEmpty { sorted }
    // 같은 날 여러 건(밀린 몫을 함께 냄 · 나눠 냄)은 한 번으로 센다
    val dates = regular.map { it.date }.distinct()
    val lastDay = dates.last().dayOfMonth
    val costs = dates.takeLast(LONG_DAY_SAMPLES).map { dayCosts(it, dues) }
    val single = singleDay(costs, lastDay)
    val pairCosts = costs.takeLast(DAY_SAMPLES * MAX_SLOTS)
    val pair = bestPair(pairCosts, lastDay)
    val twice = minOf(pair[1] - pair[0], Math.floorMod(pair[0] - pair[1], LAST_DAY)) >= SLOT_GAP_DAYS &&
        missed(pairCosts, listOf(single)) - missed(pairCosts, pair) > CHANGE_MARGIN &&
        paysTwice(regular, base)
    val recent = regular.takeLast(DAY_SAMPLES * if (twice) MAX_SLOTS else 1)
    val days = if (twice) pair else listOf(single)
    val cadence = cadenceOf(regular, days, dues)
    return FixedSchedule(
        days = days,
        slotAmounts = slotAmountsOf(recent, days, dues),
        cadence = cadence,
        phase = phaseOf(recent, days, cadence, dues),
        sample = sample,
    )
}

/**
 * 자주 내는 가게면 한 달에 보통 몇 번 내는지, 아니면 null. 최근 [SLOT_COUNT_SPAN] 달력 달(결제가 있던 달, [base] 달 앞. 그런 달이 없으면
 * 있는 달)마다 서로 다른 결제일 수의 가운데 값이 [FREQUENT_TIMES] 를 넘는 가게다. 다른 달이 있으면 중간에 시작한 첫 달은 뺀다.
 * 날마다 · 요일마다 내는 것을 몇 월 몫의 몇 번째 차례로 나누면 이어 낸 결제가 앞뒤 달 몫으로 흩어진다.
 */
private fun frequentTimes(sorted: List<Paid>, base: YearMonth): Int? {
    val byMonth = sorted.groupBy { it.month }
    val months = byMonth.keys.filter { it < base }.ifEmpty { byMonth.keys }.sorted()
    val counted = months.drop(if (months.size > 1) 1 else 0).takeLast(SLOT_COUNT_SPAN)
    return lowerMedian(counted.map { month -> byMonth.getValue(month).map { it.date }.distinct().size }).takeIf { it > FREQUENT_TIMES }
}

/**
 * 달마다 두 때에 내는지. [base] 달 앞(아직 덜 낸 이번 달은 빼고, 마지막 결제 달이 더 앞이면 그 달)까지 최근 [SLOT_COUNT_MONTHS] 달이나
 * [SLOT_COUNT_SPAN] 달력 달(첫 결제 달 앞은 빼고, 결제가 없던 달은 0)에 낸 때([occasionsOf])가 한 달 평균 한 번 반 이상인지다.
 * 짧게 보아 회선을 더한 지 두 달이면 알고, 길게 보아 한 달을 통째로 건너뛰어도 두 차례 그대로다.
 * 말일 것이 밀려 한 달력 달에 두 번 낸 달이 생겨도 그만큼 빈 달이 생겨 평균은 한 번쯤이다.
 */
private fun paysTwice(regular: List<Paid>, base: YearMonth): Boolean {
    val byMonth = regular.groupBy { it.month }
    val end = minOf(base.minusMonths(1), byMonth.keys.max())
    val recorded = end.index() - byMonth.keys.min().index() + 1
    return listOf(SLOT_COUNT_MONTHS, SLOT_COUNT_SPAN).any { months ->
        val span = recorded.coerceIn(1, months)
        (0 until span).sumOf { occasionsOf(byMonth[end.minusMonths(it.toLong())].orEmpty()) } * 2 >= span * 3
    }
}

/** 낸 때의 수. 같은 날이나 [OCCASION_DAYS] 일 안에 이어 낸 것은 한 때다(1일 · 2일에 나눠 낸 월세는 한 차례에 두 건). */
private fun occasionsOf(pays: List<Paid>): Int {
    val dates = pays.map { it.date }.distinct().sorted()
    return if (dates.isEmpty()) 0 else 1 + dates.zipWithNext().count { (a, b) -> daysBetween(a, b) > OCCASION_DAYS }
}

/**
 * 평소 날 1~31(첨자) 마다 [date] 가 가장 가까운 차례(앞뒤 달 가운데 거리가 가장 짧은 것)와 떨어진 값. 딱 맞지 않은 값([MISSED] +
 * [NEAR_DAYS] 일까지 센 거리)을 윗자리에, 끝까지 센 거리와 평소 날짜와 그대로 견준 거리를 아랫자리에 두어 한 번의 견줌으로 차례대로 가린다.
 */
private fun dayCosts(date: LocalDate, dues: DueDates): IntArray {
    val own = YearMonth.from(date)
    val months = listOf(own.minusMonths(1), own, own.plusMonths(1))
    return IntArray(LAST_DAY + 1) { day ->
        if (day == 0) return@IntArray 0
        val unshifted = { month: YearMonth -> abs(daysBetween(date, usualDateOf(month, day))) }
        val month = months.minWith(compareBy<YearMonth> { dues.distance(date, it, day) }.thenBy(unshifted))
        val distance = dues.distance(date, month, day)
        val missed = if (distance == 0) 0 else MISSED + minOf(distance, NEAR_DAYS)
        (missed * TIE_SCALE + distance) * TIE_SCALE + unshifted(month)
    }
}

/**
 * 한 차례 가게의 평소 날. 최근 [LONG_DAY_SAMPLES] 번 낸 날로 고르되, 최근 [DAY_SAMPLES] 번만으로 고른 날이 그 여섯 번에서
 * [CHANGE_MARGIN] 넘게 더 잘 맞으면 결제일을 바꾼 것으로 보고 그 날이다(바꾼 뒤 넷째 결제부터 새 날). 짧게만 보면 1일 월세를 가끔
 * 전달 말에 미리 낸 것이 주말로 밀린 1일 결제와 겹쳐 말일 납부처럼 보이고, 석 달에 한 번 이틀 늦게 낸 말일 관리비가 2일 납부처럼 보인다.
 */
private fun singleDay(costs: List<IntArray>, lastDay: Int): Int {
    val recent = costs.takeLast(DAY_SAMPLES)
    val long = bestDay(costs.takeLast(LONG_DAY_SAMPLES), lastDay)
    val short = bestDay(recent, lastDay)
    return if (missed(recent, listOf(long)) - missed(recent, listOf(short)) > CHANGE_MARGIN) short else long
}

/** [days] 가운데 가까운 날로 볼 때 [costs] 결제들의 딱 맞지 않은 값의 합 */
private fun missed(costs: List<IntArray>, days: List<Int>): Int = costs.sumOf { cost -> days.minOf { cost[it] } / (TIE_SCALE * TIE_SCALE) }

private fun bestDay(costs: List<IntArray>, lastDay: Int): Int =
    (1..LAST_DAY).minWith(compareBy<Int> { day -> costs.sumOf { it[day] } }.thenBy { offset(it, lastDay) })

private fun bestPair(costs: List<IntArray>, lastDay: Int): List<Int> {
    val pairs = (1 until LAST_DAY).flatMap { early -> (early + 1..LAST_DAY).map { listOf(early, it) } }
    return pairs.minWith(
        compareBy<List<Int>> { (early, late) -> costs.sumOf { minOf(it[early], it[late]) } }
            .thenBy { offset(it[0], lastDay) }
            .thenBy { offset(it[1], lastDay) },
    )
}

/** [day] 가 [lastDay] 에서 달 경계를 돌아 며칠 앞(음수) · 뒤(양수)인지. 31일은 1일의 하루 앞이다. */
private fun offset(day: Int, lastDay: Int): Int = Math.floorMod(day - lastDay + LAST_DAY / 2, LAST_DAY) - LAST_DAY / 2

private fun cadenceOf(regular: List<Paid>, days: List<Int>, dues: DueDates): Int {
    val months = regular.map { dues.nearestMonth(it.date, days) }.distinct().sorted()
    val gaps = months.takeLast(CADENCE_GAPS + 1).zipWithNext { a, b -> b.index() - a.index() }
    if (gaps.isEmpty()) return 1
    val gap = lowerMedian(gaps)
    return when {
        gap <= 1 -> 1
        gap <= MAX_EVERY_MONTHS -> gap
        else -> YEARLY
    }
}

private fun phaseOf(recent: List<Paid>, days: List<Int>, cadence: Int, dues: DueDates): Int {
    if (cadence == 1) return 0
    fun cost(date: LocalDate, phase: Int): Int {
        val own = YearMonth.from(date)
        return (-cadence..cadence)
            .map { own.plusMonths(it.toLong()) }
            .filter { Math.floorMod(it.index() - phase, cadence) == 0 }
            .minOf { month -> days.minOf { dues.distance(date, month, it) } }
    }
    val last = Math.floorMod(dues.nearestMonth(recent.last().date, days).index(), cadence)
    return (0 until cadence).minWith(compareBy<Int> { phase -> recent.sumOf { cost(it.date, phase) } }.thenBy { if (it == last) 0 else 1 })
}

/** 두 차례 가게의 차례마다 평소 금액(가까운 차례로 나눈 결제 금액의 가운데 값). 두 금액이 비슷하면 금액으로 가를 수 없어 null */
private fun slotAmountsOf(recent: List<Paid>, days: List<Int>, dues: DueDates): List<Long>? {
    if (days.size < MAX_SLOTS) return null
    val bySlot = recent.groupBy { pay ->
        val month = dues.nearestMonth(pay.date, days)
        days.indices.minBy { dues.distance(pay.date, month, days[it]) }
    }
    val amounts = days.indices.map { slot -> bySlot[slot]?.let { pays -> lowerMedian(pays.map { it.amount }) } ?: return null }
    return amounts.takeUnless { isNear(it[0], it[1]) || isNear(it[1], it[0]) }
}

/**
 * 달마다 내야 하는 건수([requiredIn]). 그 달 앞(마지막 결제 달이 더 앞이면 그 달)까지 최근 [SLOT_COUNT_MONTHS] 달의 건수
 * (따로 낸 것은 빼고, 결제가 없던 달은 0)의 가운데 값이고 적어도 1이다. 다만 바로 앞 달에 한 건이라도 냈으면 그 달 건수를 넘지 않는다
 * (두 청구 가운데 하나를 해지하면 다음 달부터 한 건이다. 해지한 달은 아직 안 낸 것과 가를 수 없다). 그 달 앞 기록만 보므로 덜 낸 달이
 * 제 건수를 낮추지 않고, 회선을 하나 더한 뒤에도 그 전 달들은 한 건이면 다 낸 것이며, 같은 날 본 모든 화면이 같은 달에 같은 수를 쓴다.
 * 건수는 결제를 차례에 짝지은 몫 달로 센다([matched]). 짝짓기 전에는 평소 날 [days] 의 가장 가까운 차례 달로 어림한다.
 */
internal class MonthCounts(private val counts: Map<YearMonth, Int>) {
    constructor(regular: List<Paid>, days: List<Int>, dues: DueDates) :
        this(regular.groupingBy { dues.nearestMonth(it.date, days) }.eachCount())

    private val last = counts.keys.maxOrNull()

    fun requiredIn(month: YearMonth): Int {
        val end = minOf(month.minusMonths(1), last ?: return 1)
        val usual = lowerMedian((0 until SLOT_COUNT_MONTHS).map { counts[end.minusMonths(it.toLong())] ?: 0 })
        val latest = counts[end] ?: 0
        return (if (latest > 0) minOf(usual, latest) else usual).coerceAtLeast(1)
    }

    companion object {
        /** 짝지은 몫 달마다 건수. 가까운 차례 달로 어림하면 두 차례 가운데쯤 낸 결제(결제일을 15일 옮긴 달)가 한 달에 둘로 몰린다. */
        fun matched(matching: Matching): MonthCounts = MonthCounts(matching.slotOf.values.groupingBy { it.month }.eachCount())
    }
}

/** 따로 낸 결제를 가를 때 견주는 최근 결제 수 */
private const val EXTRA_SAMPLES = 12

/** 평소 날을 짐작할 때 보는 최근 결제 수(한 차례마다). 결제일을 바꾸면 넷째 결제부터 새 날이 된다. */
private const val DAY_SAMPLES = 6

/** 한 차례 가게의 평소 날을 길게 볼 때의 최근 결제 수([singleDay]). 주말 · 연휴가 몰린 해에도 말일과 1일을 가를 만큼 길다. */
private const val LONG_DAY_SAMPLES = 24

/** 최근 결제만으로 고른 평소 날이 이만큼 넘게 더 잘 맞으면 결제일을 바꾼 것으로 본다([singleDay]). 딱 맞지 않은 결제 하나쯤의 값이다. */
private const val CHANGE_MARGIN = 5

/** 평소 날을 짐작할 때 차례에 딱 맞지 않은 결제에 더하는 값 */
private const val MISSED = 2

/** 평소 날을 짐작할 때 결제와 차례의 거리를 이 날 수까지만 센다(그보다 떨어진 결제는 모두 같은 값) */
private const val NEAR_DAYS = 3

/** 한 달에 몇 차례 · 몇 번 내야 하는지([MonthCounts]) 볼 때 보는 최근 달 수(결제가 없던 달 포함) */
private const val SLOT_COUNT_MONTHS = 3

/** 한 달에 두 때에 내는지([paysTwice]) 길게 볼 때의 최근 달 수 */
private const val SLOT_COUNT_SPAN = 6

/** 두 차례로 보는 두 평소 날의 가장 짧은 사이(앞 날에서 달 경계를 돌아 뒤 날까지 · 뒤 날에서 다음 달 앞 날까지 가운데 짧은 쪽) */
private const val SLOT_GAP_DAYS = 6

/** 한 달에 서로 다른 날 이보다 많이 내면 자주 내는 가게다([frequentTimes]) */
private const val FREQUENT_TIMES = 3

/** 한 달 차례는 많아야 둘이다(셋 넘게 나눠 내는 가게는 두 차례에 건수를 나눈다) */
internal const val MAX_SLOTS = 2

/** 이만큼 안에 이어 낸 것은 한 때로 센다([occasionsOf]) */
internal const val OCCASION_DAYS = 3

/** 주기를 짐작할 때 보는 최근 간격 수. 한 번 건너뛴 정도는 가운데 값이 흡수한다. */
private const val CADENCE_GAPS = 5

/** 'N달마다' 로 보는 가장 긴 주기. 이보다 길면 매년이다. */
private const val MAX_EVERY_MONTHS = 10

/** 따로 낸 결제로 보는 금액 차이(배) */
private const val EXTRA_RATIO = 3

/** 최근 금액 가운데 비슷한 것이 이만큼 분의 1이 안 되면 따로 낸 결제다([isExtra]) */
private const val EXTRA_SHARE = 4

/** 비슷한 금액으로 보는 폭(%) */
private const val SIMILAR_AMOUNT_PERCENT = 20L

private const val PERCENT = 100L

/** 값을 자리마다 섞는 자릿수(아랫자리의 합이 이보다 작다) */
private const val TIE_SCALE = 1024

/** 낼 날 표의 열쇠에서 한 달이 차지하는 칸 수 */
private const val DAY_KEYS = 32

private const val MONTHS_IN_YEAR = 12
