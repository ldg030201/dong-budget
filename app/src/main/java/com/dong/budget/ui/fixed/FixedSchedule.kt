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
// 다음 영업일이 다음 달로 넘어가는 말일 것은 그 달 안의 앞 영업일에 미리 빼 가는 자동이체도 있어, 그날도 딱 맞는 날로 센다
// (알림은 낼 날이 지나야 한다).
// 그래서 주말 · 연휴로 밀리거나 당겨진 결제도, 31일이 없는 달의 말일도 따로 가리지 않고 같은 셈으로 들어온다.
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
 * 따로 낸 결제(연간 결제 · 한 번 산 것)들의 id. [sorted] 는 낸 차례다([paidByTime]). 결제마다 그 앞뒤로 가까운 결제([isExtraAt])와
 * 견주므로, 금액을 크게 바꾼 뒤에도 바꾸기 전 결제는 그때의 평소 금액이다. 모든 결제가 그렇다면 따로 낸 것은 없다.
 */
internal fun extrasOf(sorted: List<Paid>): Set<Long> {
    val amounts = sorted.map { it.amount }
    val extras = sorted.indices.filter { isExtraAt(amounts, it) }.map { sorted[it].id }.toSet()
    return if (extras.size < sorted.size) extras else emptySet()
}

/**
 * [amounts] (낸 차례)의 [index] 번째 결제가 따로 낸 것인지. 앞뒤로 가까운 [EXTRA_SAMPLES] 건과 그 결제 가운데 [EXTRA_RATIO] 배 안쪽으로
 * 비슷한 금액이 [EXTRA_SHARE] 분의 1이 안 되고, 바로 앞이나 뒤 결제와도 비슷하지 않은 결제다. 바로 앞이나 뒤와 비슷하면 값을 바꾼 것이다
 * (5,500원 → 17,000원은 두 번째 새 금액부터 평소 금액). 절반과 견주면 금액이 크게 다른 두 청구(관리비 200,000원 · 주차 30,000원)가
 * 7건 · 5건으로 걸린 때 적은 쪽이 따로 낸 것이 된다.
 */
internal fun isExtraAt(amounts: List<Long>, index: Int): Boolean {
    val amount = amounts[index]
    if (listOfNotNull(amounts.getOrNull(index - 1), amounts.getOrNull(index + 1)).any { isNear(amount, it) }) return false
    val from = (index - EXTRA_SAMPLES / 2).coerceIn(0, maxOf(0, amounts.size - EXTRA_SAMPLES - 1))
    val window = amounts.subList(from, minOf(amounts.size, from + EXTRA_SAMPLES + 1))
    return window.count { it <= amount * EXTRA_RATIO && amount <= it * EXTRA_RATIO } * EXTRA_SHARE < window.size
}

/**
 * (달, 평소 날) 한 차례가 나갈 수 있는 날들. 거리를 뺄셈으로 세게 날짜를 에포크 날 수로도 들고 있다.
 * @property due 낼 날([dueDateOf]). 평소 날짜가 쉬는 날이면 다음 영업일이다(자동이체).
 * @property early 쉬는 날 앞에 미리 빼 가면 나가는 날. 낼 날이 다음 달로 넘어가면(말일 것이 쉬는 날) 그 달 안에 내려고 그 달 안의 앞 영업일에
 *   미리 빼 가는 자동이체가 있어 그날이고([KoreanHolidays.previousBusinessDay]), 아니면 평소 날짜다. 낼 날이 그 달 안이면 다음 영업일에
 *   나가므로 미리 빼 가지 않는다(모든 날에 앞 영업일을 맞는 날로 보면 30일 · 8일 자동이체가 쉬는 날이 낀 달마다 말일 · 10일에도 맞아
 *   평소 날을 뒤 날로 잘못 골랐다). 앞 영업일이 앞 달이면(1일부터 쉬는 날) 앞 달 말일 납부와 가를 수 없어 평소 날짜로 둔다.
 *   짐작과 짝짓기에서만 딱 맞는 날로 보고, 알림은 낼 날이 지나야 한다.
 */
internal class DueWindow(month: YearMonth, day: Int) {
    val due: LocalDate = dueDateOf(month, day)
    private val dueDay = due.toEpochDay()
    private val usual = usualDateOf(month, day).toEpochDay()
    private val early = KoreanHolidays.previousBusinessDay(usualDateOf(month, day))
        .takeIf { YearMonth.from(due) != month && YearMonth.from(it) == month }?.toEpochDay() ?: usual

