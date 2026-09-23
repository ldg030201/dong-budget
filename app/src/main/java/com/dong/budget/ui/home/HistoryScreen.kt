package com.dong.budget.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.format.formatSignedAmount
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.theme.BudgetTheme

@Composable
fun HistoryScreen(items: List<TransactionListItem>, onItemClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        EmptyHistory(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BudgetTheme.spacing.sectionGap),
    ) {
        item {
            Text(
                text = "거래 내역",
                style = MaterialTheme.typography.headlineSmall,
                color = BudgetTheme.colors.textPrimary,
                modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(
                        horizontal = BudgetTheme.spacing.screenHorizontal,
                        vertical = BudgetTheme.spacing.sectionPadding,
                    ),
            )
        }
        items(items = items, key = { it.id }) { item ->
            BudgetListItem(
                title = item.merchant ?: item.categoryName ?: "이름 없는 거래",
                leading = { CategoryBadge(icon = item.categoryIcon, color = item.categoryColor) },
                subtitle =
                listOfNotNull(
                    formatTime(item.occurredAt),
                    item.categoryName,
                    item.paymentMethodName,
                ).joinToString(" · "),
                trailing = {
                    Text(
                        text = formatSignedAmount(item.type, item.amount),
                        style = BudgetTheme.amount.medium,
                        color =
                        when (item.type) {
                            TransactionType.INCOME, TransactionType.REFUND -> BudgetTheme.colors.income
                            else -> BudgetTheme.colors.textPrimary
                        },
                    )
                },
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
        ) {
            Text(
                text = "아직 등록된 거래가 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
            )
            Text(
                text = "홈에서 첫 거래를 남겨보세요",
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
        }
    }
}
