package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.calc.merchantKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────────────
// 고정지출 계산. '고정지출' 분류로 적은 지출을 가게별로 묶고, 고른 달에 냈는지 · 낼 차례인지를 가린다.
// 가게는 많이 쓴 곳과 같은 열쇠(merchantKey: 띄어쓰기·대소문자 무시)로 묶는다. 이름이 비슷하기만 한 가게는 합치지 않는다.
// 몇 달마다 · 며칠쯤 · 얼마를 내는지는 따로 적어 두지 않고 지난 기록에서 그때그때 짐작한다(차례는 buildFixedExpenses).
// 고른 달 뒤의 기록은 보지 않는다. 지난 달을 보면 그 달까지 알던 대로 보인다.
// - 평소 날짜를 먼저 짐작한다. 31일에 낸 적이 있으면 짧은 달 말일(9월 30일 등)도 말일로 센다.
//   달 초와 달 말에 낸 날이 섞여 있으면 달 경계를 이어 센다(말일 것이 1일로 밀린 날이 평소 날짜가 되지 않게).
// - 결제는 낸 달이 아니라 '몇 월 몫인지' 로 센다. 1일에 내는 월세를 전달 말에 미리 냈으면 다음 달 몫이고,
//   말일 자동이체가 휴일로 다음 달 초에 밀렸으면 앞 달 몫이다. 그래서 다음 달 초 며칠까지는 읽는다.
//   한 달에 한 번 · 매달 내는 것만 옮긴다(매년 내는 것을 일찍 냈거나 한 가게에 달 초 · 달 말 두 번 내는 것은 낸 달 몫 그대로다).
// - 주기는 최근 간격 몇 개로만 본다. 내는 주기가 바뀌면 금방 따라간다. 밀린 몫을 함께 낸 달은 빈 달을 메운 것으로 센다.
// - 다음에 낼 금액은 마지막 몫의 합이지만, 밀린 몫을 함께 내 앞 몫보다 많이 낸 뒤에는 가장 최근 한 건이다.
// ─────────────────────────────────────────────────────────────────────

/** 고른 달에서 한 가게가 어떤 상태인지 */
enum class FixedStatus {
    /** 고른 달에 낼 차례인데 아직 안 냈다 */
    DUE,

    /** 고른 달에 냈다(날짜를 앞으로 적어 둔 거래도 낸 것으로 본다) */
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
 * @property usualDay 평소 내는 날(1~31). 그 달에 없는 날이면 말일로 본다([usualDateIn]).
 * @property amount 냈으면 고른 달에 낸 돈(한 달에 여러 번이면 합). 아니면 이번에 낼 것으로 보는 금액이다.
 *   가장 최근에 낸 달의 합인데, 그 달에 그 앞 달보다 많이 냈으면(밀린 몫을 함께 냈으면) 그 달 가장 최근 한 건이다.
 * @property previousAmount 그 앞에 낸 달에 낸 돈. 견줄 수 없으면(한 달에만 냈거나 두 달의 낸 횟수가 다르면) null
 * @property lastPaidOn 가장 최근에 낸 몫의 첫 결제일. 냈으면 고른 달 몫을 낸 날이다(미리 냈으면 전달, 밀려 냈으면 다음 달 날짜다).
 * @property lastPaidCount 그 몫으로 낸 횟수
 * @property nextMonth [FixedStatus.NOT_THIS_MONTH] 일 때 다음에 낼 달. 그 밖에는 null
 * @property missedMonth [FixedStatus.DUE] 인데 고른 달 전에 이미 낼 차례가 한 번 지났으면 그 달. 그 밖에는 null
 * @property daysPastUsual [FixedStatus.DUE] 이고 고른 달이 이번 달일 때, 오늘이 평소 내는 날에서 며칠 지났는지.
 *   그날이면 0, 아직이면 음수다. 그 밖에는 null
 * @property latestId 가장 최근 거래. 줄을 누르면 이 거래의 상세가 열리고, 거기서 같은 가게의 최근 1년 내역을 본다.
 * @property latestAt 가장 최근 거래의 때. '등록하기' 의 시각을 여기서 가져온다.
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
    val amount: Long,
    val previousAmount: Long?,
    val lastPaidOn: LocalDate,
    val lastPaidCount: Int,
    val nextMonth: YearMonth?,
    val missedMonth: YearMonth?,
    val daysPastUsual: Int?,
    val latestId: Long,
    val latestAt: Instant,
    val paymentMethodId: Long?,
    val paymentMethodName: String?,
    val paymentMethodIcon: String?,
    val paymentMethodColor: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
) {
    /** 이 가게를 [month] 에 평소 내는 날. 그 달에 없는 날(2월 30일 등)이면 그 달 말일이다. */
    fun usualDateIn(month: YearMonth): LocalDate = month.atDay(minOf(usualDay, month.lengthOfMonth()))
}

