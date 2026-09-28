package com.dong.budget.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.navigation.CategoryManageKey
import com.dong.budget.navigation.DeveloperKey
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.InboxKey
import com.dong.budget.navigation.Navigator
import com.dong.budget.navigation.PatchNotesKey
import com.dong.budget.navigation.SettingsKey
import com.dong.budget.navigation.ShellKey
import com.dong.budget.navigation.StatisticsKey
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.TransactionEditorKey
import com.dong.budget.ui.category.CategoryManageScreen
import com.dong.budget.ui.category.CategoryManageViewModel
import com.dong.budget.ui.devmode.DeveloperScreen
import com.dong.budget.ui.devmode.copyLog
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
import com.dong.budget.ui.stats.StatsScreen
import com.dong.budget.ui.stats.StatsTab
import com.dong.budget.ui.stats.StatsViewModel
import com.dong.budget.ui.stats.detail.StatsDetailScreen
import com.dong.budget.ui.stats.detail.StatsDetailViewModel
import com.dong.budget.ui.theme.Motion
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
        // 가로 화면에서 옆에 붙는 시스템 버튼 줄이나 카메라 구멍 밑으로 상단 바 버튼과 금액이 들어가지 않게 한다.
        // 바탕은 바깥(MainActivity)이 화면 끝까지 칠한다.
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        onBack = navigator::goBack,
        // 따로 정하지 않은 화면(설정·분류 관리·알림 등)의 이동. 라이브러리 기본값은 0.7초 동안 두 화면이 함께 흐려져 느리고 겹쳐 보인다.
        transitionSpec = { pushTransform() },
        popTransitionSpec = { popTransform() },
        // 뒤로 가기 몸짓을 하는 동안에도 손가락을 따라 뒤로 갈 때와 같은 모양으로 움직인다
        predictivePopTransitionSpec = { _ -> popTransform() },
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
                val devModeOn by DevLog.enabled.collectAsStateWithLifecycle()
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
                    devModeOn = devModeOn,
                    onOpenDeveloper = { navigator.go(DeveloperKey) },
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
                    onSelectPendingPayment = viewModel::selectPendingPaymentMethod,
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

            entry<StatisticsKey>(metadata = statsTransitions()) {
                val viewModel: StatsViewModel = viewModel(factory = statsViewModelFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // 하위 탭은 여기서 들고 있다. 회전하거나 상세에 다녀와도 그대로고, 통계를 나갔다 오면 월별부터 다시 시작한다.
                var tab by rememberSaveable { mutableStateOf(StatsTab.MONTHLY) }
                // 들어오는 전환 동안 홈의 '통계' 를 연달아 누르면 두 번째 탭이 같은 높이의 떠 있는 메뉴('일별' 자리)에 떨어진다.
                // 알림 화면처럼 자리 잡은 뒤(RESUMED)에만 탭 선택과 줄 누름을 받는다. 나가는 중이나 등록창이 올라오는 중에 누른 것도 무시한다.
                val settled = rememberSettled()
                StatsScreen(
                    state = state,
                    selectedTab = tab,
                    onSelectTab = { selected -> if (settled()) tab = selected },
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onThisMonth = viewModel::showThisMonth,
                    onShowMonth = { month -> if (settled()) viewModel.showMonth(month) },
                    onOpenDetail = { key -> if (settled()) navigator.go(key) },
                    onOpenTransaction = { id -> if (settled()) navigator.go(TransactionEditorKey(id)) },
                    onBack = navigator::goBack,
                )
            }

            // 상세는 보통 서브플로우 전환이다. 떠 있는 메뉴는 통계 본 화면 것이라 여기에는 없다.
            entry<StatsDetailKey> { key ->
                val viewModel: StatsDetailViewModel = viewModel(factory = statsDetailViewModelFactory(container, key))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // 통계 화면과 같은 이유로 자리 잡은 뒤(RESUMED)에만 거래 줄 누름을 받는다.
                // 들어오는 중에 같은 자리를 한 번 더 누르거나, 나가는 중에 누른 줄로 등록창이 열리지 않게 한다.
                val settled = rememberSettled()
                StatsDetailScreen(
                    state = state,
                    // 나가는 동안(약 0.7초) 이 화면이 위에 남아 ← 를 한 번 더 받는다. 맨 위일 때만 닫아서 통계까지 닫지 않게 한다.
                    onBack = { navigator.closeIfTop(key) },
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onOpenTransaction = { id -> if (settled()) navigator.go(TransactionEditorKey(id)) },
                )
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
                val today by viewModel.today.collectAsStateWithLifecycle()
                // 전환 애니메이션(약 0.7초) 동안에는 거의 투명한 이 화면이 맨 위에서 터치를 받는다. 홈의 종을 연달아 누르면
                // 두 번째 탭이 같은 자리의 '모두 읽음' 에 떨어져 알림창의 묻는 알림까지 치우므로, 자리 잡은 뒤(RESUMED)에만 받는다.
                // 뒤로 나가는 중이나 등록창이 올라오는 중에 누른 줄도 같은 이유로 무시한다.
                val settled = rememberSettled()
                InboxScreen(
                    items = items,
                    today = today,
                    onBack = navigator::goBack,
                    onOpen = { dedupKey ->
                        if (settled()) scope.launch { openCaptured(dedupKey, container.paymentCapture, navigator, context) }
                    },
                    onMarkAllRead = { dedupKeys -> if (settled()) viewModel.markAllRead(dedupKeys) },
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

            entry<DeveloperKey> {
                val enabled by DevLog.enabled.collectAsStateWithLifecycle()
                val entries by DevLog.entries.collectAsStateWithLifecycle()
                DeveloperScreen(
                    enabled = enabled,
                    entries = entries,
                    onEnabledChange = DevLog::setEnabled,
                    onCopy = { copyLog(context, DevLog.export()) },
                    onClear = DevLog::clear,
                    onBack = navigator::goBack,
                )
            }
        },
    )
}

