package com.dong.budget.ui.card

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.card.MAX_PERFORMANCE_TIERS
import com.dong.budget.data.card.normalizePerformanceStartDay
import com.dong.budget.data.card.normalizePerformanceTiers
import com.dong.budget.data.card.performanceTierList
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
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
 * 카드 실적 정하기 화면의 값.
 * @property rows 화면의 구간 줄 금액. 고치는 동안은 적은 순서 그대로 두고(줄이 갑자기 자리를 바꾸지 않게) 저장할 때만 정리한다.
 *   다음에 열면 작은 금액부터 보인다. 0 은 아직 금액을 안 적은 줄이라 저장하지 않는다.
 * @property startDay 실적 시작일(매달 1~31일)
 * @property startWithKeypad 실적이 없는 카드로 처음 열었는지. 빈 1구간 줄의 키패드를 연 채 시작한다.
 * @property today 시작일 아래 예시 기간을 셀 날(화면을 연 날)
 */
@Immutable
data class CardPerformanceEditState(
    val card: CardInfo,
    val rows: List<Long>,
    val startDay: Int,
    val startWithKeypad: Boolean,
    val today: LocalDate,
) {
    /** 저장할 구간(0원 빼고, 같은 금액은 하나로, 오름차순) */
    val tiers: List<Long> get() = normalizePerformanceTiers(rows)

    /** 지울 실적이 있는지(구간을 적었거나 시작일을 바꿨다). 없으면 '실적 지우기' 를 두지 않는다. */
    val hasPerformance: Boolean get() = tiers.isNotEmpty() || startDay != 1

    /** 구간 줄을 더 둘 수 있는지 */
    val canAddRow: Boolean get() = rows.size < MAX_PERFORMANCE_TIERS
}

/**
 * 카드 실적(구간 금액·시작일) 정하기. 바꾸는 대로 바로 저장한다(따로 저장 버튼이 없다, 월급 설정과 같다).
 * 화면이 그리는 값은 여기 있는 것이 기준이다. 저장소에서 다시 읽은 값을 기다리면 키패드를 빨리 누를 때 숫자가 빠진다.
 * 저장은 누른 차례대로 하고, 화면을 떠나도 끝까지 한다(마지막으로 누른 숫자가 빠지지 않게).
 *
 * @param clock 지금 시각. 시작일 예시 기간을 세는 데 쓴다.
 */
class CardPerformanceEditViewModel(
    private val repository: PaymentMethodRepository,
    private val paymentMethodId: Long,
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

    init {
        viewModelScope.launch {
            val card = repository.observe(paymentMethodId).first()
            if (card == null) {
                _closed.value = true
                return@launch
            }
            val tiers = card.performanceTierList
            val saved = SavedPerformance(tiers, card.performanceStartDay)
            opened = saved
            lastSaved = saved
            _state.value =
                CardPerformanceEditState(
                    card = card.toCardInfo(),
                    // 실적이 없으면 빈 1구간 줄 하나를 두고 시작한다
                    rows = tiers.ifEmpty { listOf(0L) },
                    startDay = card.performanceStartDay,
                    startWithKeypad = tiers.isEmpty(),
                    today = BudgetTime.toLocalDate(clock.instant()),
                )
            // 고치는 동안 결제수단이 지워지면(드물다) 닫는다
            repository.observe(paymentMethodId).first { it == null }
            _closed.value = true
        }
    }

    /** 구간 줄 [index] 의 금액 키패드. 앞의 0 은 떼고, 12자리를 넘으면 받지 않는다. */
    fun appendDigit(index: Int, digit: String) = editRow(index) { typeDigit(it, digit) }

    fun deleteDigit(index: Int) = editRow(index) { it / DECIMAL }

    fun clearAmount(index: Int) = editRow(index) { 0 }

    private inline fun editRow(index: Int, change: (Long) -> Long?) {
        val current = _state.value ?: return
        val now = current.rows.getOrNull(index) ?: return
        val next = change(now) ?: return
        if (next == now) return
        update(current.copy(rows = current.rows.toMutableList().also { it[index] = next }))
    }

    /**
     * 빈 구간 줄을 맨 아래에 하나 더한다. 금액을 적기 전이라 저장할 것은 없다.
     * @return 더한 줄의 번호(화면이 그 줄의 키패드를 연다). 더 둘 수 없으면 null
     */
    fun addRow(): Int? {
        val current = _state.value ?: return null
        if (cleared || !current.canAddRow) return null
        _state.value = current.copy(rows = current.rows + 0L)
        return current.rows.size
    }

    /**
     * 구간 줄 [index] 를 지운다. 줄을 다 지우면 실적을 안 적은 카드가 된다.
     * @return 지웠는지. 없는 줄이거나 실적을 지우고 닫는 중이면 false
     */
    fun removeRow(index: Int): Boolean {
        val current = _state.value ?: return false
        if (cleared || index !in current.rows.indices) return false
        update(current.copy(rows = current.rows.filterIndexed { i, _ -> i != index }))
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
        _state.value = next
        save(SavedPerformance(next.tiers, next.startDay))
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
        const val DECIMAL = 10L

        /** 지운 실적. 구간이 없고 1일부터 센다(새 결제수단과 같다). */
        val CLEARED = SavedPerformance(emptyList(), 1)
    }
}

