package com.dong.budget.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Year
import java.time.YearMonth

/**
 * 저축 화면(내역 · 통계 탭)의 상태. '저축' 분류 거래를 기간 없이 모두 읽어 순수 함수([buildSavings])로 계산한다.
 * 저축은 한 달에 몇 건뿐이라 모두 읽어도 가볍다. 거래를 고치거나 지우면 조회가 다시 내보내서 숫자가 바로 바뀐다.
 * 통계 탭의 달이나 내역 탭의 해를 바꾸면 새 계산이 끝날 때까지 이전 화면을 그대로 둔다(빈 화면이 깜빡이지 않게).
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 * @param scope 화면 모델이 도는 범위([viewModelScope], 화면 모델이 사라지면 끝난다). 기본은 메인 스레드이고, 메인 스레드가 없는
 *   JVM 테스트에서 기본 풀로 돌리려고 바꿀 수 있게 둔다.
 */
class SavingsViewModel(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    /**
     * 통계 탭에서 고른 지난 달. null 이면 '이번 달' 을 따라간다(통계와 같은 규칙).
     * 그래서 이번 달을 보던 중에 자정이 지나 달이 바뀌면 새 달로 넘어가고, 일부러 지난 달을 보고 있으면 그대로 둔다.
     * 저축을 나갔다 다시 들어오면 이번 달에서 시작한다.
     */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    /**
     * 내역 탭에서 고른 지난해. null 이면 '올해' 를 따라간다(고른 달과 같은 규칙).
     * 그래서 올해를 보던 중에 자정이 지나 해가 바뀌면 새해로 넘어가고, 일부러 지난해를 보고 있으면 그대로 둔다.
     * 화면 모델에 두어 탭을 오가도 남고, 저축을 나갔다 다시 들어오면 올해에서 시작한다.
     */
    private val pickedYear = MutableStateFlow<Int?>(null)

    /**
     * '저축' 분류가 있는지. 이름·순서를 바꿔도 다시 계산하지 않게 있고 없음만 따라간다.
     * 고른 달 · 해와 상관없어서, 달이나 해를 넘겨도 다시 읽지 않게 화면 모델이 살아 있는 동안 나눠 쓴다.
     */
    private val hasCategory: Flow<Boolean> =
        categories
            .observe(CategoryScope.EXPENSE)
            .map { list -> list.any { it.code == SAVINGS_CATEGORY_CODE } }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** 저축 거래 전부. [hasCategory] 처럼 고른 달 · 해와 상관없어 나눠 쓴다. */
    private val rows: Flow<List<TransactionListItem>> =
        transactions
            .observeWithCategoryCode(SAVINGS_CATEGORY_CODE)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SavingsUiState> =
        combine(pickedMonth, pickedYear, BudgetTime.today(clock)) { month, year, today ->
            // 이번 달 · 올해보다 뒤는 볼 수 없다(고를 때도 막지만, 고른 값이 그보다 뒤면 이번 달 · 올해로 본다)
            val thisMonth = YearMonth.from(today)
            Triple(month?.takeIf { it < thisMonth } ?: thisMonth, year?.takeIf { it < today.year } ?: today.year, today)
        }.distinctUntilChanged()
            .flatMapLatest { (month, year, today) ->
                combine(hasCategory, rows) { has, rows -> buildSavings(month, year, today, has, rows) }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SavingsUiState.loading(BudgetTime.toLocalDate(clock.instant())),
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

    fun showPreviousYear() = showYear(currentYear() - 1)

    /** 올해에서는 더 넘기지 않는다(다음 해 버튼도 막혀 있다) */
    fun showNextYear() = showYear(currentYear() + 1)

    /** 해 줄 오른쪽 '올해' 버튼. 다시 '올해 따라가기' 로 둔다. */
    fun showThisYear() {
        pickedYear.value = null
    }

    private fun currentYear(): Int = pickedYear.value ?: Year.now(clock).value

    private fun showYear(year: Int) {
        pickedYear.value = year.takeIf { it < Year.now(clock).value }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
