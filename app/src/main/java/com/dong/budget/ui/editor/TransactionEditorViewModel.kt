package com.dong.budget.ui.editor

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.AddResult
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.colors
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.PrefillSource
import com.dong.budget.ui.category.AddTarget
import com.dong.budget.ui.category.message
import com.dong.budget.ui.components.appendAmountDigit
import com.dong.budget.ui.components.deleteAmountDigit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * 저장하려면 반드시 채워야 하는 칸. 화면 위에서부터의 순서다.
 * 메모는 비워도 된다. 날짜와 시간은 처음부터 값이 있어서 비는 일이 없다.
 * 결제수단(PAYMENT)은 지출에서만 채워야 한다. 수입의 들어온 곳은 비워도 된다.
 */
enum class RequiredField { AMOUNT, CATEGORY, PAYMENT, MERCHANT }

/** 알림에서 읽은 결제가 이미 가계부에 있을 때의 안내. 등록창과 알림을 눌러 들어올 때가 같이 쓴다. */
const val ALREADY_REGISTERED_MESSAGE = "이미 가계부에 등록한 결제예요"

/** 알림에서 읽은 입금이 이미 가계부에 있을 때의 안내. 등록창과 알림을 눌러 들어올 때가 같이 쓴다. */
const val DEPOSIT_ALREADY_REGISTERED_MESSAGE = "이미 가계부에 등록한 입금이에요"

/** 그달 월급을 이미 등록했을 때의 안내. 등록창과 월급날 알림을 눌러 들어올 때가 같이 쓴다. */
const val SALARY_ALREADY_REGISTERED_MESSAGE = "이번 달 월급은 이미 등록했어요"

/** 화면이 한 번만 처리할 일. 화면을 돌려 다시 그려져도 다시 일어나지 않는다. */
sealed interface EditorEffect {
    /** 저장을 눌렀는데 비어 있던 첫 칸으로 옮겨 간다 */
    data class JumpTo(val field: RequiredField) : EditorEffect

    /** 분류나 결제수단(들어온 곳)을 새로 만들어 골랐다. 입력판을 닫는다. */
    data object Added : EditorEffect
}

