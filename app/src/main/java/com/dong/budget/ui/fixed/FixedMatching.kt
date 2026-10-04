package com.dong.budget.ui.fixed

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 고정지출 결제를 차례(몇 월 몫의 몇 번째)에 짝짓는다. 결제를 날짜 차례대로, 차례를 낼 날 차례대로 두고 차례를 거스르지 않게
// 짝지으며 아래 값의 합이 가장 작은 짝을 고른다(작은 동적 계획법, 가게당 결제 수십 건).
// - 결제와 차례의 거리([DueDates.distance]). 낸 달이 아닌 달의 차례면 [OTHER_MONTH] 를 더한다(낸 달 몫이 기본이다).
// - 두 차례 금액이 서로 다른 가게에서 금액이 다른 차례와 맞는 결제면 [OTHER_SLOT]. 그 결제는 그 차례에 짝지어도(넘치는 것을 둘 곳이
//   없을 때) 그 차례를 낸 것이 아니라 빈 건수를 메우지 않는다(해지한 차례의 빈 자리에 남은 차례의 다음 결제가 끌려오지 않는다).
// - 한 차례의 건수를 넘긴 결제마다 [OVERFLOW]. 다음 달 몫으로 옮기는 값(거리 + [OTHER_MONTH])과 견주어
//   새 회선이 같은 날 · 열흘 뒤에 생긴 달은 그 달 합으로 두고, 이미 찬 앞 달 대신 이번 달을 낸 것(등록하기로 오늘 적은 것)은 이번 달 몫이 된다.
// - 첫 결제 차례부터 마지막 결제일까지 낼 날이 왔는데 빈 건수마다 [MISSING]. 밀린 몫을 늦게 함께 냈으면 빈 차례를 메운다
//   (말일 관리비를 놓쳐 10월 2일에 두 건을 내면 하나는 9월, 하나는 10월 몫). 함께 나가던 건 가운데 일부만 나간 차례의 남은 건은
//   그 뒤 사흘 안에, 또는 빠진 건의 금액 쪽 결제가 이어지는 동안만 센다([missingAt], 이미 낸 건 쪽 금액이 이어지면 해지했거나 건너뛴 것이다).
//   결제가 있는 달의 빈 차례(날이 따로인 차례)도 낼 날 뒤 사흘이 지나 그 차례 금액으로 보이지 않는 결제가 오면 세지 않는다([linesOf]).
// - 따로 낸 결제(연간 결제 등, [extrasOf])는 짝짓지 않는다. 평소 낼 날 가까이 나갔어도 그 달 차례를 채우지 않는다(매달 것보다
//   하루 이틀 먼저 나간 연간 결제로 그 달이 '냈어요' 가 되지 않게). 값을 바꾼 것이면 다음 결제부터 따로 낸 것이 아니다.
// 결제는 가장 가까운 차례에서 앞뒤 한 주기 안의 차례에만 짝짓는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 한 차례. [month] 몫의 [index] 번째 차례(평소 [day] 일).
 * @property cap 이만큼은 한 차례에 함께 낸 것이고(같은 날 나눠 내는 월세는 2), 넘는 결제마다 [OVERFLOW]
 * @property need 그 차례에 딱 맞게 나갈 수 있는 첫날([DueWindow.opens], 쉬는 날이면 낼 날 전의 평소 날짜)이 마지막 결제일이거나 그 전이면 내야
 *   하는 건수. 모자라는 건수마다 [MISSING]. 차례 수보다 적게 내던 달(회선을 더하기 전)이면 0 이다. 다만 앞 달에 차례마다 낸 대로 나눈 달
 *   (해지한 다음 달, [SlotShares.counted])은 남은 차례의 건수다. 낼 날로만 재면 쉬는 날 평소 날짜에 먼저 나간 카드 결제가 마지막일 때
 *   그 차례가 아직 안 온 것이라, 그 결제를 하루 가까운 다른 차례(해지한 앞 차례)에 넣었다.
 */
