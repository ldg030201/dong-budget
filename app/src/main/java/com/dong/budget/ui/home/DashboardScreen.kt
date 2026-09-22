package com.dong.budget.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.NavIconButton
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

/**
 * 이번 달 요약 화면.
 *
 * 상단 인셋은 각 탭 화면이 직접 처리한다. 셸의 Scaffold 는 인셋을 비워둔다.
 * 콘텐츠가 상태바 아래까지 스크롤되어 올라가는 모습을 만들기 위함이다.
 */
@Composable
fun DashboardScreen(
    month: YearMonth,
    expenseTotal: Long,
    incomeTotal: Long,
    transactionCount: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        MonthSelector(
            month = month,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )

        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

        Column(modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal)) {
            Text(
                text = "이번 달 쓴 돈",
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
            Text(
                text = "${formatAmount(expenseTotal)}원",
                style = BudgetTheme.amount.hero,
                color = BudgetTheme.colors.textPrimary,
            )
        }

        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

        Row(
            modifier =
            Modifier
                .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                .fillMaxWidth()
                .background(
                    BudgetTheme.colors.sectionBackground,
                    RoundedCornerShape(BudgetTheme.radius.block),
                ).padding(BudgetTheme.spacing.sectionPadding),
        ) {
            SummaryCell(
                label = "들어온 돈",
                value = "${formatAmount(incomeTotal)}원",
                modifier = Modifier.weight(1f),
                emphasize = true,
            )
            SummaryCell(
                label = "거래",
                value = "${transactionCount}건",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

        BudgetPrimaryButton(
            text = "거래 등록하기",
            onClick = onAddTransaction,
            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
        )

        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.inlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIconButton(
            icon = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "이전 달",
            onClick = onPreviousMonth,
        )
        Text(
            text = formatMonth(month),
            style = MaterialTheme.typography.titleLarge,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.tightGap),
        )
        NavIconButton(
            icon = Icons.Filled.KeyboardArrowRight,
            contentDescription = "다음 달",
            onClick = onNextMonth,
        )
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BudgetTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        Text(
            text = value,
            style = BudgetTheme.amount.medium,
            color = if (emphasize) BudgetTheme.colors.income else BudgetTheme.colors.textPrimary,
        )
    }
}
