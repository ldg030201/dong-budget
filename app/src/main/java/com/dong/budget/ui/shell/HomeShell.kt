package com.dong.budget.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.home.HomeScreen
import com.dong.budget.ui.home.HomeUiState
import com.dong.budget.ui.home.MoreScreen
import com.dong.budget.ui.theme.Motion
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 탭 셸.
 *
 * 이 셸 전체가 백스택의 엔트리 하나다. 그래서 서브플로우를 쌓으면
 * 탭바까지 화면과 함께 통째로 밀려나가고, 돌아오면 탭 상태가 그대로 살아있다.
 *
 * 아래 메뉴는 사용자가 고른 차림([menu])이다. 홈과 전체는 늘 양 끝에 있다.
 * 메뉴는 아래 메뉴에 있으면 탭으로 바뀌고, 없으면(전체 목록·홈의 바로가기로 열 때) 셸 위에 따로 연다([onOpenPage]).
 * 통계는 탭이 아니라 늘 셸 위에 연다.
 *
 * 인셋 규칙: 여기서는 인셋을 비워두고 각 탭 화면이 상단 인셋을 직접 처리한다.
 * 콘텐츠가 상태바 아래까지 올라가 보이게 하려는 의도다.
 * 하단은 아래 메뉴([ShellBottomBar])가 처리한다.
 *
 * @param onOpenStatistics 아래 메뉴의 '통계'. 탭을 바꾸지 않고 통계 화면을 셸 위에 연다.
 * @param onOpenStatisticsAt 홈 요약의 지난달 비교 줄. 홈에서 보던 달의 통계를 연다.
 * @param onOpenPage 아래 메뉴에 없는 메뉴를 셸 위에 연다(뒤로 가기가 있는 같은 화면)
 * @param pageContent 홈·전체가 아닌 탭 화면(월급·고정지출·카드실적·내역). 탭을 처음 열 때 그 화면 모델이 만들어지게
 *   부르는 쪽(DongBudgetApp)이 채운다. 셸 위에 따로 열 때도 같은 것을 쓴다.
 */
@Composable
fun HomeShell(
    state: HomeUiState,
    menu: BottomMenu,
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
    onOpenStatisticsAt: (YearMonth) -> Unit,
    onOpenPage: (MenuItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    devModeOn: Boolean,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (MenuItem) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(MenuItem.HOME) }
    // 고른 탭을 설정에서 아래 메뉴에서 뺐으면 홈을 보여 준다
    val shownTab = selectedTab.takeIf { it.isTab && it in menu } ?: MenuItem.HOME
    // 고른 것도 홈으로 돌려 둔다. 그 탭을 나중에 다시 넣었을 때 갑자기 그 탭으로 넘어가지 않게 한다.
    LaunchedEffect(shownTab) { if (selectedTab != shownTab) selectedTab = shownTab }
    val stateHolder = rememberSaveableStateHolder()
    // 가운데 칸 스크롤. 셸이 들고 있어야 통계에 다녀와도 같은 자리이고(통계 칸이 내려올 자리), 전체에서 고른 탭이 보이게 밀 수 있다.
    val middleState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /** 고른 탭이 아래 메뉴에서 밀려나 안 보이면 보이게 민다. 아래 메뉴에서 누른 칸은 이미 보이니 전체·홈에서 고를 때만 부른다. */
    fun reveal(item: MenuItem) {
        if (!menu.scrolls) return
        val index = menu.shown.indexOf(item).takeIf { it >= 0 } ?: return
        val first = middleState.firstVisibleItemIndex
        if (isFullyShown(index, first, middleState.firstVisibleItemScrollOffset)) return
        val target = if (index <= first) index else index - (BottomMenu.VISIBLE_MIDDLE - 1)
        scope.launch { middleState.animateScrollToItem(target) }
    }

    /** 메뉴를 연다. 통계는 셸 위에, 아래 메뉴에 있는 탭은 그 탭으로, 없으면 셸 위에 따로 연다. */
    fun open(item: MenuItem, fromBar: Boolean = false) {
        when {
            !item.isTab -> onOpenStatistics()

            item in menu -> {
                selectedTab = item
                if (!fromBar) reveal(item)
            }

            else -> onOpenPage(item)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            ShellBottomBar(
                menu = menu,
                selected = shownTab,
                onClick = { item -> open(item, fromBar = true) },
                middleState = middleState,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 탭을 오갈 때 뚝 끊기지 않고 겹쳐 바뀐다. 보관은 바뀌는 동안 두 탭이 함께 그려지므로 각자의 탭 이름으로 한다.
            AnimatedContent(
                targetState = shownTab,
                transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick()) },
                label = "shellTab",
            ) { tab ->
                // 탭을 오갈 때 스크롤 위치 같은 화면 상태를 유지한다.
                stateHolder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        MenuItem.HOME ->
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
                                onOpenStatistics = onOpenStatisticsAt,
                                onOpenHistory = { open(MenuItem.HISTORY) },
                            )

                        MenuItem.MORE ->
                            MoreScreen(
                                devModeOn = devModeOn,
                                onOpenMenu = { item -> open(item) },
                                onOpenCategories = onOpenCategories,
                                onOpenSettings = onOpenSettings,
                                onOpenPatchNotes = onOpenPatchNotes,
                                onOpenDeveloper = onOpenDeveloper,
                            )

                        else -> pageContent(tab)
                    }
                }
            }
        }
    }
}
