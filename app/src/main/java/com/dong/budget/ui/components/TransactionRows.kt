package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.format.formatDayHeader
import com.dong.budget.ui.format.formatSignedAmount
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate
import java.time.YearMonth

// 거래 목록의 날짜 구분선과 거래 한 줄. 홈 목록과 통계(큰 지출, 날마다 쓴 돈, 상세), 거래 상세가 같이 쓴다.

/** 날짜 구분선. 가는 선 아래에 날짜를 적는다. */
@Composable
fun DayHeader(date: LocalDate, today: LocalDate) {
    ListHeader(formatDayHeader(date, today))
}

/** 달 구분선. 가는 선 아래에 "이번 달" / "8월" / "2025년 12월" 을 적는다. 여러 달이 섞인 목록(거래 상세의 같은 곳 내역)에 쓴다. */
@Composable
fun MonthHeader(month: YearMonth, today: LocalDate) {
    ListHeader(monthLabel(month, today))
}

@Composable
private fun ListHeader(text: String) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(top = BudgetTheme.spacing.inlineGap),
    ) {
        BudgetDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = BudgetTheme.colors.textSecondary,
            modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.tightGap),
        )
    }
}

/**
 * 거래 한 줄. 분류 뱃지, 가게(없으면 분류) 이름, 부제, 부호 붙은 금액.
 * @param onClick null 이면 누를 수 없는 줄이다(거래 상세에서 지금 보고 있는 거래).
 * @param title 이름 자리. 기본은 가게(없으면 분류) 이름. 같은 가게만 모은 목록은 날짜를 넣어 넘긴다.
 * @param subtitle 이름 아래 한 줄. 기본은 '오후 2:22 · 식비 · 하나카드'. 날짜가 섞인 목록은 날짜를 넣어 넘긴다.
 * @param colorExpense 지출 금액도 지출색(빨강)으로 칠할지. 홈 목록은 지출이 줄줄이 이어져 화면이 빨개지지 않게 본문색으로 두고,
 *   통계처럼 금액마다 방향 색을 쓰는 화면은 켠다.
 */
@Composable
fun TransactionRow(
    item: TransactionListItem,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = transactionTitle(item),
    subtitle: String = transactionSubtitle(item),
    colorExpense: Boolean = false,
) {
    BudgetListItem(
        title = title,
        modifier = modifier,
        leading = { CategoryBadge(icon = item.categoryIcon, color = item.categoryColor) },
        subtitle = subtitle,
        trailing = {
            Text(
                text = formatSignedAmount(item.type, item.amount),
                style = BudgetTheme.amount.medium,
                color = transactionAmountColor(item.type, colorExpense),
            )
        },
        onClick = onClick,
    )
}

/**
 * 거래 한 건 금액의 글자색. 들어온 돈(수입·환불)은 수입색, 지출은 [colorExpense] 면 지출색이고 아니면 본문색이다.
 * 거래 줄과 거래 상세의 큰 금액이 같이 쓴다.
 */
@Composable
fun transactionAmountColor(type: TransactionType, colorExpense: Boolean = false): Color = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> BudgetTheme.colors.income
    TransactionType.EXPENSE -> if (colorExpense) BudgetTheme.colors.expense else BudgetTheme.colors.textPrimary
    TransactionType.TRANSFER -> BudgetTheme.colors.textPrimary
}

/** 거래의 이름. 가게, 없으면 분류 이름이다. 거래 줄과 거래 상세의 머리가 같이 쓴다. */
fun transactionTitle(item: TransactionListItem): String = item.merchant ?: item.categoryName ?: "이름 없는 거래"

/** 거래 줄의 기본 부제. '오후 2:22 · 식비 · 하나카드' */
fun transactionSubtitle(item: TransactionListItem): String = listOfNotNull(
    formatTime(item.occurredAt),
    item.categoryName,
    item.paymentMethodName,
).joinToString(" · ")
