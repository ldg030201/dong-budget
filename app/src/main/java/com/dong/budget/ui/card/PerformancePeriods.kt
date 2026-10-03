package com.dong.budget.ui.card

import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────────────
// 카드실적 계산. 화면과 떼어 둔 순수 함수라 단위 테스트로 확인한다(구간 금액 읽고 쓰기는 data/card/CardPerformance.kt).
//
// 실적 기간: 시작일이 s 면 달 M 의 기간은 M 의 s일(그 달에 없으면 말일)부터 다음 달 s일(없으면 말일) 전날까지다.
// 양 끝을 같은 함수로 만들어 기간 사이에 틈도 겹침도 없다. 기간 이름은 시작한 달이다("10월 실적"). s = 1 이면 달력 달과 같다.
// 쓴 돈: 그 결제수단의 지출 − 환불(통계 Totals 와 같은 규칙). 이체·수입은 넣지 않는다. 날짜를 앞으로 적어 둔 거래도 기간 안이면 넣는다.
// 기록 없음: 그 카드를 처음 쓴 날 전에 끝난 지난 기간은 쓴 돈 0원을 '모자랐어요' 로 따지지 않고 기록이 없다고 둔다(통계가 기록 시작 전 달을 비우는 것과 같다).
// ─────────────────────────────────────────────────────────────────────

/** 상세의 막대 차트가 보여 주는 기간 수(고른 기간까지) */
const val HISTORY_PERIODS = 6

/**
 * 실적 기간 하나. [start] 날부터 [end] 날 전까지다(끝은 들지 않는다).
 * @property month 기간 이름이 되는 달(시작한 달). "10월 실적"
 */
data class PerformancePeriod(val month: YearMonth, val start: LocalDate, val end: LocalDate) {
    /** 기간의 마지막 날 */
    val lastDay: LocalDate get() = end.minusDays(1)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(end)
}

/** 시작일이 [startDay] 인 카드의 [month] 실적 기간 */
fun performancePeriod(month: YearMonth, startDay: Int): PerformancePeriod =
    PerformancePeriod(month = month, start = periodStart(month, startDay), end = periodStart(month.plusMonths(1), startDay))

/** [month] 의 [startDay] 일. 그 달에 없는 날(2월 30일 등)이면 말일이다. */
private fun periodStart(month: YearMonth, startDay: Int): LocalDate = month.atDay(startDay.coerceIn(1, month.lengthOfMonth()))

/** [date] 가 든 기간의 이름 달. 그 달 시작일 전이면 지난달 기간이다. */
fun periodMonthOf(date: LocalDate, startDay: Int): YearMonth {
    val month = YearMonth.from(date)
    return if (date.isBefore(periodStart(month, startDay))) month.minusMonths(1) else month
}

/** [today] 가 든 기간 */
fun currentPeriod(today: LocalDate, startDay: Int): PerformancePeriod = performancePeriod(periodMonthOf(today, startDay), startDay)

/**
 * 상세에서 ‹ 를 누르면 고를 기간. 고른 기간(null 이면 이번 기간)의 한 기간 앞이다.
 * 시작일을 바꿔 고른 기간이 이번 기간보다 뒤가 됐으면 화면처럼 이번 기간으로 맞춘 뒤 움직인다.
 * @param picked 지금 고른 기간의 이름 달. null 이면 이번 기간을 따라가는 중이다.
 * @param current 오늘이 든 기간의 이름 달
 */
internal fun previousPick(picked: YearMonth?, current: YearMonth): YearMonth =
    (picked?.let { minOf(it, current) } ?: current).minusMonths(1)

/** 상세에서 › 를 누르면 고를 기간. 이번 기간에 닿으면 null(다시 이번 기간 따라가기)이고, 이번 기간을 보고 있으면 그대로 null 이다. */
internal fun nextPick(picked: YearMonth?, current: YearMonth): YearMonth? {
    val base = picked?.let { minOf(it, current) } ?: return null
    return base.plusMonths(1).takeIf { it < current }
}

/** [month] 까지 최근 [HISTORY_PERIODS] 기간. 오래된 기간이 앞이고 마지막이 [month] 다. */
fun historyPeriods(month: YearMonth, startDay: Int): List<PerformancePeriod> =
    (HISTORY_PERIODS - 1 downTo 0).map { back -> performancePeriod(month.minusMonths(back.toLong()), startDay) }

/** [today] 부터 기간 끝까지 남은 날 수(오늘도 센다). 마지막 날이면 1, 기간이 끝났으면 0 이다. */
fun daysLeft(period: PerformancePeriod, today: LocalDate): Int = ChronoUnit.DAYS.between(today, period.end).coerceAtLeast(0).toInt()

/**
 * 탭이 한 번에 읽을 날 범위(from 부터 until 전까지). 시작일이 무엇이든 지난 기간 시작부터 이번 기간 끝까지 덮는다.
 * 이번 기간은 이번 달이나 지난달 이름이고 지난 기간은 그 앞 달이라, 두 달 전 1일부터 다음다음 달 1일 전까지면 된다.
 */
