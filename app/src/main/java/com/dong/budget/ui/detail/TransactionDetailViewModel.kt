package com.dong.budget.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * 거래 상세의 상태.
 *
 * 거래 한 건과, 오늘부터 1년 전까지 가게 이름이 있는 거래를 함께 읽어 순수 함수([transactionDetail])로 계산한다.
 * 등록창에서 고치면 바로 다시 계산되고, 지우면 [TransactionDetailUiState.Gone] 이 된다. 자정이 지나면 1년의 시작도 따라 밀린다.
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class TransactionDetailViewModel(
    private val repository: TransactionRepository,
    private val transactionId: Long,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionDetailUiState> =
        BudgetTime.today(clock)
            .flatMapLatest { today ->
                combine(
                    repository.observeItem(transactionId),
                    repository.observeWithMerchantSince(samePlaceStart(today)),
                ) { item, rows -> transactionDetail(item, rows, today) }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionDetailUiState.Loading,
            )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
