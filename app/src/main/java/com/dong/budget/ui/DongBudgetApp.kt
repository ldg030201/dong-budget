package com.dong.budget.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dong.budget.BuildConfig
import com.dong.budget.data.AppContainer
import com.dong.budget.navigation.CategoryManageKey
import com.dong.budget.navigation.Navigator
import com.dong.budget.navigation.PatchNotesKey
import com.dong.budget.navigation.SettingsKey
import com.dong.budget.navigation.ShellKey
import com.dong.budget.navigation.StatisticsKey
import com.dong.budget.navigation.TransactionEditorKey
import com.dong.budget.ui.category.CategoryManageScreen
import com.dong.budget.ui.category.CategoryManageViewModel
import com.dong.budget.ui.editor.TransactionEditorScreen
import com.dong.budget.ui.editor.TransactionEditorViewModel
import com.dong.budget.ui.home.HomeViewModel
import com.dong.budget.ui.patchnotes.PatchNotesScreen
import com.dong.budget.ui.settings.SettingsScreen
import com.dong.budget.ui.settings.SettingsViewModel
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
                    onOpenCategories = { navigator.go(CategoryManageKey) },
                    onOpenStatistics = { navigator.go(StatisticsKey) },
                    onOpenSettings = { navigator.go(SettingsKey) },
                    onOpenPatchNotes = { navigator.go(PatchNotesKey) },
                )
            }

            entry<TransactionEditorKey>(metadata = modalTransitions()) { key ->
                val viewModel: TransactionEditorViewModel =
                    viewModel(factory = editorViewModelFactory(container, key.transactionId))
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // 저장이 끝나면 화면을 닫는다.
                LaunchedEffect(state.saved) {
                    if (state.saved) navigator.closeIfTop(key)
                }

                TransactionEditorScreen(
                    state = state,
                    onClose = navigator::goBack,
                    onSelectType = viewModel::selectType,
                    onDigit = viewModel::appendDigit,
                    onDeleteDigit = viewModel::deleteDigit,
                    onClearAmount = viewModel::clearAmount,
                    onSelectCategory = viewModel::selectCategory,
                    onSelectPaymentMethod = viewModel::selectPaymentMethod,
                    onOpenAdd = viewModel::openAdd,
                    onDismissAdd = viewModel::dismissAdd,
                    onSubmitAdd = viewModel::submitAdd,
                    onMerchantChange = viewModel::updateMerchant,
                    onMemoChange = viewModel::updateMemo,
                    onDateChange = viewModel::updateDate,
                    onTimeChange = viewModel::updateTime,
                    onSave = viewModel::save,
                    onJumpHandled = viewModel::onJumpHandled,
                    onDeleteTransaction = viewModel::delete,
                )
            }

            entry<CategoryManageKey> {
                val viewModel: CategoryManageViewModel =
                    viewModel(factory = categoryManageViewModelFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CategoryManageScreen(
                    state = state,
                    onTabChange = viewModel::selectTab,
                    onOpenAdd = viewModel::openAdd,
                    onDismissAdd = viewModel::dismissAdd,
                    onSubmitAdd = viewModel::add,
                    onRequestDelete = viewModel::requestDelete,
                    onCancelDelete = viewModel::cancelDelete,
                    onConfirmDelete = viewModel::confirmDelete,
                    onBack = navigator::goBack,
                )
            }

            entry<StatisticsKey> {
                PlaceholderSubflow(title = "통계", onClose = navigator::goBack)
            }

            entry<SettingsKey> {
                val viewModel: SettingsViewModel =
                    viewModel(factory = settingsViewModelFactory(container))
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                val updateState by viewModel.updateState.collectAsStateWithLifecycle()

                SettingsScreen(
                    themeMode = themeMode,
                    currentVersion = viewModel.currentVersion,
                    updateState = updateState,
                    onThemeModeChange = viewModel::selectThemeMode,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadAndInstall,
                    onBack = navigator::goBack,
                )
            }

            entry<PatchNotesKey> {
                PatchNotesScreen(currentVersion = BuildConfig.VERSION_NAME, onBack = navigator::goBack)
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

private fun editorViewModelFactory(container: AppContainer, transactionId: Long?) = viewModelFactory {
    initializer {
        TransactionEditorViewModel(
            repository = container.transactionRepository,
            categoryRepository = container.categoryRepository,
            paymentMethodRepository = container.paymentMethodRepository,
            transactionId = transactionId,
        )
    }
}

private fun settingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            updateRepository = container.updateRepository,
            apkInstaller = container.apkInstaller,
        )
    }
}

private fun categoryManageViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { CategoryManageViewModel(container.categoryRepository, container.paymentMethodRepository) }
}
