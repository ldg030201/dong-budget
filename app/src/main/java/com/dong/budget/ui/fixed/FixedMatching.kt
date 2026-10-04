package com.dong.budget.ui.fixed

import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 결제를 차례(몇 월 몫의 몇 번째)에 짝짓는다. 결제를 날짜 차례대로, 차례를 낼 날 차례대로 두고 차례를 거스르지 않게
// 짝지으며 아래 값의 합이 가장 작은 짝을 고른다(작은 동적 계획법, 가게당 결제 수십 건).
// - 결제와 차례의 거리([DueDates.distance]). 낸 달이 아닌 달의 차례면 [OTHER_MONTH] 를 더한다(낸 달 몫이 기본이다).
// - 두 차례 금액이 서로 다른 가게에서 금액이 다른 차례와 맞는 결제면 [OTHER_SLOT].
// - 한 차례의 건수를 넘긴 결제마다 [OVERFLOW]. 다음 달 몫으로 옮기는 값(거리 + [OTHER_MONTH])과 견주어
//   새 회선이 같은 날 · 열흘 뒤에 생긴 달은 그 달 합으로 두고, 이미 찬 앞 달 대신 이번 달을 낸 것(등록하기로 오늘 적은 것)은 이번 달 몫이 된다.
// - 첫 결제 차례부터 마지막 결제일까지 낼 날이 왔는데 빈 건수마다 [MISSING]. 밀린 몫을 늦게 함께 냈으면 빈 차례를 메운다
//   (말일 관리비를 놓쳐 10월 2일에 두 건을 내면 하나는 9월, 하나는 10월 몫).
// - 따로 낸 결제(연간 결제 등, [extrasOf])는 짝짓지 않는다. 평소 낼 날 가까이 나갔어도 그 달 차례를 채우지 않는다(매달 것보다
//   하루 이틀 먼저 나간 연간 결제로 그 달이 '냈어요' 가 되지 않게). 값을 바꾼 것이면 다음 결제부터 따로 낸 것이 아니다.
// 결제는 가장 가까운 차례에서 앞뒤 한 주기 안의 차례에만 짝짓는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 한 차례. [month] 몫의 [index] 번째 차례(평소 [day] 일).
 * @property cap 이만큼은 한 차례에 함께 낸 것이고(같은 날 나눠 내는 월세는 2), 넘는 결제마다 [OVERFLOW]
 * @property need 낼 날이 마지막 결제일 전에 왔으면 내야 하는 건수. 모자라는 건수마다 [MISSING]. 차례 수보다 적게 내던 달(회선을 더하기 전)이면
 *   0 이다. 다만 앞 달에 차례마다 낸 대로 나눈 달(해지한 다음 달, [SlotShares.counted])은 남은 차례의 건수다.
 */
internal class Slot(val month: YearMonth, val index: Int, val day: Int, val cap: Int, val need: Int)

/** 짝지은 결과. [slotOf] 는 결제 id → 차례, [extras] 는 따로 낸 것으로 두어 어느 차례에도 들지 않은 결제, [slots] 는 짝지을 수 있던 차례들이다. */
internal class Matching(val slotOf: Map<Long, Slot>, val extras: List<Paid>, val slots: List<Slot> = emptyList())

/** 짝짓기 중의 한 갈래. [slot] 은 마지막으로 짝지은 차례(아직 없으면 -1), [count] 는 그 차례에 짝지은 수([Slot.cap] 까지만 센다). */
private class Node(val cost: Int, val slot: Int, val count: Int, val first: Boolean, val prev: Node?, val assigned: Int)

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
    val nearest = pays.associate { pay -> pay.id to slots.indices.minBy { slotDistance(pay, slots[it], dues) } }
    val ordered = orderOf(pays) { amountOrder(it, slots, nearest.getValue(it.id), schedule, dues) }
    val missingBefore = IntArray(slots.size + 1)
    slots.forEachIndexed { i, slot -> missingBefore[i + 1] = missingBefore[i] + slot.need * MISSING }
    // 마지막으로 짝지은 차례 뒤로도 마지막 결제일까지 낼 날이 온 차례가 비면 센다(빈 달 뒤의 결제를 빈 달로 끌어가지 않게)
    fun total(node: Node): Int = if (node.slot < 0) {
        node.cost
    } else {
        node.cost + (if (node.first) 0 else missingAt(slots[node.slot], node.count)) + missingBefore[slots.size] -
            missingBefore[node.slot + 1]
    }
    var nodes = listOf(Node(0, -1, 0, false, null, -1))
    for (pay in ordered) {
        val near = nearest.getValue(pay.id)
        val reach = maxOf(0, near - schedule.days.size)..minOf(slots.lastIndex, near + schedule.days.size)
        val next = HashMap<Int, Node>()
        fun offer(node: Node) {
            val key = (node.slot + 1) * KEY_SLOT + node.count * 2 + if (node.first) 1 else 0
            if ((next[key]?.cost ?: Int.MAX_VALUE) > node.cost) next[key] = node
        }
        for (node in nodes) {
            if (pay.id in extras) {
                offer(Node(node.cost, node.slot, node.count, node.first, node, -1))
                continue
            }
            // 닿는 차례가 모두 이미 지난 차례 앞이면 그 차례에 넘치게 넣는다(어느 갈래도 끊기지 않게, 갈래가 없으면 짝을 못 고른다)
            for (target in if (reach.last < node.slot) node.slot..node.slot else reach) {
                if (target < node.slot) continue
                val slot = slots[target]
                val cost = node.cost + assignCost(pay, slot, schedule, dues)
                if (target == node.slot) {
                    val over = if (node.count >= slot.cap) OVERFLOW else 0
                    offer(Node(cost + over, target, minOf(node.count + 1, slot.cap), node.first, node, target))
                } else {
                    val left = if (node.slot < 0 || node.first) 0 else missingAt(slots[node.slot], node.count)
                    val between = if (node.slot < 0) 0 else missingBefore[target] - missingBefore[node.slot + 1]
                    offer(Node(cost + left + between, target, 1, node.slot < 0, node, target))
                }
            }
        }
        nodes = next.values.toList()
    }
    return resultOf(nodes.minBy(::total), ordered, slots)
}

