package com.dong.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class HomeUiState(val month: YearMonth, val items: List<TransactionListItem>, val expenseTotal: Long, val incomeTotal: Long)

class HomeViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val selectedMonth = MutableStateFlow(BudgetTime.currentMonth())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> =
        selectedMonth
            .flatMapLatest { month ->
                repository.observeMonth(month).map { items -> buildState(month, items) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = buildState(BudgetTime.currentMonth(), emptyList()),
            )

    fun showPreviousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    private fun buildState(month: YearMonth, items: List<TransactionListItem>): HomeUiState {
        // 이체는 내 돈이 자리만 옮긴 것이라 수입에도 지출에도 넣지 않는다.
        // 환불은 해당 지출을 되돌린 것이므로 지출에서 뺀다.
        var expense = 0L
        var income = 0L
        items.forEach { item ->
            when (item.type) {
                TransactionType.EXPENSE -> expense += item.amount
                TransactionType.REFUND -> expense -= item.amount
                TransactionType.INCOME -> income += item.amount
                TransactionType.TRANSFER -> Unit
            }
        }
        return HomeUiState(month = month, items = items, expenseTotal = expense, incomeTotal = income)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
