package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 아래 메뉴 탭 화면의 머리(월급·고정지출·카드실적·전체). 왼쪽에 큰 제목, 오른쪽에 버튼 하나(설정 톱니 등)를 둔다.
 * 상단 인셋은 부르는 쪽이 처리한다(탭 화면의 statusBarsPadding).
 * @param action 오른쪽 버튼. 없어도 머리 높이는 같게 둔다.
 */
@Composable
fun TabHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(
                start = BudgetTheme.spacing.screenHorizontal,
                end = BudgetTheme.spacing.inlineGap,
                top = BudgetTheme.spacing.inlineGap,
                bottom = BudgetTheme.spacing.inlineGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) action() else Spacer(Modifier.size(BudgetTheme.size.minTouchTarget))
    }
}