/** 새 화면이 오른쪽에서 밀려 들어오고, 뒤 화면은 왼쪽으로 조금 비켜난다 */
private fun pushTransform(): ContentTransform = slideInHorizontally(Motion.screen()) { width -> width } togetherWith
    slideOutHorizontally(Motion.screen()) { width -> -width / BEHIND_SHIFT_DIVISOR }

/** [pushTransform] 을 거꾸로. 위 화면이 오른쪽으로 빠지고 뒤 화면이 제자리로 돌아온다. */
private fun popTransform(): ContentTransform = slideInHorizontally(Motion.screen()) { width -> -width / BEHIND_SHIFT_DIVISOR } togetherWith
    slideOutHorizontally(Motion.screen()) { width -> width }

/** 새 화면이 덮는 동안 뒤 화면이 비켜나는 거리. 화면 너비의 1/4 */
private const val BEHIND_SHIFT_DIVISOR = 4

/**
 * 등록창은 아래에서 위로 올라오는 모달로 띄운다.
 *
 * 나가는 화면을 그대로 두는 이유: 모달이 위로 덮는 동안 뒤 화면이 사라지면
 * 배경이 잠깐 비어 보인다.
 */
private fun modalTransitions(): Map<String, Any> = NavDisplay.transitionSpec {
    slideInVertically(Motion.screen()) { height -> height } togetherWith
        ExitTransition.KeepUntilTransitionsFinished
} +
    NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith slideOutVertically(Motion.screen()) { height -> height }
    } +
    NavDisplay.predictivePopTransitionSpec { _: Int ->
        EnterTransition.None togetherWith slideOutVertically(Motion.screen()) { height -> height }
    }

/**
 * 통계는 셸을 그대로 둔 채 그 위에서 빠르게 나타나고 사라진다. 두 화면이 함께 흐려지는 순간을 만들지 않는다.
 *
 * 들어갈 때도 나갈 때도 이 설정이 쓰인다. NavDisplay 는 두 화면 중 위에 쌓인 쪽(통계)의 설정을 고른다.
 * 아래 떠 있는 메뉴는 StatsScreen 이 이 전환에 맞춰 따로 떠오르고 가라앉게 한다.
 */
private fun statsTransitions(): Map<String, Any> = NavDisplay.transitionSpec {
    fadeIn(tween(STATS_FADE_MS)) togetherWith ExitTransition.KeepUntilTransitionsFinished
} +
    NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith fadeOut(tween(STATS_FADE_MS, delayMillis = STATS_EXIT_DELAY_MS))
    } +
    NavDisplay.predictivePopTransitionSpec { _: Int ->
        EnterTransition.None togetherWith fadeOut(tween(STATS_FADE_MS, delayMillis = STATS_EXIT_DELAY_MS))
    }

private const val STATS_FADE_MS = 200

// 나갈 때는 떠 있는 메뉴가 먼저 내려가고(BAR_SINK_MS) 그 뒤에 화면이 흐려진다. 같이 흐려지면 내려가는 게 안 보인다.
private const val STATS_EXIT_DELAY_MS = 140

/**
 * 이 화면이 전환을 마치고 자리 잡았는지(RESUMED). NavDisplay 는 전환 동안(약 0.3초) 거의 투명하거나 밀려 나가는 화면에도 터치를 넘겨서,
 * 연달아 누른 두 번째 탭이 막 뜨는(또는 나가는) 화면의 같은 자리에 떨어질 수 있다. 그런 누름은 이게 true 일 때만 받는다.
 */
@Composable
private fun rememberSettled(): () -> Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(lifecycle) { { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) } }
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

private fun statsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { StatsViewModel(container.transactionRepository) }
}

private fun statsDetailViewModelFactory(container: AppContainer, key: StatsDetailKey) = viewModelFactory {
    initializer {
        StatsDetailViewModel(
            transactionRepository = container.transactionRepository,
            categoryRepository = container.categoryRepository,
            paymentMethodRepository = container.paymentMethodRepository,
            key = key,
        )
    }
}
