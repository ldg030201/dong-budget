package com.dong.budget.ui

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.dong.budget.data.capture.CapturedPayment
import com.dong.budget.navigation.CategoryManageKey
import com.dong.budget.navigation.EditorPrefill
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
import com.dong.budget.ui.patchnotes.PatchNotesViewModel
import com.dong.budget.ui.permission.PermissionGate
import com.dong.budget.ui.settings.SettingsScreen
import com.dong.budget.ui.settings.SettingsViewModel
import com.dong.budget.ui.shell.HomeShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 앱 전체 네비게이션.
 *
 * 백스택은 [셸, 서브플로우...] 형태다. 탭은 백스택에 들어가지 않는다.
 * 그래서 서브플로우를 쌓으면 탭바가 화면과 함께 밀려나간다.
 */
@Composable
fun DongBudgetApp(container: AppContainer, capturedToOpen: String? = null, onCapturedOpened: () -> Unit = {}) {
    val backStack = rememberNavBackStack(ShellKey)
    val navigator = remember(backStack) { Navigator(backStack) }

    // 결제 등록 알림을 눌러 들어왔으면 그 결제로 채운 등록창을 연다
    val context = LocalContext.current
    LaunchedEffect(capturedToOpen) {
        val dedupKey = capturedToOpen ?: return@LaunchedEffect
        val capture = container.paymentCapture
        // 기록이 지난 알림이면 채울 내용이 없다. 그때는 그냥 앱만 열린다.
        val payment = withContext(Dispatchers.IO) { capture.find(dedupKey) }
        when {
            payment == null -> Unit

            // 이미 등록한 결제면 등록창을 열지 않고 알림만 치운다
            capture.alreadyRegistered(dedupKey) -> {
                capture.onRegistered(dedupKey)
                Toast.makeText(context, "이미 가계부에 등록한 결제예요", Toast.LENGTH_SHORT).show()
            }

            else -> navigator.go(TransactionEditorKey(prefill = payment.toPrefill()))
        }
        // 다 연 뒤에 비운다. 먼저 비우면 값이 바뀌면서 이 작업 자체가 취소된다.
        onCapturedOpened()
    }

    // 설정에서 켜야 하는 권한이 꺼져 있으면 앱을 켤 때 안내한다
    PermissionGate()

    // 앱이 화면에 나올 때마다 알림창에 남은 토스 결제 알림을 다시 살피게 한다.
    // 알림을 막 허용하고 돌아온 경우, 그전에 들어와 묻지 못한 결제를 이때 묻는다. 이미 물어본 결제는 다시 묻지 않는다.
    LifecycleResumeEffect(container) {
        container.paymentCapture.requestRescan()
        onPauseOrDispose {}
    }

    // 앱이 화면에 나올 때마다 새 버전을 확인한다. 10분 안에 다시 열면 건너뛴다(UpdateChecker).
    val scope = rememberCoroutineScope()
    LifecycleStartEffect(container) {
        scope.launch { container.updateChecker.checkIfDue() }
        onStopOrDispose {}
    }

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

                val updateVersion by container.updateChecker.bannerVersion.collectAsStateWithLifecycle(initialValue = null)
                HomeShell(
                    state = state,
                    updateVersion = updateVersion,
                    onOpenUpdate = { navigator.go(SettingsKey) },
                    onDismissUpdate = container.updateChecker::dismissBanner,
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
                    viewModel(factory = editorViewModelFactory(container, key.transactionId, key.prefill))
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // 저장이 끝나면 화면을 닫는다. 결제 알림에서 온 것이면 묻던 알림도 치운다.
                LaunchedEffect(state.saved) {
                    if (state.saved) {
                        key.prefill?.let { container.paymentCapture.onRegistered(it.dedupKey) }
                        navigator.closeIfTop(key)
                    }
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
                    onAddHandled = viewModel::onAddHandled,
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
                    onReorder = viewModel::reorder,
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
                val downloadedVersion by viewModel.downloadedVersion.collectAsStateWithLifecycle()

                SettingsScreen(
                    themeMode = themeMode,
                    currentVersion = viewModel.currentVersion,
                    updateState = updateState,
                    onThemeModeChange = viewModel::selectThemeMode,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadAndInstall,
                    downloadedVersion = downloadedVersion,
                    onInstallDownloaded = viewModel::installDownloadedManually,
                    releasePageUrl = viewModel.releasePageUrl,
                    onBack = navigator::goBack,
                )
            }

            entry<PatchNotesKey> {
                val viewModel: PatchNotesViewModel = viewModel(factory = patchNotesViewModelFactory(container))
                val newer by viewModel.newer.collectAsStateWithLifecycle()
                PatchNotesScreen(
                    currentVersion = BuildConfig.VERSION_NAME,
                    onBack = navigator::goBack,
                    newer = newer,
                    onOpenUpdate = { navigator.go(SettingsKey) },
                )
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

/** 알림에서 읽은 결제를 등록창에 채울 값으로 바꾼다. 할부는 메모로 남긴다. */
private fun CapturedPayment.toPrefill() = EditorPrefill(
    amount = amount,
    merchant = merchant,
    paymentName = paymentName,
    memo = installmentMonths?.let { "${it}개월 할부" },
    occurredAtMillis = occurredAtMillis,
    dedupKey = dedupKey,
)

private fun homeViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { HomeViewModel(container.transactionRepository) }
}

private fun editorViewModelFactory(container: AppContainer, transactionId: Long?, prefill: EditorPrefill?) = viewModelFactory {
    initializer {
        TransactionEditorViewModel(
            repository = container.transactionRepository,
            categoryRepository = container.categoryRepository,
            paymentMethodRepository = container.paymentMethodRepository,
            transactionId = transactionId,
            prefill = prefill,
        )
    }
}

private fun settingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            updateRepository = container.updateRepository,
            apkInstaller = container.apkInstaller,
            updateChecker = container.updateChecker,
        )
    }
}

private fun patchNotesViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { PatchNotesViewModel(container.updateRepository, container.updateChecker) }
}

private fun categoryManageViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { CategoryManageViewModel(container.categoryRepository, container.paymentMethodRepository) }
}
