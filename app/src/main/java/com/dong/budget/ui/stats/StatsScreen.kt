package com.dong.budget.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.FloatingSubBar
import com.dong.budget.ui.components.FloatingSubBarScrim
import com.dong.budget.ui.components.MonthStepper
import com.dong.budget.ui.components.SubBarTab
import com.dong.budget.ui.components.floatingBarClearance
import com.dong.budget.ui.stats.tab.BreakdownKind
import com.dong.budget.ui.stats.tab.BreakdownTab
import com.dong.budget.ui.stats.tab.DailyTab
import com.dong.budget.ui.stats.tab.MonthlyTab
import com.dong.budget.ui.theme.Motion
import java.time.YearMonth

/**
 * 통계. 아래 메뉴의 '통계' 로 들어온다.
 * 맨 위 달 줄과 아래 떠 있는 메뉴(뒤로 · 월별 · 일별 · 분류 · 결제수단) 사이에 고른 탭을 보여준다.
 *
 * 네 탭이 달 하나를 같이 쓴다. 탭을 바꿔도 달은 그대로고, 달을 바꿔도 탭마다 보던 스크롤 위치는 그대로다.
 * 탭 이동은 뒤로 기록에 쌓지 않는다. 뒤로(메뉴의 ←, 시스템 뒤로)는 늘 통계를 나가 들어오기 전 화면으로 간다.
 *
 * 화면은 셸 위에서 빠르게 나타나고 사라지고(DongBudgetApp 의 statsTransitions), 떠 있는 메뉴는 따로 아래에서 떠오르고 가라앉는다.
 * 위에 등록창이나 상세가 쌓일 때도 메뉴가 가라앉았다가 돌아오면 다시 떠오른다. 그래서 NavDisplay 안에서만 그릴 수 있다.
 *
 * 상단 인셋은 이 화면이 직접 처리하고, 아래 인셋은 떠 있는 메뉴와 탭 목록의 아래 여백([floatingBarClearance])이 처리한다.
 *
 * @param selectedTab 지금 탭. 바깥(DongBudgetApp)이 들고 있어야 화면 전환 중에 눌린 탭을 거를 수 있다.
 * @param onThisMonth 다른 달을 보고 있을 때 달 줄 오른쪽에 나오는 '이번 달'
 * @param onShowMonth 월별 탭의 6개월 표에서 다른 달을 눌렀을 때
 * @param onOpenDetail 분류나 결제수단 하나의 상세
 * @param onOpenTransaction 거래 줄을 누르면 그 거래의 등록창
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    selectedTab: StatsTab,
    onSelectTab: (StatsTab) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    onOpenDetail: (StatsDetailKey) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 탭을 오갈 때 탭마다 스크롤 위치 같은 화면 상태를 따로 보관한다
    val tabStates = rememberSaveableStateHolder()
    val contentPadding = PaddingValues(bottom = floatingBarClearance())
    // Surface 는 터치를 받는다. 셸 위로 나타나고 사라지는 동안 통계의 빈 곳을 누른 탭이 밑의 셸로 새지 않는다.
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = "통계" },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 가로 화면의 좌우 인셋은 앱 전체(DongBudgetApp)에서 한 번에 뺀다
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                MonthStepper(
                    month = state.month,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    trailing = {
                        AnimatedVisibility(
                            visible = state.month != YearMonth.from(state.today),
                            enter = fadeIn(Motion.quick()) + scaleIn(Motion.standard(), initialScale = HIDDEN_BUTTON_SCALE),
                            exit = fadeOut(Motion.quick()) + scaleOut(Motion.standard(), targetScale = HIDDEN_BUTTON_SCALE),
                        ) {
                            BudgetTextButton(text = "이번 달", onClick = onThisMonth)
                        }
                    },
                )
                // 첫 계산이 끝나기 전에는 달 줄과 메뉴만 둔다. 빈 상태 안내가 잠깐 보였다 사라지지 않게 한다.
                val page =
                    when {
                        !state.loaded -> null
                        !state.hasAnyRecord -> StatsPage.Empty
                        else -> StatsPage.Tab(selectedTab)
                    }
                // 탭을 바꾸거나 첫 계산이 끝나면 내용이 겹쳐 바뀐다
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick()) },
                    label = "statsPage",
                ) { shown ->
                    // 바뀌는 동안 두 탭이 함께 있다. 화면 읽기가 새 탭 이름만 알리도록 들어오는 쪽에만 이름을 붙인다.
                    val incoming = transition.targetState == EnterExitState.Visible
                    when (shown) {
                        null -> Box(Modifier.fillMaxSize())

                        StatsPage.Empty ->
                            StatsEmpty(title = "아직 통계로 볼 거래가 없어요", body = "홈에서 거래를 남기면 여기서 모아 볼 수 있어요")

                        is StatsPage.Tab ->
                            tabStates.SaveableStateProvider(shown.tab.name) {
                                Box(modifier = Modifier.fillMaxSize().semantics { if (incoming) paneTitle = "${shown.tab.label} 통계" }) {
                                    TabContent(
                                        tab = shown.tab,
                                        state = state,
                                        contentPadding = contentPadding,
                                        onSelectTab = onSelectTab,
                                        onShowMonth = onShowMonth,
                                        onOpenDetail = onOpenDetail,
                                        onOpenTransaction = onOpenTransaction,
                                    )
                                }
                            }
                    }
                }
            }
            FloatingSubBarScrim(modifier = Modifier.align(Alignment.BottomCenter))
            FloatingSubBar(
                tabs = TABS,
                selectedIndex = selectedTab.ordinal,
                onSelect = { index -> onSelectTab(StatsTab.entries[index]) },
                onBack = onBack,
                modifier = Modifier.align(Alignment.BottomCenter).floatingBarMotion(),
            )
        }
    }
}

private val TABS = StatsTab.entries.map { SubBarTab(label = it.label, icon = it.icon) }

/** 통계 화면 가운데에 보이는 것. 첫 계산 전(null)·기록 없음·탭 하나 */
private sealed interface StatsPage {
    data object Empty : StatsPage

