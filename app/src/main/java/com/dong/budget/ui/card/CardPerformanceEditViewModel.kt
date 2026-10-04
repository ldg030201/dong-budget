package com.dong.budget.ui.card

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.card.MAX_PERFORMANCE_TIERS
import com.dong.budget.data.card.normalizePerformanceStartDay
import com.dong.budget.data.card.normalizePerformanceTiers
import com.dong.budget.data.card.performanceTierList
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.ui.components.appendAmountDigit
import com.dong.budget.ui.components.deleteAmountDigit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * 편집 화면의 구간 줄 하나.
 * @property id 줄을 더할 때 정해져 줄을 지우거나 앞 줄이 없어져도 바뀌지 않는다. 열린 키패드가 이것으로 줄을 가리킨다.
 * @property amount 금액. 0 은 아직 금액을 안 적은 줄이라 저장하지 않는다.
 */
data class TierRow(val id: Int, val amount: Long)

/**
 * 카드 실적 정하기 화면의 값.
 * @property rows 화면의 구간 줄. 고치는 동안은 적은 순서 그대로 두고(줄이 갑자기 자리를 바꾸지 않게) 저장할 때만 정리한다.
 *   다음에 열면 작은 금액부터 보인다.
 * @property startDay 실적 시작일(매달 1~31일)
 * @property startKeypadRow 처음 열 때 키패드를 열 줄. 실적이 없는 카드로 처음 열면 빈 1구간 줄이고, 아니면 null 이다.
 * @property today 시작일 아래 예시 기간을 셀 날(화면을 연 날)
 */
@Immutable
data class CardPerformanceEditState(
    val card: CardInfo,
    val rows: List<TierRow>,
    val startDay: Int,
    val startKeypadRow: Int?,
    val today: LocalDate,
) {
    /** 줄 금액(적은 순서) */
    val amounts: List<Long> get() = rows.map { it.amount }

    /** 저장할 구간(0원 빼고, 같은 금액은 하나로, 오름차순) */
    val tiers: List<Long> get() = normalizePerformanceTiers(amounts)

    /** 지울 실적이 있는지(구간을 적었거나 시작일을 바꿨다). 없으면 '실적 지우기' 를 두지 않는다. */
    val hasPerformance: Boolean get() = tiers.isNotEmpty() || startDay != 1

    /** 구간 줄을 더 둘 수 있는지 */
    val canAddRow: Boolean get() = rows.size < MAX_PERFORMANCE_TIERS
}

/**
 * 카드 실적(구간 금액·시작일) 정하기. 바꾸는 대로 바로 저장한다(따로 저장 버튼이 없다, 월급 설정과 같다).
 * 화면이 그리는 값은 여기 있는 것이 기준이다. 저장소에서 다시 읽은 값을 기다리면 키패드를 빨리 누를 때 숫자가 빠진다.
 * 저장은 누른 차례대로 하고, 화면을 떠나도 끝까지 한다(마지막으로 누른 숫자가 빠지지 않게).
 * 고치는 값(줄 순서·빈 줄·줄 id·시작일)은 [savedState] 에도 둔다. 프로세스가 죽었다 돌아와도 DB 의 정리된 값 대신 적던 그대로 잇는다.
 *
 * @param clock 지금 시각. 시작일 예시 기간을 세는 데 쓴다.
 */
