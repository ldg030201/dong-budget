package com.dong.budget.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.CardPerformanceDetailKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 카드 하나의 실적 상세 상태.
 *
 * 기간은 이 화면만 따로 가진다. 처음에는 오늘이 든 기간이고, 앞으로 넘겨 지난 기간을 볼 수 있다(이번 기간보다 뒤로는 못 간다).
 * 고른 기간까지 최근 6기간의 이 카드 지출·환불과 이 카드를 처음 쓴 날을 읽어 순수 함수([buildCardDetail])로 계산한다.
 * 실적(구간·시작일)이나 거래를 고치면 바로 다시 계산된다. 백스택에 있는 동안 계속 구독해 편집 화면에서 돌아와도 옛 값을 먼저 그리지 않는다([stateWhileAlive]).
 * 오늘은 자정에, 그리고 폰이 잠든 사이 자정이 지났을 때를 위해 화면이 다시 보일 때마다 다시 읽는다.
 * 결제수단을 지웠으면 [CardPerformanceDetailUiState.gone] 이다.
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class CardPerformanceDetailViewModel(
    paymentMethodRepository: PaymentMethodRepository,
    private val transactionRepository: TransactionRepository,
    key: CardPerformanceDetailKey,
    clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    /**
     * 사용자가 고른 기간의 이름 달. null 이면 오늘이 든 기간을 따라간다.
     * 그래서 이번 기간을 보던 중에 날이 바뀌어 새 기간이 시작되면 새 기간으로 넘어가고, 일부러 지난 기간을 보고 있으면 그대로 둔다.
     */
    private val picked = MutableStateFlow<YearMonth?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CardPerformanceDetailUiState> =
        viewModelScope.stateWhileAlive(clock, CardPerformanceDetailUiState.loading(BudgetTime.toLocalDate(clock.instant()))) { todayFlow ->
            combine(
                combine(paymentMethodRepository.observe(key.paymentMethodId), picked, todayFlow) { card, pick, today ->
                    val kept = card?.let { keptPick(pick, periodMonthOf(today, it.performanceStartDay)) }
                    // 시작일을 바꿔 고른 기간이 이번 기간 이상이 됐으면 다시 이번 기간을 따라간다(다음 기간이 시작되면 넘어가게)
                    if (card != null && kept != pick) picked.compareAndSet(pick, null)
                    Triple(card, kept, today)
                }.distinctUntilChanged()
                    .flatMapLatest { (card, kept, today) ->
                        if (card == null) return@flatMapLatest flowOf(DetailRows(null, YearMonth.from(today), today, emptyList()))
                        val month = kept ?: periodMonthOf(today, card.performanceStartDay)
                        val periods = historyPeriods(month, card.performanceStartDay)
                        transactionRepository
                            .observeByPaymentMethod(card.id, periods.first().start, periods.last().end)
                            .map { rows -> DetailRows(card, month, today, rows) }
                    },
                // 이 카드를 처음 쓴 날. 그 전에 끝난 기간은 기록이 없는 기간으로 둔다. 기간과 상관없어 기간을 넘기거나 실적을 고쳐도
                // 다시 구독하지 않게 위 흐름 밖에서 한 번만 구독한다.
                transactionRepository.observeFirstUseDate(key.paymentMethodId),
            ) { input, firstUse ->
                input.card?.let { buildCardDetail(it, input.month, input.today, input.rows, firstUse) }
                    ?: CardPerformanceDetailUiState.loading(input.today, gone = true)
            }
                // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                .flowOn(Dispatchers.Default)
        }

    /**
     * 앞 기간. 그려진 기간(uiState)이 아니라 고른 값에서 바로 움직인다. 그려진 기간은 조회와 계산을 거쳐 한 박자 늦게 바뀌어서,
     * 그것을 기준으로 하면 ‹ 를 빨리 두 번 누를 때 두 번 다 같은 기간을 골라 한 칸만 간다.
     * 이번 기간의 이름 달은 오늘과 시작일에만 달려서 잠깐 늦게 바뀌어도 괜찮다.
     */
    fun showPreviousPeriod() {
        val current = uiState.value.currentMonth ?: return
        picked.value = previousPick(picked.value, current)
    }

    /** 다음 기간. 이번 기간에 닿으면 다시 '이번 기간 따라가기' 로 둔다. 이번 기간이면 아무것도 하지 않는다. */
    fun showNextPeriod() {
        val current = uiState.value.currentMonth ?: return
        picked.value = nextPick(picked.value, current)
    }

    /** 기간 줄 오른쪽 '이번 달'(시작일이 1일이 아니면 '이번 기간'). 다시 오늘이 든 기간을 따라간다. */
    fun showCurrentPeriod() {
        picked.value = null
    }
}

/** 상세를 계산할 카드·고른 기간·오늘·그 6기간 거래. 카드가 null 이면 결제수단이 없어졌다. */
private class DetailRows(val card: PaymentMethodEntity?, val month: YearMonth, val today: LocalDate, val rows: List<TransactionListItem>)
