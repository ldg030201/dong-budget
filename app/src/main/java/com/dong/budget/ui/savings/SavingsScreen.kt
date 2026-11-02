package com.dong.budget.ui.savings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.dong.budget.R
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.FloatingSubBar
import com.dong.budget.ui.components.FloatingSubBarScrim
import com.dong.budget.ui.components.SubBarTab
import com.dong.budget.ui.components.TabEmptyState
import com.dong.budget.ui.components.floatingBarClearance
import com.dong.budget.ui.components.floatingBarMotion
import com.dong.budget.ui.theme.Motion

/**
 * 저축. 아래 메뉴(또는 전체)의 '저축' 으로 들어온다. '저축' 분류로 적은 돈은 쓴 돈에서 빠지고 여기에 모인다.
 * 아래 떠 있는 메뉴(뒤로 · 내역 · 통계 · 고정저축) 위에 고른 탭을 보여 준다.
 * - 내역: 지금까지 · 이번 달 모은 돈과 저축 거래를 달마다(내역 화면과 같은 목록)
 * - 통계: 고른 달 모은 돈, 지난달 대비, 최근 6개월, 올해 · 그 달까지 모은 돈, 상품별, 결제수단별
 * - 고정저축: [fixedContent] 가 그린다(고정지출 화면을 고정저축 종류로, DongBudgetApp). 저축 거래나 '저축' 분류가 없어도 그 탭은 제 화면을 그린다.
 * 달 줄은 화면 공통이 아니라 탭마다 둔다. 내역에는 달 줄이 없고, 통계와 고정저축은 저마다 달 줄이 있다.
 *
 * 통계와 같은 틀이다. 탭 이동은 뒤로 기록에 쌓지 않고, 뒤로(메뉴의 ←, 시스템 뒤로)는 늘 저축을 나간다. 탭마다 스크롤 위치를 따로 보관한다.
 * 화면은 셸 위에서 빠르게 나타나고 사라지고(DongBudgetApp 의 subflowTransitions), 떠 있는 메뉴는 따로 아래에서 떠오르고 가라앉는다
 * ([floatingBarMotion]). 그래서 NavDisplay 안에서만 그릴 수 있다.
 * 아래 메뉴의 '저축' 칸을 첫 칸으로 옮겨 오는 연출(통계의 STATS_ENTRY_SHARED_KEY)은 하지 않는다. 첫 칸 이름이 '내역' 이라 글자가 겹쳐 바뀐다.
 *
 * 상단 인셋은 이 화면이 직접 처리하고, 아래 인셋은 떠 있는 메뉴와 탭 목록의 아래 여백([floatingBarClearance])이 처리한다.
 *
 * @param selectedTab 지금 탭. 바깥(DongBudgetApp)이 들고 있어야 화면 전환 중에 눌린 탭을 거를 수 있다.
 * @param onThisMonth 통계 탭에서 지난 달을 보고 있을 때 달 줄 오른쪽에 나오는 '이번 달'
 * @param onOpenTransaction 거래 줄을 누르면 그 거래의 상세
 * @param onOpenCategories '저축' 분류를 지웠을 때 다시 만들러 분류 관리를 연다
 * @param fixedContent 고정저축 탭의 내용. 받은 여백(아래 떠 있는 메뉴에 가리지 않을 만큼)을 목록에 둔다. 상단 인셋은 이 화면이 이미 뺐다.
 */
