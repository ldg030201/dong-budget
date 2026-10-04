package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.calc.merchantKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 계산. '고정지출' 분류로 적은 지출을 가게별로 묶고, 고른 달에 냈는지 · 낼 차례인지를 가린다.
// 가게는 많이 쓴 곳과 같은 열쇠(merchantKey: 띄어쓰기·대소문자 무시)로 묶는다. 이름이 비슷하기만 한 가게는 합치지 않는다.
// 몇 달마다 · 며칠쯤 · 얼마를 내는지는 따로 적어 두지 않고 지난 기록에서 그때그때 짐작한다.
// 1. 일정([estimateSchedule]): 한 달에 몇 차례 · 차례마다 평소 날 · 몇 달마다를, 결제가 가장 가까운 차례의 낼 날과 덜 떨어지는 쪽으로 고른다.
//    오늘이 든 달까지 최근 [HISTORY_MONTHS] 달 기록으로만 짐작해서 같은 날 본 모든 달 화면이 같은 일정을 쓴다.
//    연간 결제처럼 금액이 동떨어진 결제는 따로 낸 것으로 보고 일정 짐작에서 뺀다.
// 2. 몫([matchPayments]): 결제를 날짜 차례대로 차례(몇 월 몫의 몇 번째)에 짝짓는다. 쉬는 날로 밀린 말일 자동이체, 1일 월세를 미리 낸 것,
//    밀린 몫을 늦게 함께 낸 것, 같은 날 새로 생긴 회선, 이미 낸 앞 달 대신 이번 달을 일찍 낸 것이 모두 같은 셈으로 정해진다.
// 3. 고른 달: 그 달 몫으로 짝지은 결제가 그 달 건수만큼이면 냈어요, 덜이면 일부만 낸 '아직 안 냈어요', 없으면 마지막으로 낸 달과의 간격으로 본다.
// ─────────────────────────────────────────────────────────────────────

/** 고른 달에서 한 가게가 어떤 상태인지 */
enum class FixedStatus {
    /** 고른 달에 낼 차례인데 아직 안 냈다. 일부만 냈으면([FixedExpenseItem.paidCount] 가 0보다 큼) 남은 차례를 아직 안 낸 것이다. */
    DUE,

    /** 고른 달 몫을 냈다(전달 말에 미리 냈거나 다음 달 초에 밀려 냈어도, 날짜를 앞으로 적어 둔 거래도 낸 것으로 본다) */
    PAID,

    /** 몇 달마다 · 매년 내는 것이라 고른 달은 낼 달이 아니다 */
    NOT_THIS_MONTH,

    /** 낼 차례가 지나고도 한 달 넘게 안 냈다. 그만둔 것으로 보고 접어 둔다. */
    STOPPED,
}

