package com.dong.budget.ui.card

import androidx.compose.runtime.Immutable
import com.dong.budget.data.card.performanceTierList
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.ui.home.DayGroup
import com.dong.budget.ui.home.groupByDay
import com.dong.budget.ui.home.localDate
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 카드실적 탭과 상세가 그리는 값. 결제수단 목록과 거래 행에서 순수 함수로 만든다(PerformancePeriods 의 계산을 쓴다).
// ─────────────────────────────────────────────────────────────────────

/** 카드 하나의 이름·아이콘·색(결제수단 그대로). 분류 관리에서 바꾸면 따라 바뀐다. */
data class CardInfo(val id: Long, val name: String, val icon: String, val color: String)

internal fun PaymentMethodEntity.toCardInfo(): CardInfo = CardInfo(id = id, name = name, icon = icon, color = color)

/**
 * 실적을 적은 카드 한 장(탭의 판 하나).
 * @property period 오늘이 든 기간
 * @property daysLeft 그 기간에 남은 날(오늘도 센다)
 * @property progress 이번 기간에 구간을 얼마나 채웠는지
 * @property previousMonth 바로 앞 기간의 이름 달
 * @property previous 바로 앞 기간. 카드 혜택은 보통 전월 실적으로 정해져서 함께 보여 준다.
 */
data class TrackedCard(
    val card: CardInfo,
    val period: PerformancePeriod,
    val daysLeft: Int,
    val progress: TierProgress,
    val previousMonth: YearMonth,
    val previous: TierProgress,
)

/**
 * 실적을 안 적은 카드 한 줄.
 * @property period 오늘이 든 기간(시작일을 안 바꿨으면 이번 달)
 * @property spent 그 기간에 쓴 돈
 */
data class UntrackedCard(val card: CardInfo, val period: PerformancePeriod, val spent: Long)

/**
 * 카드실적 탭의 상태.
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 탭 머리만 그린다(빈 안내가 잠깐 비치지 않게).
 * @property tracked 실적을 적은 카드(결제수단 순서)
 * @property untracked 실적을 안 적은 카드(이번 기간에 쓴 돈이 많은 순, 같으면 결제수단 순서)
 */
@Immutable
data class CardPerformanceUiState(
    val loaded: Boolean,
    val today: LocalDate,
    val tracked: List<TrackedCard>,
    val untracked: List<UntrackedCard>,
) {
    /** 실적을 볼 카드가 하나라도 있는지 */
    val hasCards: Boolean get() = tracked.isNotEmpty() || untracked.isNotEmpty()

    companion object {
        fun loading(today: LocalDate): CardPerformanceUiState =
            CardPerformanceUiState(loaded = false, today = today, tracked = emptyList(), untracked = emptyList())
    }
}

/**
 * 탭의 상태를 만든다.
 * @param methods 결제수단 전부(결제수단 순서). 현금·계좌이체는 여기서 뺀다([isPerformanceTarget]).
 * @param rows 모든 결제수단의 거래([tabReadRange] 범위)
 */
fun buildCardPerformance(methods: List<PaymentMethodEntity>, rows: List<TransactionListItem>, today: LocalDate): CardPerformanceUiState {
    val tracked = mutableListOf<TrackedCard>()
    val untracked = mutableListOf<UntrackedCard>()
    methods.filter { it.isPerformanceTarget() }.forEach { method ->
        val tiers = method.performanceTierList
        val period = currentPeriod(today, method.performanceStartDay)
        val spent = spentIn(rows, method.id, period)
        if (tiers.isEmpty()) {
            untracked += UntrackedCard(card = method.toCardInfo(), period = period, spent = spent)
        } else {
            val previous = performancePeriod(period.month.minusMonths(1), method.performanceStartDay)
            tracked +=
                TrackedCard(
                    card = method.toCardInfo(),
                    period = period,
                    daysLeft = daysLeft(period, today),
                    progress = TierProgress(spent, tiers),
                    previousMonth = previous.month,
                    previous = TierProgress(spentIn(rows, method.id, previous), tiers),
                )
        }
    }
    // 정렬은 안정적이라 쓴 돈이 같으면 결제수단 순서가 남는다
    return CardPerformanceUiState(loaded = true, today = today, tracked = tracked, untracked = untracked.sortedByDescending { it.spent })
}

