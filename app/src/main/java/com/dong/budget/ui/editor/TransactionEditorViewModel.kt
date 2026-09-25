package com.dong.budget.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.AddResult
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.category.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** 등록 화면에서 새로 만들 수 있는 것 */
enum class AddTarget { CATEGORY, PAYMENT }

/**
 * 저장하려면 반드시 채워야 하는 칸. 화면 위에서부터의 순서다.
 * 메모는 비워도 된다. 날짜와 시간은 처음부터 값이 있어서 비는 일이 없다.
 */
enum class RequiredField { AMOUNT, CATEGORY, PAYMENT, MERCHANT }

/** 금액 입력 자리수 상한. 원 단위라 12자리면 조 단위까지 들어간다. */
private const val MAX_AMOUNT_DIGITS = 12

data class EditorUiState(
    val isEditing: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    /** 숫자만 담는다. 표시할 때 세 자리마다 끊는다. */
    val amountDigits: String = "",
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    val merchant: String = "",
    val memo: String = "",
    val occurredAt: Instant = Instant.now(),
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    /** 열려 있는 추가 시트. 없으면 null */
    val addTarget: AddTarget? = null,
    val addError: String? = null,
    /** 방금 추가에 성공한 것. 화면이 이 값의 변화를 보고 입력판을 닫는다. */
    val lastAddedCategoryId: Long? = null,
    val lastAddedPaymentId: Long? = null,
    /** 저장을 눌렀을 때 비어 있던 첫 칸. 그 칸 밑에 채우라는 안내를 보여준다. */
    val invalidField: RequiredField? = null,
    /**
     * 화면이 옮겨 가야 할 빈 칸. 한 번 옮겨 가면 화면이 [TransactionEditorViewModel.onJumpHandled] 로 비운다.
     * [invalidField] 와 따로 두는 이유: 화면을 돌려 다시 그려질 때 이미 처리한 이동이 또 일어나면
     * 사용자가 열어둔 입력판이 닫히고 엉뚱한 칸에 키보드가 뜬다.
     */
    val pendingJump: RequiredField? = null,
    val saved: Boolean = false,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    /**
     * 아직 비어 있는 필수 칸. 화면에 보이는 것과 같은 기준으로 본다.
     * 예를 들어 지운 결제수단이 걸린 옛 거래는 칸이 비어 보이므로 비어 있는 것으로 친다.
     */
    val missingFields: List<RequiredField>
        get() =
            buildList {
                if (amount <= 0) add(RequiredField.AMOUNT)
                if (selectedCategory == null) add(RequiredField.CATEGORY)
                if (selectedPaymentMethod == null) add(RequiredField.PAYMENT)
                if (merchant.isBlank()) add(RequiredField.MERCHANT)
            }

    /** 이 칸 밑에 채우라는 안내를 보여줄지. 저장을 눌러 안내한 칸이 아직 비어 있을 때만 보인다. 채우면 바로 사라진다. */
    fun showsMissing(field: RequiredField): Boolean = invalidField == field && field in missingFields

    val selectedCategory: CategoryEntity? get() = categories.firstOrNull { it.id == categoryId }

    val selectedPaymentMethod: PaymentMethodEntity? get() = paymentMethods.firstOrNull { it.id == paymentMethodId }

    val usedCategoryColors: Set<String> get() = categories.mapTo(mutableSetOf()) { it.color }

    val usedPaymentColors: Set<String> get() = paymentMethods.mapTo(mutableSetOf()) { it.color }
}