fun tabReadRange(today: LocalDate): Pair<LocalDate, LocalDate> {
    val month = YearMonth.from(today)
    return month.minusMonths(2).atDay(1) to month.plusMonths(2).atDay(1)
}

/**
 * 카드실적을 볼 결제수단인지. 현금과 계좌이체만 뺀다.
 * 알림으로 생긴 카드와 직접 만든 결제수단은 종류가 '기타' 로 저장돼서, 종류가 카드인 것만 고르면 기본 카드 두 개만 남는다.
 */
fun PaymentMethodEntity.isPerformanceTarget(): Boolean = type != PaymentMethodType.CASH && type != PaymentMethodType.ACCOUNT

/**
 * [period] 에 이 카드 기록이 있는지. 카드를 처음 쓴 날([firstUse])보다 앞에 끝난 기간은 기록이 없다.
 * 한 번도 안 쓴 카드(null)는 지난 기간이 모두 기록이 없다. 오늘이 든 기간([current])은 언제나 있다고 본다(이제부터 쓰면 된다).
 */
fun hasRecord(period: PerformancePeriod, firstUse: LocalDate?, current: YearMonth): Boolean =
    period.month >= current || (firstUse != null && firstUse.isBefore(period.end))

/**
 * 결제수단 [paymentMethodId] 를 지출·환불에 처음 쓴 날. 첫 사용일 조회([firstUse])와 거래 목록([rows])은 따로 방출돼
 * 잠깐 어긋날 수 있다(첫 거래를 막 등록한 직후 등). 보이는 거래보다 늦은 첫 사용일은 있을 수 없어 둘 중 이른 날을 쓴다(통계의 기록 시작일과 같다).
 */
internal fun effectiveFirstUse(rows: List<TransactionListItem>, paymentMethodId: Long, firstUse: LocalDate?): LocalDate? {
    val earliestRow = rows.filter { it.paymentMethodId == paymentMethodId && it.type in SPENDING_TYPES }.minOfOrNull { it.localDate() }
    return listOfNotNull(firstUse, earliestRow).minOrNull()
}

/** 쓴 돈에 드는 거래 종류(지출에서 환불을 뺀다) */
private val SPENDING_TYPES = setOf(TransactionType.EXPENSE, TransactionType.REFUND)

/** [rows] 중 결제수단 [paymentMethodId] 로 [period] 안에 쓴 돈(지출 − 환불). 환불이 더 많으면 음수다. */
fun spentIn(rows: List<TransactionListItem>, paymentMethodId: Long, period: PerformancePeriod): Long =
    rows.filter { it.paymentMethodId == paymentMethodId && it.localDate() in period }.totals().expense

/**
 * 한 기간에 실적 구간을 얼마나 채웠는지. 쓴 돈이 구간 금액과 같으면 채운 것이다(카드사의 '30만원 이상').
 * @property spent 그 기간에 쓴 돈(지출 − 환불)
 * @property tiers 구간 금액(오름차순). 실적을 안 적었으면 비어 있다.
 */
data class TierProgress(val spent: Long, val tiers: List<Long>) {
    /** 채운 구간 수 */
    val reachedCount: Int = tiers.count { spent >= it }

    /** 채운 가장 높은 구간. 하나도 못 채웠으면 null */
    val reached: Long? get() = tiers.getOrNull(reachedCount - 1)

    /** 다음에 채울 구간. 다 채웠거나 구간이 없으면 null */
    val next: Long? get() = tiers.getOrNull(reachedCount)

    /** 다음 구간까지 남은 돈. 다 채웠으면 0 */
    val remaining: Long get() = next?.let { it - spent } ?: 0

    /** 가장 높은 구간까지 채웠는지 */
    val allReached: Boolean get() = tiers.isNotEmpty() && next == null

    /** 막대를 채울 비율(0~1). 가장 높은 구간이 막대 끝이다. */
    val fraction: Float
        get() = tiers.lastOrNull()?.let { top -> (spent.coerceAtLeast(0).toDouble() / top).coerceIn(0.0, 1.0).toFloat() } ?: 0f

    /** 막대 위 구간 자리(0~1). 가장 높은 구간은 막대 끝이라 넣지 않는다. */
    val ticks: List<Float> get() = tiers.dropLast(1).map { (it.toDouble() / tiers.last()).toFloat() }
}

/**
 * 남은 날 동안 다음 구간을 채우려면 하루에 써야 하는 돈. 날마다 이만큼 쓰면 넉넉히 채우도록 원 아래를 올린다.
 * 다 채웠거나 남은 날이 없으면 null
 */
fun dailyNeed(progress: TierProgress, daysLeft: Int): Long? {
    val remaining = progress.remaining
    if (progress.next == null || remaining <= 0 || daysLeft <= 0) return null
    return (remaining + daysLeft - 1) / daysLeft
}
