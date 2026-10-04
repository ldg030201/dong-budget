package com.dong.budget.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/**
 * 카드실적 탭의 상태. 결제수단 목록과, 지난 기간 시작부터 이번 기간 끝까지의 거래([tabReadRange]), 카드마다 처음 쓴 날을 읽어
 * 순수 함수([buildCardPerformance])로 계산한다. 거래나 실적을 고치면 바로 다시 계산되고, 날이 바뀌면 오늘을 새로 잡는다.
 * 탭을 처음 열 때 만들어져 셸과 같이 살고, 그동안 계속 구독한다([stateWhileAlive]). '실적 추가' 로 편집 화면에 오래 있다 돌아와도
 * 옛 값(실적을 적은 카드가 '실적을 적지 않은 카드' 에 있는 등)을 잠깐 그렸다가 옮기지 않게 한다.
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
                val (first, last) = tabReadRange(today)
                combine(
                    paymentMethodRepository.observeAll(),
                    transactionRepository.observeMonths(first, last),
                    // 카드마다 처음 쓴 날. 그 전에 끝난 지난 기간은 '모자랐어요' 대신 기록이 없다고 둔다.
                    transactionRepository.observeFirstUseDates(),
                ) { methods, rows, firstUse -> buildCardPerformance(methods, rows, today, firstUse) }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateWhileAlive(viewModelScope, CardPerformanceUiState.loading(BudgetTime.toLocalDate(clock.instant())))
}

/**
 * 화면 모델이 사는 동안 계속 구독하는 상태. 다른 화면이 위에 떠 있는 동안에도 새 값을 받는다.
 * 그리는 동안만 구독하면(WhileSubscribed) 편집 화면에 5초 넘게 있다 돌아올 때 옛 시작일·구간으로 만든 값을 먼저 그리고,
 * 새 값이 오면 기간 제목이 ‹ 를 누른 것처럼 미끄러지고 막대가 다시 자란다. 상세는 백스택에서 빠지면 화면 모델과 함께 구독도 끝난다.
 */
internal fun <T> Flow<T>.stateWhileAlive(scope: CoroutineScope, initialValue: T): StateFlow<T> =
    stateIn(scope, SharingStarted.Eagerly, initialValue)
