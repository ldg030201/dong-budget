package com.dong.budget.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.AddCategoryResult
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionType
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
    val showAddCategory: Boolean = false,
    val addCategoryError: String? = null,
    val saved: Boolean = false,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    /** 금액이 0이면 저장할 게 없다. */
    val canSave: Boolean get() = amount > 0

    val selectedCategory: CategoryEntity? get() = categories.firstOrNull { it.id == categoryId }

    val selectedPaymentMethod: PaymentMethodEntity? get() = paymentMethods.firstOrNull { it.id == paymentMethodId }

    val usedColors: Set<String> get() = categories.mapTo(mutableSetOf()) { it.color }
}

class TransactionEditorViewModel(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
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
        // 이미 고른 것을 다시 누르면 선택을 푼다. 결제수단은 비워둘 수 있다.
        _uiState.update { it.copy(paymentMethodId = if (it.paymentMethodId == id) null else id) }
    }

    fun updateMerchant(value: String) {
        _uiState.update { it.copy(merchant = value) }
    }

    fun updateMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    fun openAddCategory() {
        _uiState.update { it.copy(showAddCategory = true, addCategoryError = null) }
    }

    fun dismissAddCategory() {
        _uiState.update { it.copy(showAddCategory = false, addCategoryError = null) }
    }

    /** 등록 도중에 분류를 새로 만든다. 만들어지면 바로 그 분류를 고른 상태가 된다. */
    fun addCategory(name: String, icon: String, color: String) {
        val scope = _uiState.value.type.categoryScope()
        viewModelScope.launch {
            when (val result = categoryRepository.add(scope, name, icon, color)) {
                is AddCategoryResult.Added ->
                    _uiState.update {
                        it.copy(categoryId = result.id, showAddCategory = false, addCategoryError = null)
                    }

                else -> _uiState.update { it.copy(addCategoryError = result.message()) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
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

/** 분류 추가가 거절된 이유를 사람이 읽을 문장으로 */
fun AddCategoryResult.message(): String? = when (this) {
    is AddCategoryResult.Added -> null
    AddCategoryResult.BlankName -> "이름을 적어주세요"
    AddCategoryResult.NameTooLong -> "이름은 ${CategoryRepository.MAX_NAME_LENGTH}자까지 쓸 수 있어요"
    AddCategoryResult.DuplicateName -> "이미 있는 이름이에요"
}
