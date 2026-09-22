package com.dong.budget.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val saved: Boolean = false,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    /** 금액이 0이면 저장할 게 없다. */
    val canSave: Boolean get() = amount > 0
}

class TransactionEditorViewModel(private val repository: TransactionRepository, private val transactionId: Long?) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState(isEditing = transactionId != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        observeChoices(CategoryScope.EXPENSE)
        viewModelScope.launch {
            repository.observePaymentMethods().collect { methods ->
                _uiState.update { it.copy(paymentMethods = methods) }
            }
        }
        if (transactionId != null) loadExisting(transactionId)
    }

    private fun observeChoices(scope: CategoryScope) {
        viewModelScope.launch {
            repository.observeCategories(scope).collect { categories ->
                _uiState.update { state ->
                    // 종류를 바꾸면 이전 카테고리는 더 이상 유효하지 않다.
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
            if (existing.type == TransactionType.INCOME) observeChoices(CategoryScope.INCOME)
        }
    }

    fun selectType(type: TransactionType) {
        if (_uiState.value.type == type) return
        _uiState.update { it.copy(type = type) }
        observeChoices(
            if (type == TransactionType.INCOME) CategoryScope.INCOME else CategoryScope.EXPENSE,
        )
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
        _uiState.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    }

    fun selectPaymentMethod(id: Long) {
        _uiState.update { it.copy(paymentMethodId = if (it.paymentMethodId == id) null else id) }
    }

    fun updateMerchant(value: String) {
        _uiState.update { it.copy(merchant = value) }
    }

    fun updateMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
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