data class EditorUiState(
    val isEditing: Boolean = false,
    /**
     * 알림에서 읽은 카드(입금 알림이면 통장) 이름인데 같은 이름의 결제수단(들어온 곳)이 아직 없을 때 그 이름. 이것을 고른 채 저장하면 새로 만든다.
     * 다른 것을 골라도 남겨 둔다. 표의 맨 뒤에 보여서 다시 고를 수 있다([isPendingPaymentSelected]).
     */
    val pendingPaymentName: String? = null,
    /** [pendingPaymentName] 을 만들 목록. 결제 알림이면 결제수단(지출), 입금 알림·월급날이면 들어온 곳(수입)이다. 다른 종류에서는 보이지 않는다. */
    val pendingPaymentScope: CategoryScope = CategoryScope.EXPENSE,
    /**
     * 수입에서 들어온 곳을 비워 두기로 했다(고른 것을 다시 눌러 풀었다). 새로 만들 들어온 곳([pendingPaymentName])이 있어도 고르지 않은 것으로 본다.
     * 다른 것을 고르거나 지출·수입을 바꾸면 풀린다.
     */
    val paymentCleared: Boolean = false,
    /** 알림에서 읽은 결제의 열쇠. 같은 결제를 두 번 등록하지 않게 거래에 함께 저장한다. 직접 입력하거나 고정지출로 채웠으면 null */
    val dedupKey: String? = null,
    /** 채워 연 곳(결제·입금 알림·월급날·고정지출). 직접 입력하면 null */
    val prefillSource: PrefillSource? = null,
    /** 저장이 거절된 이유(이미 등록한 결제 등). 없으면 null */
    val saveError: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    /** 숫자만 담는다. 표시할 때 세 자리마다 끊는다. */
    val amountDigits: String = "",
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    val merchant: String = "",
    val memo: String = "",
    val occurredAt: Instant = Instant.now(),
    val categories: List<CategoryEntity> = emptyList(),
    /** 결제수단과 들어온 곳 모두. 고르는 표는 거래 종류에 맞는 목록([paymentMethods])만 본다. */
    val allPaymentMethods: List<PaymentMethodEntity> = emptyList(),
    /**
     * 고쳐 여는 거래가 처음 가리키던 결제수단과 그 거래의 종류. 1.8.1 까지 수입에 고른 카드처럼 그 종류의 목록에 없는 것도
     * 그 종류에서는 표에 함께 두어 골라진 채 보이게 한다(DB 6 에서 들어온 곳으로 옮기지 못한 옛 수입). 새로 등록하면 null
     */
    val loadedPaymentMethodId: Long? = null,
    val loadedType: TransactionType? = null,
    /** 열려 있는 추가 시트. 없으면 null */
    val addTarget: AddTarget? = null,
    val addError: String? = null,
    /** 저장을 눌렀을 때 비어 있던 첫 칸. 그 칸 밑에 채우라는 안내를 보여준다. */
    val invalidField: RequiredField? = null,
    /** 새로 등록할 때 금액 키패드를 열어 둔 채 시작할지('금액 키패드 바로 열기'). 열 때의 스위치 값이다. */
    val keypadOnStart: Boolean = true,
    /** 새로 만드는 분류·결제수단(들어온 곳)의 색을 아직 안 쓴 색으로 고를지('새 분류·결제수단은 안 쓴 색으로'). 아니면 회색이다. */
    val pickUnusedColor: Boolean = true,
    /** 저장이나 삭제가 끝났다. 화면을 닫는다. */
    val saved: Boolean = false,
    /** 끝난 것이 삭제였는지. 아래에 깔린 그 거래의 상세도 같이 치운다. */
    val deleted: Boolean = false,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    /** 결제·입금 알림·월급날·고정지출에서 채워 연 등록창인지. 금액이 채워져 있어 키패드를 열지 않고 시작한다. 열쇠가 없는 고정지출도 들어간다. */
    val isPrefilled: Boolean get() = prefillSource != null

    /** 수입이면 결제수단 칸이 '들어온 곳' 이 된다. 들어온 곳 목록에서 고르고, 비워 둬도 된다. */
    val isIncome: Boolean get() = type == TransactionType.INCOME

    /** 지금 종류의 결제수단 목록. 지출이면 결제수단, 수입이면 들어온 곳이다. */
    private val paymentScope: CategoryScope get() = type.categoryScope()

    /**
     * 아직 비어 있는 필수 칸. 화면에 보이는 것과 같은 기준으로 본다.
     * 예를 들어 지운 결제수단이 걸린 옛 거래는 칸이 비어 보이므로 비어 있는 것으로 친다. 수입의 들어온 곳은 비워도 된다.
     */
    val missingFields: List<RequiredField>
        get() =
            buildList {
                if (amount <= 0) add(RequiredField.AMOUNT)
                if (selectedCategory == null) add(RequiredField.CATEGORY)
                if (!isIncome && selectedPaymentMethod == null && !isPendingPaymentSelected) add(RequiredField.PAYMENT)
                if (merchant.isBlank()) add(RequiredField.MERCHANT)
            }

    /** 이 칸 밑에 채우라는 안내를 보여줄지. 저장을 눌러 안내한 칸이 아직 비어 있을 때만 보인다. 채우면 바로 사라진다. */
    fun showsMissing(field: RequiredField): Boolean = invalidField == field && field in missingFields

    val selectedCategory: CategoryEntity? get() = categories.firstOrNull { it.id == categoryId }

    /**
     * 고르는 표에 둘 결제수단. 지금 종류의 목록(지출은 결제수단, 수입은 들어온 곳)에, 고쳐 여는 거래가 처음 가리키던 다른 목록의 것을
     * 그 거래의 종류일 때만 맨 뒤에 붙인다([loadedPaymentMethodId]).
     */
    val paymentMethods: List<PaymentMethodEntity>
        get() {
            val scope = paymentScope
            return allPaymentMethods.filter { it.scope == scope } +
                allPaymentMethods.filter { it.scope != scope && type == loadedType && it.id == loadedPaymentMethodId }
        }

    val selectedPaymentMethod: PaymentMethodEntity? get() = paymentMethods.firstOrNull { it.id == paymentMethodId }

    /** 새로 만들 결제수단(들어온 곳) 이름. 그 목록의 종류일 때만 있다. 지출·수입을 바꾸면 숨었다가 돌아오면 다시 보인다. */
    val visiblePendingPaymentName: String? get() = pendingPaymentName?.takeIf { pendingPaymentScope == paymentScope }

    /**
     * 새로 만들 결제수단([pendingPaymentName])을 고른 상태인지. 다른 것을 고르지 않았으면 이것이 골라져 있다.
     * 수입에서 고른 것을 다시 눌러 비웠으면([paymentCleared]) 고르지 않은 것이다.
     */
    val isPendingPaymentSelected: Boolean get() = visiblePendingPaymentName != null && paymentMethodId == null && !paymentCleared

    /** [pendingPaymentName] 으로 새로 만들 것의 아이콘. 결제수단은 카드, 들어온 곳은 통장이다. 저장할 때 만들어질 모양(findOrCreate)과 같다. */
    val pendingPaymentIcon: String get() = PaymentMethodRepository.newItemIcon(pendingPaymentScope)

    /** [pendingPaymentName] 으로 새로 만들 것의 색. 저장할 때 만들어질 색(findOrCreate, 그 목록에서 안 쓴 색)과 같다. */
    val pendingPaymentColor: String
        get() =
            if (pickUnusedColor) {
                CategoryStyle.firstUnusedColor(allPaymentMethods.filter { it.scope == pendingPaymentScope }.colors())
            } else {
                CategoryStyle.FALLBACK_COLOR
            }

    /** 결제수단 표의 + 가 여는 추가 시트. 수입이면 들어온 곳을 만든다. */
    val paymentAddTarget: AddTarget get() = if (isIncome) AddTarget.INCOME_SOURCE else AddTarget.PAYMENT

    /** 추가 시트가 기본 색을 겹치지 않게 고를 때 본다. 결제수단·들어온 곳은 그 목록의 색만 본다. */
    fun usedColors(target: AddTarget): Set<String> = when (target) {
        AddTarget.CATEGORY -> categories.colors()
        AddTarget.PAYMENT, AddTarget.INCOME_SOURCE -> allPaymentMethods.filter { it.scope == target.paymentScope }.colors()
    }
}