/**
 * 고른 달의 고정지출을 상태별로 나눈 것. 빈 묶음은 화면에서 숨긴다.
 * @property due 아직 안 냈어요. 지난 차례도 놓친 것이 먼저, 그다음 평소 날짜가 이른 것부터
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
    /** 고른 달에 낸 돈 */
    val paidTotal: Long get() = paid.sumOf { it.amount }

    /** 고른 달에 아직 안 낸 것들의 평소 금액 합 */
    val dueTotal: Long get() = due.sumOf { it.amount }

    /** 고른 달에 낼 차례였던 것(냈든 안 냈든)의 수 */
    val dueCount: Int get() = due.size + paid.size

    val isEmpty: Boolean get() = due.isEmpty() && paid.isEmpty() && notThisMonth.isEmpty() && stopped.isEmpty()
}

/**
 * [month] 의 고정지출을 계산한다. 가게별로 묶고, 가게마다 아래 차례로 센다.
 *
 * 1. 평소 날짜: 고른 달 말일까지 낸 최근 결제일(같은 날은 한 번, 최대 [USUAL_DAY_SAMPLES] 번)의 가운데 값(짝수 개면 이른 쪽).
 *    31일에 낸 적이 있으면 짧은 달 말일도 말일로 센다. 달 초와 달 말이 섞여 있으면 달 경계를 이어 센다([usualDayOf]).
 * 2. 몇 월 몫인지([shareMonths]): 평소 날짜를 보고 달 끝·다음 달 초에 낸 것을 옆 달 몫으로 옮긴다.
 *    옆 달이 비었는지는 낸 달(달력 달)로 보고, 한 달에 보통 한 번([usualCountOf]) 내고 옮겨 센 몫이 매달일 때만 옮긴다. 고른 달 뒤의 몫은 뺀다.
 * 3. 같은 달 몫은 합친다. 그 몫의 날짜는 첫 결제일이다(실제로 낸 날 그대로).
 * 4. 주기: 최근 [CADENCE_GAPS] 개까지의 낸 몫 사이 간격(달 수)의 가운데 값. 간격이 짝수 개면 둘 중 짧은 쪽이다
 *    (늦게 알리는 것보다 일찍 알리는 게 낫다). 옛 간격은 보지 않아서 2달마다 내다 매달 내게 바뀌어도 금방 따라간다.
 *    한 달에 보통([usualCountOf]) 보다 k 번 더 낸 몫은 밀린 몫을 함께 낸 것이라 그 앞 간격을 k 달 줄여 센다.
 *    한 달에만 냈으면 매달로 본다. 11달 넘게 벌어지면 매년이다.
 * 5. 상태: 고른 달 몫을 냈으면 [FixedStatus.PAID]. 아니면 마지막으로 낸 몫에서 몇 달 지났는지(gap)를 주기와 견준다.
 *    gap < 주기면 [FixedStatus.NOT_THIS_MONTH], 주기 ≤ gap < 주기 + [DUE_MONTHS] 면 [FixedStatus.DUE], 그보다 길면 [FixedStatus.STOPPED].
 * 6. 금액: 냈으면 그 몫으로 낸 돈(합). 아니면 [nextAmount]. 지난번 금액과는 두 몫의 낸 횟수가 같을 때만 견준다.
 *
 * @param today 오늘. 고른 달이 이번 달이면 평소 날짜가 며칠 지났는지 센다.
 * @param rows '고정지출' 분류의 지출. [fixedHistoryStart] 부터 [fixedHistoryEnd] 말일까지 읽은 것이다.
 *   다음 달 [SHIFT_DAYS] 일 뒤의 행과 지출이 아닌 행은 거른다.
 */
