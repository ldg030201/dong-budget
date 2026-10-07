package com.dong.budget.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.dong.budget.R
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.components.TabHeader
import com.dong.budget.ui.shell.color
import com.dong.budget.ui.shell.description
import com.dong.budget.ui.shell.icon
import com.dong.budget.ui.shell.label
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 대메뉴 화면.
 *
 * 여기 항목을 누르면 서브플로우로 들어가면서 탭바가 사라지고
 * 왼쪽 위에 뒤로가기만 남는다. 아래 메뉴에 둔 메뉴(월급·고정지출·카드실적·내역)는 그 탭으로 바뀐다.
 *
 * 항목마다 색을 달리하되 옅은 원 안에만 칠한다. 글자와 배경은 그대로 둬서 요란하지 않게 한다.
 */
@Composable
fun MoreScreen(
    devModeOn: Boolean,
    onOpenMenu: (MenuItem) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 메뉴가 늘면 가로 화면에서 아래 줄이 하단 메뉴에 가려진다. 스크롤해서 모두 닿게 한다.
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 설정은 목록이 아니라 제목 줄 오른쪽 위에 둔다. 메뉴가 늘어나도 자리를 찾기 쉽다.
        TabHeader(
            title = "전체",
            modifier = Modifier.statusBarsPadding(),
            action = {
                BudgetIconButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_sym_settings),
                    contentDescription = "설정",
                    onClick = onOpenSettings,
                )
            },
        )
        // 전체는 앱의 모든 메뉴를 늘어놓는 곳이라 아래 메뉴에 있는 것도 둔다. 아래 메뉴에 있으면 그 탭으로, 없으면 따로 연다.
        MenuRow(MenuItem.SALARY, onOpenMenu)
        BudgetListItem(
            title = "분류 관리",
            subtitle = "분류와 결제수단을 추가하거나 지워요",
            leading = { IconBadge(iconRes = R.drawable.ic_sym_category, swatch = BudgetTheme.categoryPalette["indigo"]) },
            onClick = onOpenCategories,
        )
        MenuRow(MenuItem.STATISTICS, onOpenMenu)
        MenuRow(MenuItem.HISTORY, onOpenMenu)
        MenuRow(MenuItem.FIXED_EXPENSE, onOpenMenu)
        MenuRow(MenuItem.CARD_PERFORMANCE, onOpenMenu)
        BudgetListItem(
            title = "패치노트",
            subtitle = "버전마다 바뀐 점을 봐요",
            leading = { IconBadge(iconRes = R.drawable.ic_sym_new_releases, swatch = BudgetTheme.categoryPalette["purple"]) },
            onClick = onOpenPatchNotes,
        )
        BudgetListItem(
            title = "개발자 모드",
            // 켜 둔 동안에는 로그가 쌓인다는 것을 여기서도 알 수 있게 한다
            subtitle = if (devModeOn) "켜져 있어요 · 로그를 쌓고 있어요" else "오류가 났을 때 기록을 모아 보내요",
            leading = { IconBadge(iconRes = R.drawable.ic_sym_bug_report, swatch = BudgetTheme.categoryPalette["gray"]) },
            onClick = onOpenDeveloper,
        )
    }
}

/** 아래 메뉴에 둘 수 있는 메뉴의 줄. 이름·아이콘은 아래 메뉴와 같은 것을 쓴다. */
@Composable
private fun MenuRow(item: MenuItem, onOpen: (MenuItem) -> Unit) {
    BudgetListItem(
        title = item.label,
        subtitle = item.description,
        leading = { IconBadge(icon = item.icon(), swatch = BudgetTheme.categoryPalette[item.color]) },
        onClick = { onOpen(item) },
    )
}
