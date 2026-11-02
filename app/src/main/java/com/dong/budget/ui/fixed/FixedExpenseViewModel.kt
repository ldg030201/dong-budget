package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.FixedMerchantRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.FixedKind
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.db.fixedHistoryEnd
import com.dong.budget.data.db.fixedHistoryStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
 * 고정지출(고정저축) 탭의 상태.
 * @property kind 어느 탭인지(고정지출 · 고정저축). 그 분류([FixedKind.categoryCode], '고정지출' · '저축') 지출만 세고, 화면 글도 종류를 따른다([texts]).
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 탭 머리만 그린다(빈 상태 안내가 잠깐 비치지 않게).
 * @property month 보고 있는 달. 이번 달보다 뒤로는 가지 않는다.
 * @property hasCategory 그 분류(코드 FIXED · SAVINGS)가 있는지. 분류 관리에서 지웠고 같은 이름(띄어쓰기 무시)의 분류도 없으면 false
 * @property board 목록에 등록한 곳과, 끝 달([FixedEndedPlace.endMonth])까지의 해지한 곳만 센 판. 해지한 곳의 덜 낸 차례는 기다리지 않는다.
 * @property candidates 그 분류로 적었는데 목록에 등록할지 물을 곳([splitByRegistry]). 고른 달과 상관없이 같다.
 * @property declined 등록 안 한 곳. 접어 두고 다시 등록할 수 있게 한다.
 * @property ended 해지한 곳(다시 묻는 곳은 빼고). 접어 두고 되돌릴 수 있게 한다. 고른 달과 상관없이 같다.
 * @property endedKeys [board] 에 보이는 해지한 곳(끝 달까지)의 열쇠. 그 줄에는 다시 해지하라고 하지 않는다.
 * @property hasEnded 해지한 곳이 있는지(다시 묻는 곳 포함, 고른 달과 상관없이 같다). 있으면 판이 비어도(끝 달 뒤) 목록에 넣은 적은 있어서
 *   요약에 '목록에 넣은 고정지출이 없어요' 가 아니라 '낼 고정지출이 없어요' 로 적는다([countSentence]).
 */
@Immutable
data class FixedExpenseUiState(
    val kind: FixedKind,
    val loaded: Boolean,
    val month: YearMonth,
    val today: LocalDate,
    val hasCategory: Boolean,
    val board: FixedExpenseBoard,
    val candidates: List<FixedCandidate> = emptyList(),
    val declined: List<FixedCandidate> = emptyList(),
    val ended: List<FixedCandidate> = emptyList(),
    val endedKeys: Set<String> = emptySet(),
    val hasEnded: Boolean = false,
) {
    val isThisMonth: Boolean get() = month == YearMonth.from(today)

    /** 이 탭의 문장 묶음. 탭 이름 · 분류 이름이 든 문장이 종류마다 다르다. */
    internal val texts: FixedTexts get() = kind.texts

    /** 보여 줄 것이 하나도 없는지(등록한 곳 · 물을 곳 · 등록 안 한 곳 · 해지한 곳 모두 없다) */
    val isEmpty: Boolean get() = board.isEmpty && candidates.isEmpty() && declined.isEmpty() && ended.isEmpty()

    /**
     * 판 줄 [key] 가 끝 달까지 보이는 해지한 곳인지([endedKeys], 고른 달마다 다르다). 그 줄은 남은 차례를 더 내지 않으니
     * '등록하기' 도 '해지' 도 두지 않고, 길게 눌러 띄운 빼기 확인 창에도 '해지' 를 두지 않는다(다시 해지할 것이 없다).
     */
    fun isEnded(key: String): Boolean = key in endedKeys

    /** 판 줄 [item] 에 '등록하기' 를 둘지. 이번 달에 아직 안 낸 줄이다. 가게 이름 없이 묶인 것은 무엇을 등록할지 몰라 두지 않는다. */
    fun canRegister(item: FixedExpenseItem): Boolean =
        !isEnded(item.key) && item.status == FixedStatus.DUE && isThisMonth && item.merchant != null

    /** 판 줄 [item] 에 '해지' 를 둘지. 안 냈거나 한동안 안 낸 줄은 그만둔 것일 수 있어 바로 누르게 한다(냈거나 안 내는 달인 줄은 길게 눌러서). */
    fun canEnd(item: FixedExpenseItem): Boolean =
        !isEnded(item.key) && (item.status == FixedStatus.DUE || item.status == FixedStatus.STOPPED)

    companion object {
        /** 첫 계산 전 자리 값 */
        fun loading(kind: FixedKind, today: LocalDate): FixedExpenseUiState = FixedExpenseUiState(
            kind = kind,
            loaded = false,
            month = YearMonth.from(today),
            today = today,
            hasCategory = true,
            board = FixedExpenseBoard(),
        )

        /** 목록으로 나눈 지출([split])로 [month] 를 [today] 에 본 상태. 화면 모델이 계산을 마칠 때마다 만든다. */
        fun of(kind: FixedKind, month: YearMonth, today: LocalDate, hasCategory: Boolean, split: FixedRegistrySplit): FixedExpenseUiState =
            FixedExpenseUiState(
                kind = kind,
                loaded = true,
                month = month,
                today = today,
                hasCategory = hasCategory,
                board = split.board(month, today),
                candidates = split.candidates,
                declined = split.declined,
                ended = split.endedPlaces,
                endedKeys = split.endedKeysIn(month),
                hasEnded = split.ended.isNotEmpty(),
            )
    }
}

