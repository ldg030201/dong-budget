package com.dong.budget.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.AddResult
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.StyledItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 관리 화면의 탭.
 * @property scope 지출 쪽인지 수입 쪽인지. 분류 탭은 분류의, 결제수단 탭은 결제수단 표의 목록이다.
 * @property isPayment 결제수단 표(결제수단·들어온 곳)를 다루는 탭인지. 아니면 분류다.
 */
enum class ManageTab(val label: String, val scope: CategoryScope, val isPayment: Boolean) {
    EXPENSE("지출", CategoryScope.EXPENSE, isPayment = false),
    INCOME("수입", CategoryScope.INCOME, isPayment = false),
    PAYMENT("결제수단", CategoryScope.EXPENSE, isPayment = true),

    /** 수입이 들어온 곳(현금·통장 등). 결제수단 표의 수입 쪽 목록이다. */
    INCOME_SOURCE("들어온 곳", CategoryScope.INCOME, isPayment = true),
    ;

    /** 이 탭의 추가 시트 */
    val addTarget: AddTarget
        get() = when {
            !isPayment -> AddTarget.CATEGORY
            scope == CategoryScope.INCOME -> AddTarget.INCOME_SOURCE
            else -> AddTarget.PAYMENT
        }
}

/** 관리 목록 한 줄. 분류와 결제수단·들어온 곳이 같은 모양으로 보인다. */
data class ManagedItem(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val transactionCount: Int,
    /** '기타' 처럼 지울 수 없는 것은 false. 이런 항목은 순서도 옮길 수 없고 늘 맨 뒤에 있다. */
    val deletable: Boolean,
) {
    /** 끌어서 순서를 옮길 수 있는지 */
    val movable: Boolean get() = deletable

    companion object {
        fun of(item: StyledItem, transactionCount: Int) =
            ManagedItem(item.id, item.name, item.icon, item.color, transactionCount, deletable = !item.isSystem)
    }
}

/**
 * [fromId] 항목을 [toId] 항목 자리로 옮긴 목록. 옮길 수 없으면 null
 * - 옮길 수 없는 항목('기타')은 끌 수도 없고, 다른 항목이 그 자리로 들어갈 수도 없다.
 */
fun List<ManagedItem>.moved(fromId: Long, toId: Long): List<ManagedItem>? {
    val from = indexOfFirst { it.id == fromId }
    val to = indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return null
    if (!this[from].movable || !this[to].movable) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

data class CategoryManageUiState(
    val tab: ManageTab = ManageTab.EXPENSE,
    val items: List<ManagedItem> = emptyList(),
    /**
     * [items] 가 어느 탭의 목록인지. 탭을 누르면 [tab] 은 바로 바뀌지만 목록은 DB 에서 읽어 온 뒤에 바뀐다.
     * 그 사이 옛 목록을 새 탭의 것으로 그리면 줄들이 사라졌다 다시 나타난다.
     */
    val itemsTab: ManageTab = ManageTab.EXPENSE,
    val showAdd: Boolean = false,
    val addError: String? = null,
    /** 지우기 전 확인을 받는 중인 항목 */
    val pendingDelete: ManagedItem? = null,
) {
    val usedColors: Set<String> get() = items.mapTo(mutableSetOf()) { it.color }
}

class CategoryManageViewModel(
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryManageUiState())
    val uiState: StateFlow<CategoryManageUiState> = _uiState.asStateFlow()

    init {
        observeItems()
    }

    /** 탭을 바꾸면 이전 구독을 끊고 새 목록만 따라간다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeItems() {
        viewModelScope.launch {
            _uiState
                .map { it.tab }
                .distinctUntilChanged()
                .flatMapLatest { tab -> itemsFor(tab).map { list -> tab to list } }
                .collect { (tab, list) -> _uiState.update { it.copy(items = list, itemsTab = tab) } }
        }
    }

    private fun itemsFor(tab: ManageTab): Flow<List<ManagedItem>> = if (tab.isPayment) {
        paymentMethodRepository.observeWithCount(tab.scope).map { list ->
            list.map { ManagedItem.of(it.paymentMethod, it.transactionCount) }
        }
    } else {
        categoryRepository.observeWithCount(tab.scope).map { list -> list.map { ManagedItem.of(it.category, it.transactionCount) } }
    }

    fun selectTab(tab: ManageTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun openAdd() {
        _uiState.update { it.copy(showAdd = true, addError = null) }
    }

    fun dismissAdd() {
        _uiState.update { it.copy(showAdd = false, addError = null) }
    }

    fun add(name: String, icon: String, color: String) {
        val tab = _uiState.value.tab
        viewModelScope.launch {
            val result =
                if (tab.isPayment) {
                    paymentMethodRepository.add(tab.scope, name, icon, color)
                } else {
                    categoryRepository.add(tab.scope, name, icon, color)
                }
            _uiState.update {
                if (result is AddResult.Added) {
                    it.copy(showAdd = false, addError = null)
                } else {
                    it.copy(addError = result.message())
                }
            }
        }
    }

    /**
     * 끌어서 바꾼 순서를 저장한다. 옮길 수 있는 항목만 넘긴다('기타' 는 늘 맨 뒤).
     * 저장하면 목록 구독으로 바뀐 순서가 다시 들어온다.
     */
    fun reorder(items: List<ManagedItem>) {
        val tab = _uiState.value.tab
        val orderedIds = items.filter { it.movable }.map { it.id }
        // 저장이 끝나기 전에 목록이 예전 순서로 잠깐 되돌아가 보이지 않게 먼저 바꿔 둔다
        _uiState.update { if (it.tab == tab) it.copy(items = items) else it }
        viewModelScope.launch {
            if (tab.isPayment) paymentMethodRepository.reorder(orderedIds) else categoryRepository.reorder(orderedIds)
        }
    }

    fun requestDelete(item: ManagedItem) {
        if (!item.deletable) return
        _uiState.update { it.copy(pendingDelete = item) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val target = state.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            // 결제수단·들어온 곳은 비워 둘 수 있는 칸이라 걸린 거래에서 비우고, 분류는 '기타' 로 옮긴다
            if (state.tab.isPayment) paymentMethodRepository.delete(target.id) else categoryRepository.delete(target.id)
        }
    }
}