@Composable
fun SavingsScreen(
    state: SavingsUiState,
    selectedTab: SavingsTab,
    onSelectTab: (SavingsTab) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
    fixedContent: @Composable (PaddingValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 탭을 오갈 때 탭마다 스크롤 위치 같은 화면 상태를 따로 보관한다
    val tabStates = rememberSaveableStateHolder()
    val contentPadding = PaddingValues(bottom = floatingBarClearance())
    // Surface 는 터치를 받는다. 셸 위로 나타나고 사라지는 동안 저축의 빈 곳을 누른 탭이 밑의 셸로 새지 않는다.
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = "저축" },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 가로 화면의 좌우 인셋은 앱 전체(DongBudgetApp)에서 한 번에 뺀다
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                val page =
                    when {
                        // 고정저축은 따로 읽어서 저축 거래나 분류와 상관없이 그 탭을 그린다
                        selectedTab == SavingsTab.FIXED -> SavingsPage.Tab(selectedTab)

                        // 첫 계산이 끝나기 전에는 메뉴만 둔다. 빈 상태 안내가 잠깐 보였다 사라지지 않게 한다.
                        !state.loaded -> null

                        !state.hasCategory -> SavingsPage.NoCategory

                        !state.hasAny -> SavingsPage.Empty

                        else -> SavingsPage.Tab(selectedTab)
                    }
                // 탭을 바꾸거나 첫 계산이 끝나면 내용이 겹쳐 바뀐다
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick()) },
                    label = "savingsPage",
                ) { shown ->
                    // 바뀌는 동안 두 탭이 함께 있다. 화면 읽기가 새 탭 이름만 알리도록 들어오는 쪽에만 이름을 붙인다.
                    val incoming = transition.targetState == EnterExitState.Visible
                    when (shown) {
                        null -> Box(Modifier.fillMaxSize())

                        SavingsPage.NoCategory ->
                            SavingsEmpty(title = NO_CATEGORY_TITLE, body = NO_CATEGORY_BODY, contentPadding = contentPadding) {
                                BudgetPrimaryButton(text = OPEN_CATEGORIES_TEXT, onClick = onOpenCategories)
                            }

                        SavingsPage.Empty -> SavingsEmpty(title = EMPTY_TITLE, body = EMPTY_BODY, contentPadding = contentPadding)

                        is SavingsPage.Tab ->
                            tabStates.SaveableStateProvider(shown.tab.name) {
                                Box(modifier = Modifier.fillMaxSize().semantics { if (incoming) paneTitle = shown.tab.paneTitle }) {
                                    when (shown.tab) {
                                        SavingsTab.HISTORY ->
                                            SavingsHistoryTab(
                                                history = state.history,
                                                today = state.today,
                                                contentPadding = contentPadding,
                                                onOpenTransaction = onOpenTransaction,
                                            )

                                        SavingsTab.STATS ->
                                            SavingsStatsTab(
                                                state = state,
                                                contentPadding = contentPadding,
                                                onPreviousMonth = onPreviousMonth,
                                                onNextMonth = onNextMonth,
                                                onThisMonth = onThisMonth,
                                            )

                                        SavingsTab.FIXED -> fixedContent(contentPadding)
                                    }
                                }
                            }
                    }
                }
            }
            FloatingSubBarScrim(modifier = Modifier.align(Alignment.BottomCenter))
            FloatingSubBar(
                tabs = TABS,
                selectedIndex = selectedTab.ordinal,
                onSelect = { index -> onSelectTab(SavingsTab.entries[index]) },
                onBack = onBack,
                modifier = Modifier.align(Alignment.BottomCenter).floatingBarMotion(),
            )
        }
    }
}

private val TABS = SavingsTab.entries.map { tab -> SubBarTab(label = tab.label, icon = tab.icon) }

/** 저축 화면 가운데에 보이는 것. 첫 계산 전(null)·'저축' 분류 없음·저축 없음·탭 하나 */
private sealed interface SavingsPage {
    data object NoCategory : SavingsPage

    data object Empty : SavingsPage

    data class Tab(val tab: SavingsTab) : SavingsPage
}

/** 내역 · 통계 탭의 빈 화면. 아래 떠 있는 메뉴에 가운데가 가리지 않게 그만큼 띄운다. */
@Composable
private fun SavingsEmpty(title: String, body: String, contentPadding: PaddingValues, action: (@Composable () -> Unit)? = null) {
    TabEmptyState(
        iconRes = R.drawable.ic_sym_savings,
        color = SAVINGS_COLOR,
        title = title,
        body = body,
        modifier = Modifier.padding(contentPadding),
        action = action,
    )
}

/** 저축 화면의 색(분류 색 팔레트 이름). 전체 목록의 '저축' 아이콘 바탕색과 같다. 빈 화면 뱃지와 최근 6개월의 고른 달 막대에 쓴다. */
internal const val SAVINGS_COLOR = "amber"