class TransactionEditorViewModel(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val transactionId: Long?,
    private val prefill: EditorPrefill? = null,
    /** 알림에서 읽은 결제(월급날이면 그달 월급)가 가계부에 들어갔다(방금 등록했거나 이미 있었다). 묻던 알림을 치운다. */
    private val onCaptureRegistered: (dedupKey: String) -> Unit = {},
    /**
     * 자동 기능 스위치(앱 전체가 따라가는 값). 키패드는 처음 그릴 때의 값만 쓴다. 색은 계속 따라간다.
     * 앱이 강제 종료됐다 등록창째 되살아나면 저장소를 다 읽기 전(모두 켜짐)에 만들어지므로, 값이 도착하면 색을 맞춘다.
     * 맞추지 않으면 '안 쓴 색' 을 꺼 둬도 새 카드가 색이 든 채 만들어진다.
     */
    private val autoSettings: StateFlow<AutoSettings> = MutableStateFlow(AutoSettings()),
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    init {
        observeCategories()
        viewModelScope.launch {
            autoSettings.map { it[AutoOption.NEW_ITEM_COLOR] }.distinctUntilChanged().collect { on ->
                _uiState.update { it.copy(pickUnusedColor = on) }
            }
        }
        viewModelScope.launch {
            // 두 목록을 함께 따라간다. 표는 거래 종류에 맞는 목록만 보여 주고(EditorUiState.paymentMethods),
            // 고쳐 여는 옛 수입이 가리키는 결제수단도 찾을 수 있게 한다.
            // '없는 카드·통장은 새로 추가하기' 를 껐으면 새로 만들 것을 두지 않는다. 사용자가 고른다.
            val keepPending = prefill?.addMissingCard != false
            paymentMethodRepository.observeAll().collect { methods ->
                _uiState.update { it.withPaymentMethods(methods, keepPending) }
            }
        }
        if (transactionId != null) loadExisting(transactionId)
        // '같은 가게면 지난 분류 고르기' 를 껐으면 분류는 사용자가 고른다
        if (transactionId == null && prefill != null && prefill.guessCategory) guessCategory(prefill.merchant, _uiState.value.type)
    }

    /** 새 등록이면서 알림에서 읽은 값이 있으면 그 값으로 채워 시작한다 */
    private fun initialState(): EditorUiState {
        val auto = autoSettings.value
        val base =
            EditorUiState(
                isEditing = transactionId != null,
                keypadOnStart = auto[AutoOption.EDITOR_KEYPAD],
                pickUnusedColor = auto[AutoOption.NEW_ITEM_COLOR],
            )
        val data = prefill?.takeIf { transactionId == null } ?: return base
        val type =
            when (data.source) {
                PrefillSource.PAYDAY, PrefillSource.DEPOSIT_ALERT -> TransactionType.INCOME
                PrefillSource.PAYMENT_ALERT, PrefillSource.FIXED_EXPENSE -> TransactionType.EXPENSE
            }
        return base.copy(
            type = type,
            prefillSource = data.source,
            amountDigits = data.amount.takeIf { it > 0 }?.toString()?.take(MAX_AMOUNT_DIGITS).orEmpty(),
            merchant = data.merchant,
            memo = data.memo.orEmpty(),
            occurredAt = Instant.ofEpochMilli(data.occurredAtMillis),
            // 저장할 때 만들 이름과 똑같이 다듬어 둔다. 그래야 이미 있는 결제수단과 맞춰 볼 수 있다.
            pendingPaymentName = data.paymentName?.let(PaymentMethodRepository::normalizeName),
            // 입금 알림의 통장과 월급날의 '통장' 은 들어온 곳에서 찾는다
            pendingPaymentScope = type.categoryScope(),
            // 번호로 고른 결제수단을 그사이 지웠으면 표에 없어 비어 보이고, 저장할 때 빈 칸으로 막혀 사용자가 고른다.
            paymentMethodId = data.paymentMethodId,
            dedupKey = data.dedupKey,
        )
    }

    /**
     * 전에 같은 가게(입금이면 보낸 사람)로 같은 종류(지출·수입)를 등록한 적이 있으면 그때의 분류를 미리 골라둔다.
     * 사용자가 이미 골랐거나 그사이 지출·수입을 바꿨으면 건드리지 않는다.
     */
    private fun guessCategory(merchant: String, type: TransactionType) {
        if (merchant.isBlank()) return
        viewModelScope.launch {
            val categoryId = repository.lastCategoryIdForMerchant(merchant, type) ?: return@launch
            _uiState.update { if (it.categoryId == null && it.type == type) it.copy(categoryId = categoryId) else it }
        }
    }

    /**
     * 지출/수입에 맞는 분류 목록을 따라간다.
     *
     * 종류가 바뀔 때마다 새로 구독하되 이전 구독은 끊는다(flatMapLatest).
     * 끊지 않으면 지출 구독이 살아남아서, 분류 표가 바뀌는 순간
     * 수입 화면에 지출 분류가 덮어써진다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCategories() {
        viewModelScope.launch {
            _uiState
                .map { it.type.categoryScope() }
                .distinctUntilChanged()
                .flatMapLatest { scope -> categoryRepository.observe(scope) }
                .collect { categories ->
                    _uiState.update { state ->
                        // 종류를 바꾸면 이전 분류는 더 이상 맞지 않는다.
                        val stillValid = categories.any { it.id == state.categoryId }
                        state.copy(
                            categories = categories,
                            categoryId = if (stillValid) state.categoryId else presetCategory(categories, state)?.id,
                        )
                    }
                }
        }
    }

    /**
     * 채워 연 등록창이 미리 고를 기본 분류(월급날이면 급여). 코드로 찾고, 사용자가 지웠으면 비워 둔다.
     * 지출로 바꿨다가 수입으로 돌아와도 다시 골라 둔다.
     */
    private fun presetCategory(categories: List<CategoryEntity>, state: EditorUiState): CategoryEntity? {
        val code = prefill?.takeIf { transactionId == null }?.categoryCode ?: return null
        if (prefill.source == PrefillSource.PAYDAY && state.type != TransactionType.INCOME) return null
        return categories.firstOrNull { it.code == code }
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val existing = repository.findById(id) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    type = existing.type,
                    amountDigits = existing.amount.toString(),
                    categoryId = existing.categoryId,
                    paymentMethodId = existing.paymentMethodId,
                    loadedPaymentMethodId = existing.paymentMethodId,
                    loadedType = existing.type,
                    merchant = existing.merchant.orEmpty(),
                    memo = existing.memo.orEmpty(),
                    occurredAt = existing.occurredAt,
                )
            }
        }
    }

    fun selectType(type: TransactionType) {
        _uiState.update { it.withType(type) }
    }

    // 숫자 규칙은 월급 설정·카드 실적 편집과 같은 것을 쓴다. 0 이면 빈 칸(안내 글)으로 둔다.
    fun appendDigit(digit: String) {
        _uiState.update { state ->
            val next = appendAmountDigit(state.amount, digit) ?: return@update state
            state.copy(amountDigits = amountText(next))
        }
    }

    fun deleteDigit() {
        _uiState.update { it.copy(amountDigits = amountText(deleteAmountDigit(it.amount))) }
    }

    fun clearAmount() {
        _uiState.update { it.copy(amountDigits = "") }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(categoryId = id) }
    }

    fun selectPaymentMethod(id: Long) {
        _uiState.update { it.withPaymentMethodTapped(id) }
    }

    /** 알림에서 읽은 이름으로 새로 만들 결제수단(들어온 곳)을 다시 고른다. 수입에서 이미 고른 채 다시 누르면 비운다. */
    fun selectPendingPaymentMethod() {
        _uiState.update { it.withPendingPaymentTapped() }
    }

    /**
     * 날짜만 바꾸고 시각은 그대로 둔다.
     * 시간대는 기기 설정이 아니라 서울로 고정한다(BudgetTime). 해외에서 고쳐도 날짜가 밀리지 않는다.
     */
    fun updateDate(date: LocalDate) {
        _uiState.update { state ->
            val time = BudgetTime.toLocalTime(state.occurredAt)
            state.copy(occurredAt = date.atTime(time).atZone(BudgetTime.ZONE).toInstant())
        }
    }

    /** 시각만 바꾸고 날짜는 그대로 둔다. 초는 0 으로 맞춘다. */
    fun updateTime(hour: Int, minute: Int) {
        _uiState.update { state ->
            val date = BudgetTime.toLocalDate(state.occurredAt)
            state.copy(occurredAt = date.atTime(hour, minute).atZone(BudgetTime.ZONE).toInstant())
        }
    }

    fun updateMerchant(value: String) {
        _uiState.update { it.copy(merchant = value) }
    }

    fun updateMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    fun openAdd(target: AddTarget) {
        _uiState.update { it.copy(addTarget = target, addError = null) }
    }

    fun dismissAdd() {
        _uiState.update { it.copy(addTarget = null, addError = null) }
    }

    /**
     * 등록 도중에 분류나 결제수단(수입이면 들어온 곳)을 새로 만든다. '새로 만든 분류·결제수단 바로 고르기' 가 켜져 있으면 그것을 고르고 표를 닫는다.
     * 꺼져 있으면 추가 창만 닫고 표는 열어 둔다.
     */
    fun submitAdd(name: String, icon: String, color: String) {
        val state = _uiState.value
        val target = state.addTarget ?: return
        viewModelScope.launch {
            val result =
                when (target) {
                    AddTarget.CATEGORY -> categoryRepository.add(state.type.categoryScope(), name, icon, color)
                    AddTarget.PAYMENT, AddTarget.INCOME_SOURCE -> paymentMethodRepository.add(target.paymentScope, name, icon, color)
                }
            if (result !is AddResult.Added) {
                _uiState.update { it.copy(addError = result.message()) }
                return@launch
            }
            // '새로 만든 분류·결제수단 바로 고르기' 를 껐으면 추가 창만 닫고 표는 열어 둔다. 새 칸이 보이니 직접 누른다.
            if (!autoSettings.value[AutoOption.EDITOR_SELECT_ADDED]) {
                _uiState.update { it.copy(addTarget = null, addError = null) }
                return@launch
            }
            _uiState.update {
                when (target) {
                    AddTarget.CATEGORY -> it.copy(categoryId = result.id, addTarget = null, addError = null)

                    AddTarget.PAYMENT, AddTarget.INCOME_SOURCE ->
                        it.copy(paymentMethodId = result.id, paymentCleared = false, addTarget = null, addError = null)
                }
            }
            _effects.send(EditorEffect.Added)
        }
    }

    /**
     * 저장이나 삭제가 진행 중이거나 이미 끝났는지. 버튼을 연달아 눌러도 한 번만 처리하기 위함이다.
     * 직접 입력한 거래는 막아 줄 고유값(dedupKey)이 없어서, 이게 없으면 두 번 누를 때 같은 거래가 두 건 생긴다.
     * 화면이 닫히는 동안에도 버튼이 눌리므로, 끝난 뒤에도 풀지 않는다(실패했을 때만 푼다).
     * save 와 delete 는 메인 스레드에서만 불리므로 따로 동기화하지 않는다.
     */
    private var busy = false

    /** 빈 필수 칸이 있으면 저장하지 않고, 그 첫 칸을 화면에 알린다. */
    fun save() {
        if (busy) return
        val state = _uiState.value
        val missing = state.missingFields.firstOrNull()
        if (missing != null) {
            _uiState.update { it.copy(invalidField = missing) }
            _effects.trySend(EditorEffect.JumpTo(missing))
            return
        }
        busy = true
        // 지난 거절 문구를 지운다. 같은 이유로 또 거절되면 문구가 새로 나타나 화면 읽기가 다시 읽어 준다.
        _uiState.update { it.copy(saveError = null) }
        viewModelScope.launch {
            if (transactionId == null) {
                // 월급날로 연 등록창을 지출로 바꿔 저장하면 월급이 아니다. 그달 월급을 등록한 것으로 치지 않게 열쇠를 떼어 낸다.
                val dedupKey = state.dedupKey.takeUnless {
                    state.prefillSource == PrefillSource.PAYDAY &&
                        state.type != TransactionType.INCOME
                }
                // 이미 등록한 결제면 카드를 만들기 전에 멈춘다
                if (dedupKey != null && repository.isRegistered(dedupKey)) {
                    rejectAlreadyRegistered(dedupKey)
                    return@launch
                }
                // 알림에서 읽은 카드(통장)가 아직 결제수단(들어온 곳)에 없으면 이때 만든다. 등록을 취소하면 만들지 않는다.
                val paymentMethodId =
                    state.paymentMethodId ?: state.visiblePendingPaymentName?.takeIf { state.isPendingPaymentSelected }?.let {
                        paymentMethodRepository.findOrCreate(state.pendingPaymentScope, it, pickUnusedColor = state.pickUnusedColor)
                    }
                try {
                    repository.add(
                        type = state.type,
                        amount = state.amount,
                        occurredAt = state.occurredAt,
                        categoryId = state.categoryId,
                        paymentMethodId = paymentMethodId,
                        merchant = state.merchant,
                        memo = state.memo,
                        dedupKey = dedupKey,
                    )
                } catch (e: SQLiteConstraintException) {
                    // 확인한 사이 같은 알림으로 이미 등록됐다(dedupKey 가 겹침)
                    DevLog.warn(LogTag.TRANSACTION, "같은 결제가 이미 등록돼 있어 저장하지 않았어요 ($dedupKey)", e)
                    rejectAlreadyRegistered(dedupKey ?: throw e)
                    return@launch
                }
                dedupKey?.let(onCaptureRegistered)
            } else {
                repository.update(
                    id = transactionId,
                    type = state.type,
                    amount = state.amount,
                    occurredAt = state.occurredAt,
                    categoryId = state.categoryId,
                    paymentMethodId = state.paymentMethodId,
                    merchant = state.merchant,
                    memo = state.memo,
                )
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    private fun rejectAlreadyRegistered(dedupKey: String) {
        onCaptureRegistered(dedupKey)
        val message =
            when (_uiState.value.prefillSource) {
                PrefillSource.PAYDAY -> SALARY_ALREADY_REGISTERED_MESSAGE
                PrefillSource.DEPOSIT_ALERT -> DEPOSIT_ALREADY_REGISTERED_MESSAGE
                else -> ALREADY_REGISTERED_MESSAGE
            }
        _uiState.update { it.copy(saveError = message) }
        busy = false
    }

    fun delete() {
        val id = transactionId ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { it.copy(saved = true, deleted = true) }
        }
    }
}

fun TransactionType.categoryScope(): CategoryScope = if (this == TransactionType.INCOME) CategoryScope.INCOME else CategoryScope.EXPENSE

// ── 결제수단 칸의 상태 바꾸기 ──
// 화면 모델은 아래를 부르기만 한다. 화면 모델 없이 규칙을 시험하려고 상태 위의 함수로 둔다.

/**
 * 결제수단·들어온 곳 목록이 새로 왔다. 알림에서 읽은 카드(통장) 이름과 같은 것이 그 목록에 있으면(띄어쓰기·대소문자 무시,
 * 예전에 잘려 만든 이름 포함) 새로 만들 것이 없다. 그 이름을 고른 상태였으면 있는 것을 고른다. 등록창에서 같은 이름을 직접 추가한 경우도 여기로 온다.
 * @param keepPending 같은 이름이 없을 때 새로 만들 것으로 남겨 둘지('없는 카드·통장은 새로 추가하기')
 */
internal fun EditorUiState.withPaymentMethods(methods: List<PaymentMethodEntity>, keepPending: Boolean): EditorUiState {
    val sameList = methods.filter { it.scope == pendingPaymentScope }
    val match = pendingPaymentName?.let { PaymentMethodRepository.findSameCard(sameList, it) }
    if (match == null) return copy(allPaymentMethods = methods, pendingPaymentName = pendingPaymentName.takeIf { keepPending })
    val pick = paymentMethodId == null && !paymentCleared && pendingPaymentScope == type.categoryScope()
    return copy(allPaymentMethods = methods, paymentMethodId = if (pick) match.id else paymentMethodId, pendingPaymentName = null)
}

/**
 * 지출·수입을 바꾼다. 고르는 목록도 바뀌어서(결제수단 ↔ 들어온 곳) 고른 것이 새 목록에 없으면 비운다(분류와 같다).
 * 목록을 아직 읽기 전이면 고른 것을 그대로 둔다. 수입에서 비워 두기로 한 것은 풀린다.
 */
internal fun EditorUiState.withType(type: TransactionType): EditorUiState {
    val next = copy(type = type, paymentCleared = false)
    val stale = next.allPaymentMethods.isNotEmpty() && next.paymentMethodId != null && next.selectedPaymentMethod == null
    return if (stale) next.copy(paymentMethodId = null) else next
}

/**
 * 표에서 결제수단(들어온 곳) [id] 를 눌렀다. 들어온 곳(수입)은 비워 둘 수 있어서 고른 것을 다시 누르면 선택을 푼다.
 * 결제수단(지출)은 필수라서 이미 고른 것을 다시 눌러도 풀지 않는다.
 * 알림에서 읽은 이름은 지우지 않는다. 마음이 바뀌면 표에서 다시 고를 수 있다.
 */
internal fun EditorUiState.withPaymentMethodTapped(id: Long): EditorUiState = if (isIncome && paymentMethodId == id) {
    copy(paymentMethodId = null, paymentCleared = true)
} else {
    copy(paymentMethodId = id, paymentCleared = false)
}

/** 표에서 새로 만들 결제수단(들어온 곳)을 눌렀다. 그것을 고른다. 수입에서 이미 고른 채 다시 누르면 비운다. */
internal fun EditorUiState.withPendingPaymentTapped(): EditorUiState = when {
    visiblePendingPaymentName == null -> this
    isIncome && isPendingPaymentSelected -> copy(paymentCleared = true)
    else -> copy(paymentMethodId = null, paymentCleared = false)
}

/** 키패드 금액 칸에 둘 숫자. 0 이면 아직 적지 않은 것이라 비운다. */
private fun amountText(amount: Long): String = amount.takeIf { it > 0 }?.toString().orEmpty()