/**
 * 고정지출 탭. [fixedHistoryStart] 부터 [fixedHistoryEnd](오늘이 든 달)까지의 '고정지출' 분류 지출을 읽어, 고정지출 목록에 등록한 곳의 것만
 * 순수 함수([buildFixedExpenses])로 나눈다. 해지한 곳은 끝 달까지 해지 전 결제만 넣고 남은 차례는 안 나간 것으로 본다([FixedRegistrySplit.board]).
 * 목록에 없는 곳은 등록할지 묻는다([splitByRegistry]).
 * 고정저축 탭도 이 화면 모델이다. 목록 저장소의 종류([FixedMerchantRepository.kind])를 따라 '저축' 분류 지출을 고정저축 목록으로 똑같이 나눈다.
 * 지난 달을 볼 때도 이번 달까지 읽어, 일정은 오늘이 든 달 기준 기록으로 짐작하고(같은 날 본 모든 달 화면이 같은 일정) 다음 달에 밀려 낸
 * 고른 달 몫도 센다.
 * 등록창에서 저장하거나 거래를 고치면 조회가 다시 내보내서 '아직 안 냈어요' 가 바로 '냈어요' 로 옮겨 간다. 목록을 바꿔도 그렇다.
 * 달을 바꾸면 새 계산이 끝날 때까지 이전 달 화면을 그대로 둔다(빈 화면이 깜빡이지 않게).
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 * @param scope 화면 모델이 도는 범위([viewModelScope], 화면 모델이 사라지면 끝난다). 기본은 메인 스레드이고, 메인 스레드가 없는
 *   JVM 테스트에서 기본 풀로 돌리려고 바꿀 수 있게 둔다.
 */