/**
 * 고정지출 한 가게. 모두 고른 달까지의 기록으로 낸 값이다.
 *
 * @property key 묶는 열쇠(merchantKey). 가게 이름 없이 적은 지출(백업으로만 들어온다)은 모두 [NO_MERCHANT_KEY] 하나로 묶는다.
 * @property name 보이는 이름. 가장 최근 거래의 가게 이름(앞뒤 공백을 뺀다), 이름이 없으면 [NO_MERCHANT_NAME]
 * @property merchant 등록창에 채울 가게 이름. 이름 없이 묶인 것은 null
 * @property cadence 몇 달마다 내는지. 1(매달), 2~10(그 달마다), 12(매년)
 * @property usualDay 평소 내는 날(1~31). 한 달에 두 차례 내면 이른 차례의 날이다. 그 달에 없는 날이면 말일로 보고, 쉬는 날이면 다음 영업일에 낸다.
 * @property usualDays 차례마다 평소 내는 날, 이른 차례부터(3일 · 28일). 한 달에 한 번이면 [usualDay] 하나다.
 * @property timesPerMonth 한 달에 서로 다른 날 여러 번 내는 자주 내는 가게(평일마다 내는 돌봄 · 주 3회 PT)면 한 달에 보통 몇 번 내는지.
 *   차례 없이 달력 달로 보아 그 달에 한 번이라도 냈으면 냈어요이고, 낼 돈은 가장 최근에 낸 달의 합이며 낼 날 알림이 없다. 그 밖에는 null
 * @property dueDay 고른 달에 다음으로 낼 차례의 평소 날. 일부만 냈으면 아직 안 낸 첫 차례, 아니면 [usualDay] 다([usualDateIn] · [dueDateIn]).
 * @property amount 냈으면 고른 달에 낸 돈(그 달 몫의 합에 그 달에 따로 낸 것을 더함). 일부만 냈으면 남은 차례의 평소 금액,
 *   아니면 이번에 낼 것으로 보는 금액(가장 최근에 다 낸 달 몫의 합, 따로 낸 것은 빼고)이다.
 * @property previousAmount 냈어요일 때 그 앞에 다 낸 달 몫의 합. 견줄 수 없으면(앞 달이 없거나 낸 횟수가 다르거나 따로 낸 것이 섞였거나
 *   자주 내는 가게라 달마다 낸 횟수가 다르면) null
 * @property lastPaidOn 가장 최근에 낸 몫의 첫 결제일. 냈으면 고른 달 몫을 낸 날이다(미리 냈으면 전달, 밀려 냈으면 다음 달 날짜다).
 *   '냈어요' · '마지막' 날짜에만 쓴다. 몇 월 몫인지는 [lastShareMonth] 다.
 * @property lastShareMonth 가장 최근에 낸 몫이 몇 월 몫인지. 매년 내는 것의 '매년 3월' 은 이 달로 적는다(낸 날의 달과 다를 수 있다).
 * @property lastPaidCount 그 몫으로 낸 횟수(그 달에 따로 낸 것 포함)
 * @property paidCount 고른 달 몫으로 낸 횟수(따로 낸 것 빼고). [requiredCount] 보다 적고 0보다 크면 일부만 낸 것이다.
 * @property requiredCount 고른 달에 내야 하는 횟수(한 달에 두 차례 내거나 한 차례에 두 건씩 내면 2)
 * @property paidAmount 고른 달에 낸 돈(그 달 몫과 그 달에 따로 낸 것). 아무것도 안 냈으면 0
 * @property nextMonth [FixedStatus.NOT_THIS_MONTH] 일 때 다음에 낼 달. 다음 차례 몫을 미리 냈으면([prepaidMonth]) 그다음 차례 달이다. 그 밖에는 null
 * @property prepaidMonth [FixedStatus.NOT_THIS_MONTH] 인데 다음 차례 몫을 고른 달이 끝나기 전에 미리 냈으면 그 몫의 달(매년 3월 것을 2월에 갱신).
 *   그 결제는 그 몫의 달에 낸 돈이라 고른 달 낸 돈에는 들지 않는다. 그 밖에는 null
 * @property prepaidOn [prepaidMonth] 몫을 낸 첫 결제일. 그 밖에는 null
 * @property missedMonth [FixedStatus.DUE] 이고 고른 달 몫을 하나도 안 냈는데, 바로 앞 차례 달도 비었고 그 달의 낼 날이 오늘 전이면 그 달.
 *   말일이 쉬는 날이라 다음 달 초에 나가는 차례는 그날이 와야 지났다고 한다([waitingMonth]). 그 밖에는 null
 * @property waitingMonth [missedMonth] 와 같은데 그 달의 낼 날이 아직 안 왔으면(말일이 쉬는 날이라 이번 달 초에 나가는 차례) 그 달.
 *   그사이 '등록하기' 로 적은 결제는 그 달 몫이 되므로 줄에 함께 알린다. 그 밖에는 null
 * @property daysPastUsual [FixedStatus.DUE] 이고 고른 달이 이번 달일 때, 낼 날([dueDateIn], 평소 날짜가 쉬는 날이면 다음 영업일)이
 *   지났으면 평소 날짜([usualDateIn])에서 며칠 지났는지(25일 것이 연휴로 28일에 나가는 달은 29일에 4). 낼 날이면 0, 아직이면 낼 날까지
 *   남은 날의 음수다. 그 밖에는 null
 * @property latestId 가장 최근 거래. 줄을 누르면 이 거래의 상세가 열리고, 거기서 같은 가게의 최근 1년 내역을 본다.
 * @property latestAt 가장 최근 거래의 때. '등록하기' 의 시각을 여기서 가져온다.
 * @property latestAmount 가장 최근 거래의 금액. 자주 내는 가게의 '등록하기' 는 한 달 합이 아니라 이 금액이다.
 * @property paymentMethodId 가장 최근 거래의 결제수단. 지웠거나 비웠으면 null이고, 이름·아이콘·색도 같다.
 * @property categoryIcon 가장 최근 거래의 분류 아이콘(결제수단이 없을 때 뱃지에 쓴다)
 */
