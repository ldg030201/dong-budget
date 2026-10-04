package com.dong.budget.ui.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.PaymentMethodEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.plus
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 카드실적 탭의 상태. 결제수단 목록과, 카드들의 시작일에 맞춘 가장 좁은 달 범위([tabReadRange])의 지출·환불(쓴 돈을 세는 칸만),
 * 카드마다 처음 쓴 날을 읽어 순수 함수([buildCardPerformance])로 계산한다. 거래나 실적을 고치면 바로 다시 계산되고, 날이 바뀌면 오늘을 새로 잡는다
 * (자정에, 그리고 폰이 잠든 사이 자정이 지났을 때를 위해 탭이 다시 보일 때마다 시계를 다시 읽는다).
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
        viewModelScope.stateWhileAlive(clock, CardPerformanceUiState.loading(BudgetTime.toLocalDate(clock.instant()))) { todayFlow ->
            // 오늘·결제수단과 그에 맞춰 읽을 달 범위. 아래에서 두 번 쓰여 한 번만 구독하게 나눠 쓴다.
            val input =
                combine(todayFlow, paymentMethodRepository.observeAll()) { today, methods ->
                    TabInput(today, methods, tabReadRange(today, methods))
                }.flowOn(Dispatchers.Default)
                    .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
            // 읽는 달 범위의 거래. 실적을 고칠 때마다가 아니라 범위(시작일이나 실적을 적었는지)가 바뀔 때만 다시 구독한다.
            val rows =
                input.map { it.range }.distinctUntilChanged().flatMapLatest { range ->
                    transactionRepository.observeCardSpending(range.first, range.second).map { range to it }
                }
            combine(
                input,
                rows,
                // 카드마다 처음 쓴 날. 그 전에 끝난 지난 기간은 '모자랐어요' 대신 기록이 없다고 둔다.
                transactionRepository.observeFirstUseDates(),
            ) { input, (range, rows), firstUse ->
                // 범위가 막 넓어졌으면 새 범위의 거래가 올 때까지 기다린다(덜 읽은 지난 기간을 0원으로 세지 않게)
                if (range == input.range) buildCardPerformance(input.methods, rows, input.today, firstUse) else null
            }.filterNotNull()
                // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                .flowOn(Dispatchers.Default)
        }
}

/** 탭을 계산할 오늘·결제수단과 읽을 달 범위 */
private class TabInput(val today: LocalDate, val methods: List<PaymentMethodEntity>, val range: Pair<YearMonth, YearMonth>)

/**
 * 화면 모델이 사는 동안([this] 범위) 계속 구독하는 상태. 다른 화면이 위에 떠 있는 동안에도 새 값을 받는다.
 * 그리는 동안만 구독하면(WhileSubscribed) 편집 화면에 5초 넘게 있다 돌아올 때 옛 시작일·구간으로 만든 값을 먼저 그리고,
 * 새 값이 오면 기간 제목이 ‹ 를 누른 것처럼 미끄러지고 막대가 다시 자란다. 상세는 백스택에서 빠지면 화면 모델과 함께 구독도 끝난다.
 *
 * 계속 구독하면 오늘([BudgetTime.today])도 다시 구독할 일이 없다. 그런데 자정까지 기다리는 delay 는 폰이 깊이 잠든 동안 멈춰서,
 * 밤에 잠근 폰을 아침에 열면 어제를 오늘로 하루 넘게 들고 있을 수 있다. 그래서 [upstream] 에 넘기는 오늘은 화면이 다시 구독할 때마다
 * (다른 화면이나 홈에서 돌아올 때) 시계를 다시 읽는다. 같은 날이면 다시 내보내지 않아 다시 계산하지도 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> CoroutineScope.stateWhileAlive(
    clock: Clock,
    initialValue: T,
    upstream: (today: Flow<LocalDate>) -> Flow<T>,
): StateFlow<T> {
    val state = MutableStateFlow(initialValue)
    // 그리는 화면이 없다가 생길 때마다 한 번씩. 처음 만들 때도 한 번 읽는다.
    val shown = state.subscriptionCount.map { it > 0 }.distinctUntilChanged().filter { it }.onStart { emit(true) }
    val today = shown.flatMapLatest { BudgetTime.today(clock) }.distinctUntilChanged()
    // 계산하는 기본 풀에서 그대로 받아 둔다(메인 스레드로 한 번 더 넘기지 않는다). StateFlow 는 어느 스레드에서 바꿔도 된다.
    upstream(today).onEach { state.value = it }.launchIn(this + Dispatchers.Default)
    return state.asStateFlow()
}