internal class Slot(val month: YearMonth, val index: Int, val day: Int, val cap: Int, val need: Int)

/** 짝지은 결과. [slotOf] 는 결제 id → 차례, [extras] 는 따로 낸 것으로 두어 어느 차례에도 들지 않은 결제, [slots] 는 짝지을 수 있던 차례들이다. */
internal class Matching(val slotOf: Map<Long, Slot>, val extras: List<Paid>, val slots: List<Slot> = emptyList())

/**
 * 짝짓기 중의 한 갈래. [slot] 은 마지막으로 짝지은 차례(아직 없으면 -1), [count] 는 그 차례에 짝지은 수([Slot.cap] 까지만 센다),
 * [fits] 는 그 가운데 금액이 그 차례와 맞는 수([OTHER_SLOT] 이 붙지 않은 것), [partly] 는 금액이 맞는 결제가 건수보다 적게 있으면 그 차례에서
 * 남은 건을 기다리는 모양([Partly], [missingAt])이고 그 밖에는 null 이다. [astray] 는 그 차례의 첫 결제가 같은 달 뒤 차례의 기준 금액에 더
 * 가까운지(뒤 차례 몫처럼 보이면 뒤 차례를 안 나간 것으로 보지 않는다)다.
 */
private class Node(
    val cost: Int,
    val slot: Int,
    val count: Int,
    val fits: Int,
    val partly: Partly?,
    val first: Boolean,
    val astray: Boolean,
    val prev: Node?,
    val assigned: Int,
) {
    /** 같은 열쇠의 갈래는 앞으로 더할 값이 같아 값이 작은 것만 남긴다 */
    val key: Any get() = if (partly == null) state else Pair(state, partly)

    private val state: Long get() = ((slot + 1L) * KEY_SLOT + count * KEY_COUNT + fits) * 4 + (if (first) 1 else 0) + if (astray) 2 else 0
}

/**
 * 함께 나가던 건 가운데 일부만 나간 차례가 남은 건을 기다리는 모양. [since] 는 그 차례 낼 날과 첫 결제 가운데 늦은 날의 에포크 날 수,
 * [amount] 는 그 첫 결제 금액, [others] 는 빠진 건의 기준 금액(그 차례 기준 금액에서 첫 결제와 가장 가까운 것을 뺀 것, 모르면 빔)이다.
 */
private data class Partly(val since: Int, val amount: Long, val others: List<Long>)

/**
 * [pays] 를 [schedule] 의 차례에 짝짓는다. 차례는 결제가 있는 달 앞뒤 한 주기와 [through] 달까지 있다.
 * 차례 달마다 건수는 [counts] 로 정한다. 낼 날이 마지막 결제일이거나 그 전인 차례만 비었다고 센다(오늘과 상관없이 같은 짝).
 * [extras] 는 따로 낸 결제의 id 다.
 */
