package com.dong.budget.ui.stats.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.StyledItem
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.stats.calc.TREND_MONTHS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 상세(분류나 결제수단 하나)의 상태.
 *
 * 달은 이 화면만 따로 가진다. 누른 줄의 달([StatsDetailKey.year], [StatsDetailKey.month])로 시작하고,
 * 여기서 달을 바꿔도 통계 본 화면의 달은 그대로다.
 * 고른 달까지 최근 6개월을 한 번에 읽어 순수 함수([buildDetail])로 계산한다. 거래를 고치거나 지우면 바로 다시 계산된다.
 * 이름·아이콘·색은 분류(결제수단) 목록에서 id 로 찾으므로, 분류 관리에서 바꾸면 따라 바뀐다.
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class StatsDetailViewModel(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    paymentMethodRepository: PaymentMethodRepository,
    private val key: StatsDetailKey,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.of(key.year, key.month))

    /** 이 상세가 보는 분류(결제수단). 분류는 지출·수입 쪽 목록에서 찾는다. */
    private val entity: Flow<DetailEntity> =
        when (key.dimension) {
            StatsDimension.EXPENSE_CATEGORY -> categoryRepository.observe(CategoryScope.EXPENSE)
            StatsDimension.INCOME_CATEGORY -> categoryRepository.observe(CategoryScope.INCOME)
            StatsDimension.PAYMENT_METHOD -> paymentMethodRepository.observeAll()
        }.map { items: List<StyledItem> -> detailEntity(key.dimension, key.id, items) }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsDetailUiState> =
        combine(month, BudgetTime.today(clock)) { month, today -> month to today }
            .distinctUntilChanged()
            .flatMapLatest { (month, today) ->
                combine(
                    transactionRepository.observeMonths(month.minusMonths(TREND_MONTHS - 1L), month),
                    transactionRepository.observeFirstRecordDate(),
                    entity,
                ) { rows, firstRecord, entity ->
                    buildDetail(key.dimension, key.id, month, today, rows, firstRecord, entity)
                }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StatsDetailUiState.loading(key.dimension, groupKeyOf(key.id), month.value, LocalDate.now(clock)),
            )

    fun showPreviousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun showNextMonth() {
        month.value = month.value.plusMonths(1)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