    /** 이 차례에 딱 맞게 나갈 수 있는 첫날(평소 날짜나 [early], 쉬는 날이면 낼 날 전이다)의 에포크 날 수 */
    val opens: Long = minOf(usual, early)

    /** 에포크 날 수 [epochDay] 가 평소 날짜와 며칠 떨어졌는지 */
    fun plain(epochDay: Long): Int = abs(epochDay - usual).toInt()

    /**
     * 에포크 날 수 [epochDay] 가 이 차례와 며칠 떨어졌는지. 평소 날짜(카드는 쉬는 날에도 그날 나간다), 낼 날(자동이체는 다음 영업일에
     * 나간다), 앞 영업일([early], 쉬는 날 앞에 미리 빼 가는 자동이체) 가운데 가까운 쪽으로 센다.
     */
    fun distance(epochDay: Long): Int = minOf(abs(epochDay - usual), abs(epochDay - dueDay), abs(epochDay - early)).toInt()
}

/**
 * 낼 날 표. (달, 평소 날)마다 나갈 수 있는 날들([DueWindow])을 한 번만 구한다. 가게와 상관없어 한 번 계산하는 모든 가게의 짐작 · 짝짓기 ·
 * 정렬이 한 표를 함께 쓴다([buildFixedExpenses]). 짐작은 결제마다 앞뒤 달의 1~31일을 모두 묻는다.
 */
internal class DueDates {
    private val cache = HashMap<Int, DueWindow>()

    /** 구해 둔 차례 수 */
    val size: Int get() = cache.size

    fun window(month: YearMonth, day: Int): DueWindow = cache.getOrPut(month.index() * DAY_KEYS + day) { DueWindow(month, day) }

    fun due(month: YearMonth, day: Int): LocalDate = window(month, day).due

