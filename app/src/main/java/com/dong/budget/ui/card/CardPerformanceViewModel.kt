package com.dong.budget.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/**
 * 카드실적 탭의 상태. 결제수단 목록과, 지난 기간 시작부터 이번 기간 끝까지의 거래([tabReadRange])를 한 번에 읽어
 * 순수 함수([buildCardPerformance])로 계산한다. 거래나 실적을 고치면 바로 다시 계산되고, 날이 바뀌면 오늘을 새로 잡는다.
 * 탭을 처음 열 때 만들어져 셸과 같이 살고, 탭을 떠나 있으면 구독을 멈춘다.
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class CardPerformanceViewModel(
    paymentMethodRepository: PaymentMethodRepository,
    transactionRepository: TransactionRepository,
    clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CardPerformanceUiState> =
        BudgetTime
            .today(clock)
            .flatMapLatest { today ->
                val (from, until) = tabReadRange(today)
                combine(paymentMethodRepository.observeAll(), transactionRepository.observeBetween(from, until)) { methods, rows ->
                    buildCardPerformance(methods, rows, today)
                }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CardPerformanceUiState.loading(BudgetTime.toLocalDate(clock.instant())),
            )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