@Immutable
data class FixedExpenseItem(
    val key: String,
    val name: String,
    val merchant: String?,
    val status: FixedStatus,
    val cadence: Int,
    val usualDay: Int,
    val usualDays: List<Int>,
    val timesPerMonth: Int?,
    val dueDay: Int,
    val amount: Long,
    val previousAmount: Long?,
    val lastPaidOn: LocalDate,
    val lastShareMonth: YearMonth,
    val lastPaidCount: Int,
    val paidCount: Int,
    val requiredCount: Int,
    val paidAmount: Long,
    val nextMonth: YearMonth?,
    val prepaidMonth: YearMonth?,
    val prepaidOn: LocalDate?,
    val missedMonth: YearMonth?,
    val waitingMonth: YearMonth?,
    val daysPastUsual: Int?,
    val latestId: Long,
    val latestAt: Instant,
    val latestAmount: Long,
    val paymentMethodId: Long?,
    val paymentMethodName: String?,
    val paymentMethodIcon: String?,
    val paymentMethodColor: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
) {
    /**
     * 이 가게를 [month] 에 평소 내는 날(다음으로 낼 차례, [dueDay]). 그 달에 없는 날(2월 30일 등)이면 그 달 말일이다.
     * 쉬는 날이어도 밀지 않는다('등록하기' 날짜).
     */
    fun usualDateIn(month: YearMonth): LocalDate = usualDateOf(month, dueDay)

    /** 이 가게를 [month] 에 낼 날. 평소 날짜([usualDateIn])가 쉬는 날이면 다음 영업일이다([dueDateOf]). */
    fun dueDateIn(month: YearMonth): LocalDate = dueDateOf(month, dueDay)
}

/**
 * 고른 달의 고정지출을 상태별로 나눈 것. 빈 묶음은 화면에서 숨긴다.
 * @property due 아직 안 냈어요. 지난 차례도 놓친 것이 먼저, 그다음 낼 날([FixedExpenseItem.dueDateIn])이 이른 것부터
 * @property paid 냈어요. 낸 날 순서
 * @property notThisMonth 이번 달엔 안 내요. 다음에 낼 달이 이른 것부터
 * @property stopped 한동안 안 냈어요. 마지막으로 낸 날이 최근인 것부터
 */
@Immutable
data class FixedExpenseBoard(
    val due: List<FixedExpenseItem> = emptyList(),
    val paid: List<FixedExpenseItem> = emptyList(),
    val notThisMonth: List<FixedExpenseItem> = emptyList(),
    val stopped: List<FixedExpenseItem> = emptyList(),
) {
    /** 고른 달에 낸 돈. 다 낸 것에 일부만 낸 것의 낸 몫과 그 달에 따로 낸 것을 더한다([FixedExpenseItem.paidAmount]). */
    val paidTotal: Long get() = (due + paid + notThisMonth + stopped).sumOf { it.paidAmount }

    /** 고른 달에 아직 안 낸 것들의 평소 금액 합(일부만 냈으면 남은 차례의 금액) */
    val dueTotal: Long get() = due.sumOf { it.amount }

    /** 고른 달에 낼 차례였던 것(냈든 안 냈든)의 수 */
    val dueCount: Int get() = due.size + paid.size

    val isEmpty: Boolean get() = due.isEmpty() && paid.isEmpty() && notThisMonth.isEmpty() && stopped.isEmpty()
}

/**
 * [month] 의 고정지출을 계산한다. 가게별로 묶고 가게마다 일정을 짐작해([estimateSchedule]) 결제를 차례에 짝지은 뒤([matchPayments])
 * 고른 달을 본다.
 * - 상태: 고른 달 몫으로 짝지은 결제가 그 달 건수([MonthCounts.requiredIn]) 이상이면 [FixedStatus.PAID], 덜이면 일부만 낸 [FixedStatus.DUE].
 *   하나도 없으면 마지막으로 낸 몫의 달에서 몇 달 지났는지(gap)를 주기와 견준다. gap < 주기면 [FixedStatus.NOT_THIS_MONTH],
 *   주기 ≤ gap < 주기 + [DUE_MONTHS] 면 [FixedStatus.DUE], 그보다 길면 [FixedStatus.STOPPED].
 * - 금액: 냈으면 그 달 몫과 그 달에 따로 낸 것의 합. 일부만 냈으면 남은 차례의 금액, 아니면 가장 최근에 다 낸 달 몫의 합(따로 낸 것은 빼고)이다.
 *
 * @param today 오늘. 고른 달이 이번 달이면 낼 날([FixedExpenseItem.dueDateIn])이 며칠 지났는지 센다.
 * @param rows '고정지출' 분류의 지출. [fixedHistoryStart] 부터 [fixedHistoryEnd] 말일까지 읽은 것이다.
 *   지출이 아닌 행과 오늘이 든 달 뒤의 행은 거른다. 일정은 오늘이 든 달 기준 [fixedHistoryStart] 부터의 행으로만 짐작해서
 *   지난 달 화면이 더 오래된 결제를 읽어도 같은 날 본 이번 달 화면과 같은 일정을 쓴다.
 */
