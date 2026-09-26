package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

/**
 * 화면 맨 위의 달 고르기. ‹ 2026년 9월 › 와 오른쪽 끝 자리([trailing]).
 * 홈은 오른쪽에 종을, 통계는 '이번 달' 버튼을 둔다.
 */
@Composable
fun MonthStepper(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BudgetIconButton(
            icon = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "이전 달",
            onClick = onPreviousMonth,
        )
        Text(
            text = formatMonth(month),
            style = MaterialTheme.typography.titleLarge,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.tightGap).semantics { heading() },
        )
        BudgetIconButton(
            icon = Icons.Filled.KeyboardArrowRight,
            contentDescription = "다음 달",
            onClick = onNextMonth,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
