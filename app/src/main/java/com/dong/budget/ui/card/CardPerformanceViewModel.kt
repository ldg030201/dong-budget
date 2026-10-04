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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 카드실적 탭의 상태. 결제수단 목록과, 카드들의 시작일에 맞춘 가장 좁은 달 범위([tabReadRange])의 지출·환불(쓴 돈을 세는 칸만),
 * 카드마다 처음 쓴 날을 읽어 순수 함수([buildCardPerformance])로 계산한다. 거래나 실적을 고치면 바로 다시 계산되고, 날이 바뀌면 오늘을 새로 잡는다.
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
    /** 오늘·결제수단과 그에 맞춰 읽을 달 범위. 아래에서 두 번 쓰여 한 번만 구독하게 나눠 쓴다. */
    private val input =
        combine(BudgetTime.today(clock), paymentMethodRepository.observeAll()) { today, methods ->
            TabInput(today, methods, tabReadRange(today, methods))
        }.flowOn(Dispatchers.Default)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /** 읽는 달 범위의 거래. 실적을 고칠 때마다가 아니라 범위(시작일이나 실적을 적었는지)가 바뀔 때만 다시 구독한다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows =
        input.map { it.range }.distinctUntilChanged().flatMapLatest { range ->
            transactionRepository.observeCardSpending(range.first, range.second).map { range to it }
        }

    val uiState: StateFlow<CardPerformanceUiState> =
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
            .stateWhileAlive(viewModelScope, CardPerformanceUiState.loading(BudgetTime.toLocalDate(clock.instant())))
}

/** 탭을 계산할 오늘·결제수단과 읽을 달 범위 */
private class TabInput(val today: LocalDate, val methods: List<PaymentMethodEntity>, val range: Pair<YearMonth, YearMonth>)

/**
 * 화면 모델이 사는 동안 계속 구독하는 상태. 다른 화면이 위에 떠 있는 동안에도 새 값을 받는다.
 * 그리는 동안만 구독하면(WhileSubscribed) 편집 화면에 5초 넘게 있다 돌아올 때 옛 시작일·구간으로 만든 값을 먼저 그리고,
 * 새 값이 오면 기간 제목이 ‹ 를 누른 것처럼 미끄러지고 막대가 다시 자란다. 상세는 백스택에서 빠지면 화면 모델과 함께 구독도 끝난다.
 */
internal fun <T> Flow<T>.stateWhileAlive(scope: CoroutineScope, initialValue: T): StateFlow<T> =
    stateIn(scope, SharingStarted.Eagerly, initialValue)