fun buildFixedExpenses(month: YearMonth, today: LocalDate, rows: List<TransactionListItem>): FixedExpenseBoard =
    buildFixedExpenses(month, today, rows, DueDates())

/** [buildFixedExpenses] 를 낼 날 표 [dues] 하나로. 낼 날은 가게와 상관없는 (달, 날)의 값이라 모든 가게의 짐작 · 짝짓기 · 정렬이 함께 쓴다. */
internal fun buildFixedExpenses(month: YearMonth, today: LocalDate, rows: List<TransactionListItem>, dues: DueDates): FixedExpenseBoard {
    val base = maxOf(month, YearMonth.from(today))
    val knownUntil = base.atEndOfMonth()
    val estimationFrom = fixedHistoryStart(base).atDay(1)
    val items =
        rows
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .map { Paid(it, it.localDate()) }
            .filter { !it.date.isAfter(knownUntil) }
            .groupBy { merchantKey(it.row.merchant) ?: NO_MERCHANT_KEY }
            .mapNotNull { (key, pays) ->
                val estimation = pays.filter { !it.date.isBefore(estimationFrom) }.ifEmpty { pays }
                fixedItem(key, pays, estimateSchedule(estimation, base, dues), month, today, dues)
            }
    return FixedExpenseBoard(
        due =
        items
            .filter { it.status == FixedStatus.DUE }
            .sortedWith(compareBy<FixedExpenseItem> { it.missedMonth == null }.thenBy { dues.due(month, it.dueDay) }.thenBy { it.name }),
        paid = items.filter { it.status == FixedStatus.PAID }.sortedWith(compareBy<FixedExpenseItem> { it.lastPaidOn }.thenBy { it.name }),
        notThisMonth =
        items
            .filter { it.status == FixedStatus.NOT_THIS_MONTH }
            .sortedWith(compareBy<FixedExpenseItem> { it.nextMonth }.thenBy { it.usualDay }.thenBy { it.name }),
        stopped =
        items
            .filter { it.status == FixedStatus.STOPPED }
            .sortedWith(compareByDescending<FixedExpenseItem> { it.lastPaidOn }.thenBy { it.name }),
    )
}

/**
 * [month] 를 계산하려면 읽어야 하는 첫 달. 매년 내는 것이 한 달 늦어진 때에도 그 앞 해의 결제까지 보여야 매년인 줄 알 수 있어서,
 * 고른 달 앞으로 [HISTORY_MONTHS] 달을 읽는다. 첫 달 몫을 전달 말에 미리 냈을 수도 있어 한 달 더 읽는다.
 */
fun fixedHistoryStart(month: YearMonth): YearMonth = month.minusMonths(HISTORY_MONTHS)

/**
 * [month] 를 [today] 에 계산하려면 읽어야 하는 마지막 달. 오늘이 든 달이다. 말일에 낼 것이 쉬는 날로 다음 달에 밀렸으면
 * 그 결제도 고른 달 몫으로 세고, 날짜를 앞으로 적어 둔 이번 달 거래도 센다. 일정은 오늘이 든 달까지의 기록으로 짐작한다.
 */
fun fixedHistoryEnd(month: YearMonth, today: LocalDate): YearMonth = maxOf(month, YearMonth.from(today))

/**
 * 한 가게([pays], 오늘이 든 달까지)를 [month] 에서 본 것. [schedule] 은 오늘이 든 달 기준 기록으로 짐작한 일정이다
 * (그 기간에 결제가 없는 옛 가게는 읽은 기록 모두로). 고른 달까지 낸 몫이 없으면(첫 결제 전 달) null
 */
