package com.dong.budget.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.dong.budget.R
import com.dong.budget.ui.components.STATS_ENTRY_SHARED_KEY
import com.dong.budget.ui.components.sharedNavElement
import com.dong.budget.ui.home.HomeScreen
import com.dong.budget.ui.home.HomeUiState
import com.dong.budget.ui.home.MoreScreen
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressFeedback

/** 아래 메뉴의 탭. 이름(name)은 탭마다 화면 상태를 보관하는 열쇠라 바꾸지 않는다. */
enum class ShellTab(val label: String) {
    // 거래 목록은 홈 달력 아래에 있다. 따로 '내역' 탭을 두면 같은 목록이 두 군데 생긴다.
    HOME("홈"),

    /** 실시간 월급 */
    SALARY("월급"),
    MORE("전체"),
}

/** 탭 아이콘. 월급은 기본 수입 분류 '급여' 와 같은 아이콘이다(Material Symbols 는 리소스라 여기서 읽는다). */
@Composable
private fun ShellTab.icon(): ImageVector = when (this) {
    ShellTab.HOME -> Icons.Filled.Home
    ShellTab.SALARY -> ImageVector.vectorResource(R.drawable.ic_sym_payments)
    ShellTab.MORE -> Icons.Filled.Menu
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
 *
 * @param onOpenStatistics 아래 메뉴의 '통계'. 탭을 바꾸지 않고 통계 화면을 셸 위에 연다.
 * @param salaryContent 월급 탭 화면. 월급 탭을 처음 열 때 그 화면 모델이 만들어지게 부르는 쪽(DongBudgetApp)이 채운다.
 */
@Composable
fun HomeShell(
    state: HomeUiState,
    updateVersion: String?,
    hasNewNotice: Boolean,
    onOpenUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onSkipUpdate: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    devModeOn: Boolean,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
    salaryContent: @Composable () -> Unit = {},
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
                val itemColors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = BudgetTheme.colors.textTertiary,
                        unselectedTextColor = BudgetTheme.colors.textTertiary,
                        indicatorColor = Color.Transparent,
                    )
                ShellTab.entries.forEach { tab ->
                    ShellNavItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = tab.label,
                        colors = itemColors,
                    )
                    // 통계는 탭이 아니라 입구다. 누르면 통계 화면이 셸 위로 올라오고, 이 칸은 고른 칸이 되지 않는다.
                    // 그래서 ShellTab 에 넣지 않고 월급 바로 뒤에 끼운다(홈 · 월급 · 통계 · 전체).
                    // 통계를 열면 이 칸의 아이콘과 글자가 통계 하위 메뉴의 첫 칸 '통계' 자리로 옮겨 가고, 닫으면 여기로 내려온다.
                    if (tab == ShellTab.SALARY) {
                        ShellNavItem(
                            selected = false,
                            onClick = onOpenStatistics,
                            icon = {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.ic_sym_bar_chart),
                                    contentDescription = null,
                                    modifier = Modifier.sharedNavElement("$STATS_ENTRY_SHARED_KEY-icon"),
                                )
                            },
                            label = "통계",
                            labelSharedKey = "$STATS_ENTRY_SHARED_KEY-label",
                            colors = itemColors,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 탭을 오갈 때 뚝 끊기지 않고 겹쳐 바뀐다. 보관은 바뀌는 동안 두 탭이 함께 그려지므로 각자의 탭 이름으로 한다.
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick()) },
                label = "shellTab",
            ) { tab ->
                // 탭을 오갈 때 스크롤 위치 같은 화면 상태를 유지한다.
                stateHolder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        ShellTab.HOME ->
                            HomeScreen(
                                state = state,
                                updateVersion = updateVersion,
                                hasNewNotice = hasNewNotice,
                                onOpenUpdate = onOpenUpdate,
                                onDismissUpdate = onDismissUpdate,
                                onSkipUpdate = onSkipUpdate,
                                onPreviousMonth = onPreviousMonth,
                                onNextMonth = onNextMonth,
                                onAddTransaction = onAddTransaction,
                                onOpenTransaction = onOpenTransaction,
                                onOpenInbox = onOpenInbox,
                            )

                        ShellTab.SALARY -> salaryContent()

                        ShellTab.MORE ->
                            MoreScreen(
                                devModeOn = devModeOn,
                                onOpenSalary = { selectedTab = ShellTab.SALARY },
                                onOpenCategories = onOpenCategories,
                                onOpenStatistics = onOpenStatistics,
                                onOpenSettings = onOpenSettings,
                                onOpenPatchNotes = onOpenPatchNotes,
                                onOpenDeveloper = onOpenDeveloper,
                            )
                    }
                }
            }
        }
    }
}

/**
 * 하단 탭 한 칸. 앱은 리플을 꺼 두었으므로(Theme) 누르면 아이콘과 글자가 눌려 들어가게 한다.
 * @param labelSharedKey 글자를 다른 화면의 같은 글자와 잇는 열쇠([sharedNavElement]). 없으면 null
 */
@Composable
private fun RowScope.ShellNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    colors: NavigationBarItemColors,
    labelSharedKey: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                modifier = if (labelSharedKey != null) Modifier.sharedNavElement(labelSharedKey) else Modifier,
            )
        },
        colors = colors,
        interactionSource = interactionSource,
        modifier =
        Modifier.pressFeedback(
            interactionSource,
            RoundedCornerShape(BudgetTheme.radius.control),
            pressedScale = NAV_PRESSED_SCALE,
        ),
    )
}

/** 하단 탭은 바탕 없이 작은 아이콘과 글자뿐이라 버튼보다 더 줄인다 */
private const val NAV_PRESSED_SCALE = 0.9f