    data class Tab(val tab: StatsTab) : StatsPage
}

/** '이번 달' 버튼이 나타나고 사라질 때 이 크기에서 커지고 여기까지 줄어든다 */
private const val HIDDEN_BUTTON_SCALE = 0.8f

@Composable
private fun TabContent(
    tab: StatsTab,
    state: StatsUiState,
    contentPadding: PaddingValues,
    onSelectTab: (StatsTab) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    onOpenDetail: (StatsDetailKey) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    when (tab) {
        StatsTab.MONTHLY ->
            MonthlyTab(
                state = state,
                contentPadding = contentPadding,
                onShowMonth = onShowMonth,
                onShowTab = onSelectTab,
                onOpenDetail = onOpenDetail,
                onOpenTransaction = onOpenTransaction,
            )

        StatsTab.DAILY -> DailyTab(state = state, contentPadding = contentPadding, onOpenTransaction = onOpenTransaction)

        StatsTab.CATEGORY ->
            BreakdownTab(state = state, kind = BreakdownKind.CATEGORY, contentPadding = contentPadding, onOpenDetail = onOpenDetail)

        StatsTab.PAYMENT ->
            BreakdownTab(state = state, kind = BreakdownKind.PAYMENT, contentPadding = contentPadding, onOpenDetail = onOpenDetail)
    }
}

/**
 * 떠 있는 메뉴가 화면 전환을 따라 아래에서 떠오르고 가라앉게 한다.
 * 화면 전환(NavDisplay)에 묶여 있어서 되돌아가는 연출과 예측 뒤로(손가락을 따라 내려감)가 저절로 맞는다.
 */
@Composable
private fun Modifier.floatingBarMotion(): Modifier {
    val navScope = LocalNavAnimatedContentScope.current
    return with(navScope) {
        this@floatingBarMotion.animateEnterExit(
            enter =
            slideInVertically(tween(BAR_RISE_MS, easing = FastOutSlowInEasing)) { height -> height } +
                fadeIn(tween(BAR_FADE_IN_MS)),
            // 뒤로 갈 때는 흐려지지 않고 끝까지 내려간다. 화면은 메뉴가 거의 내려간 뒤에 흐려진다(statsTransitions).
            exit = slideOutVertically(tween(BAR_SINK_MS, easing = FastOutLinearInEasing)) { height -> height },
        )
    }
}

// 떠오를 때는 화면이 나타나는 것(200ms)보다 조금 늦게 자리 잡아 아래에서 올라오는 게 보이게 한다.
// 가라앉을 때는 화면이 흐려지기 시작하기(STATS_EXIT_DELAY_MS) 전부터 내려가서, 내려가는 게 보인다.
private const val BAR_RISE_MS = 260
private const val BAR_FADE_IN_MS = 200
internal const val BAR_SINK_MS = 240