private fun fixedItem(
    key: String,
    pays: List<Paid>,
    schedule: FixedSchedule,
    month: YearMonth,
    today: LocalDate,
    dues: DueDates,
): FixedExpenseItem? {
    // 자주 내는 가게는 차례 없이 낸 달 몫이고 한 번만 내도 그 달을 냈다
    val frequent = schedule.timesPerMonth != null
    val extraIds = if (frequent) emptySet() else extrasOf(pays.sortedWith(paidByTime))
    val prior = if (frequent) MonthCounts(emptyMap()) else MonthCounts(pays.filter { it.id !in extraIds }, schedule.days, dues)
    val first = if (frequent) matchByMonth(pays) else matchPayments(pays, schedule, prior, month, dues, extraIds)
    // 달마다 건수를 짝지은 몫으로 다시 세어 한 번 더 짝짓는다(건수가 바뀐 달이 없으면 그대로)
    val counts = if (frequent) prior else MonthCounts.matched(first)
    val changed = first.slots.map { it.month }.distinct().any { counts.requiredIn(it) != prior.requiredIn(it) }
    val matching = if (changed) matchPayments(pays, schedule, counts, month, dues, extraIds) else first
    val slotOf = matching.slotOf
    val shares = pays.filter { it.id in slotOf }.groupBy { slotOf.getValue(it.id).month }
    val extras = matching.extras.groupBy { it.month }
    val last = shares.keys.filter { it <= month }.maxOrNull() ?: return null
    val here = shares[month].orEmpty()
    val extraHere = extras[month].orEmpty()
    val required = counts.requiredIn(month)
    val cadence = schedule.cadence
    val gap = month.index() - last.index()
    val status =
        when {
            here.size >= required -> FixedStatus.PAID
            here.isNotEmpty() -> FixedStatus.DUE
            gap < cadence -> FixedStatus.NOT_THIS_MONTH
            gap < cadence + DUE_MONTHS -> FixedStatus.DUE
            else -> FixedStatus.STOPPED
        }
    // 가장 최근에 다 낸 달(고른 달 전). 낼 돈과 지난번 금액은 이 달 몫으로 센다.
    val full = shares.keys.filter { it < month && shares.getValue(it).size >= counts.requiredIn(it) }.maxOrNull()
    val days = schedule.days
    // 고른 달 몫을 하나도 안 냈는데 그 앞 차례 달도 비었으면 그 달(낼 날이 지났으면 놓친 것, 아직이면 기다리는 것)
    val skipped = last.plusMonths(cadence.toLong()).takeIf { status == FixedStatus.DUE && here.isEmpty() && gap > cadence }
    // 몇 달마다 · 매년 내는 것의 다음 차례 몫을 고른 달이 끝나기 전에 미리 냈으면 그 몫. 다음 차례는 그다음이다.
    val ahead = last.plusMonths(cadence.toLong())
    val prepaidOn = shares[ahead]?.minOf { it.date }?.takeIf { status == FixedStatus.NOT_THIS_MONTH && it <= month.atEndOfMonth() }
    val lastPays = (shares.getValue(last) + extras[last].orEmpty()).sortedWith(paidByTime)
    val latest = (shares.filterKeys { it <= month }.values.flatten() + matching.extras.filter { it.month <= month }).maxWith(paidByTime)
    val name = latest.row.merchant?.trim()?.takeIf { key != NO_MERCHANT_KEY }
    val item =
        FixedExpenseItem(
            key = key,
            name = name ?: NO_MERCHANT_NAME,
            merchant = name,
            status = status,
            cadence = cadence,
            usualDay = days.first(),
            usualDays = days,
            timesPerMonth = schedule.timesPerMonth,
            dueDay = days[openSlot(here, slotOf, days.size, required)],
            amount =
            when {
                status == FixedStatus.PAID -> (here + extraHere).sumOf { it.amount }
                here.isNotEmpty() -> remainingAmount(here, full?.let(shares::getValue), slotOf, days.size, required)
                else -> shares.getValue(full ?: last).sumOf { it.amount }
            },
            previousAmount =
            full?.let(shares::getValue)?.takeIf {
                status == FixedStatus.PAID && extraHere.isEmpty() && it.size == here.size && !frequent
            }?.sumOf { it.amount },
            lastPaidOn = lastPays.minOf { it.date },
            lastShareMonth = last,
            lastPaidCount = lastPays.size,
            paidCount = here.size,
            requiredCount = required,
            paidAmount = (here + extraHere).sumOf { it.amount },
            nextMonth = ahead.plusMonths(if (prepaidOn != null) cadence.toLong() else 0).takeIf { status == FixedStatus.NOT_THIS_MONTH },
            prepaidMonth = ahead.takeIf { prepaidOn != null },
            prepaidOn = prepaidOn,
            missedMonth = skipped?.takeIf { today.isAfter(dues.due(it, days.last())) },
            waitingMonth = skipped?.takeIf { !today.isAfter(dues.due(it, days.last())) },
            daysPastUsual = null,
            latestId = latest.id,
            latestAt = latest.row.occurredAt,
            latestAmount = latest.amount,
            paymentMethodId = latest.row.paymentMethodId,
            paymentMethodName = latest.row.paymentMethodName,
            paymentMethodIcon = latest.row.paymentMethodIcon,
            paymentMethodColor = latest.row.paymentMethodColor,
            categoryIcon = latest.row.categoryIcon,
            categoryColor = latest.row.categoryColor,
        )
    // 낼 날을 넘겼는지는 이번 달에만 센다. 지난 달은 이미 끝났다. 자주 내는 가게는 낼 날이 없다.
    if (status != FixedStatus.DUE || month != YearMonth.from(today) || frequent) return item
    val due = item.dueDateIn(month)
    // 낼 날이 지나야 '지났어요' 이고, 지난 날 수는 평소 날짜부터 센다(쉬는 날로 밀린 만큼 덜 세지 않게)
    val from = if (today.isAfter(due)) item.usualDateIn(month) else due
    return item.copy(daysPastUsual = daysBetween(from, today))
}

