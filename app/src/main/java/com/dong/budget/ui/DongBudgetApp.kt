package com.dong.budget.ui

import android.content.Context
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
import com.dong.budget.data.capture.CaptureStore
import com.dong.budget.data.capture.CapturedPayment
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.navigation.CategoryManageKey
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.InboxKey
import com.dong.budget.navigation.Navigator
import com.dong.budget.navigation.PatchNotesKey
import com.dong.budget.navigation.SettingsKey
import com.dong.budget.navigation.ShellKey
import com.dong.budget.navigation.StatisticsKey
import com.dong.budget.navigation.TransactionEditorKey
import com.dong.budget.ui.category.CategoryManageScreen
import com.dong.budget.ui.category.CategoryManageViewModel
import com.dong.budget.ui.editor.ALREADY_REGISTERED_MESSAGE
import com.dong.budget.ui.editor.TransactionEditorScreen
import com.dong.budget.ui.editor.TransactionEditorViewModel
import com.dong.budget.ui.home.HomeViewModel
import com.dong.budget.ui.inbox.InboxScreen
import com.dong.budget.ui.inbox.InboxViewModel
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
        openCaptured(dedupKey, container.paymentCapture, navigator, context)
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
                val hasNewNotice by viewModel.hasNewNotice.collectAsStateWithLifecycle()
                HomeShell(
                    state = state,
                    updateVersion = updateVersion,
                    hasNewNotice = hasNewNotice,
                    onOpenUpdate = { navigator.go(SettingsKey) },
                    onDismissUpdate = container.updateChecker::dismissBanner,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onAddTransaction = { navigator.go(TransactionEditorKey()) },
                    onEditTransaction = { id -> navigator.go(TransactionEditorKey(id)) },
                    onOpenInbox = { navigator.go(InboxKey) },
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

                // 저장이 끝나면 화면을 닫는다
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
                    effects = viewModel.effects,
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

            entry<InboxKey> {
                val viewModel: InboxViewModel = viewModel(factory = inboxViewModelFactory(container))
                val items by viewModel.items.collectAsStateWithLifecycle()
                InboxScreen(
                    items = items,
                    onBack = navigator::goBack,
                    onOpen = { dedupKey -> scope.launch { openCaptured(dedupKey, container.paymentCapture, navigator, context) } },
                    onMarkAllRead = viewModel::markAllRead,
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

/**
 * 결제 등록 알림을 연다. 알림창의 알림을 눌렀을 때와 알림 화면에서 눌렀을 때 똑같이 동작한다.
 * 이미 등록한 결제면 등록창을 열지 않고 알려준다(PaymentCapture.open).
 */
private suspend fun openCaptured(dedupKey: String, capture: PaymentCapture, navigator: Navigator, context: Context) {
    when (val opened = withContext(Dispatchers.IO) { capture.open(dedupKey) }) {
        // 보관 기간이 지나 채울 내용이 없다. 알림창의 알림은 그때 저절로 사라지므로 드물다.
        PaymentCapture.OpenResult.Expired -> Toast.makeText(context, EXPIRED_CAPTURE_MESSAGE, Toast.LENGTH_SHORT).show()

        PaymentCapture.OpenResult.AlreadyRegistered -> Toast.makeText(context, ALREADY_REGISTERED_MESSAGE, Toast.LENGTH_SHORT).show()

        is PaymentCapture.OpenResult.Editor -> navigator.go(TransactionEditorKey(prefill = opened.payment.toPrefill()))
    }
}

private const val EXPIRED_CAPTURE_MESSAGE = "${CaptureStore.RETENTION_DAYS}일이 지난 알림이라 열 수 없어요"

/** 알림에서 읽은 결제를 등록창에 채울 값으로 바꾼다. 할부는 메모로 남긴다. */
private fun CapturedPayment.toPrefill() = EditorPrefill(
    amount = amount,
    merchant = merchant,
    paymentName = paymentName,
    memo = installmentLabel,
    occurredAtMillis = occurredAtMillis,
    dedupKey = dedupKey,
)

private fun homeViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { HomeViewModel(container.transactionRepository, container.paymentCapture) }
}

private fun editorViewModelFactory(container: AppContainer, transactionId: Long?, prefill: EditorPrefill?) = viewModelFactory {
    initializer {
        TransactionEditorViewModel(
            repository = container.transactionRepository,
            categoryRepository = container.categoryRepository,
            paymentMethodRepository = container.paymentMethodRepository,
            transactionId = transactionId,
            prefill = prefill,
            onCaptureRegistered = container.paymentCapture::onRegistered,
        )
    }
}

private fun settingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            apkInstaller = container.apkInstaller,
            updateChecker = container.updateChecker,
        )
    }
}

private fun inboxViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { InboxViewModel(container.transactionRepository, container.paymentCapture) }
}

private fun patchNotesViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { PatchNotesViewModel(container.updateChecker) }
}

private fun categoryManageViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { CategoryManageViewModel(container.categoryRepository, container.paymentMethodRepository) }
}