internal fun matchPayments(
    pays: List<Paid>,
    schedule: FixedSchedule,
    counts: MonthCounts,
    through: YearMonth,
    dues: DueDates,
    extras: Set<Long>,
): Matching {
    val slots = slotsFor(pays, schedule, counts, through, dues)
    val distances = pays.associate { pay -> pay.id to IntArray(slots.size) { slotDistance(pay, slots[it], dues) } }
    val nearest = distances.mapValues { (_, distance) -> distance.indices.minBy { distance[it] } }
    val lines = linesOf(pays, slots, schedule.days.size, distances, extras)
    val dueDays = LongArray(slots.size) { dues.due(slots[it].month, slots[it].day).toEpochDay() }
    val amountSlots = pays.associate { pay -> pay.id to amountOrder(pay, slots, nearest.getValue(pay.id), schedule, lines, dues) }
    val ordered = orderOf(pays, { orderDay(it, slots[amountSlots.getValue(it.id)], schedule, dues) }) { amountSlots.getValue(it.id) }
    val missingBefore = IntArray(slots.size + 1)
    slots.forEachIndexed { i, slot -> missingBefore[i + 1] = missingBefore[i] + slot.need * MISSING }

    // [pay] 를 [target] 차례의 첫 결제로 넣을 때, 그 금액이 같은 달 뒤 차례의 기준 금액에 더 가까운지([Node.astray])
    fun astray(pay: Paid, target: Int, byAmount: Int?): Boolean = (target + 1 until slots.size).asSequence()
        .takeWhile { slots[it].month == slots[target].month }
        .any { looksLike(pay.amount, lines[it], lines[target], fallback = byAmount == slots[it].index) }

    // 결제가 있는 달(마지막으로 짝지은 차례 [from] 의 달, [pay] 를 넣는 차례 [to] 의 달)의 빈 차례는 낼 날 뒤 [DROP_GRACE_DAYS] 일이 지나
    // [pay] 가 왔고 그 금액이 빈 차례 쪽이 아니면 안 나간 것(해지했거나 건너뜀)이라 세지 않는다. 화면이 날이 따로인 차례를 안 나간 것으로
    // 보는 것과 같아서, 해지한 달의 빈 차례가 다음 달 결제를 끌어오지 않는다. [from] 의 결제가 그 빈 차례 몫처럼 보이면([strayed])
    // 그 달 결제가 있다고 하지 않는다(앞 차례를 해지한 달에 뒤 차례 결제를 하루 가까운 앞 차례에 넣고 뒤 차례를 안 나간 것으로 보지 않게).
    // 결제가 하나도 없는 달은 그대로 센다(밀린 몫을 늦게 메운다).
    fun between(from: Int, to: Int, pay: Paid, byAmount: Int?, strayed: Boolean): Int {
        if (from < 0) return 0
        var cost = missingBefore[to] - missingBefore[from + 1]
        fun waive(k: Int) {
            val dropped = pay.date.toEpochDay() > dueDays[k] + DROP_GRACE_DAYS &&
                !looksLike(pay.amount, lines[k], lines[to], fallback = byAmount == slots[k].index)
            if (dropped) cost -= slots[k].need * MISSING
        }
        var k = from + 1
        while (k < to && slots[k].month == slots[from].month) {
            if (!strayed) waive(k)
            k++
        }
        var j = to - 1
        while (j >= k && slots[j].month == slots[to].month) waive(j--)
        return cost
    }

    // 마지막으로 짝지은 차례 뒤로도 마지막 결제일까지 낼 날이 온 차례가 비면 센다(빈 달 뒤의 결제를 빈 달로 끌어가지 않게)
    fun total(node: Node): Int = if (node.slot < 0) {
        node.cost
    } else {
        node.cost + (if (node.first) 0 else missingAt(slots[node.slot], node.fits, node.partly, next = null)) +
            missingBefore[slots.size] - missingBefore[node.slot + 1]
    }
    var nodes = listOf(Node(0, -1, 0, 0, null, first = false, astray = false, prev = null, assigned = -1))
    for (pay in ordered) {
        val near = nearest.getValue(pay.id)
        val reach = maxOf(0, near - schedule.days.size)..minOf(slots.lastIndex, near + schedule.days.size)
        val byAmount = schedule.slotByAmount(pay.amount)
        val next = HashMap<Any, Node>()
        fun offer(node: Node) {
            val key = node.key
            if ((next[key]?.cost ?: Int.MAX_VALUE) > node.cost) next[key] = node
        }
        for (node in nodes) {
            if (pay.id in extras) {
                offer(Node(node.cost, node.slot, node.count, node.fits, node.partly, node.first, node.astray, node, -1))
                continue
            }
            // 닿는 차례가 모두 이미 지난 차례 앞이면 그 차례에 넘치게 넣는다(어느 갈래도 끊기지 않게, 갈래가 없으면 짝을 못 고른다)
            for (target in if (reach.last < node.slot) node.slot..node.slot else reach) {
                if (target < node.slot) continue
                val slot = slots[target]
                val cost = node.cost + assignCost(pay, slot, byAmount, dues)
                val fit = if (byAmount == null || byAmount == slot.index) 1 else 0
                if (target == node.slot) {
                    val over = if (node.count >= slot.cap) OVERFLOW else 0
                    val fits = minOf(node.fits + fit, slot.cap)
                    val partly = when {
                        node.fits == 0 && fit > 0 -> partlyOf(slot, fits, pay, lines[target], dues)
                        fits < slot.need -> node.partly
                        else -> null
                    }
                    offer(Node(cost + over, target, minOf(node.count + 1, slot.cap), fits, partly, node.first, node.astray, node, target))
                } else {
                    val left = if (node.slot < 0 || node.first) 0 else missingAt(slots[node.slot], node.fits, node.partly, pay)
                    val skipped = between(node.slot, target, pay, byAmount, node.astray)
                    val partly = if (fit > 0) partlyOf(slot, fit, pay, lines[target], dues) else null
                    val strays = astray(pay, target, byAmount)
                    offer(Node(cost + left + skipped, target, 1, fit, partly, node.slot < 0, strays, node, target))
                }
            }
        }
        nodes = next.values.toList()
    }
    return resultOf(nodes.minBy(::total), ordered, slots)
}