/** 상세 막대 차트의 한 칸. 한 기간에 쓴 돈과 구간을 얼마나 채웠는지 */
data class PeriodSpent(val period: PerformancePeriod, val progress: TierProgress)

/**
 * 카드실적 상세의 상태.
 * @property loaded 첫 계산이 끝났는지
 * @property gone 결제수단이 없어졌는지(지웠음). 화면을 닫는다.
 * @property card 카드 이름·아이콘·색. 첫 계산 전에는 null
 * @property tiers 구간 금액(오름차순). 실적을 지웠으면 비어 있다.
 * @property period 고른 기간. 이번 기간보다 뒤로는 못 간다.
 * @property currentMonth 오늘이 든 기간의 이름 달
 * @property daysLeft 고른 기간이 이번 기간일 때 남은 날. 지난 기간이면 0
 * @property history 고른 기간까지 최근 [HISTORY_PERIODS] 기간(오래된 것이 앞, 마지막이 고른 기간)
 * @property days 고른 기간의 거래를 날짜별로(최근 날이 먼저)
 * @property count 고른 기간의 거래 수(지출·환불)
 */
@Immutable
data class CardPerformanceDetailUiState(
    val loaded: Boolean,
    val gone: Boolean,
    val today: LocalDate,
    val card: CardInfo?,
    val tiers: List<Long>,
    val period: PerformancePeriod?,
    val currentMonth: YearMonth?,
    val daysLeft: Int,
    val progress: TierProgress,
    val history: List<PeriodSpent>,
    val days: List<DayGroup>,
    val count: Int,
) {
    /** 고른 기간이 오늘이 든 기간인지 */
    val isCurrent: Boolean get() = period != null && period.month == currentMonth

    companion object {
        fun loading(today: LocalDate, gone: Boolean = false): CardPerformanceDetailUiState = CardPerformanceDetailUiState(
            loaded = false,
            gone = gone,
            today = today,
            card = null,
            tiers = emptyList(),
            period = null,
            currentMonth = null,
            daysLeft = 0,
            progress = TierProgress(0, emptyList()),
            history = emptyList(),
            days = emptyList(),
            count = 0,
        )
    }
}

/**
 * 상세의 상태를 만든다.
 * @param month 보고 싶은 기간의 이름 달. 이번 기간보다 뒤면 이번 기간으로 맞춘다(앞으로의 기간은 못 본다).
 * @param rows 이 카드의 지출·환불([historyPeriods] 의 첫 기간 시작부터 마지막 기간 끝까지)
 */
fun buildCardDetail(
    method: PaymentMethodEntity,
    month: YearMonth,
    today: LocalDate,
    rows: List<TransactionListItem>,
): CardPerformanceDetailUiState {
    val startDay = method.performanceStartDay
    val tiers = method.performanceTierList
    val currentMonth = periodMonthOf(today, startDay)
    val shown = minOf(month, currentMonth)
    val history = historyPeriods(shown, startDay).map { PeriodSpent(it, TierProgress(spentIn(rows, method.id, it), tiers)) }
    val selected = history.last()
    val inPeriod = rows.filter { it.paymentMethodId == method.id && it.localDate() in selected.period }
    return CardPerformanceDetailUiState(
        loaded = true,
        gone = false,
        today = today,
        card = method.toCardInfo(),
        tiers = tiers,
        period = selected.period,
        currentMonth = currentMonth,
        daysLeft = if (shown == currentMonth) daysLeft(selected.period, today) else 0,
        progress = selected.progress,
        history = history,
        days = groupByDay(inPeriod),
        count = inPeriod.size,
    )
}
