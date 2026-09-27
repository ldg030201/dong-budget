package com.dong.budget.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.ui.stats.calc.TREND_MONTHS
import com.dong.budget.ui.stats.calc.buildStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 화면의 상태. 네 탭(월별·일별·분류·결제수단)이 고른 달 하나를 같이 쓴다.
 *
 * 고른 달이 들어 있는 여러 달을 한 번에 읽어 순수 함수(calc/buildStatistics)로 모두 계산한다.
 * 거래를 고치거나 지우면 조회가 다시 내보내서 숫자가 바로 바뀐다.
 * 달을 바꾸면 새 계산이 끝날 때까지 이전 달 화면을 그대로 둔다(빈 화면이 깜빡이지 않게).
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class StatsViewModel(private val repository: TransactionRepository, private val clock: Clock = Clock.system(BudgetTime.ZONE)) :
    ViewModel() {
    /**
     * 사용자가 고른 달. null 이면 '이번 달' 을 따라간다(홈과 같은 규칙).
     * 그래서 이번 달을 보던 중에 달이 바뀌면 새 달로 넘어가고, 일부러 다른 달을 보고 있으면 그대로 둔다.
     * 통계를 나갔다 다시 들어오면 늘 이번 달에서 시작한다.
     */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> =
        combine(pickedMonth, BudgetTime.today(clock)) { picked, today -> (picked ?: YearMonth.from(today)) to today }
            .distinctUntilChanged()
            .flatMapLatest { (month, today) ->
                // 최근 6개월 추이, 최근 3달 요일별, 올해 모아 보기를 한 번에 덮는 창. 1월이면 앞 해 8월부터, 12월이면 1월부터다.
                val from = minOf(month.minusMonths(TREND_MONTHS - 1L), YearMonth.of(month.year, 1))
                combine(repository.observeMonths(from, month), repository.observeFirstRecordDate()) { rows, firstRecord ->
                    buildStatistics(month, today, rows, firstRecord)
                }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = LocalDate.now(clock).let { StatsUiState.loading(YearMonth.from(it), it) },
            )

    fun showPreviousMonth() = moveMonth(-1)

    fun showNextMonth() = moveMonth(1)

    /** 달 줄 오른쪽 '이번 달' 버튼. 다시 '이번 달 따라가기' 로 둔다. */
    fun showThisMonth() {
        pickedMonth.value = null
    }

    /** 월별 탭의 6개월 표에서 다른 달 줄을 눌렀을 때. 네 탭 모두 그 달로 바뀐다. 이번 달이면 다시 '이번 달 따라가기' 로 둔다. */
    fun showMonth(month: YearMonth) {
        pickedMonth.value = month.takeIf { it != YearMonth.now(clock) }
    }

    private fun moveMonth(offset: Long) = showMonth((pickedMonth.value ?: YearMonth.now(clock)).plusMonths(offset))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
