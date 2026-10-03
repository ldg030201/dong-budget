package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
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
 * 고정지출 탭의 상태.
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 탭 머리만 그린다(빈 상태 안내가 잠깐 비치지 않게).
 * @property month 보고 있는 달. 이번 달보다 뒤로는 가지 않는다.
 * @property hasCategory '고정지출'(코드 FIXED) 분류가 있는지. 분류 관리에서 지웠고 같은 이름(띄어쓰기 무시)의 분류도 없으면 false
 */
@Immutable
data class FixedExpenseUiState(
    val loaded: Boolean,
    val month: YearMonth,
    val today: LocalDate,
    val hasCategory: Boolean,
    val board: FixedExpenseBoard,
) {
    val isThisMonth: Boolean get() = month == YearMonth.from(today)

    companion object {
        /** 첫 계산 전 자리 값 */
        fun loading(today: LocalDate): FixedExpenseUiState = FixedExpenseUiState(
            loaded = false,
            month = YearMonth.from(today),
            today = today,
            hasCategory = true,
            board = FixedExpenseBoard(),
        )
    }
}

/**
 * 고정지출 탭. [fixedHistoryStart] 부터 [fixedHistoryEnd] 까지의 '고정지출' 분류 지출을 읽어 순수 함수([buildFixedExpenses])로 나눈다.
 * 고른 달 뒤의 기록은 다음 달 초에 밀려 낸 고른 달 몫만 쓴다.
 * 등록창에서 저장하거나 거래를 고치면 조회가 다시 내보내서 '아직 안 냈어요' 가 바로 '냈어요' 로 옮겨 간다.
 * 달을 바꾸면 새 계산이 끝날 때까지 이전 달 화면을 그대로 둔다(빈 화면이 깜빡이지 않게).
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class FixedExpenseViewModel(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    /**
     * 사용자가 고른 지난 달. null 이면 '이번 달' 을 따라간다(통계와 같은 규칙).
     * 그래서 이번 달을 보던 중에 자정이 지나 달이 바뀌면 새 달로 넘어가고, 일부러 지난 달을 보고 있으면 그대로 둔다.
     */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    /** '고정지출' 분류가 있는지. 이름·순서를 바꿔도 다시 계산하지 않게 있고 없음만 따라간다. */
    private val hasCategory: Flow<Boolean> =
        categories
            .observe(CategoryScope.EXPENSE)
            .map { list -> list.any { it.code == FIXED_CATEGORY_CODE } }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FixedExpenseUiState> =
        combine(pickedMonth, BudgetTime.today(clock)) { picked, today ->
            // 이번 달보다 뒤는 볼 수 없다(고를 때도 막지만, 고른 값이 이번 달 이후면 이번 달로 본다)
            val thisMonth = YearMonth.from(today)
            (picked?.takeIf { it < thisMonth } ?: thisMonth) to today
        }.distinctUntilChanged()
            .flatMapLatest { (month, today) ->
                combine(
                    hasCategory,
                    transactions.observeExpensesWithCategoryCode(FIXED_CATEGORY_CODE, fixedHistoryStart(month), fixedHistoryEnd(month)),
                ) { has, rows ->
                    FixedExpenseUiState(
                        loaded = true,
                        month = month,
                        today = today,
                        hasCategory = has,
                        board = buildFixedExpenses(month, today, rows),
                    )
                }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = FixedExpenseUiState.loading(BudgetTime.toLocalDate(clock.instant())),
            )

    fun showPreviousMonth() = showMonth(currentMonth().minusMonths(1))

    /** 이번 달에서는 더 넘기지 않는다(다음 달 버튼도 막혀 있다) */
    fun showNextMonth() = showMonth(currentMonth().plusMonths(1))

    /** 달 줄 오른쪽 '이번 달' 버튼. 다시 '이번 달 따라가기' 로 둔다. */
    fun showThisMonth() {
        pickedMonth.value = null
    }

    private fun currentMonth(): YearMonth = pickedMonth.value ?: YearMonth.now(clock)

    private fun showMonth(month: YearMonth) {
        pickedMonth.value = month.takeIf { it < YearMonth.now(clock) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
