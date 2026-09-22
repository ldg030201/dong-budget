package com.dong.budget.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dong.budget.ui.home.DashboardScreen
import com.dong.budget.ui.home.HistoryScreen
import com.dong.budget.ui.home.HomeUiState
import com.dong.budget.ui.home.MoreScreen
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

enum class ShellTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    HISTORY("내역", Icons.AutoMirrored.Filled.List),
    MORE("전체", Icons.Filled.Menu),
}

/**
 * 탭 셸.
 *
 * 이 셸 전체가 백스택의 엔트리 하나다. 그래서 서브플로우를 쌓으면
 * 탭바까지 화면과 함께 통째로 밀려나가고, 돌아오면 탭 상태가 그대로 살아있다.
 *
 * 인셋 규칙: 여기서는 인셋을 비워두고 각 탭 화면이 상단 인셋을 직접 처리한다.
 * 콘텐츠가 상태바 아래까지 올라가 보이게 하려는 의도다.
 * 하단은 NavigationBar 가 자체적으로 처리한다.
 */
@Composable
fun HomeShell(
    state: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    val stateHolder = rememberSaveableStateHolder()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = BudgetTheme.elevation.none,
            ) {
                ShellTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = BudgetTheme.colors.textTertiary,
                            unselectedTextColor = BudgetTheme.colors.textTertiary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 탭을 오갈 때 스크롤 위치 같은 화면 상태를 유지한다.
            stateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    ShellTab.HOME ->
                        DashboardScreen(
                            month = state.month,
                            expenseTotal = state.expenseTotal,
                            incomeTotal = state.incomeTotal,
                            transactionCount = state.items.size,
                            onPreviousMonth = onPreviousMonth,
                            onNextMonth = onNextMonth,
                            onAddTransaction = onAddTransaction,
                        )

                    ShellTab.HISTORY ->
                        HistoryScreen(
                            items = state.items,
                            onItemClick = onEditTransaction,
                        )

                    ShellTab.MORE ->
                        MoreScreen(
                            onOpenStatistics = onOpenStatistics,
                            onOpenSettings = onOpenSettings,
                        )
                }
            }
        }
    }
}

/** 프리뷰나 초기 상태에서 쓰는 빈 값 */
internal fun emptyHomeUiState(): HomeUiState = HomeUiState(
    month = YearMonth.of(2026, 1),
    items = emptyList(),
    expenseTotal = 0,
    incomeTotal = 0,
)