    /** [date] 가 [month] 의 [day] 일 차례와 며칠 떨어졌는지([DueWindow.distance]) */
    fun distance(date: LocalDate, month: YearMonth, day: Int): Int = window(month, day).distance(date.toEpochDay())

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
 * @property timesPerMonth 자주 내는 가게([frequentTimes])면 한 달에 보통 몇 번 내는지. 차례 없이 달력 달로 본다(그 밖에는 null).
 */
internal class FixedSchedule(
    val days: List<Int>,
    val slotAmounts: List<Long>?,
    val cadence: Int,
    val phase: Int,
    val timesPerMonth: Int? = null,
) {
    fun isSlotMonth(month: YearMonth): Boolean = Math.floorMod(month.index() - phase, cadence) == 0

    /** 금액으로 본 차례(0 · 1). 한 차례 가게이거나 어느 쪽과도(또는 두 쪽 다) 비슷하면 null */
    fun slotByAmount(amount: Long): Int? {
        val amounts = slotAmounts ?: return null
        val near = amounts.map { isNear(amount, it) }
        return if (near[0] == near[1]) null else near.indexOf(true)
    }
}

/**
 * [estimation] 결제(같은 날 본 모든 화면이 같은 것을 넘긴다)로 짐작한 일정. 따로 낸 결제([extrasOf])는 빼고 센다.
 * 1. 평소 날: 낸 날(같은 날은 한 번)마다 앞뒤 달 차례 가운데 가장 가까운 것과 며칠 떨어졌는지([DueDates.distance])를 보아,
 *    딱 맞으면 0, 아니면 [MISSED] 에 [NEAR_DAYS] 일까지 센 거리를 더한 값의 합이 가장 작은 날이다(한 차례면 [singleDay], 두 차례면 최근
 *    [DAY_SAMPLES] × [MAX_SLOTS] 번으로 두 날). 딱 맞는 결제가 가장 많은 날을 고르는 셈이라 가끔 늦게 낸 것이나 결제일을 바꾸기 전 결제가
 *    평소 날을 사이 날로 끌지 않고, 말일 자동이체를 하루 차이인 1일 납부로 보지 않는다. 같으면 거리를 끝까지 더한 합,
 *    평소 날짜와 그대로 견준 합이 작은 쪽, 그래도 같으면 마지막 결제일에서 볼 때 이른 날이다(늦게 알리는 것보다 일찍 알리는 게 낫다).
 * 2. 차례 수: 두 날로 보면 한 날로 볼 때보다 딱 맞지 않는 결제가 [CHANGE_MARGIN] 넘게 줄고, 최근 달마다 두 때에 냈으면([paysTwice])
 *    두 차례다. 두 날이 하루만 떨어져도 된다. 하루 · 며칠 사이로 따로 나가는 두 청구(5일 · 6일, 5일 · 9일, 25일 · 28일)도, 1일과 말일에
 *    나가는 두 청구도, 달마다 1일 · 2일에 나눠 내는 월세도 두 차례다(남은 것의 날 전에는 지났다고 하지 않는다). 말일 것이 쉬는 날로 다음 달
 *    초에 밀려 한 달력 달에 두 번 낸 달이 생겨도(한 날로 다 맞고 달마다 한 번이다), 말일 것을 가끔 이틀 늦게 내도, 한 달에 한 번 내는 것이
 *    5일 · 6일로 하루씩 흔들려도(달마다 한 때다) 한 차례다. 나눠 낸 몫을 같은 날 낼 때도 이튿날 낼 때도 있으면 한 달 평균 때 수로 갈린다.
 * 3. 주기: 결제마다 가장 가까운 차례의 달을 모아 최근 [CADENCE_GAPS] 간격의 가운데 값(짝수 개면 짧은 쪽, 늦게 알리는 것보다 일찍 알리는 게 낫다).
 * 4. 몇 달마다면 차례 달: 최근 결제가 가장 덜 떨어지는 달들(같으면 마지막 결제의 달을 지나는 쪽)이다.
 * 자주 내는 가게([frequentTimes], 평일마다 내는 돌봄 · 주 3회 PT)는 차례를 짐작하지 않고 매달 내는 것으로 둔다.
 */
internal fun estimateSchedule(estimation: List<Paid>, base: YearMonth, dues: DueDates): FixedSchedule {
    // 같은 날 낸 것도 적은 차례로 줄 세워 최근 금액의 끝이 읽은 차례(조회는 최신순)에 따라 달라지지 않게 한다
    val sorted = estimation.sortedWith(paidByTime)
    frequentTimes(sorted, base)?.let { times ->
        return FixedSchedule(listOf(1), slotAmounts = null, cadence = 1, phase = 0, timesPerMonth = times)
    }
    val extras = extrasOf(sorted)
    val regular = sorted.filter { it.id !in extras }
    // 같은 날 여러 건(밀린 몫을 함께 냄 · 나눠 냄)은 한 번으로 센다
    val dates = regular.map { it.date }.distinct()
    val lastDay = dates.last().dayOfMonth
    val costs = dates.takeLast(LONG_DAY_SAMPLES).map { dayCosts(it, dues) }
    val single = singleDay(costs, lastDay)
    val pairCosts = costs.takeLast(DAY_SAMPLES * MAX_SLOTS)
    val pair = bestPair(pairCosts, lastDay)
    val twice = missed(pairCosts, listOf(single)) - missed(pairCosts, pair) > CHANGE_MARGIN && paysTwice(regular, base, pair, dues)
    val recent = regular.takeLast(DAY_SAMPLES * if (twice) MAX_SLOTS else 1)
    val days = if (twice) pair else listOf(single)
    val cadence = cadenceOf(regular, days, dues)
    return FixedSchedule(
        days = days,
        slotAmounts = slotAmountsOf(recent, days, dues),
        cadence = cadence,
        phase = phaseOf(recent, days, cadence, dues),
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
 * 달마다 두 때에 내는지. 결제를 두 평소 날 [pair] 의 가장 가까운 차례 달로 모아(말일 것이 쉬는 날로 다음 달 초에 밀려도 그 달 몫이다)
 * [base] 달 앞(아직 덜 낸 이번 달은 빼고, 마지막 결제 달이 더 앞이면 그 달)까지 최근 [SLOT_COUNT_MONTHS] 달이나
 * [SLOT_COUNT_SPAN] 달(첫 결제 달 앞은 빼고, 결제가 없던 달은 0)에 낸 때([occasionsOf])가 한 달 평균 한 번 반 이상인지다.
 * 짧게 보아 회선을 더한 지 두 달이면 알고, 길게 보아 한 달을 통째로 건너뛰어도 두 차례 그대로다.
 * 사이 달에 결제가 한 번도 없는 몇 달마다 내는 가게([strictCadence])면 차례 달(마지막 결제 달부터 주기마다)만 센다. 사이 달(늘 0)까지
 * 세면 2달마다 3일 · 20일에 내는 가게가 평균 한 번이라 매달 한 차례로 보아, 차례 달 20일 결제를 사이 달 3일 몫으로 짝지었다.
 * 두 평소 날의 낼 날이 같은 달(5일 · 6일이 주말 · 연휴로 함께 7일에 밀림)에 한 때에 냈으면 따로 나가는 두 청구도 한날 나가 날짜로는
 * 두 때인지 가를 수 없어 세지 않는다. 그런 달까지 한 때로 세면 쉬는 날이 몰린 철(1월 · 3월 · 5월)에 하루 차이 두 청구를 한 차례로
 * 보았다. 그런 달에도 서로 다른 날 냈으면(카드는 쉬는 날에도 그날 나간다) 두 때인 증거라 센다.
 */
private fun paysTwice(regular: List<Paid>, base: YearMonth, pair: List<Int>, dues: DueDates): Boolean {
    val byMonth = regular.groupBy { dues.nearestMonth(it.date, pair) }
    val step = strictCadence(regular)
    // 몇 달마다면 이번 달 앞의 마지막 결제 달(차례 달)부터 센다
    val last = if (step == 1) base.minusMonths(1) else byMonth.keys.filter { it < base }.maxOrNull() ?: byMonth.keys.max()
    val end = minOf(last, byMonth.keys.max())
    val recorded = (end.index() - byMonth.keys.min().index()) / step + 1
    return listOf(SLOT_COUNT_MONTHS, SLOT_COUNT_SPAN).any { months ->
        val counted = (0 until recorded.coerceIn(1, months))
            .map { end.minusMonths(it.toLong() * step) }
            .filter { dues.due(it, pair[0]) != dues.due(it, pair[1]) || occasionsOf(byMonth[it].orEmpty()) > 1 }
        counted.isNotEmpty() && counted.sumOf { occasionsOf(byMonth[it].orEmpty()) } * 2 >= counted.size * 3
    }
}

/**
 * 결제한 달력 달로 본 주기. 최근 [CADENCE_GAPS] 간격이 모두 같은 달 수(2 이상)의 배수면 그 달 수, 아니면(간격이 그보다 적은 짧은 기록도) 1이다.
 * 사이 달에 결제가 한 번도 없는 몇 달마다 내는 가게만 그렇다. 가끔 다음 달 초로 늦게 내 결제가 없는 달이 생긴 매달 것(말일 관리비를 석 달에
 * 한 번 이틀 늦게 냄)은 간격이 1 · 2 로 섞여 1이다.
 */
private fun strictCadence(regular: List<Paid>): Int {
    val months = regular.map { it.month }.distinct().sorted().takeLast(CADENCE_GAPS + 1)
    val gaps = months.zipWithNext { a, b -> b.index() - a.index() }
    val gap = gaps.minOrNull()?.takeIf { gaps.size == CADENCE_GAPS } ?: return 1
    return if (gap > 1 && gaps.all { it % gap == 0 }) gap else 1
}

/**
 * 낸 때의 수. 서로 다른 날마다 한 때다(같은 날 여러 건은 한 때). 하루 차이로 따로 나가는 두 청구(5일 · 6일)도, 1일 · 2일에 나눠 낸
 * 월세도 두 때다. 둘이 쉬는 날로 같은 영업일에 밀려 나간 달은 한 때다(그런 달은 [paysTwice] 가 세지 않는다).
 */
private fun occasionsOf(pays: List<Paid>): Int = pays.map { it.date }.distinct().size

/**
 * 평소 날 1~31(첨자) 마다 [date] 가 가장 가까운 차례(앞뒤 달 가운데 거리가 가장 짧은 것)와 떨어진 값. 딱 맞지 않은 값([MISSED] +
 * [NEAR_DAYS] 일까지 센 거리)을 윗자리에, 끝까지 센 거리와 평소 날짜와 그대로 견준 거리를 아랫자리에 두어 한 번의 견줌으로 차례대로 가린다.
 */
private fun dayCosts(date: LocalDate, dues: DueDates): IntArray {
    val own = YearMonth.from(date)
    val months = arrayOf(own.minusMonths(1), own, own.plusMonths(1))
    val epochDay = date.toEpochDay()
    return IntArray(LAST_DAY + 1) { day ->
        if (day == 0) return@IntArray 0
        // 앞뒤 달마다 거리를 한 번씩만 구해 가장 가까운 차례(같으면 평소 날짜와 그대로 견준 거리가 짧은 쪽, 그래도 같으면 앞 달)를 고른다
        var distance = Int.MAX_VALUE
        var unshifted = Int.MAX_VALUE
        for (month in months) {
            val window = dues.window(month, day)
            val plain = window.plain(epochDay)
            val shifted = window.distance(epochDay)
            if (shifted < distance || (shifted == distance && plain < unshifted)) {
                distance = shifted
                unshifted = plain
            }
        }
        val missed = if (distance == 0) 0 else MISSED + minOf(distance, NEAR_DAYS)
        (missed * TIE_SCALE + distance) * TIE_SCALE + unshifted
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

/**
 * 두 차례 가게의 차례마다 평소 금액(가까운 차례로 나눈 결제 금액의 가운데 값). 두 금액이 비슷하면 금액으로 가를 수 없어 null.
 * 어느 차례든 가장 최근 결제가 평소 금액과 비슷하지 않으면(금액을 바꾸는 중) 그것도 null 이다. 가운데 값은 바뀐 금액을 몇 달 늦게 따라가서,
 * 15일 것이 1일 것과 같은 금액으로 바뀌거나 두 금액이 맞바뀐 뒤 그동안 바뀐 결제를 다른 차례 금액으로 보아 제 차례를 메우지 못했다.
 * 두 차례가 같은 날이라(30일 · 말일 두 청구의 30일 달, 둘이 쉬는 날로 한날 밀린 달) 날짜로 어느 차례인지 모르는 결제는 빼고 센다.
 * 그런 결제를 앞 차례로 세면 두 금액이 섞여 금액으로 가를 수 없게 되고, 한 청구를 해지한 뒤 남은 결제가 해지한 차례에 짝지어진다.
 */
private fun slotAmountsOf(recent: List<Paid>, days: List<Int>, dues: DueDates): List<Long>? {
    if (days.size < MAX_SLOTS) return null
    val bySlot = recent.groupBy { pay ->
        val month = dues.nearestMonth(pay.date, days)
        val distances = days.map { dues.distance(pay.date, month, it) }
        distances.indices.minBy { distances[it] }.takeIf { nearest -> distances.count { it == distances[nearest] } == 1 }
    }
    val amounts = days.indices.map { slot ->
        val pays = bySlot[slot] ?: return null
        lowerMedian(pays.map { it.amount }).takeIf { isNear(pays.last().amount, it) } ?: return null
    }
    return amounts.takeUnless { isNear(it[0], it[1]) || isNear(it[1], it[0]) }
}

/**
 * 달마다 내야 하는 건수([requiredIn]). 그 달 앞 차례 달(마지막 결제 달이 더 앞이면 그 달)까지 최근 [SLOT_COUNT_MONTHS] 차례 달의 건수
 * (따로 낸 것은 빼고, 첫 결제 달 앞은 빼고, 결제가 없던 달은 0)의 가운데 값이고 적어도 1이다. 첫 결제 달 앞을 0으로 세면 두 건씩 내는
 * 새 가게가 둘째 · 셋째 달에 한 건으로 떨어져, 쉬는 날로 다음 달 초에 밀린 두 건 가운데 하나를 다음 달 몫으로 보았다.
 * 차례 달은 [cadence] 달마다 [phase] 인 달이라([FixedSchedule.isSlotMonth]) 2달마다 내는 가게는 사이 달(늘 0)을 세지 않는다.
 * 다만 바로 앞 차례 달에 한 건이라도 냈으면 그 달 건수를 넘지 않는다(두 청구 가운데 하나를 해지하면 다음 달부터 한 건이다. 해지한 달은
 * 아직 안 낸 것과 가를 수 없다). 그 달 앞 기록만 보므로 덜 낸 달이 제 건수를 낮추지 않고, 회선을 하나 더한 뒤에도 그 전 달들은 한 건이면 다 낸 것이며, 같은 날 본 모든 화면이 같은 달에 같은 수를 쓴다.
 * 건수는 결제를 차례에 짝지은 몫 달로 센다([matched]). 짝짓기 전에는 결제마다 가장 가까운 차례 달로 어림한다.
 * [bySlot] 은 몫 달마다 차례(번호)별 건수다([sharesIn]). 어림에서는 결제마다 가장 가까운 차례로 세고, 어느 차례인지 모르는 결제가 있는 달은
 * 없다([nearestSlots]).
 */
internal class MonthCounts(
    private val counts: Map<YearMonth, Int>,
    private val bySlot: Map<YearMonth, Map<Int, Int>> = emptyMap(),
    private val cadence: Int = 1,
    private val phase: Int = 0,
) {
    constructor(regular: List<Paid>, schedule: FixedSchedule, dues: DueDates) : this(
        regular.groupingBy { dues.nearestMonth(it.date, schedule.days) }.eachCount(),
        nearestSlots(regular, schedule.days, dues),
        schedule.cadence,
        schedule.phase,
    )

    private val first = counts.keys.minOrNull()
    private val last = counts.keys.maxOrNull()

    /** [month] 이거나 그 앞의 가장 가까운 차례 달 */
    private fun slotMonthUntil(month: YearMonth): YearMonth = month.minusMonths(Math.floorMod(month.index() - phase, cadence).toLong())

    /** [month] 의 건수를 정하는 달. 그 달 앞 차례 달(마지막 결제 달이 더 앞이면 그 달까지의 차례 달)이고, 결제가 없으면 null */
    fun endOf(month: YearMonth): YearMonth? = last?.let { slotMonthUntil(minOf(month.minusMonths(1), it)) }

    /**
     * [month] 의 건수. [lowers] 가 아니면 앞 차례 달이 덜 낸 건수로 낮추지 않는다(화면이 오늘 보아 앞 달의 덜 낸 차례를 아직 기다릴 때,
     * 쉬는 날로 이번 달 초에 밀린 말일 것). 짝짓기는 오늘과 상관없이 늘 낮춘다.
     */
    fun requiredIn(month: YearMonth, lowers: Boolean = true): Int {
        val end = endOf(month) ?: return 1
        val start = first ?: return 1
        val months = (0 until SLOT_COUNT_MONTHS).map { end.minusMonths(it.toLong() * cadence) }.filter { it >= start }.ifEmpty { return 1 }
        val usual = lowerMedian(months.map { counts[it] ?: 0 })
        val latest = counts[end] ?: 0
        return (if (latest > 0 && lowers) minOf(usual, latest) else usual).coerceAtLeast(1)
    }

    /**
     * [month] 의 [slots] 차례마다 건수. 건수를 정한 달([requiredIn] 이 보는 앞 달)에 차례마다 짝지은 건수의 합이 그 건수와 같으면
     * 그 나눔이다. 앞 차례를 해지하면 다음 달부터 뒤 차례만 한 건이고(앞 차례부터 채우면 없는 차례를 기다렸다), 5일 한 건 · 21일 두 건이면
     * 1 · 2, 그 가운데 5일 것을 해지하면 0 · 2 다. 아니면(덜 내거나 더 낸 달 뒤, 어느 차례인지 모르는 달 뒤) 앞 차례부터 고루 나눈다
     * (3건을 두 차례면 2 · 1). 짝짓기 · 다음 차례 · 남은 금액이 함께 쓴다. [lowers] 는 [requiredIn] 과 같다.
     */
    fun sharesIn(month: YearMonth, slots: Int, lowers: Boolean = true): SlotShares {
        val required = requiredIn(month, lowers)
        val paid = endOf(month)?.let(bySlot::get)?.let { bySlot -> List(slots) { bySlot[it] ?: 0 } }
        if (paid != null && paid.sum() == required) return SlotShares(paid, counted = true)
        return SlotShares(List(slots) { required / slots + if (it < required % slots) 1 else 0 }, counted = required >= slots)
    }

    companion object {
        /**
         * 짝지은 몫 달마다 건수와 차례별 건수. 가까운 차례 달로 어림하면 두 차례 가운데쯤 낸 결제(결제일을 15일 옮긴 달)가 한 달에
         * 둘로 몰린다.
         */
        fun matched(matching: Matching, schedule: FixedSchedule): MonthCounts {
            val byMonth = matching.slotOf.values.groupBy { it.month }
            val bySlot = byMonth.mapValues { (_, slots) -> slots.groupingBy { it.index }.eachCount() }
            return MonthCounts(byMonth.mapValues { (_, slots) -> slots.size }, bySlot, schedule.cadence, schedule.phase)
        }
    }
}

/**
 * [regular] 결제마다 가장 가까운 차례(달은 [DueDates.nearestMonth], 그 달 평소 날 [days] 가운데 가장 가까운 날)로 센 달마다 차례별 건수.
 * 다른 차례도 [DROP_GRACE_DAYS] 일 안에 있어 어느 차례인지 모르는 결제(1일 · 2일에 나눠 내는 월세를 같은 날 낸 것, 30일 · 말일 두 청구,
 * 쉬는 날로 두 청구가 한날 밀린 것)가 있는 달은 뺀다(그 달 뒤는 고루 나눈다). 짝짓기 전 어림에도 차례별 나눔을 알아서, 한 차례에 한 건 ·
 * 다른 차례에 두 건 내는 가게를 고루(2 · 1) 나눠 짝지은 것이 굳지 않는다.
 */
private fun nearestSlots(regular: List<Paid>, days: List<Int>, dues: DueDates): Map<YearMonth, Map<Int, Int>> {
    val unknown = HashSet<YearMonth>()
    val bySlot = HashMap<YearMonth, MutableMap<Int, Int>>()
    for (pay in regular) {
        val month = dues.nearestMonth(pay.date, days)
        val own = YearMonth.from(pay.date)
        val distances = listOf(own.minusMonths(1), own, own.plusMonths(1)).flatMap { near ->
            days.map { dues.distance(pay.date, near, it) }
        }
        val (best, second) = distances.sorted()
        if (second <= maxOf(best, DROP_GRACE_DAYS.toInt())) {
            unknown += month
            continue
        }
        val index = days.indices.minBy { dues.distance(pay.date, month, days[it]) }
        bySlot.getOrPut(month) { HashMap() }.merge(index, 1, Int::plus)
    }
    return bySlot.filterKeys { it !in unknown }
}

/**
 * 한 달의 차례마다 건수([counts], 합이 그 달 건수). [counted] 면 낼 날이 지난 빈 차례를 센다([Slot.need]). 건수가 차례 수만큼이거나
 * 앞 달에 차례마다 낸 대로 나눠 어느 차례가 남았는지 알 때다(해지한 다음 달). 건수가 차례 수보다 적은데 고루 나눈 달(회선을 더하기 전)은
 * 어느 차례가 빌지 몰라 세지 않는다.
 */
internal data class SlotShares(val counts: List<Int>, val counted: Boolean)

/** 따로 낸 결제를 가를 때 견주는 앞뒤 결제 수 */
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

/** 한 달에 서로 다른 날 이보다 많이 내면 자주 내는 가게다([frequentTimes]) */
private const val FREQUENT_TIMES = 3

/** 한 달 차례는 많아야 둘이다(셋 넘게 나눠 내는 가게는 두 차례에 건수를 나눈다) */
internal const val MAX_SLOTS = 2

/** 주기를 짐작할 때 보는 최근 간격 수. 한 번 건너뛴 정도는 가운데 값이 흡수한다. */
private const val CADENCE_GAPS = 5

/** 'N달마다' 로 보는 가장 긴 주기. 이보다 길면 매년이다. */
private const val MAX_EVERY_MONTHS = 10

/** 따로 낸 결제로 보는 금액 차이(배) */
private const val EXTRA_RATIO = 3

/** 앞뒤 금액 가운데 비슷한 것이 이만큼 분의 1이 안 되면 따로 낸 결제다([isExtraAt]) */
private const val EXTRA_SHARE = 4

/** 비슷한 금액으로 보는 폭(%) */
private const val SIMILAR_AMOUNT_PERCENT = 20L

private const val PERCENT = 100L

/** 값을 자리마다 섞는 자릿수(아랫자리의 합이 이보다 작다) */
private const val TIE_SCALE = 1024

/** 낼 날 표의 열쇠에서 한 달이 차지하는 칸 수 */
private const val DAY_KEYS = 32

private const val MONTHS_IN_YEAR = 12