/**
 * 짝지을 결제 차례. 줄 세우는 날([day], 대개 낸 날) 차례이되, 무리의 첫 결제부터 [OCCASION_DAYS] 일 안에 낸 결제 무리 안에서는 같은 금액끼리
 * 몇 번째인지로 먼저 줄 세운다. 밀린 몫과 이번 몫을 260,000원 · 260,000원 · 240,000원 · 240,000원 차례로 냈어도 앞 달 몫과 이번 달 몫이
 * 한 벌씩이 된다. 무리는 첫 결제에서 재므로 사흘 안팎으로 이어 낸 결제가 몇 달 이어져도(평일마다 내는 돌봄) 몇 달 뒤 결제를 앞으로 당기지
 * 않는다. 같은 날 낸 것끼리는 [sameDay] 차례다.
 */
private fun orderOf(pays: List<Paid>, day: (Paid) -> LocalDate, sameDay: (Paid) -> Int): List<Paid> {
    val byDate = pays.sortedWith(compareBy(day).thenBy(sameDay).then(paidByTime))
    val rank = HashMap<Long, Int>()
    var start = 0
    for (end in byDate.indices) {
        val last = end == byDate.lastIndex || daysBetween(day(byDate[start]), day(byDate[end + 1])) > OCCASION_DAYS
        if (!last) continue
        val seen = HashMap<Long, Int>()
        for (pay in byDate.subList(start, end + 1)) rank[pay.id] = start * RANK_SCALE + seen.merge(pay.amount, 1, Int::plus)!!
        start = end + 1
    }
    return byDate.sortedBy { rank.getValue(it.id) }
}

/** 자주 내는 가게([FixedSchedule.timesPerMonth])의 짝. 차례 없이 결제마다 낸 달 몫이다. */
internal fun matchByMonth(pays: List<Paid>): Matching =
    Matching(pays.associate { it.id to Slot(it.month, index = 0, day = 1, cap = 1, need = 0) }, extras = emptyList())

/** 짝지은 길을 거슬러 결제마다 차례를 읽는다 */
private fun resultOf(best: Node, ordered: List<Paid>, slots: List<Slot>): Matching {
    val slotOf = HashMap<Long, Slot>()
    val extras = mutableListOf<Paid>()
    var node: Node? = best
    for (pay in ordered.asReversed()) {
        val current = node ?: break
        if (current.assigned < 0) extras += pay else slotOf[pay.id] = slots[current.assigned]
        node = current.prev
    }
    return Matching(slotOf, extras.asReversed(), slots)
}

