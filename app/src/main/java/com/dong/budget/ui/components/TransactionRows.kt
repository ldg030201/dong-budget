package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.format.formatDayHeader
import com.dong.budget.ui.format.formatSignedAmount
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate

// 거래 목록의 날짜 구분선과 거래 한 줄. 홈 목록과 통계(큰 지출, 날마다 쓴 돈, 상세)가 같이 쓴다.

/** 날짜 구분선. 가는 선 아래에 날짜를 적는다. */
@Composable
fun DayHeader(date: LocalDate, today: LocalDate) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(top = BudgetTheme.spacing.inlineGap),
    ) {
        BudgetDivider()
        Text(
            text = formatDayHeader(date, today),
            style = MaterialTheme.typography.labelMedium,
            color = BudgetTheme.colors.textSecondary,
            modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.tightGap),
        )
    }
}

/**
 * 거래 한 줄. 분류 뱃지, 가게(없으면 분류) 이름, 부제, 부호 붙은 금액.
 * @param subtitle 이름 아래 한 줄. 기본은 '오후 2:22 · 식비 · 하나카드'. 날짜가 섞인 목록은 날짜를 넣어 넘긴다.
 */
@Composable
fun TransactionRow(item: TransactionListItem, onClick: () -> Unit, subtitle: String = defaultSubtitle(item)) {
    BudgetListItem(
        title = item.merchant ?: item.categoryName ?: "이름 없는 거래",
        leading = { CategoryBadge(icon = item.categoryIcon, color = item.categoryColor) },
        subtitle = subtitle,
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
        onClick = onClick,
    )
}

private fun defaultSubtitle(item: TransactionListItem): String = listOfNotNull(
    formatTime(item.occurredAt),
    item.categoryName,
    item.paymentMethodName,
).joinToString(" · ")
