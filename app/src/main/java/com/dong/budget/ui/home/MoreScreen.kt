package com.dong.budget.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.R
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 대메뉴 화면.
 *
 * 여기 항목을 누르면 서브플로우로 들어가면서 탭바가 사라지고
 * 왼쪽 위에 뒤로가기만 남는다.
 *
 * 항목마다 색을 달리하되 옅은 원 안에만 칠한다. 글자와 배경은 그대로 둬서 요란하지 않게 한다.
 */
@Composable
fun MoreScreen(onOpenCategories: () -> Unit, onOpenStatistics: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
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
        MenuItem(
            title = "분류 관리",
            subtitle = "분류와 결제수단을 추가하거나 지워요",
            iconRes = R.drawable.ic_sym_category,
            color = "indigo",
            onClick = onOpenCategories,
        )
        MenuItem(
            title = "통계",
            subtitle = "분류별로 얼마나 썼는지 봐요",
            iconRes = R.drawable.ic_sym_bar_chart,
            color = "teal",
            onClick = onOpenStatistics,
        )
        MenuItem(
            title = "설정",
            subtitle = "테마, 업데이트",
            iconRes = R.drawable.ic_sym_settings,
            color = "gray",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun MenuItem(title: String, subtitle: String, @DrawableRes iconRes: Int, color: String, onClick: () -> Unit) {
    BudgetListItem(
        title = title,
        subtitle = subtitle,
        leading = { IconBadge(iconRes = iconRes, swatch = BudgetTheme.categoryPalette[color]) },
        onClick = onClick,
    )
}