class TransactionEditorViewModel(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val transactionId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState(isEditing = transactionId != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        observeCategories()
        viewModelScope.launch {
            repository.observePaymentMethods().collect { methods ->
                _uiState.update { it.copy(paymentMethods = methods) }
            }
        }
        if (transactionId != null) loadExisting(transactionId)
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
                .flatMapLatest { scope -> repository.observeCategories(scope) }
                .collect { categories ->
                    _uiState.update { state ->
                        // 종류를 바꾸면 이전 분류는 더 이상 맞지 않는다.
                        val stillValid = categories.any { it.id == state.categoryId }
                        state.copy(
                            categories = categories,
                            categoryId = if (stillValid) state.categoryId else null,
                        )
                    }
                }
        }
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
                    merchant = existing.merchant.orEmpty(),
                    memo = existing.memo.orEmpty(),
                    occurredAt = existing.occurredAt,
                )
            }
        }
    }

    fun selectType(type: TransactionType) {
        _uiState.update { it.copy(type = type) }
    }

    fun appendDigit(digit: String) {
        _uiState.update { state ->
            val next = (state.amountDigits + digit).trimStart('0')
            if (next.length > MAX_AMOUNT_DIGITS) state else state.copy(amountDigits = next)
        }
    }

    fun deleteDigit() {
        _uiState.update { it.copy(amountDigits = it.amountDigits.dropLast(1)) }
    }

    fun clearAmount() {
        _uiState.update { it.copy(amountDigits = "") }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(categoryId = id) }
    }

    fun selectPaymentMethod(id: Long) {
        // 결제수단은 필수라서 이미 고른 것을 다시 눌러도 선택을 풀지 않는다
        _uiState.update { it.copy(paymentMethodId = id) }
    }

    /**
     * 날짜만 바꾸고 시각은 그대로 둔다.
     * 시간대는 기기 설정이 아니라 서울로 고정한다(BudgetTime). 해외에서 고쳐도 날짜가 밀리지 않는다.
     */
    fun updateDate(date: LocalDate) {
        _uiState.update { state ->
            val time = state.occurredAt.atZone(BudgetTime.ZONE).toLocalTime()
            state.copy(occurredAt = date.atTime(time).atZone(BudgetTime.ZONE).toInstant())
        }
    }

    /** 시각만 바꾸고 날짜는 그대로 둔다. 초는 0 으로 맞춘다. */
    fun updateTime(hour: Int, minute: Int) {
        _uiState.update { state ->
            val date = state.occurredAt.atZone(BudgetTime.ZONE).toLocalDate()
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

    /** 등록 도중에 분류나 결제수단을 새로 만든다. 만들어지면 바로 그것을 고른 상태가 된다. */
    fun submitAdd(name: String, icon: String, color: String) {
        val state = _uiState.value
        val target = state.addTarget ?: return
        viewModelScope.launch {
            val result =
                when (target) {
                    AddTarget.CATEGORY -> categoryRepository.add(state.type.categoryScope(), name, icon, color)
                    AddTarget.PAYMENT -> paymentMethodRepository.add(name, icon, color)
                }
            _uiState.update {
                if (result !is AddResult.Added) {
                    it.copy(addError = result.message())
                } else {
                    when (target) {
                        AddTarget.CATEGORY ->
                            it.copy(categoryId = result.id, lastAddedCategoryId = result.id, addTarget = null, addError = null)

                        AddTarget.PAYMENT ->
                            it.copy(paymentMethodId = result.id, lastAddedPaymentId = result.id, addTarget = null, addError = null)
                    }
                }
            }
        }
    }

    fun onJumpHandled() {
        _uiState.update { it.copy(pendingJump = null) }
    }

    /** 빈 필수 칸이 있으면 저장하지 않고, 그 첫 칸을 화면에 알린다. */
    fun save() {
        val state = _uiState.value
        val missing = state.missingFields.firstOrNull()
        if (missing != null) {
            _uiState.update { it.copy(invalidField = missing, pendingJump = missing) }
            return
        }
        viewModelScope.launch {
            if (transactionId == null) {
                repository.add(
                    type = state.type,
                    amount = state.amount,
                    occurredAt = state.occurredAt,
                    categoryId = state.categoryId,
                    paymentMethodId = state.paymentMethodId,
                    merchant = state.merchant,
                    memo = state.memo,
                )
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

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { it.copy(saved = true) }
        }
    }
}

fun TransactionType.categoryScope(): CategoryScope = if (this == TransactionType.INCOME) CategoryScope.INCOME else CategoryScope.EXPENSE