/** 저장된(저장할) 실적. 구간은 정리한 것이다. */
internal data class SavedPerformance(val tiers: List<Long>, val startDay: Int)

/**
 * 금액 키패드 한 번 누름. 앞의 0 은 떼고, 12자리를 넘으면 받지 않는다(null). 월급 설정과 같은 규칙이다.
 * @param digit 키패드가 주는 글자("0"~"9", "00")
 */
internal fun typeDigit(amount: Long, digit: String): Long? {
    val digits = (amount.takeIf { it > 0 }?.toString().orEmpty() + digit).trimStart('0')
    if (digits.length > MAX_AMOUNT_DIGITS) return null
    return digits.toLongOrNull() ?: 0
}

/** 시작일 입력판. 구간 줄 입력판은 줄 번호(0 부터)다. 저장 상태에 Int 하나로 들어간다. */
internal const val START_DAY_PANEL = -1

/** 구간 줄 [removed] 를 지운 뒤 열려 있을 입력판. 지운 줄의 키패드면 닫고, 아래 줄이면 한 칸 당긴다. 시작일 판은 그대로다. */
internal fun panelAfterRemoval(panel: Int?, removed: Int): Int? = when {
    panel == null || panel == START_DAY_PANEL -> panel
    panel == removed -> null
    panel > removed -> panel - 1
    else -> panel
}

/**
 * 구간 줄 [keypad] 의 키패드 누름을 받을지. 누르는 순간 열린 입력판([open])이 그 줄일 때만 받는다.
 * 닫히며 내려가는 중(null)이거나 다른 줄로 바뀌며 사라지는 키패드는 옛 줄 번호를 들고 있어서, 받으면 엉뚱한 줄 금액이 바뀐다.
 */
internal fun keypadAccepts(open: Int?, keypad: Int): Boolean = open == keypad

/**
 * 실적을 바꿨을 때 남길 로그. 카드 이름과 금액은 적지 않고 구간 개수와 시작일만 적는다. 그대로면 null
 * "카드 실적을 바꿨어요 · 구간 2개 · 매달 15일부터" / "카드 실적을 지웠어요"
 */
internal fun performanceChangeLog(opened: SavedPerformance, now: SavedPerformance): String? = when {
    now == opened -> null
    now.tiers.isEmpty() && now.startDay == 1 -> "카드 실적을 지웠어요"
    else -> "카드 실적을 바꿨어요 · 구간 ${now.tiers.size}개 · ${startDayValue(now.startDay)}"
}