/** 결제를 적은 차례. 같은 시각이면 나중에 적은(id 가 큰) 것이 나중이다. */
internal val paidByTime: Comparator<Paid> = compareBy<Paid> { it.row.occurredAt }.thenBy { it.id }

/**
 * 짝지을 차례들(낼 날 차례). 결제가 가장 가까운 차례 달들의 앞뒤 한 주기와 [through] 달 뒤 한 주기까지의 차례 달마다 차례 수만큼이다.
 * 차례마다 건수는 [MonthCounts.sharesIn] 이다(같은 날 나눠 내는 월세는 한 차례에 2건).
 */
private fun slotsFor(pays: List<Paid>, schedule: FixedSchedule, counts: MonthCounts, through: YearMonth, dues: DueDates): List<Slot> {
    val days = schedule.days
    val months = pays.map { dues.nearestMonth(it.date, days) }
    val latest = pays.maxOf { it.date }
    val step = schedule.cadence.toLong()
    val last = maxOf(through, months.max()).plusMonths(step)
    return generateSequence(months.min().minusMonths(step)) { it.plusMonths(1) }
        .takeWhile { it <= last }
        .filter(schedule::isSlotMonth)
        .flatMap { month ->
            val shares = counts.sharesIn(month, days.size)
            days.indices.asSequence().map { i ->
                val share = shares.counts[i].coerceAtMost(MAX_CAP)
                val passed = dues.window(month, days[i]).opens <= latest.toEpochDay()
                Slot(month, i, days[i], cap = maxOf(1, share), need = if (shares.counted && passed) share else 0)
            }
        }.toList()
}

private fun slotDistance(pay: Paid, slot: Slot, dues: DueDates): Int = dues.distance(pay.date, slot.month, slot.day)

/**
 * 차례마다 기준 금액들. 같은 번호의 앞 차례 가운데 그 차례 건수([Slot.cap])만큼 결제가 가장 가까웠던 가장 최근 차례의 그 결제 금액이다
 * (같은 날 두 회선이면 두 금액). [distances] 는 결제마다 차례들과의 거리다. 두 차례와 거리가 같아 어느 차례인지 모르는 결제와
 * 따로 낸 결제([extras])는 세지 않는다. 그런 앞 차례가 없으면 비었다.
 */
private fun linesOf(
    pays: List<Paid>,
    slots: List<Slot>,
    perMonth: Int,
    distances: Map<Long, IntArray>,
    extras: Set<Long>,
): List<List<Long>> {
    val amounts = HashMap<Int, MutableList<Long>>()
    for (pay in pays) {
        if (pay.id in extras) continue
        val distance = distances.getValue(pay.id)
        val best = distance.min()
        if (distance.count { it == best } == 1) amounts.getOrPut(distance.indexOf(best)) { mutableListOf() } += pay.amount
    }
    return slots.indices.map { slot ->
        generateSequence(slot - perMonth) { it - perMonth }
            .takeWhile { it >= 0 }
            .firstNotNullOfOrNull { before -> amounts[before]?.takeIf { it.size >= slots[slot].cap } }
            .orEmpty()
    }
}

/**
 * 금액 [amount] 가 빈 건의 기준 금액 [missing] 쪽인지(늦게 메운 결제인지). [missing] 가운데 가장 가까운 것이 견줄 기준 금액 [other]
 * 가운데 가장 가까운 것보다 더 가까우면 그렇다(같으면 가를 수 없어 아니다). 기준 금액을 모르면 [fallback] 이다.
 */
private fun looksLike(amount: Long, missing: List<Long>, other: List<Long>, fallback: Boolean): Boolean =
    if (missing.isEmpty() || other.isEmpty()) fallback else gapTo(amount, missing) < gapTo(amount, other)

/** [amount] 와 [amounts] 가운데 가장 가까운 것의 차이 */
private fun gapTo(amount: Long, amounts: List<Long>): Long = amounts.minOf { abs(amount - it) }