class FixedExpenseViewModel(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val fixedMerchants: FixedMerchantRepository,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    /**
     * 사용자가 고른 지난 달. null 이면 '이번 달' 을 따라간다(통계와 같은 규칙).
     * 그래서 이번 달을 보던 중에 자정이 지나 달이 바뀌면 새 달로 넘어가고, 일부러 지난 달을 보고 있으면 그대로 둔다.
     */
    private val pickedMonth = MutableStateFlow<YearMonth?>(null)

    /** 어느 탭인지. 목록 저장소의 종류를 따라가서, 읽는 분류 · 목록 · 정리가 서로 어긋나지 않는다. */
    private val kind: FixedKind = fixedMerchants.kind

    /**
     * 그 분류('고정지출' · '저축')가 있는지. 이름·순서를 바꿔도 다시 계산하지 않게 있고 없음만 따라간다.
     * 고른 달과 상관없어서, 달을 넘겨도 다시 읽지 않게 화면 모델이 살아 있는 동안 나눠 쓴다.
     */
    private val hasCategory: Flow<Boolean> =
        categories
            .observe(CategoryScope.EXPENSE)
            .map { list -> list.any { it.code == kind.categoryCode } }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /**
     * 그 분류 지출이 하나도 남지 않은 곳을 목록에서 지운다(제 종류 목록만). 목록은 이 탭에서만 보여서 앱을 켤 때가 아니라 탭을 열 때 한 번 한다.
     * 목록은 정리가 끝난 뒤에 읽는다. 첫 판이 정리된 목록을 보고, 정리 때문에 판을 한 번 더 계산하지 않는다.
     */
    private val pruned = viewModelScope.async { fixedMerchants.prune() }

    /** 고정지출(고정저축) 목록. [hasCategory] 처럼 고른 달과 상관없어 나눠 쓴다. '안 함' 은 마지막으로 읽은 목록으로 해지한 곳인지 본다. */
    private val registry: SharedFlow<Map<String, FixedMerchantEntity>> =
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
                        kind.categoryCode,
                        fixedHistoryStart(month),
                        fixedHistoryEnd(month, today),
                    ),
                    registry,
                ) { has, fixedRows, registry ->
                    val rows = fixedRows.map { it.item }
                    val created = fixedRows.associate { it.item.id to it.createdAt }
                    FixedExpenseUiState.of(kind, month, today, has, splitByRegistry(rows, registry, created, today))
                }
                    // 계산만 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
                    .flowOn(Dispatchers.Default)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = FixedExpenseUiState.loading(kind, BudgetTime.toLocalDate(clock.instant())),
            )

    fun showPreviousMonth() = showMonth(currentMonth().minusMonths(1))

    /** 이번 달에서는 더 넘기지 않는다(다음 달 버튼도 막혀 있다) */
    fun showNextMonth() = showMonth(currentMonth().plusMonths(1))

    /** 달 줄 오른쪽 '이번 달' 버튼. 다시 '이번 달 따라가기' 로 둔다. */
    fun showThisMonth() {
        pickedMonth.value = null
    }

    /** '등록' 을 누른 곳([key])을 목록에 넣는다. 묻던 줄이 사라지고 판에 나타난다. 해지한 곳에 다시 물은 것이면 해지가 풀린다. */
    fun register(key: String) {
        viewModelScope.launch { fixedMerchants.register(key, clock.instant()) }
    }

    /**
     * 묻는 줄에서 '안 함' 을 누른 곳([key]). 지난 거래의 분류는 그대로이고, 지금 뒤에 새로 낸 그 분류 지출이 생기면 다시 묻는다.
     * 해지한 곳에 다시 물은 것이면 해지한 채로 둔다(해지 전 결제는 끝 달까지 판에 그대로다). 등록 안 한 곳으로 두면 해지한 때가 지워져
     * 지난 달 판에서도 통째로 빠진다. 해지한 곳인지는 판을 그린 목록으로 본다(그사이 해지가 풀렸으면 저장소가 등록 안 한 곳으로 둔다).
     */
    fun decline(key: String) {
        val ended = registry.replayCache.firstOrNull()?.get(key)?.state == FixedMerchantState.ENDED
        viewModelScope.launch {
            val at = clock.instant()
            if (ended) fixedMerchants.keepEnded(key, at) else fixedMerchants.decline(key, at)
        }
    }

    /** 판의 줄을 길게 눌러 뺀 곳([key]). 해지한 곳이어도 등록 안 한 곳이 되어 어느 달 판에도 안 보인다. 다시 내면 묻는다. */
    fun remove(key: String) {
        viewModelScope.launch { fixedMerchants.decline(key, clock.instant()) }
    }

    /** '해지' 를 누른 곳([key]). 해지 전에 낸 마지막 몫의 달까지는 판에 그대로 보이고, 그다음 달부터는 '해지한 곳' 에만 있다. */
    fun end(key: String) {
        viewModelScope.launch { fixedMerchants.end(key, clock.instant()) }
    }

    /** '해지한 곳' 에서 '되돌리기' 를 누른 곳([key]). 다시 등록한 곳이 되어(해지한 때를 지운다) 해지한 뒤에 낸 것까지 판에 센다. */
    fun restore(key: String) {
        viewModelScope.launch { fixedMerchants.register(key, clock.instant()) }
    }

    private fun currentMonth(): YearMonth = pickedMonth.value ?: YearMonth.now(clock)

    private fun showMonth(month: YearMonth) {
        pickedMonth.value = month.takeIf { it < YearMonth.now(clock) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
