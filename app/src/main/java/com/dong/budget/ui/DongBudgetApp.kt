package com.dong.budget.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dong.budget.data.AppContainer
import com.dong.budget.navigation.Navigator
import com.dong.budget.navigation.SettingsKey
import com.dong.budget.navigation.ShellKey
import com.dong.budget.navigation.StatisticsKey
import com.dong.budget.navigation.TransactionEditorKey
import com.dong.budget.ui.home.HomeViewModel
import com.dong.budget.ui.shell.HomeShell

/**
 * 앱 전체 네비게이션.
 *
 * 백스택은 [셸, 서브플로우...] 형태다. 탭은 백스택에 들어가지 않는다.
 * 그래서 서브플로우를 쌓으면 탭바가 화면과 함께 밀려나간다.
 */
@Composable
fun DongBudgetApp(container: AppContainer) {
    val backStack = rememberNavBackStack(ShellKey)
    val navigator = remember(backStack) { Navigator(backStack) }

    NavDisplay(
        backStack = backStack,
        onBack = navigator::goBack,
        entryDecorators =
        listOf(
            // 탭 스크롤 위치 같은 화면 상태를 엔트리별로 보관한다
            rememberSaveableStateHolderNavEntryDecorator(),
            // ViewModel 을 엔트리 수명에 묶는다
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider =
        entryProvider {
            entry<ShellKey> {
                val viewModel: HomeViewModel =
                    viewModel(factory = homeViewModelFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                HomeShell(
                    state = state,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onAddTransaction = { navigator.go(TransactionEditorKey()) },
                    onEditTransaction = { id -> navigator.go(TransactionEditorKey(id)) },
                    onOpenStatistics = { navigator.go(StatisticsKey) },
                    onOpenSettings = { navigator.go(SettingsKey) },
                )
            }

            entry<TransactionEditorKey>(metadata = modalTransitions()) { key ->
                PlaceholderSubflow(
                    title = if (key.transactionId == null) "거래 등록" else "거래 수정",
                    onClose = navigator::goBack,
                    isModal = true,
                )
            }

            entry<StatisticsKey> {
                PlaceholderSubflow(title = "통계", onClose = navigator::goBack)
            }

            entry<SettingsKey> {
                PlaceholderSubflow(title = "설정", onClose = navigator::goBack)
            }
        },
    )
}

/**
 * 등록창은 아래에서 위로 올라오는 모달로 띄운다.
 *
 * 나가는 화면을 그대로 두는 이유: 모달이 위로 덮는 동안 뒤 화면이 사라지면
 * 배경이 잠깐 비어 보인다.
 */
private fun modalTransitions(): Map<String, Any> = NavDisplay.transitionSpec {
    slideInVertically(initialOffsetY = { height -> height }) togetherWith
        ExitTransition.KeepUntilTransitionsFinished
} +
    NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith slideOutVertically(targetOffsetY = { height -> height })
    } +
    NavDisplay.predictivePopTransitionSpec { _: Int ->
        EnterTransition.None togetherWith slideOutVertically(targetOffsetY = { height -> height })
    }

private fun homeViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { HomeViewModel(container.transactionRepository) }
}