/** [pay] 를 [slot] 에 짝지을 때 더하는 값. [byAmount] 는 금액으로 본 [pay] 의 차례([FixedSchedule.slotByAmount])다. */
private fun assignCost(pay: Paid, slot: Slot, byAmount: Int?, dues: DueDates): Int = slotDistance(pay, slot, dues) +
    (if (slot.month != pay.month) OTHER_MONTH else 0) +
    if (byAmount != null && byAmount != slot.index) OTHER_SLOT else 0

/**
 * 같은 날 낸 결제끼리의 차례. 금액으로 차례를 가를 수 있으면 그 차례 가운데 가까운 것의 차례대로다(28일 것이 쉬는 날로 밀려
 * 다음 달 3일 것과 한날 나가면 28일 금액의 결제가 먼저라 앞 달 몫이 된다). 두 차례 금액이 비슷해 가르지 못해도 닿는 차례 가운데 기준 금액
 * ([lines])이 더 가까운 번호가 하나면 그 차례로 본다(1일 30,000원 · 말일 33,000원이 쉬는 날로 한날 나가면 33,000원이 앞 달 말일 몫).
 * 그래도 모르면 가장 가까운 차례[near] 다.
 */
private fun amountOrder(pay: Paid, slots: List<Slot>, near: Int, schedule: FixedSchedule, lines: List<List<Long>>, dues: DueDates): Int {
    val reach = maxOf(0, near - schedule.days.size)..minOf(slots.lastIndex, near + schedule.days.size)
    val slot = schedule.slotByAmount(pay.amount) ?: closerLine(pay.amount, reach, slots, lines) ?: return near
    return reach.filter { slots[it].index == slot }.minByOrNull { slotDistance(pay, slots[it], dues) } ?: near
}

/** 닿는 차례 [reach] 가운데 기준 금액([lines])이 [amount] 에 가장 가까운 차례 번호. 두 번호가 같거나 기준 금액을 모르면 null */
private fun closerLine(amount: Long, reach: IntRange, slots: List<Slot>, lines: List<List<Long>>): Int? {
    val known = reach.filter { lines[it].isNotEmpty() }
    val gaps = known.groupBy { slots[it].index }.mapValues { (_, at) -> at.minOf { gapTo(amount, lines[it]) } }
    val best = gaps.values.minOrNull() ?: return null
    return gaps.filterValues { it == best }.keys.singleOrNull()?.takeIf { gaps.size > 1 }
}

/**
 * 짝지을 때 [pay] 를 줄 세우는 날. 금액으로 어느 차례인지 아는 결제가 그 차례 [slot] 에 딱 맞는 날(쉬는 날의 평소 날짜, 카드)에 낼 날보다
 * 먼저 나갔으면 그 차례 낼 날이고, 그 밖에는 낸 날이다. 하루 차이 두 청구의 앞 것(자동이체)이 쉬는 날로 뒤 것 낼 날까지 밀린 달에
 * 뒤 것(카드)이 평소 날짜에 먼저 나가도 짝짓기가 차례를 거슬러 앞 것을 다음 달 몫으로 넘기지 않는다.
 */
private fun orderDay(pay: Paid, slot: Slot, schedule: FixedSchedule, dues: DueDates): LocalDate {
    val due = dues.due(slot.month, slot.day)
    val early = schedule.slotByAmount(pay.amount) == slot.index && pay.date < due && slotDistance(pay, slot, dues) == 0
    return if (early) due else pay.date
}

