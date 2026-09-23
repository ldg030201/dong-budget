package com.dong.budget.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.AddResult
import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.db.CategoryScope
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

/** 관리 화면의 탭 */
enum class ManageTab(val label: String) {
    EXPENSE("지출"),
    INCOME("수입"),
    PAYMENT("결제수단"),
}

/** 관리 목록 한 줄. 분류와 결제수단이 같은 모양으로 보인다. */
data class ManagedItem(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val transactionCount: Int,
    /** '기타' 처럼 지울 수 없는 것은 false */
    val deletable: Boolean,
)

data class CategoryManageUiState(
    val tab: ManageTab = ManageTab.EXPENSE,
    val items: List<ManagedItem> = emptyList(),
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
                .flatMapLatest(::itemsFor)
                .collect { list -> _uiState.update { it.copy(items = list) } }
        }
    }

    private fun itemsFor(tab: ManageTab): Flow<List<ManagedItem>> = when (tab) {
        ManageTab.EXPENSE, ManageTab.INCOME ->
            categoryRepository.observeWithCount(tab.scope()).map { list ->
                list.map {
                    ManagedItem(
                        id = it.category.id,
                        name = it.category.name,
                        icon = it.category.icon,
                        color = it.category.color,
                        transactionCount = it.transactionCount,
                        deletable = !it.category.isSystem,
                    )
                }
            }

        ManageTab.PAYMENT ->
            paymentMethodRepository.observeWithCount().map { list ->
                list.map {
                    ManagedItem(
                        id = it.paymentMethod.id,
                        name = it.paymentMethod.name,
                        icon = it.paymentMethod.icon,
                        color = it.paymentMethod.color,
                        transactionCount = it.transactionCount,
                        deletable = !it.paymentMethod.isSystem,
                    )
                }
            }
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
                when (tab) {
                    ManageTab.EXPENSE, ManageTab.INCOME -> categoryRepository.add(tab.scope(), name, icon, color)
                    ManageTab.PAYMENT -> paymentMethodRepository.add(name, icon, color)
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
            when (state.tab) {
                ManageTab.EXPENSE, ManageTab.INCOME -> categoryRepository.delete(target.id)
                ManageTab.PAYMENT -> paymentMethodRepository.delete(target.id)
            }
        }
    }
}

private fun ManageTab.scope(): CategoryScope = if (this == ManageTab.INCOME) CategoryScope.INCOME else CategoryScope.EXPENSE