/** 고른 달에 다음으로 낼 차례(아직 덜 낸 첫 차례). 다 냈거나 하나도 안 냈으면 첫 차례다. */
private fun openSlot(here: List<Paid>, slotOf: Map<Long, Slot>, slots: Int, required: Int): Int {
    if (here.isEmpty()) return 0
    val paid = here.groupingBy { slotOf.getValue(it.id).index }.eachCount()
    return (0 until slots).firstOrNull { (paid[it] ?: 0) < shareOf(required, slots, it) } ?: 0
}

/**
 * 일부만 낸 달([here])의 남은 금액. 덜 낸 차례마다 앞서 다 낸 달([reference]) 그 차례의 합에서 이번에 그 차례로 낸 것을 뺀다
 * (3일 50,000원만 내고 28일 것이 남았으면 30,000원). 그 차례를 앞서 알 수 없으면 한 건 평균에 모자라는 건수를 곱한다.
 */
private fun remainingAmount(here: List<Paid>, reference: List<Paid>?, slotOf: Map<Long, Slot>, slots: Int, required: Int): Long {
    val paid = here.groupBy { slotOf.getValue(it.id).index }
    val before = reference?.groupBy { slotOf.getValue(it.id).index }.orEmpty()
    val average = (reference ?: here).let { pays -> pays.sumOf { it.amount } / pays.size }
    return (0 until slots).sumOf { slot ->
        val inSlot = paid[slot].orEmpty()
        val missing = shareOf(required, slots, slot) - inSlot.size
        val fromBefore = before[slot]?.sumOf { it.amount }?.minus(inSlot.sumOf { it.amount })
        when {
            missing <= 0 -> 0L
            fromBefore != null && fromBefore > 0 -> fromBefore
            else -> average * missing
        }
    }
}

/** 가게 이름 없이 적은 지출을 묶는 열쇠. merchantKey 는 빈 글을 내지 않으므로 어느 가게와도 겹치지 않는다. */
const val NO_MERCHANT_KEY = ""

/** 가게 이름 없이 적은 지출 묶음의 이름 */
const val NO_MERCHANT_NAME = "이름 없음"

/** 평소 날이 이 날이면 달마다 그 달 말일에 낸다고 본다([FixedExpenseItem.usualDateIn] 이 그 달 말일로 자른다) */
internal const val LAST_DAY = 31

/** 매년. 11달 이상 벌어지는 주기는 모두 매년으로 본다. */
const val YEARLY = 12

/** 낼 차례가 된 달부터 이만큼(그 달과 다음 달) '아직 안 냈어요' 로 두고, 그 뒤로는 '한동안 안 냈어요' 로 접는다. */
private const val DUE_MONTHS = 2

/** 고른 달 앞으로 읽는 달 수. 매년(직전 간격 최대 13) + 한 달 늦음(1) + 그 앞 해(12) + 첫 달 몫을 전달 말에 낸 것(1) */
private const val HISTORY_MONTHS = 27L