fun buildFixedExpenses(month: YearMonth, today: LocalDate, rows: List<TransactionListItem>): FixedExpenseBoard {
    // 다음 달 초에 밀려 낸 고른 달 몫까지 본다
    val end = month.plusMonths(1).atDay(SHIFT_DAYS + 1)
    val items =
        rows
            .filter { it.type == TransactionType.EXPENSE && it.localDate().isBefore(end) }
            .groupBy { merchantKey(it.merchant) ?: NO_MERCHANT_KEY }
            .mapNotNull { (key, group) -> fixedItem(key, group, month, today) }
    return FixedExpenseBoard(
        due =
        items
            .filter { it.status == FixedStatus.DUE }
            .sortedWith(compareBy<FixedExpenseItem> { it.missedMonth == null }.thenBy { it.usualDateIn(month) }.thenBy { it.name }),
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
 * 고른 달 앞으로 [HISTORY_MONTHS] 달을 읽는다. 매년은 간격 11달 이상을 모두 받으므로, 말일과 1일을 오가 직전 간격이 13달인 것까지 센다.
 */
fun fixedHistoryStart(month: YearMonth): YearMonth = month.minusMonths(HISTORY_MONTHS)

/** [month] 를 계산하려면 읽어야 하는 마지막 달. 말일에 낼 것이 휴일로 다음 달 초에 밀렸어도 그 달 몫으로 세려고 다음 달까지 읽는다. */
fun fixedHistoryEnd(month: YearMonth): YearMonth = month.plusMonths(1)

/** 한 가게가 한 달 몫으로 낸 것. [firstDate] 는 그 몫의 첫 결제일, [count] 는 낸 횟수, [latestAmount] 는 그 몫 가장 최근 한 건의 금액 */
private data class MonthPayment(val month: YearMonth, val total: Long, val firstDate: LocalDate, val count: Int, val latestAmount: Long)

/** 거래를 적은 차례. 같은 시각이면 나중에 적은(id 가 큰) 것이 나중이다. */
private val byTime = compareBy<TransactionListItem> { it.occurredAt }.thenBy { it.id }

/** 한 가게를 [month] 에서 본 것. 고른 달까지 낸 몫이 없으면(첫 결제 전 달) null */
private fun fixedItem(key: String, rows: List<TransactionListItem>, month: YearMonth, today: LocalDate): FixedExpenseItem? {
    // 평소 날짜·보통 건수는 고른 달까지 낸 것으로만 짐작한다
    val known = rows.filter { !it.localDate().isAfter(month.atEndOfMonth()) }
    if (known.isEmpty()) return null
    val usualDay = usualDayOf(known.map { it.localDate() }.distinct().sorted().takeLast(USUAL_DAY_SAMPLES))
    val usualCount = usualCountOf(known)
    val shares = shareMonths(rows, month, usualDay, usualCount)
    val kept = rows.filter { shares.getValue(it.id) <= month }
    if (kept.isEmpty()) return null
    val latest = kept.maxWith(byTime)
    val payments = paymentsOf(kept, shares)
    val last = payments.last()
    val previous = payments.getOrNull(payments.lastIndex - 1)
    val cadence = cadenceOf(payments, usualCount)
    val gap = ChronoUnit.MONTHS.between(last.month, month).toInt()
    val status =
        when {
            gap <= 0 -> FixedStatus.PAID
            gap < cadence -> FixedStatus.NOT_THIS_MONTH
            gap < cadence + DUE_MONTHS -> FixedStatus.DUE
            else -> FixedStatus.STOPPED
        }
    val name = latest.merchant?.trim()?.takeIf { key != NO_MERCHANT_KEY }
    val item =
        FixedExpenseItem(
            key = key,
            name = name ?: NO_MERCHANT_NAME,
            merchant = name,
            status = status,
            cadence = cadence,
            usualDay = usualDay,
            amount = if (status == FixedStatus.PAID) last.total else nextAmount(last, previous),
            // 밀린 몫을 함께 낸 달처럼 횟수가 다르면 금액이 달라도 값이 오르내린 것이 아니다
            previousAmount = previous?.takeIf { it.count == last.count }?.total,
            lastPaidOn = last.firstDate,
            lastPaidCount = last.count,
            nextMonth = last.month.plusMonths(cadence.toLong()).takeIf { status == FixedStatus.NOT_THIS_MONTH },
            missedMonth = last.month.plusMonths(cadence.toLong()).takeIf { status == FixedStatus.DUE && gap > cadence },
            daysPastUsual = null,
            latestId = latest.id,
            latestAt = latest.occurredAt,
            paymentMethodId = latest.paymentMethodId,
            paymentMethodName = latest.paymentMethodName,
            paymentMethodIcon = latest.paymentMethodIcon,
            paymentMethodColor = latest.paymentMethodColor,
            categoryIcon = latest.categoryIcon,
            categoryColor = latest.categoryColor,
        )
    // 평소 날짜를 넘겼는지는 이번 달에만 센다. 지난 달은 이미 끝났다.
    if (status != FixedStatus.DUE || month != YearMonth.from(today)) return item
    return item.copy(daysPastUsual = ChronoUnit.DAYS.between(item.usualDateIn(month), today).toInt())
}

/**
 * 다음에 낼 것으로 보는 금액. 마지막으로 낸 달([last])의 합인데, 그 달에 앞 달([previous])보다 많이 냈으면
 * 밀린 몫을 함께 낸 것으로 보고 그 달 가장 최근 한 건의 금액이다(합을 쓰면 다음 달 낼 돈이 두 배가 된다).
 * 나눠 내는 월세처럼 달마다 같은 횟수로 내거나, 한 달에만 냈으면 합 그대로다.
 */
private fun nextAmount(last: MonthPayment, previous: MonthPayment?): Long =
    if (previous != null && last.count > previous.count) last.latestAmount else last.total

/**
 * 평소 내는 날. [dates] 의 날짜 가운데 값(짝수 개면 이른 쪽)이다.
 * 31일에 낸 적이 있으면 말일에 내는 것으로 보고, 짧은 달의 말일(2월 28일·9월 30일 등)도 31(말일)로 센다.
 * 31일에 낸 적이 없으면(매달 30일에 내는 등) 날짜 그대로 센다.
 *
 * 달 초(1~[SHIFT_DAYS] 일)와 달 말([LATE_PAY_FROM] 일 이후)에 낸 날이 섞여 있으면 달 경계를 이어 센다. 달 초 날짜를 말일 뒤(31 + 날짜)로 놓고
 * 가운데 값을 구해, 말일을 넘으면 달 초 납부(1일에 내는 월세를 가끔 전달 말에 미리 냄), 아니면 달 말 납부(말일 것이 가끔 다음 달 초로 밀림)다.
 * 그냥 가운데 값을 쓰면 말일 것 하나가 1일로 밀린 두 건(8월 31일·10월 1일)에서 평소 날짜가 1일이 된다.
 */
private fun usualDayOf(dates: List<LocalDate>): Int {
    val paysOnLastDay = dates.any { it.dayOfMonth == LAST_DAY }
    val days = dates.map { if (paysOnLastDay && it.dayOfMonth == it.lengthOfMonth()) LAST_DAY else it.dayOfMonth }
    if (days.none { it <= SHIFT_DAYS } || days.none { it >= LATE_PAY_FROM }) return lowerMedian(days)
    val wrapped = lowerMedian(days.map { if (it <= SHIFT_DAYS) LAST_DAY + it else it })
    return if (wrapped > LAST_DAY) wrapped - LAST_DAY else wrapped
}

/**
 * 결제([rows])마다 몇 월 몫인지(거래 id → 몫의 달). 보통은 낸 달의 몫이고, 달 끝과 다음 달 초 사이에서만 옮긴다.
 * 옆 달이 비었는지 · 그 달 몫을 냈는지는 옮기기 전의 낸 달(달력 달)로 본다. 결제일이 28일에서 3일로 바뀐 뒤의 결제가 줄줄이 앞 달로 끌려가지 않는다.
 * - 평소 날짜가 [LATE_PAY_FROM] 일 이후(말일 납부)인데 앞 달에 낸 것이 없으면, 그 달 1~[SHIFT_DAYS] 일에 낸 것은 앞 달 몫이다(말일 자동이체가 휴일로 밀림).
 * - 평소 날짜가 [SHIFT_DAYS] 일 이하(달 초 납부)인데 그 달 몫(1일 ~ 평소 날짜 + [SHIFT_DAYS] 일)을 이미 냈고 다음 달에 낸 것이 없으면,
 *   그 달 끝 [SHIFT_DAYS] 일 안에 낸 것은 다음 달 몫이다(월세를 전달 말에 미리 냄).
 * 옮겨서 센 몫이 매달일 때만 옮긴다. 매년 · 몇 달마다 내는 것은 앞뒤 달이 원래 비어 있어서, 일찍 낸 것을 앞 달로 끌어가면 낸 달이 '안 내요' 가 된다.
 * 한 달에 보통 한 번 내는 것만 옮긴다. 한 가게에 3일 · 28일 두 번 내는 것은 28일 것이 늘 다음 달 몫으로 보여, 새 달 초에 3일 것을 안 냈는데도 '냈어요' 가 된다.
 * 그래서 나눠 내는 월세가 휴일로 다음 달 초에 밀린 것도 옮기지 않는다.
 *
 * @param month 고른 달. 매달인지는 이 달까지의 몫으로 본다.
 */
private fun shareMonths(rows: List<TransactionListItem>, month: YearMonth, usualDay: Int, usualCount: Int): Map<Long, YearMonth> {
    val own = rows.associate { it.id to YearMonth.from(it.localDate()) }
    if (usualCount != 1) return own
    val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
    val moved = own.toMutableMap()
    byMonth.forEach { (paidMonth, items) ->
        if (usualDay >= LATE_PAY_FROM && paidMonth.minusMonths(1) !in byMonth) {
            items.filter { it.localDate().dayOfMonth <= SHIFT_DAYS }.forEach { moved[it.id] = paidMonth.minusMonths(1) }
        }
        val paidOwnShare = items.any { it.localDate().dayOfMonth <= usualDay + SHIFT_DAYS }
        if (usualDay <= SHIFT_DAYS && paidOwnShare && paidMonth.plusMonths(1) !in byMonth) {
            items.filter { it.localDate().isInLastDays() }.forEach { moved[it.id] = paidMonth.plusMonths(1) }
        }
    }
    if (moved == own) return own
    val shifted = paymentsOf(rows.filter { moved.getValue(it.id) <= month }, moved)
    return if (cadenceOf(shifted, usualCount) == 1) moved else own
}

/** 달 끝 [SHIFT_DAYS] 일 안인지 */
private fun LocalDate.isInLastDays(): Boolean = dayOfMonth > lengthOfMonth() - SHIFT_DAYS

/** [rows] 를 몫([shares])별로 합친 것(오래된 몫부터). 몫의 날짜는 첫 결제일(실제로 낸 날)이다. */
private fun paymentsOf(rows: List<TransactionListItem>, shares: Map<Long, YearMonth>): List<MonthPayment> = rows
    .groupBy { shares.getValue(it.id) }
    .map { (share, items) ->
        MonthPayment(
            month = share,
            total = items.sumOf { it.amount },
            firstDate = items.minOf { it.localDate() },
            count = items.size,
            latestAmount = items.maxWith(byTime).amount,
        )
    }.sortedBy { it.month }

/**
 * 한 달에 보통 몇 번 내는지. 결제가 있었던 최근 [USUAL_COUNT_MONTHS] 달(달력 달)의 결제 수의 가운데 값(짝수 개면 적은 쪽)이다.
 * 나눠 내는 월세나 한 가게에 두 번 내는 것은 2, 대부분은 1이다.
 */
private fun usualCountOf(rows: List<TransactionListItem>): Int = lowerMedian(
    rows
        .groupBy { YearMonth.from(it.localDate()) }
        .toSortedMap()
        .values
        .map { it.size }
        .takeLast(USUAL_COUNT_MONTHS),
)

/**
 * 낸 몫들(오래된 것부터)의 최근 [CADENCE_GAPS] 개까지의 간격으로 본 주기. 1(매달), 2~[MAX_EVERY_MONTHS], [YEARLY]
 * 보통([usualCount])보다 k 번 더 낸 몫은 밀린 몫을 함께 낸 것으로 보고, 그 몫으로 들어오는 간격에서 k 달을 뺀다(1달보다 짧게는 안 센다).
 * 7월 25일 다음 9월 5일(8월 몫)·25일에 냈으면 간격 2가 아니라 1이다.
 */
private fun cadenceOf(payments: List<MonthPayment>, usualCount: Int): Int {
    val recent = payments.takeLast(CADENCE_GAPS + 1)
    if (recent.size < 2) return 1
    val gaps =
        recent.zipWithNext { a, b ->
            val extra = (b.count - usualCount).coerceAtLeast(0)
            (ChronoUnit.MONTHS.between(a.month, b.month).toInt() - extra).coerceAtLeast(1)
        }
    val gap = lowerMedian(gaps)
    return when {
        gap <= 1 -> 1
        gap <= MAX_EVERY_MONTHS -> gap
        else -> YEARLY
    }
}

/** 가운데 값. 짝수 개면 가운데 둘 중 작은 쪽이다. */
private fun lowerMedian(values: List<Int>): Int = values.sorted()[(values.size - 1) / 2]

/** 가게 이름 없이 적은 지출을 묶는 열쇠. merchantKey 는 빈 글을 내지 않으므로 어느 가게와도 겹치지 않는다. */
const val NO_MERCHANT_KEY = ""

/** 가게 이름 없이 적은 지출 묶음의 이름 */
const val NO_MERCHANT_NAME = "이름 없음"

/** 평소 날이 이 날이면 달마다 그 달 말일에 낸다고 본다([FixedExpenseItem.usualDateIn] 이 그 달 말일로 자른다) */
internal const val LAST_DAY = 31

/** 매년. 11달 넘게 벌어지는 주기는 모두 매년으로 본다. */
const val YEARLY = 12

/** 'N달마다' 로 보는 가장 긴 주기. 이보다 길면 매년이다. */
private const val MAX_EVERY_MONTHS = 10

/** 낼 차례가 된 달부터 이만큼(그 달과 다음 달) '아직 안 냈어요' 로 두고, 그 뒤로는 '한동안 안 냈어요' 로 접는다. */
private const val DUE_MONTHS = 2

/** 주기를 짐작할 때 보는 최근 간격 수. 한 번 건너뛴 정도는 가운데 값이 흡수한다. */
private const val CADENCE_GAPS = 5

/** 평소 날짜를 짐작할 때 보는 최근 결제일 수 */
private const val USUAL_DAY_SAMPLES = 6

/** 한 달에 보통 몇 번 내는지 짐작할 때 보는 최근 결제 달 수 */
private const val USUAL_COUNT_MONTHS = 6

/** 달 끝·다음 달 초의 이만큼(일) 안에 낸 것은 옆 달 몫일 수 있다([shareMonths]) */
private const val SHIFT_DAYS = 5

/** 평소 날짜가 이날 이후(말일 포함)면 다음 달 초에 낸 것을 밀린 앞 달 몫으로 볼 수 있다 */
private const val LATE_PAY_FROM = 26

/** 고른 달 앞으로 읽는 달 수. 매년(직전 간격 최대 13) + 한 달 늦음(1) + 그 앞 해(12) */
private const val HISTORY_MONTHS = 26L