class CardPerformanceEditViewModel(
    private val repository: PaymentMethodRepository,
    private val paymentMethodId: Long,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    /** 지금 값. 결제수단을 다 읽기 전에는 null 이다. */
    private val _state = MutableStateFlow<CardPerformanceEditState?>(null)
    val state: StateFlow<CardPerformanceEditState?> = _state.asStateFlow()

    /** 화면을 닫을지. 실적을 지웠거나 결제수단이 없어졌다. */
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    /** 화면을 열 때의 실적. 닫을 때 바뀌었으면 로그에 한 번 남긴다. */
    private var opened: SavedPerformance? = null

    /** 마지막으로 저장하라고 한 값. 같은 값(같은 금액 줄을 하나 더 적는 등)은 다시 저장하지 않는다. */
    private var lastSaved: SavedPerformance? = null

    /** 실적을 지우고 닫는 중인지. 그 뒤에 들어온 누름은 저장하지 않는다(지운 실적이 되살아나지 않게). */
    private var cleared = false

    /** 저장을 누른 차례대로 하게 한다(Mutex 는 먼저 온 쪽부터 잠금을 준다) */
    private val writes = Mutex()

    /** 다음에 더할 줄의 id. 지운 줄의 id 를 다시 쓰지 않게 늘기만 한다. */
    private var nextRowId = 0

    init {
        viewModelScope.launch {
            val card = repository.observe(paymentMethodId).first()
            if (card == null) {
                _closed.value = true
                return@launch
            }
            val saved = SavedPerformance(card.performanceTierList, card.performanceStartDay)
            lastSaved = saved
            val today = BudgetTime.toLocalDate(clock.instant())
            val buffer = savedState.readEditBuffer()
            if (buffer == null) {
                opened = saved
                // 실적이 없으면 빈 1구간 줄 하나를 두고 그 키패드를 연 채 시작한다
                val rows = saved.tiers.ifEmpty { listOf(0L) }.mapIndexed { id, amount -> TierRow(id, amount) }
                nextRowId = rows.size
                val startKeypadRow = rows.first().id.takeIf { saved.tiers.isEmpty() }
                show(CardPerformanceEditState(card.toCardInfo(), rows, saved.startDay, startKeypadRow, today))
            } else {
                // 프로세스가 죽었다 돌아왔다. 적던 줄을 그대로 잇고, 마지막 누름이 저장되기 전에 죽었으면 여기서 마저 저장한다.
                opened = buffer.opened
                nextRowId = buffer.nextRowId
                update(CardPerformanceEditState(card.toCardInfo(), buffer.rows, buffer.startDay, startKeypadRow = null, today))
            }
            // 고치는 동안 결제수단이 지워지면(드물다) 닫는다
            repository.observe(paymentMethodId).first { it == null }
            _closed.value = true
        }
    }

    /** 구간 줄 [rowId] 의 금액 키패드. 앞의 0 은 떼고, 12자리를 넘으면 받지 않는다. 지운 줄이면 아무것도 하지 않는다. */
    fun appendDigit(rowId: Int, digit: String) = editRow(rowId) { appendAmountDigit(it, digit) }

    fun deleteDigit(rowId: Int) = editRow(rowId) { deleteAmountDigit(it) }

    fun clearAmount(rowId: Int) = editRow(rowId) { 0 }

    private inline fun editRow(rowId: Int, change: (Long) -> Long?) {
        val current = _state.value ?: return
        update(current.changeRow(rowId, change) ?: return)
    }

    /**
     * 빈 구간 줄을 맨 아래에 하나 더한다. 금액을 적기 전이라 저장할 것은 없다.
     * @return 더한 줄의 id(화면이 그 줄의 키패드를 연다). 더 둘 수 없으면 null
     */
    fun addRow(): Int? {
        val current = _state.value ?: return null
        if (cleared || !current.canAddRow) return null
        val row = TierRow(nextRowId++, 0L)
        show(current.copy(rows = current.rows + row))
        return row.id
    }

    /**
     * 구간 줄 [rowId] 를 지운다. 줄을 다 지우면 실적을 안 적은 카드가 된다.
     * @return 지웠는지. 없는 줄이거나 실적을 지우고 닫는 중이면 false
     */
    fun removeRow(rowId: Int): Boolean {
        val current = _state.value ?: return false
        if (cleared || current.rows.none { it.id == rowId }) return false
        update(current.copy(rows = current.rows.filter { it.id != rowId }))
        return true
    }

    fun setStartDay(day: Int) {
        val current = _state.value ?: return
        update(current.copy(startDay = normalizePerformanceStartDay(day)))
    }

    /**
     * 실적을 지운다(구간을 비우고 시작일을 1일로). 저장을 맡겨 두고 바로 화면을 닫는다.
     * 화면의 값은 그대로 둔다. 닫히며 미끄러져 나가는 동안 줄이 한꺼번에 사라져 판이 출렁이지 않게 한다.
     */
    fun clear() {
        if (_state.value == null || cleared) return
        cleared = true
        save(CLEARED)
        _closed.value = true
    }

    private fun update(next: CardPerformanceEditState) {
        if (cleared) return
        show(next)
        save(SavedPerformance(next.tiers, next.startDay))
    }

    /** 화면 값을 바꾸고 저장 상태에도 둔다(프로세스가 죽었다 돌아와도 잇게) */
    private fun show(next: CardPerformanceEditState) {
        _state.value = next
        val first = opened ?: return
        savedState.writeEditBuffer(EditBuffer(next.rows, next.startDay, nextRowId, first))
    }

    private fun save(saving: SavedPerformance) {
        if (saving == lastSaved) return
        lastSaved = saving
        viewModelScope.launch {
            withContext(NonCancellable) {
                writes.withLock { repository.setPerformance(paymentMethodId, saving.tiers, saving.startDay) }
            }
        }
    }

    override fun onCleared() {
        val first = opened ?: return
        val last = if (cleared) CLEARED else _state.value?.let { SavedPerformance(it.tiers, it.startDay) } ?: return
        performanceChangeLog(first, last)?.let { DevLog.info(LogTag.SETTINGS, it) }
    }

    private companion object {
        /** 지운 실적. 구간이 없고 1일부터 센다(새 결제수단과 같다). */
        val CLEARED = SavedPerformance(emptyList(), 1)
    }
}

/** 저장된(저장할) 실적. 구간은 정리한 것이다. */
internal data class SavedPerformance(val tiers: List<Long>, val startDay: Int)

/** 시작일 입력판. 구간 줄 입력판은 줄 id(0 부터)다. 저장 상태에 Int 하나로 들어간다. */
internal const val START_DAY_PANEL = -1

