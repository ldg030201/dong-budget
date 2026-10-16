package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.FixedMerchantRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.fixedHistoryEnd
import com.dong.budget.data.db.fixedHistoryStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 고정지출 탭의 상태.
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 탭 머리만 그린다(빈 상태 안내가 잠깐 비치지 않게).
 * @property month 보고 있는 달. 이번 달보다 뒤로는 가지 않는다.
 * @property hasCategory '고정지출'(코드 FIXED) 분류가 있는지. 분류 관리에서 지웠고 같은 이름(띄어쓰기 무시)의 분류도 없으면 false
 * @property board 고정지출 목록에 등록한 곳만 센 판
 * @property candidates '고정지출' 분류로 적었는데 목록에 등록할지 물을 곳([splitByRegistry]). 고른 달과 상관없이 같다.
 * @property declined 등록 안 한 곳. 접어 두고 다시 등록할 수 있게 한다.
 */
@Immutable
data class FixedExpenseUiState(
    val loaded: Boolean,
    val month: YearMonth,
    val today: LocalDate,
    val hasCategory: Boolean,
    val board: FixedExpenseBoard,
    val candidates: List<FixedCandidate> = emptyList(),
    val declined: List<FixedCandidate> = emptyList(),
) {
    val isThisMonth: Boolean get() = month == YearMonth.from(today)

    /** 보여 줄 것이 하나도 없는지(등록한 곳 · 물을 곳 · 등록 안 한 곳 모두 없다) */
    val isEmpty: Boolean get() = board.isEmpty && candidates.isEmpty() && declined.isEmpty()

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
 * 고정지출 탭. [fixedHistoryStart] 부터 [fixedHistoryEnd](오늘이 든 달)까지의 '고정지출' 분류 지출을 읽어, 고정지출 목록에 등록한 곳의 것만
 * 순수 함수([buildFixedExpenses])로 나눈다. 목록에 없는 곳은 등록할지 묻는다([splitByRegistry]).
 * 지난 달을 볼 때도 이번 달까지 읽어, 일정은 오늘이 든 달 기준 기록으로 짐작하고(같은 날 본 모든 달 화면이 같은 일정) 다음 달에 밀려 낸
 * 고른 달 몫도 센다.
 * 등록창에서 저장하거나 거래를 고치면 조회가 다시 내보내서 '아직 안 냈어요' 가 바로 '냈어요' 로 옮겨 간다. 목록을 바꿔도 그렇다.
 * 달을 바꾸면 새 계산이 끝날 때까지 이전 달 화면을 그대로 둔다(빈 화면이 깜빡이지 않게).
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class FixedExpenseViewModel(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val fixedMerchants: FixedMerchantRepository,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    /**
     * 사용자가 고른 지난 달. null 이면 '이번 달' 을 따라간다(통계와 같은 규칙).
     * 그래서 이번 달을 보던 중에 자정이 지나 달이 바뀌면 새 달로 넘어가고, 일부러 지난 달을 보고 있으면 그대로 둔다.
     */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    /**
     * '고정지출' 분류가 있는지. 이름·순서를 바꿔도 다시 계산하지 않게 있고 없음만 따라간다.
     * 고른 달과 상관없어서, 달을 넘겨도 다시 읽지 않게 화면 모델이 살아 있는 동안 나눠 쓴다.
     */
    private val hasCategory: Flow<Boolean> =
        categories
            .observe(CategoryScope.EXPENSE)
            .map { list -> list.any { it.code == FIXED_CATEGORY_CODE } }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /**
     * '고정지출' 지출이 하나도 남지 않은 곳을 목록에서 지운다. 목록은 이 탭에서만 보여서 앱을 켤 때가 아니라 탭을 열 때 한 번 한다.
     * 목록은 정리가 끝난 뒤에 읽는다. 첫 판이 정리된 목록을 보고, 정리 때문에 판을 한 번 더 계산하지 않는다.
     */
    private val pruned = viewModelScope.async { fixedMerchants.prune() }

    /** 고정지출 목록. [hasCategory] 처럼 고른 달과 상관없어 나눠 쓴다. */
    private val registry: Flow<Map<String, FixedMerchantEntity>> =
        flow {
            pruned.await()
            emitAll(fixedMerchants.observe())
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

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
                    transactions.observeExpensesWithCategoryCode(
                        FIXED_CATEGORY_CODE,
                        fixedHistoryStart(month),
                        fixedHistoryEnd(month, today),
                    ),
                    registry,
                ) { has, fixedRows, registry ->
                    val rows = fixedRows.map { it.item }
                    val created = fixedRows.associate { it.item.id to it.createdAt }
                    val split = splitByRegistry(rows, registry, created, today)
                    FixedExpenseUiState(
                        loaded = true,
                        month = month,
                        today = today,
                        hasCategory = has,
                        board = buildFixedExpenses(month, today, split.registered),
                        candidates = split.candidates,
                        declined = split.declined,
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

    /** '등록' 을 누른 곳([key])을 고정지출 목록에 넣는다. 묻던 줄이 사라지고 판에 나타난다. */
    fun register(key: String) {
        viewModelScope.launch { fixedMerchants.register(key, clock.instant()) }
    }

    /** '안 함' 을 누르거나 탭에서 뺀 곳([key]). 지난 거래의 분류는 그대로이고, 지금 뒤에 새로 낸 '고정지출' 지출이 생기면 다시 묻는다. */
    fun decline(key: String) {
        viewModelScope.launch { fixedMerchants.decline(key, clock.instant()) }
    }

    private fun currentMonth(): YearMonth = pickedMonth.value ?: YearMonth.now(clock)

    private fun showMonth(month: YearMonth) {
        pickedMonth.value = month.takeIf { it < YearMonth.now(clock) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
