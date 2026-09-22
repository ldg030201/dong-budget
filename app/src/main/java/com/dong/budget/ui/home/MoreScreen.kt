package com.dong.budget.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 대메뉴 화면.
 *
 * 여기 항목을 누르면 서브플로우로 들어가면서 탭바가 사라지고
 * 왼쪽 위에 뒤로가기만 남는다.
 */
@Composable
fun MoreScreen(onOpenStatistics: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "전체",
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
        BudgetListItem(
            title = "통계",
            subtitle = "카테고리별로 얼마나 썼는지 봅니다",
            onClick = onOpenStatistics,
        )
        BudgetListItem(
            title = "설정",
            subtitle = "테마, 백업",
            onClick = onOpenSettings,
        )
    }
}