/** 줄 [rowId] 의 금액을 [change] 로 바꾼 값. 없는 줄(지운 줄)이거나 바뀌지 않으면 null 이다. */
internal inline fun CardPerformanceEditState.changeRow(rowId: Int, change: (Long) -> Long?): CardPerformanceEditState? {
    val index = rows.indexOfFirst { it.id == rowId }
    if (index < 0) return null
    val now = rows[index].amount
    val next = change(now)?.takeIf { it != now } ?: return null
    return copy(rows = rows.toMutableList().also { it[index] = TierRow(rowId, next) })
}

/**
 * 처음 연 편집 화면에 열어 둘 입력판. 입력을 받을 수 있을 때([awaitReady], 화면이 다 밀려 들어와 RESUMED)까지 기다린 뒤,
 * 실적이 없는 카드면 빈 1구간 키패드([keypadRow])를 연다. 밀려 들어오는 동안은 키패드 누름을 받지 않아서, 먼저 열면 그동안 친 숫자가
 * 말없이 버려지고 남은 숫자로 저장된다(1,500,000 을 빨리 치면 500,000). 기다리는 동안 사용자가 연 입력판이 있으면 그대로 둔다.
 * @param panel 지금 입력판. 기다린 뒤의 값을 읽도록 함수로 받는다.
 */
internal suspend fun startPanel(panel: () -> Int?, keypadRow: Int?, awaitReady: suspend () -> Unit): Int? {
    awaitReady()
    return panel() ?: keypadRow
}

/**
 * 프로세스가 죽었다 돌아와도 이을 편집 값. 화면 값([CardPerformanceEditState])과 함께 저장 상태에 둔다.
 * @property nextRowId 다음에 더할 줄의 id. 되살아난 뒤 더한 줄이 남아 있던 줄 id 와 겹치지 않게 함께 둔다.
 * @property opened 화면을 처음 열 때의 실적(닫을 때 남길 로그의 비교 기준)
 */
internal data class EditBuffer(val rows: List<TierRow>, val startDay: Int, val nextRowId: Int, val opened: SavedPerformance)

internal fun SavedStateHandle.writeEditBuffer(buffer: EditBuffer) {
    this[KEY_ROW_IDS] = buffer.rows.map { it.id }.toIntArray()
    this[KEY_ROW_AMOUNTS] = buffer.rows.map { it.amount }.toLongArray()
    this[KEY_START_DAY] = buffer.startDay
    this[KEY_NEXT_ROW_ID] = buffer.nextRowId
    this[KEY_OPENED_TIERS] = buffer.opened.tiers.toLongArray()
    this[KEY_OPENED_START_DAY] = buffer.opened.startDay
}

/** 저장 상태의 편집 값. 처음 연 화면이면(또는 값이 온전하지 않으면) null 이다. */
internal fun SavedStateHandle.readEditBuffer(): EditBuffer? {
    val ids = get<IntArray>(KEY_ROW_IDS) ?: return null
    val amounts = get<LongArray>(KEY_ROW_AMOUNTS)?.takeIf { it.size == ids.size } ?: return null
    return EditBuffer(
        rows = ids.zip(amounts.toList()) { id, amount -> TierRow(id, amount) },
        startDay = get<Int>(KEY_START_DAY) ?: return null,
        nextRowId = get<Int>(KEY_NEXT_ROW_ID) ?: return null,
        opened = SavedPerformance(get<LongArray>(KEY_OPENED_TIERS)?.toList() ?: return null, get<Int>(KEY_OPENED_START_DAY) ?: return null),
    )
}

private const val KEY_ROW_IDS = "rowIds"
private const val KEY_ROW_AMOUNTS = "rowAmounts"
private const val KEY_START_DAY = "startDay"
private const val KEY_NEXT_ROW_ID = "nextRowId"
private const val KEY_OPENED_TIERS = "openedTiers"
private const val KEY_OPENED_START_DAY = "openedStartDay"

/**
 * 입력판 [shown](구간 줄 키패드나 시작일 날짜판)의 누름을 받을지. 누르는 순간 열린 입력판([open])이 그것일 때만 받는다.
 * 닫히며 내려가는 중(null)이거나 다른 입력판으로 바뀌며 사라지는 판도 전환 동안 누름을 받아서, 받으면 닫은 판의 값이 바뀌어 저장된다
 * (사라지는 날짜판의 칸이 키패드 틈으로 눌려 시작일이 바뀌는 등).
 */
internal fun panelAccepts(open: Int?, shown: Int): Boolean = open == shown

/**
 * 실적을 바꿨을 때 남길 로그. 카드 이름과 금액은 적지 않고 구간 개수와 시작일만 적는다. 그대로면 null
 * "카드 실적을 바꿨어요 · 구간 2개 · 매달 15일부터" / "카드 실적을 지웠어요"
 */
internal fun performanceChangeLog(opened: SavedPerformance, now: SavedPerformance): String? = when {
    now == opened -> null
    now.tiers.isEmpty() && now.startDay == 1 -> "카드 실적을 지웠어요"
    else -> "카드 실적을 바꿨어요 · 구간 ${now.tiers.size}개 · ${startDayValue(now.startDay)}"
}
