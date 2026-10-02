package com.dong.budget.ui.card

import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionListItem
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