/**
 * 짝지을 결제 차례. 날짜 차례이되, 무리의 첫 결제부터 [OCCASION_DAYS] 일 안에 낸 결제 무리 안에서는 같은 금액끼리 몇 번째인지로 먼저 줄 세운다.
 * 밀린 몫과 이번 몫을 260,000원 · 260,000원 · 240,000원 · 240,000원 차례로 냈어도 앞 달 몫과 이번 달 몫이 한 벌씩이 된다.
 * 무리는 첫 결제에서 재므로 사흘 안팎으로 이어 낸 결제가 몇 달 이어져도(평일마다 내는 돌봄) 몇 달 뒤 결제를 앞으로 당기지 않는다.
 * 같은 날 낸 것끼리는 [sameDay] 차례다.
 */
private fun orderOf(pays: List<Paid>, sameDay: (Paid) -> Int): List<Paid> {
    val byDate = pays.sortedWith(compareBy<Paid> { it.date }.thenBy(sameDay).then(paidByTime))
    val rank = HashMap<Long, Int>()
    var start = 0
    for (end in byDate.indices) {
        val last = end == byDate.lastIndex || daysBetween(byDate[start].date, byDate[end + 1].date) > OCCASION_DAYS
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
                val passed = !dues.due(month, days[i]).isAfter(latest)
                Slot(month, i, days[i], cap = maxOf(1, share), need = if (shares.counted && passed) share else 0)
            }
        }.toList()
}

private fun slotDistance(pay: Paid, slot: Slot, dues: DueDates): Int = dues.distance(pay.date, slot.month, slot.day)

private fun assignCost(pay: Paid, slot: Slot, schedule: FixedSchedule, dues: DueDates): Int {
    val byAmount = schedule.slotByAmount(pay.amount)
    return slotDistance(pay, slot, dues) +
        (if (slot.month != pay.month) OTHER_MONTH else 0) +
        if (byAmount != null && byAmount != slot.index) OTHER_SLOT else 0
}

/**
 * 같은 날 낸 결제끼리의 차례. 금액으로 차례를 가를 수 있으면 그 차례 가운데 가까운 것의 차례대로다(28일 것이 쉬는 날로 밀려
 * 다음 달 3일 것과 한날 나가면 28일 금액의 결제가 먼저라 앞 달 몫이 된다). 가를 수 없으면 가장 가까운 차례[near] 다.
 */
private fun amountOrder(pay: Paid, slots: List<Slot>, near: Int, schedule: FixedSchedule, dues: DueDates): Int {
    val slot = schedule.slotByAmount(pay.amount) ?: return near
    val reach = maxOf(0, near - schedule.days.size)..minOf(slots.lastIndex, near + schedule.days.size)
    return reach.filter { slots[it].index == slot }.minByOrNull { slotDistance(pay, slots[it], dues) } ?: near
}

/** [slot] 에 [count] 건을 짝지었을 때 모자라는 건수의 값 */
private fun missingAt(slot: Slot, count: Int): Int = (slot.need - minOf(count, slot.need)) * MISSING

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

/** 갈래 열쇠에서 차례 하나가 차지하는 칸 수(건수 × 2 + 첫 차례 여부가 이보다 작다) */
private const val KEY_SLOT = 64
