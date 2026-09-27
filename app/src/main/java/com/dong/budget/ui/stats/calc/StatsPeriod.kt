package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.Period
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 통계의 모든 탭이 같이 쓰는 규칙: 고른 달의 때, 평균에 넣을 날, 반올림, 수입 대비 지출.
// ─────────────────────────────────────────────────────────────────────

/** 통계에 넣는 거래인지. 이체는 내 돈이 자리만 옮긴 것이라 금액·건수·목록·빈 달 판정 어디에도 넣지 않는다. */
internal val TransactionListItem.isRecord: Boolean get() = type != TransactionType.TRANSFER

/** 고른 달이 오늘에 견줘 지나간 달인지, 이번 달인지, 아직 오지 않은 달인지 */
fun periodOf(month: YearMonth, today: LocalDate): Period {
    val thisMonth = YearMonth.from(today)
    return when {
        month.isBefore(thisMonth) -> Period.PAST
        month.isAfter(thisMonth) -> Period.FUTURE
        else -> Period.CURRENT
    }
}

/**
 * 하루 평균, 돈 안 쓴 날처럼 '지나간 날' 로 세는 범위(일).
 *
 * 기록을 시작하기 전 날과 오늘 뒤 날은 세지 않는다. 기록 전을 0원으로 세면 평균이 낮게 나오고,
 * 오늘 뒤는 아직 쓰지 않은 날이다. 그래서 max(1일, [firstRecord]) 부터 min(말일, [today]) 까지다.
 *
 * @return 기록이 없거나(null), 아직 오지 않은 달이거나, 기록을 이 달 뒤에 시작했으면 비어 있다.
 */
fun countedDays(month: YearMonth, today: LocalDate, firstRecord: LocalDate?): IntRange {
    firstRecord ?: return IntRange.EMPTY
    val start = maxOf(month.atDay(1), firstRecord)
    val end = minOf(month.atEndOfMonth(), today)
    if (start.isAfter(end)) return IntRange.EMPTY
    return start.dayOfMonth..end.dayOfMonth
}

/**
 * 나눗셈을 정수로 반올림한다. 0.5 는 0 에서 먼 쪽으로 올린다(-2.5 → -3).
 * 평균과 비율처럼 사람이 읽을 값에 쓴다. 정수 나눗셈은 늘 버려서 평균이 조금씩 작게 나온다.
 * @param divisor 0 보다 커야 한다.
 */
fun divRound(dividend: Long, divisor: Long): Long {
    require(divisor > 0) { "나누는 수는 0 보다 커야 한다: $divisor" }
    val quotient = abs(dividend) / divisor
    val rounded = if (abs(dividend) % divisor * 2 >= divisor) quotient + 1 else quotient
    return if (dividend < 0) -rounded else rounded
}

fun divRound(dividend: Long, divisor: Int): Long = divRound(dividend, divisor.toLong())

internal const val PERCENT = 100L
