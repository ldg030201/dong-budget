package com.dong.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionListItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth,
    val today: LocalDate,
    val totals: Totals,
    /** 달력 칸에 적을 날짜별 합계. 거래가 없는 날은 없다 */
    val days: Map<LocalDate, Totals>,
    /** 날짜 구분선 아래 거래들. 최신 → 오래된 순 */
    val groups: List<DayGroup>,
    /** null 이면 지난달 비교 문구를 숨긴다 */
    val comparison: SpendingComparison?,
)

/**
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class HomeViewModel(private val repository: TransactionRepository, private val clock: Clock = Clock.system(BudgetTime.ZONE)) :
    ViewModel() {
    /**
     * 사용자가 화살표로 고른 달. null 이면 '이번 달' 을 따라간다.
     * 그래서 이번 달을 보던 중에 달이 바뀌면 새 달로 넘어가고, 일부러 다른 달을 보고 있으면 그대로 둔다.
     */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    /** 서울 기준 오늘. 구독하는 동안 자정마다 새 날짜를 내보낸다. 화면을 켜 둔 채 날이 바뀌어도 '오늘' 이 따라온다. */
    private val today: Flow<LocalDate> =
        flow {
            while (true) {
                val now = clock.instant()
                emit(BudgetTime.toLocalDate(now))
                delay(BudgetTime.millisUntilNextDay(now))
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> =
        combine(pickedMonth, today) { picked, now -> (picked ?: YearMonth.from(now)) to now }
            .distinctUntilChanged()
            .flatMapLatest { (month, now) ->
                // 지난달 비교에 지난달 거래도 필요하다
                combine(
                    repository.observeMonth(month),
                    repository.observeMonth(month.minusMonths(1)),
                ) { current, previous -> buildState(month, now, current, previous) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = LocalDate.now(clock).let { buildState(YearMonth.from(it), it, emptyList(), emptyList()) },
            )

    fun showPreviousMonth() = moveMonth(-1)

    fun showNextMonth() = moveMonth(1)

    private fun moveMonth(offset: Long) {
        val thisMonth = YearMonth.now(clock)
        val target = (pickedMonth.value ?: thisMonth).plusMonths(offset)
        // 이번 달로 돌아오면 다시 '이번 달 따라가기' 로 둔다
        pickedMonth.value = target.takeIf { it != thisMonth }
    }

    private fun buildState(month: YearMonth, today: LocalDate, current: List<TransactionListItem>, previous: List<TransactionListItem>) =
        HomeUiState(
            month = month,
            today = today,
            totals = current.totals(),
            days = dailyTotals(current),
            groups = groupByDay(current),
            comparison = compareSpending(month, today, current, previous),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