/**
 * [slot] 에 금액이 맞는 결제 [fits] 건을 짝지었을 때 모자라는 건수의 값. 하나도 없으면 모자라는 건수마다 [MISSING] 이다(밀린 몫을 늦게 내면
 * 메운다). 하나라도 있으면(함께 나가던 건 가운데 일부만 나감, [partly]) 다음 결제 [next](기록 끝이면 null)가 그 차례 낼 날과 첫 결제 가운데
 * 늦은 날 뒤 [DROP_GRACE_DAYS] 일 안에 오거나, 이미 낸 건보다 빠진 건의 기준 금액([Partly.others])에 더 가까워 남은 건일 수 있을 때만 센다
 * (기준 금액을 모르면 이미 낸 건과 금액이 다를 때). 그 뒤로 이미 낸 건 쪽 금액의 결제가 이어졌으면 남은 건은 안 나간 것(해지했거나 건너뜀)이라
 * 다음 달 몫을 미리 · 일찍 낸 결제를 끌어와 메우지 않는다(화면이 같은 날 회선을 안 나간 것으로 보는 것과 같다). 늦게 메운 남은 회선(빠진
 * 회선 금액, 이미 낸 회선과 비슷해도)은 그대로 그 달 몫이다. 두 회선 금액이 같으면 가를 수 없어 다음 달 몫으로 본다.
 */
private fun missingAt(slot: Slot, fits: Int, partly: Partly?, next: Paid?): Int {
    val short = slot.need - minOf(fits, slot.need)
    val waiting = partly == null || next == null || next.date.toEpochDay() <= partly.since + DROP_GRACE_DAYS ||
        looksLike(next.amount, partly.others, listOf(partly.amount), fallback = !isNear(next.amount, partly.amount))
    return if (short > 0 && (fits == 0 || waiting)) short * MISSING else 0
}

/**
 * [slot] 에 금액이 맞는 결제가 [fits] 건 짝지어졌을 때 남은 건을 기다리는 모양. 첫 결제 [pay] 의 날과 그 차례 낼 날 가운데 늦은 날, 그 금액,
 * 그 차례 기준 금액 [lines] 에서 첫 결제와 가장 가까운 것을 뺀 빠진 건의 금액이고, 건수가 다 찼거나 낼 날이 아직 안 와 셀 것이 없으면
 * null 이다(갈래를 쓸데없이 나누지 않게).
 */
private fun partlyOf(slot: Slot, fits: Int, pay: Paid, lines: List<Long>, dues: DueDates): Partly? = if (fits < slot.need) {
    val others = lines.toMutableList().apply { minByOrNull { abs(it - pay.amount) }?.let(::remove) }
    Partly(maxOf(dues.due(slot.month, slot.day), pay.date).toEpochDay().toInt(), pay.amount, others)
} else {
    null
}

/** 낸 달이 아닌 달의 차례에 짝지을 때 더하는 값(낸 달 몫이 기본이다) */
private const val OTHER_MONTH = 20

/** 금액이 다른 차례와 맞는 결제를 짝지을 때 더하는 값 */
private const val OTHER_SLOT = 30

/** 한 차례의 건수를 넘긴 결제마다 더하는 값 */
private const val OVERFLOW = 15

/** 첫 결제 차례부터 마지막 결제일까지 낼 날이 왔는데 빈 건수마다 더하는 값 */
private const val MISSING = 40

/** 한 차례에 함께 내는 건수의 한도(짝짓기 갈래 수를 묶는다) */
private const val MAX_CAP = 8

/** 짝지을 차례를 금액으로 줄 세우는 결제 무리의 길이(첫 결제부터 이만큼 안, [orderOf]) */
private const val OCCASION_DAYS = 3

/** 결제 무리 안에서 같은 금액끼리 몇 번째인지를 무리의 첫 자리와 섞는 자릿수(한 무리의 같은 금액이 이만큼 많지 않다) */
private const val RANK_SCALE = 1_000

/** 갈래 열쇠에서 차례 하나가 차지하는 칸 수(건수 × [KEY_COUNT] + 맞는 수가 이보다 작다) */
private const val KEY_SLOT = 256L

/** 갈래 열쇠에서 건수 하나가 차지하는 칸 수(맞는 수가 [MAX_CAP] 까지라 이보다 작다) */
private const val KEY_COUNT = 16
