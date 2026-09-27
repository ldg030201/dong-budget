package com.dong.budget.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.dong.budget.R
import com.dong.budget.ui.components.BudgetIconButton
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
fun MoreScreen(
    devModeOn: Boolean,
    onOpenCategories: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 설정은 목록이 아니라 제목 줄 오른쪽 위에 둔다. 메뉴가 늘어나도 자리를 찾기 쉽다.
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = BudgetTheme.spacing.screenHorizontal,
                    end = BudgetTheme.spacing.inlineGap,
                    top = BudgetTheme.spacing.inlineGap,
                    bottom = BudgetTheme.spacing.inlineGap,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "전체",
                style = MaterialTheme.typography.headlineSmall,
                color = BudgetTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            BudgetIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_sym_settings),
                contentDescription = "설정",
                onClick = onOpenSettings,
            )
        }
        MenuItem(
            title = "분류 관리",
            subtitle = "분류와 결제수단을 추가하거나 지워요",
            iconRes = R.drawable.ic_sym_category,
            color = "indigo",
            onClick = onOpenCategories,
        )
        // 전체는 앱의 모든 메뉴를 늘어놓는 곳이라 아래 메뉴에 있는 통계도 여기 둔다
        MenuItem(
            title = "통계",
            subtitle = "월별·일별·분류·결제수단으로 나눠 봐요",
            iconRes = R.drawable.ic_sym_bar_chart,
            color = "orange",
            onClick = onOpenStatistics,
        )
        MenuItem(
            title = "패치노트",
            subtitle = "버전마다 바뀐 점을 봐요",
            iconRes = R.drawable.ic_sym_new_releases,
            color = "purple",
            onClick = onOpenPatchNotes,
        )
        MenuItem(
            title = "개발자 모드",
            // 켜 둔 동안에는 로그가 쌓인다는 것을 여기서도 알 수 있게 한다
            subtitle = if (devModeOn) "켜져 있어요 · 로그를 쌓고 있어요" else "오류가 났을 때 기록을 모아 보내요",
            iconRes = R.drawable.ic_sym_bug_report,
            color = "gray",
            onClick = onOpenDeveloper,
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
