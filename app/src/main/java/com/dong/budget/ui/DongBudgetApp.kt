package com.dong.budget.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.lock.PinLock
import com.dong.budget.data.salary.SalaryLockReset
import com.dong.budget.data.salary.salaryKey
import com.dong.budget.data.salary.salaryMonthOf
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.navigation.AdvancedSettingsKey
import com.dong.budget.navigation.AppInfoKey
import com.dong.budget.navigation.AppPinSetupKey
import com.dong.budget.navigation.CardPerformanceDetailKey
import com.dong.budget.navigation.CardPerformanceEditKey
import com.dong.budget.navigation.CategoryManageKey
import com.dong.budget.navigation.DeveloperKey
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.InboxKey
import com.dong.budget.navigation.Navigator
import com.dong.budget.navigation.PatchNotesKey
import com.dong.budget.navigation.SalaryPinSetupKey
import com.dong.budget.navigation.SalarySettingsKey
import com.dong.budget.navigation.SettingsKey
import com.dong.budget.navigation.ShellKey
import com.dong.budget.navigation.StatisticsKey
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.TransactionDetailKey
import com.dong.budget.navigation.TransactionEditorKey
import com.dong.budget.ui.card.CardPerformanceDetailScreen
import com.dong.budget.ui.card.CardPerformanceDetailViewModel
import com.dong.budget.ui.card.CardPerformanceEditScreen
import com.dong.budget.ui.card.CardPerformanceEditViewModel
import com.dong.budget.ui.card.CardPerformanceScreen
import com.dong.budget.ui.card.CardPerformanceViewModel
import com.dong.budget.ui.category.CategoryManageScreen
import com.dong.budget.ui.category.CategoryManageViewModel
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.LocalAppLocked
import com.dong.budget.ui.components.LocalSharedTransitionScope
import com.dong.budget.ui.components.LocalWeekStart
import com.dong.budget.ui.detail.TransactionDetailScreen
import com.dong.budget.ui.detail.TransactionDetailUiState
import com.dong.budget.ui.detail.TransactionDetailViewModel
import com.dong.budget.ui.devmode.DevModeBadge
import com.dong.budget.ui.devmode.DeveloperScreen
import com.dong.budget.ui.devmode.copyLog
import com.dong.budget.ui.editor.ALREADY_REGISTERED_MESSAGE
import com.dong.budget.ui.editor.SALARY_ALREADY_REGISTERED_MESSAGE
import com.dong.budget.ui.editor.TransactionEditorScreen
import com.dong.budget.ui.editor.TransactionEditorViewModel
import com.dong.budget.ui.editor.salaryPrefill
import com.dong.budget.ui.editor.toPrefill
import com.dong.budget.ui.fixed.FixedExpenseScreen
import com.dong.budget.ui.fixed.FixedExpenseViewModel
import com.dong.budget.ui.home.HomeViewModel
import com.dong.budget.ui.inbox.InboxScreen
import com.dong.budget.ui.inbox.InboxViewModel
import com.dong.budget.ui.lock.APP_LOCK_TEXTS
import com.dong.budget.ui.lock.AppLockScreen
import com.dong.budget.ui.lock.DoubleLockQuestion
import com.dong.budget.ui.lock.HideFromRecents
import com.dong.budget.ui.lock.LockControls
import com.dong.budget.ui.lock.PinLockTexts
import com.dong.budget.ui.lock.PinSetupScreen
import com.dong.budget.ui.patchnotes.PatchNotesScreen
import com.dong.budget.ui.patchnotes.PatchNotesViewModel
import com.dong.budget.ui.permission.PermissionGate
import com.dong.budget.ui.salary.SALARY_LOCK_TEXTS
import com.dong.budget.ui.salary.SalaryLockScreen
import com.dong.budget.ui.salary.SalaryScreen
import com.dong.budget.ui.salary.SalarySettingsScreen
import com.dong.budget.ui.salary.SalarySettingsViewModel
import com.dong.budget.ui.salary.SalaryTabGate
import com.dong.budget.ui.salary.SalaryViewModel
import com.dong.budget.ui.settings.AdvancedSettingsScreen
import com.dong.budget.ui.settings.AdvancedSettingsViewModel
import com.dong.budget.ui.settings.AppInfoScreen
import com.dong.budget.ui.settings.AppInfoViewModel
import com.dong.budget.ui.settings.BackupActions
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * 앱 전체 네비게이션.
 *
 * 백스택은 [셸, 서브플로우...] 형태다. 탭은 백스택에 들어가지 않는다.
 * 그래서 서브플로우를 쌓으면 탭바가 화면과 함께 밀려나간다.
 */
@Composable
fun DongBudgetApp(container: AppContainer, openRequest: OpenRequest? = null, onOpened: () -> Unit = {}) {
    val backStack = rememberNavBackStack(ShellKey)
    val navigator = remember(backStack) { Navigator(backStack) }

    // 결제 등록·월급날 알림을 눌러 들어왔으면 그 값으로 채운 등록창을 연다
    val context = LocalContext.current
    LaunchedEffect(openRequest) {
        when (val request = openRequest ?: return@LaunchedEffect) {
            is OpenRequest.Captured -> openCaptured(request.dedupKey, container, navigator, context)
            is OpenRequest.Salary -> salaryMonthOf(request.key)?.let { openSalary(it, container, navigator, context) }
        }
        // 다 연 뒤에 비운다. 먼저 비우면 값이 바뀌면서 이 작업 자체가 취소된다.
        onOpened()
    }

    val scope = rememberCoroutineScope()

    // 앱 잠금(설정 > 잠금). 잠겨 있으면 잠금 화면이 앱 위를 덮는다. 켜 두면 최근 앱 미리보기에도 남기지 않는다.
    val appLock = container.appLock
    val appLockState by appLock.state.collectAsStateWithLifecycle()
    val appUnlocked by appLock.unlocked.collectAsStateWithLifecycle()
    val appLocked = !appLock.isOpen(appLockState, appUnlocked)
    HideFromRecents(active = appLockState.enabled)

    // 월급 잠금. 월급이 보이는 곳(탭·월급 설정·PIN 바꾸기)이 이 값 하나를 같이 쓴다.
    // 앱 잠금이 켜져 있고 2중 잠금을 껐으면 앱 잠금이 대신 지켜 따로 묻지 않는다.
    val salaryLock = container.salaryLock
    val salaryLockState by salaryLock.state.collectAsStateWithLifecycle()
    val salaryUnlocked by salaryLock.unlocked.collectAsStateWithLifecycle()
    val salaryOpen = salaryLock.isOpen(salaryLockState, salaryUnlocked, appLockState.enabled)
    // 월급 PIN 을 잊었다. 월급 설정과 잠금을 함께 지운다(처음 안내는 남긴다).
    val forgetSalaryPin = { scope.launch { container.clearSalary(SalaryLockReset.PIN) } }

    // 앱이 화면에 나올 때마다 알림창에 남은 토스 결제 알림을 다시 살피게 한다.
    // 알림을 막 허용하고 돌아온 경우, 그전에 들어와 묻지 못한 결제를 이때 묻는다. 이미 물어본 결제는 다시 묻지 않는다.
    LifecycleResumeEffect(container) {
        container.paymentCapture.requestRescan()
        onPauseOrDispose {}
    }

    // 앱이 화면에 나올 때마다 새 버전을 확인한다. 10분 안에 다시 열면 건너뛴다(UpdateChecker).
    // '앱을 열 때 새 버전 확인하기' 를 껐으면 확인하지 않는다(설정에서 직접 확인은 된다). 저장소를 다 읽은 값으로 본다.
    LifecycleStartEffect(container) {
        scope.launch {
            if (container.settingsRepository.autoSettings.first()[AutoOption.UPDATE_CHECK]) container.updateChecker.checkIfDue()
        }
        // 자동 백업 주기가 지났으면 다운로드 폴더에 저장한다(설정 > 백업 > 자동 백업)
        container.runScheduledBackup()
        onStopOrDispose {}
    }

    // 자동 기능 스위치의 지금 값. 화면마다 쓰는 스위치만 골라 넘긴다.
    val autoSettings by container.autoSettings.collectAsStateWithLifecycle()
    // 한 주를 시작하는 요일. 달력·통계가 LocalWeekStart 로 읽는다.
    val weekStart by container.weekStart.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // 화면을 오갈 때 두 화면의 같은 요소를 이어 주는 범위(아래 메뉴의 '통계' 가 통계 하위 메뉴 첫 칸으로 옮겨 가는 연출).
        // NavDisplay 의 sharedTransitionScope 로는 넘기지 않는다. 넘기면 화면 전체를 장면 사이 공유 요소로 감싸는데, 이 앱은 장면이 하나라 쓸 데가 없다.
        // 앱이 잠긴 동안에도 아래 화면은 그대로 둔다(보던 화면·쓰던 등록창이 풀린 뒤 남아 있게). 화면 읽기와 확인 창만 막는다.
        // 잠금 화면(AppLockScreen)만 이 범위 밖에 두어, 그 화면의 확인 창·지문 창은 잠긴 동안에도 뜬다.
        SharedTransitionLayout(modifier = Modifier.fillMaxSize().then(if (appLocked) Modifier.clearAndSetSemantics {} else Modifier)) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalWeekStart provides weekStart,
                LocalAppLocked provides appLocked,
            ) {
                // 설정에서 켜야 하는 권한이 꺼져 있으면 앱을 켤 때 안내한다. 잠긴 동안에는 안내창만 숨긴다(풀린 뒤에 묻는다).
                PermissionGate()
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

                            val bannerVersion by container.updateChecker.bannerVersion.collectAsStateWithLifecycle(initialValue = null)
                            // 새 버전 자동 확인을 껐으면 홈에 알림 줄도 띄우지 않는다(앱 정보에서 직접 확인한 결과는 앱 정보와 패치노트에서 보인다)
                            val updateVersion = bannerVersion.takeIf { autoSettings[AutoOption.UPDATE_CHECK] }
                            val hasNewNotice by viewModel.hasNewNotice.collectAsStateWithLifecycle()
                            val devModeOn by DevLog.enabled.collectAsStateWithLifecycle()
                            HomeShell(
                                state = state,
                                updateVersion = updateVersion,
                                hasNewNotice = hasNewNotice,
                                onOpenUpdate = { navigator.go(AppInfoKey) },
                                onDismissUpdate = container.updateChecker::dismissBanner,
                                onSkipUpdate = container.updateChecker::skipLatest,
                                onPreviousMonth = viewModel::showPreviousMonth,
                                onNextMonth = viewModel::showNextMonth,
                                onAddTransaction = { navigator.go(TransactionEditorKey()) },
                                onOpenTransaction = { id -> navigator.go(TransactionDetailKey(id)) },
                                onOpenInbox = { navigator.go(InboxKey) },
                                onOpenCategories = { navigator.go(CategoryManageKey) },
                                onOpenStatistics = { navigator.go(StatisticsKey) },
                                onOpenSettings = { navigator.go(SettingsKey) },
                                onOpenPatchNotes = { navigator.go(PatchNotesKey) },
                                devModeOn = devModeOn,
                                onOpenDeveloper = { navigator.go(DeveloperKey) },
                                // ── 고정지출 탭 ──
                                // 화면 모델은 월급처럼 이 람다 안에서 만들어 셸과 같이 살게 한다(탭을 처음 열 때 생기고, 떠나 있으면 구독을 멈춘다).
                                // '등록하기' 는 지난번 값으로 채운 등록창을, 줄은 그 가게의 가장 최근 거래 상세를 연다.
                                // '고정지출' 분류를 지웠으면 다시 만들러 분류 관리를 연다.
                                // 위에 다른 화면이 올라오는 동안 연달아 누른 탭은 받지 않는다(서브플로우와 같은 규칙).
                                fixedExpenseContent = {
                                    val fixedViewModel: FixedExpenseViewModel = viewModel(factory = fixedExpenseViewModelFactory(container))
                                    val fixedState by fixedViewModel.uiState.collectAsStateWithLifecycle()
                                    val settled = rememberSettled()
                                    FixedExpenseScreen(
                                        state = fixedState,
                                        onPreviousMonth = fixedViewModel::showPreviousMonth,
                                        onNextMonth = fixedViewModel::showNextMonth,
                                        onThisMonth = fixedViewModel::showThisMonth,
                                        onRegister = { prefill -> if (settled()) navigator.go(TransactionEditorKey(prefill = prefill)) },
                                        onOpenTransaction = { id -> if (settled()) navigator.go(TransactionDetailKey(id)) },
                                        onOpenCategories = { if (settled()) navigator.go(CategoryManageKey) },
                                    )
                                },
                                salaryContent = {
                                    // 처음이면 연봉 공개 주의 안내, 잠겨 있으면 PIN·지문, 그 뒤에야 월급을 그린다
                                    SalaryTabGate(
                                        lock = salaryLock,
                                        state = salaryLockState,
                                        open = salaryOpen,
                                        onIntroConfirm = { wantsLock ->
                                            salaryLock.markIntroDone()
                                            if (wantsLock) navigator.go(SalaryPinSetupKey)
                                        },
                                        onForgot = { forgetSalaryPin() },
                                    ) {
                                        HideFromRecents(active = salaryLockState.enabled)
                                        // 월급 탭을 처음 열 때 만들어지고, 셸과 같이 산다. 탭을 떠나 있으면 구독을 멈춘다(WhileSubscribed).
                                        val salaryViewModel: SalaryViewModel = viewModel(factory = salaryViewModelFactory(container))
                                        val salaryState by salaryViewModel.uiState.collectAsStateWithLifecycle()
                                        SalaryScreen(
                                            state = salaryState,
                                            onOpenSettings = { navigator.go(SalarySettingsKey) },
                                            onRegisterSalary = { month ->
                                                scope.launch { openSalary(month, container, navigator, context) }
                                            },
                                            onOpenTransaction = { id -> navigator.go(TransactionDetailKey(id)) },
                                        )
                                    }
                                },
                                // ── 카드실적 ──
                                // 화면 모델은 월급처럼 이 람다 안에서 만들어 셸과 같이 살게 한다. 카드 판은 아래 '카드실적 상세',
                                // '실적 추가' 는 '카드실적 편집' 엔트리로 연다. 셸이 자리 잡은 뒤(RESUMED)에만 받아, 다른 카드 판을 연달아 눌러
                                // 상세가 두 겹 쌓이거나 나가는 상세의 ← 를 연달아 누른 탭이 막 드러난 판에 떨어지지 않게 한다.
                                cardPerformanceContent = {
                                    val cardViewModel: CardPerformanceViewModel =
                                        viewModel(factory = cardPerformanceViewModelFactory(container))
                                    val cardState by cardViewModel.uiState.collectAsStateWithLifecycle()
                                    val cardSettled = rememberSettled()
                                    CardPerformanceScreen(
                                        state = cardState,
                                        onOpenDetail = { id -> if (cardSettled()) navigator.go(CardPerformanceDetailKey(id)) },
                                        onAddPerformance = { id -> if (cardSettled()) navigator.go(CardPerformanceEditKey(id)) },
                                        onOpenCategories = { if (cardSettled()) navigator.go(CategoryManageKey) },
                                    )
                                },
                            )
                        }

                        entry<TransactionEditorKey>(metadata = modalTransitions()) { key ->
                            val viewModel: TransactionEditorViewModel =
                                viewModel(factory = editorViewModelFactory(container, key.transactionId, key.prefill))
                            val state by viewModel.uiState.collectAsStateWithLifecycle()

                            // 저장이 끝나면 화면을 닫는다. 지웠으면 아래 깔린 그 거래의 상세도 같이 치워서, 내려가는 등록창 밑으로 그 아래 화면이 바로 보이게 한다.
                            LaunchedEffect(state.saved) {
                                if (!state.saved) return@LaunchedEffect
                                if (state.deleted) key.transactionId?.let { navigator.remove(TransactionDetailKey(it)) }
                                navigator.closeIfTop(key)
                            }

                            // 수정 창은 거래 상세 위에 올라온다. 상세의 '수정' 과 이 창의 '삭제' 가 같은 자리라서, '수정' 을 연달아 누른 두 번째 탭이
                            // 올라오는 중인 '삭제' 에 떨어지지 않게 자리 잡은 뒤(RESUMED)에만 '삭제' 를 받는다.
                            val settled = rememberSettled()
                            TransactionEditorScreen(
                                state = state,
                                // 내려가는 동안 X 를 한 번 더 받아도 맨 위일 때만 닫아서 아래 화면(거래 상세 등)까지 닫지 않게 한다
                                onClose = { navigator.closeIfTop(key) },
                                acceptsTaps = settled,
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
                                pickUnusedColor = autoSettings[AutoOption.NEW_ITEM_COLOR],
                            )
                        }

                        entry<StatisticsKey>(metadata = statsTransitions()) {
                            val viewModel: StatsViewModel = viewModel(factory = statsViewModelFactory(container))
                            val state by viewModel.uiState.collectAsStateWithLifecycle()
                            // 하위 탭은 여기서 들고 있다. 회전하거나 상세에 다녀와도 그대로고, 통계를 나갔다 오면 첫 칸 '통계' 부터 다시 시작한다.
                            var tab by rememberSaveable { mutableStateOf(StatsTab.OVERVIEW) }
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
                                onOpenTransaction = { id -> if (settled()) navigator.go(TransactionDetailKey(id)) },
                                onBack = navigator::goBack,
                                autoPickDay = autoSettings[AutoOption.STATS_DAY],
                            )
                        }

                        // 상세는 보통 서브플로우 전환이다. 떠 있는 메뉴는 통계 본 화면 것이라 여기에는 없다.
                        entry<StatsDetailKey> { key ->
                            val viewModel: StatsDetailViewModel = viewModel(factory = statsDetailViewModelFactory(container, key))
                            val state by viewModel.uiState.collectAsStateWithLifecycle()
                            // 통계 화면과 같은 이유로 자리 잡은 뒤(RESUMED)에만 거래 줄 누름을 받는다.
                            // 들어오는 중에 같은 자리를 한 번 더 누르거나, 나가는 중에 누른 줄로 거래 상세가 열리지 않게 한다.
                            val settled = rememberSettled()
                            StatsDetailScreen(
                                state = state,
                                // ← 는 자리 잡은 뒤에만 받는다. 전환 중에는 나가는 화면과 들어오는 화면이 모두 같은 자리의 탭을 받아서,
                                // 위의 거래 상세에서 ← 를 연달아 누른 두 번째 탭이 막 드러난 이 화면의 ← 에 떨어져 통계 상세까지 닫힌다.
                                // 맨 위일 때만 닫으므로 이 화면이 나가는 동안 받은 ← 도 통계까지 닫지 않는다.
                                onBack = { if (settled()) navigator.closeIfTop(key) },
                                onPreviousMonth = viewModel::showPreviousMonth,
                                onNextMonth = viewModel::showNextMonth,
                                onOpenTransaction = { id -> if (settled()) navigator.go(TransactionDetailKey(id)) },
                            )
                        }

                        entry<TransactionDetailKey> { key ->
                            val viewModel: TransactionDetailViewModel =
                                viewModel(factory = transactionDetailViewModelFactory(container, key.transactionId))
                            val state by viewModel.uiState.collectAsStateWithLifecycle()
                            // 거래가 없어졌으면 이 화면도 치운다. 등록창에서 지운 경우는 등록창이 먼저 치우므로, 앱을 다시 띄웠는데 이미 지운 거래였을 때 같은 드문 경우다.
                            val gone = state == TransactionDetailUiState.Gone
                            LaunchedEffect(gone) {
                                if (gone) navigator.remove(key)
                            }
                            // 통계 화면과 같은 이유로 자리 잡은 뒤(RESUMED)에만 '수정' 과 같은 곳 내역 줄 누름을 받는다.
                            // 거래 줄을 연달아 누른 두 번째 탭이 막 뜨는 이 화면의 같은 자리에 떨어져 다른 거래가 열리지 않게 한다.
                            val settled = rememberSettled()
                            TransactionDetailScreen(
                                state = state,
                                // 같은 곳 내역에서 상세 위에 상세를 쌓을 수 있다. ← 는 자리 잡은 뒤에만 받는다. 전환 중에는 들어오는 화면도 같은 자리의 탭을 받아서,
                                // 위 상세의 ← 나 수정 창의 X 를 연달아 누른 두 번째 탭이 막 드러난 이 화면의 ← 에 떨어져 이 상세까지 닫힌다.
                                // 맨 위일 때만 닫으므로 이 화면이 나가는 동안 받은 ← 도 아래 상세까지 닫지 않는다.
                                onBack = { if (settled()) navigator.closeIfTop(key) },
                                onEdit = { if (settled()) navigator.go(TransactionEditorKey(key.transactionId)) },
                                onOpenTransaction = { id -> if (settled()) navigator.go(TransactionDetailKey(id)) },
                            )
                        }

                        entry<SettingsKey> {
                            val viewModel: SettingsViewModel =
                                viewModel(factory = settingsViewModelFactory(container))
                            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                            val newerVersion by viewModel.newerVersion.collectAsStateWithLifecycle()
                            val backup by viewModel.backup.collectAsStateWithLifecycle()
                            val lastBackup by container.backupHistory.last.collectAsStateWithLifecycle()
                            val backupSchedule by container.backupSchedule.collectAsStateWithLifecycle()
                            ShowToasts(viewModel.messages)
                            // 설정은 전체의 톱니에서 들어온다. 들어오는 중에 두 번 누른 탭이 아래 줄(고급 설정·앱 정보)에 떨어지지 않게 한다.
                            val settled = rememberSettled()

                            SettingsScreen(
                                themeMode = themeMode,
                                currentVersion = viewModel.currentVersion,
                                onThemeModeChange = viewModel::selectThemeMode,
                                weekStart = weekStart,
                                onWeekStartChange = viewModel::selectWeekStart,
                                onBack = navigator::goBack,
                                newerVersion = newerVersion,
                                onOpenAdvanced = { if (settled()) navigator.go(AdvancedSettingsKey) },
                                onOpenAppInfo = { if (settled()) navigator.go(AppInfoKey) },
                                backup = backup,
                                lastBackup = lastBackup,
                                backupSchedule = backupSchedule,
                                appLock =
                                LockControls(
                                    state = appLockState,
                                    onSetPin = { if (settled()) navigator.go(AppPinSetupKey) },
                                    onDisable = container::disableAppLock,
                                    onBiometricChange = appLock::setBiometric,
                                ),
                                backupActions =
                                BackupActions(
                                    onCopy = viewModel::copyBackup,
                                    onSave = viewModel::saveBackup,
                                    onRestoreFile = viewModel::readRestoreFile,
                                    onRestorePasted = viewModel::readRestorePasted,
                                    onConfirmRestore = viewModel::confirmRestore,
                                    onDismissRestore = viewModel::dismissRestore,
                                    onScheduleChange = viewModel::setBackupSchedule,
                                ),
                            )
                        }

                        entry<AppInfoKey> {
                            val viewModel: AppInfoViewModel = viewModel(factory = appInfoViewModelFactory(container))
                            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
                            val downloadedVersion by viewModel.downloadedVersion.collectAsStateWithLifecycle()
                            AppInfoScreen(
                                currentVersion = viewModel.currentVersion,
                                updateState = updateState,
                                onCheckUpdate = viewModel::checkForUpdate,
                                onDownloadUpdate = viewModel::downloadAndInstall,
                                downloadedVersion = downloadedVersion,
                                onInstallDownloaded = viewModel::installDownloadedManually,
                                releasePageUrl = viewModel.releasePageUrl,
                                onBack = navigator::goBack,
                            )
                        }

                        entry<SalarySettingsKey> { key ->
                            // 월급 설정도 월급이 보이는 곳이라 같이 잠근다(앱을 나갔다 오면 여기서도 다시 묻는다)
                            SalaryGuard(
                                open = salaryOpen,
                                lock = salaryLock,
                                state = salaryLockState,
                                lockedTitle = "월급 설정",
                                onBack = navigator::goBack,
                                onForgot = {
                                    forgetSalaryPin()
                                    navigator.closeIfTop(key)
                                },
                            ) {
                                val viewModel: SalarySettingsViewModel = viewModel(factory = salarySettingsViewModelFactory(container))
                                val settings by viewModel.settings.collectAsStateWithLifecycle()
                                val settled = rememberSettled()
                                SalarySettingsScreen(
                                    settings = settings,
                                    onChange = viewModel::update,
                                    onDigit = viewModel::appendDigit,
                                    onDeleteDigit = viewModel::deleteDigit,
                                    onClearAmount = viewModel::clearAmount,
                                    onBack = navigator::goBack,
                                    onReset = viewModel::reset,
                                    lock =
                                    LockControls(
                                        state = salaryLockState,
                                        onSetPin = { if (settled()) navigator.go(SalaryPinSetupKey) },
                                        onDisable = salaryLock::disable,
                                        onBiometricChange = salaryLock::setBiometric,
                                        showDouble = true,
                                        appLockOn = appLockState.enabled,
                                        onDoubleChange = { on -> salaryLock.setDouble(on, wasOpen = salaryOpen) },
                                    ),
                                )
                            }
                        }

                        entry<AppPinSetupKey> { key ->
                            PinSetupPage(
                                lock = appLock,
                                texts = APP_LOCK_TEXTS,
                                onBack = navigator::goBack,
                                // 월급 잠금이 켜져 있으면 월급을 한 번 더 잠글지 묻는다. '앱 잠금만 쓰기' 를 고르면 월급 PIN 없이 월급이 열리므로
                                // 이번에 월급 PIN 을 넣어 연 사람에게만 묻는다. 묻지 않으면 2중 잠금 그대로다(앱 잠금을 끌 때 처음대로 돌려 둔다).
                                shouldAskDouble = { salaryLockState.enabled && salaryUnlocked },
                                onDoubleChosen = { double -> salaryLock.setDouble(double, wasOpen = salaryOpen) },
                                onClose = { navigator.closeIfTop(key) },
                            )
                        }

                        entry<SalaryPinSetupKey> { key ->
                            // PIN 을 바꾸려면 먼저 풀려 있어야 한다. 바꾸던 중에 앱을 나갔다 오면 다시 묻는다.
                            SalaryGuard(
                                open = salaryOpen,
                                lock = salaryLock,
                                state = salaryLockState,
                                lockedTitle = "PIN 바꾸기",
                                onBack = navigator::goBack,
                                onForgot = {
                                    forgetSalaryPin()
                                    navigator.closeIfTop(key)
                                },
                            ) {
                                PinSetupPage(
                                    lock = salaryLock,
                                    texts = SALARY_LOCK_TEXTS,
                                    onBack = navigator::goBack,
                                    // 앱 잠금이 켜져 있으면 월급을 한 번 더 잠글지 묻는다(방금 월급 PIN 을 정한 사람이다)
                                    shouldAskDouble = { appLockState.enabled },
                                    onDoubleChosen = { double -> salaryLock.setDouble(double, wasOpen = true) },
                                    onClose = { navigator.closeIfTop(key) },
                                )
                            }
                        }

                        entry<AdvancedSettingsKey> {
                            val viewModel: AdvancedSettingsViewModel =
                                viewModel(factory = advancedSettingsViewModelFactory(container))
                            val prompt by viewModel.prompt.collectAsStateWithLifecycle()
                            val busy by viewModel.busy.collectAsStateWithLifecycle()
                            ShowToasts(viewModel.messages)
                            AdvancedSettingsScreen(
                                autoSettings = autoSettings,
                                onAutoChange = viewModel::setAuto,
                                prompt = prompt,
                                busy = busy,
                                onResetSettings = viewModel::askResetSettings,
                                onResetData = viewModel::askResetData,
                                onConfirmReset = viewModel::confirmReset,
                                onDismissReset = viewModel::dismissReset,
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
                                    if (settled()) scope.launch { openCaptured(dedupKey, container, navigator, context) }
                                },
                                onMarkAllRead = { dedupKeys -> if (settled()) viewModel.markAllRead(dedupKeys) },
                                onDeleteRead = viewModel::deleteRead,
                            )
                        }

                        entry<PatchNotesKey> {
                            val viewModel: PatchNotesViewModel = viewModel(factory = patchNotesViewModelFactory(container))
                            val newer by viewModel.newer.collectAsStateWithLifecycle()
                            PatchNotesScreen(
                                currentVersion = BuildConfig.VERSION_NAME,
                                onBack = navigator::goBack,
                                newer = newer,
                                onOpenUpdate = { navigator.go(AppInfoKey) },
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

                        // ── 카드실적 상세·편집 ──
                        // 카드실적 탭의 판이 상세를, 탭의 '실적 추가' 와 상세의 '수정' 이 편집을 연다.
                        entry<CardPerformanceDetailKey> { key ->
                            val viewModel: CardPerformanceDetailViewModel =
                                viewModel(factory = cardPerformanceDetailViewModelFactory(container, key))
                            val state by viewModel.uiState.collectAsStateWithLifecycle()
                            // 결제수단이 없어졌으면 이 화면도 치운다. 앱을 다시 띄웠는데 그사이 지운 카드였을 때 같은 드문 경우다.
                            LaunchedEffect(state.gone) {
                                if (state.gone) navigator.remove(key)
                            }
                            // 통계 상세와 같은 이유로 자리 잡은 뒤(RESUMED)에만 ←·'수정'·거래 줄 누름을 받는다.
                            val settled = rememberSettled()
                            CardPerformanceDetailScreen(
                                state = state,
                                onBack = { if (settled()) navigator.closeIfTop(key) },
                                onEdit = { if (settled()) navigator.go(CardPerformanceEditKey(key.paymentMethodId)) },
                                onPreviousPeriod = viewModel::showPreviousPeriod,
                                onNextPeriod = viewModel::showNextPeriod,
                                onThisPeriod = viewModel::showCurrentPeriod,
                                onOpenTransaction = { id -> if (settled()) navigator.go(TransactionDetailKey(id)) },
                            )
                        }

                        entry<CardPerformanceEditKey> { key ->
                            val viewModel: CardPerformanceEditViewModel =
                                viewModel(factory = cardPerformanceEditViewModelFactory(container, key))
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            val closed by viewModel.closed.collectAsStateWithLifecycle()
                            // 실적을 지웠거나 결제수단이 없어졌으면 닫는다
                            LaunchedEffect(closed) {
                                if (closed) navigator.closeIfTop(key)
                            }
                            // 자리 잡은 뒤(RESUMED)에만 고침을 받는다. 탭의 '실적 추가' 를 연달아 누른 두 번째 탭이 올라오는 중인
                            // 구간 지우기 버튼 등에 떨어져 실적이 바뀌지 않게 한다. 그래서 빈 1구간 키패드도 화면이 자리 잡은 뒤에 연다.
                            val settled = rememberSettled()
                            CardPerformanceEditScreen(
                                state = state,
                                onDigit = { index, pressed -> if (settled()) viewModel.appendDigit(index, pressed) },
                                onDeleteDigit = { index -> if (settled()) viewModel.deleteDigit(index) },
                                onClearAmount = { index -> if (settled()) viewModel.clearAmount(index) },
                                onAddRow = { if (settled()) viewModel.addRow() else null },
                                onRemoveRow = { index -> settled() && viewModel.removeRow(index) },
                                onStartDayChange = { day -> if (settled()) viewModel.setStartDay(day) },
                                onClear = viewModel::clear,
                                onBack = { if (settled()) navigator.closeIfTop(key) },
                            )
                        }
                    },
                )
            }
        }
        if (appLocked) AppLockScreen(lock = appLock, state = appLockState, onForgotVerified = container::disableAppLock)
        // 개발자 모드가 켜져 있으면 어느 화면에서든 오른쪽 아래에 벌레 표시를 띄워 둔다. 보이기만 하고 누름은 아래 화면이 받는다.
        val devModeOn by DevLog.enabled.collectAsStateWithLifecycle()
        DevModeBadge(visible = devModeOn)
    }
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
private suspend fun openCaptured(dedupKey: String, container: AppContainer, navigator: Navigator, context: Context) {
    when (val opened = withContext(Dispatchers.IO) { container.paymentCapture.open(dedupKey) }) {
        // 보관 기간이 지나 채울 내용이 없다. 알림창의 알림은 그때 저절로 사라지므로 드물다.
        PaymentCapture.OpenResult.Expired -> Toast.makeText(context, EXPIRED_CAPTURE_MESSAGE, Toast.LENGTH_SHORT).show()

        PaymentCapture.OpenResult.AlreadyRegistered -> Toast.makeText(context, ALREADY_REGISTERED_MESSAGE, Toast.LENGTH_SHORT).show()

        // 채우기 스위치는 저장소를 다 읽은 값으로 본다. 알림을 눌러 앱을 막 켠 때에도 끈 스위치가 켜진 것처럼 동작하지 않게 한다.
        is PaymentCapture.OpenResult.Editor ->
            navigator.go(TransactionEditorKey(prefill = opened.payment.toPrefill(container.settingsRepository.autoSettings.first())))
    }
}

private const val EXPIRED_CAPTURE_MESSAGE = "${CaptureStore.RETENTION_DAYS}일이 지난 알림이라 열 수 없어요"

private const val SALARY_NOT_SET_MESSAGE = "월급 설정이 없어 채울 금액이 없어요. 월급 탭에서 월급을 정해 주세요"

/**
 * [month] 월급으로 채운 수입 등록창을 연다. 월급날 알림과 월급 탭이 같이 쓴다.
 * 금액은 알림에 담지 않고 지금 설정에서 읽는다. 알림을 띄운 뒤 월급을 고쳤을 수 있다.
 * 이미 등록했으면(알림·탭으로 등록했거나 급여 분류 수입을 직접 적었으면) 등록창 대신 알려 주고 알림을 치운다.
 */
private suspend fun openSalary(month: YearMonth, container: AppContainer, navigator: Navigator, context: Context) {
    val settings = container.salaryRepository.settings.first()
    // 월급 설정을 지운 뒤(데이터 초기화 등) 남은 알림을 누르면 채울 금액이 없다
    if (!settings.isReady || settings.payFor(month) <= 0) {
        container.salaryNotifier.dismiss(salaryKey(month))
        Toast.makeText(context, SALARY_NOT_SET_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    val (from, until) = settings.salaryPeriod(month)
    val registered =
        withContext(Dispatchers.IO) {
            container.transactionRepository.isSalaryRegistered(salaryKey(month), SALARY_CATEGORY_CODE, from, until)
        }
    if (registered) {
        container.salaryNotifier.dismiss(salaryKey(month))
        Toast.makeText(context, SALARY_ALREADY_REGISTERED_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    navigator.go(TransactionEditorKey(prefill = salaryPrefill(settings, month)))
}

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
            // 월급을 등록하면 월급날 알림을, 결제를 등록하면 묻던 결제 알림을 치운다
            onCaptureRegistered = { key ->
                if (salaryMonthOf(key) != null) container.salaryNotifier.dismiss(key) else container.paymentCapture.onRegistered(key)
            },
            autoSettings = container.autoSettings,
        )
    }
}

/** 뒤로 가기가 있는 잠금 화면의 틀(월급 설정이 잠겼을 때, 월급·앱 잠금의 PIN 정하기) */
@Composable
private fun LockPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = title)
            content()
        }
    }
}

/**
 * 월급이 보이는 화면(월급 설정·월급 PIN 바꾸기)의 문. 잠겨 있으면 뒤로 가기가 있는 틀에 월급 잠금 화면을, 풀려 있으면 [content] 를 그린다.
 * 월급이 보이는 동안은 최근 앱 미리보기에 남기지 않는다.
 */
@Composable
private fun SalaryGuard(
    open: Boolean,
    lock: PinLock,
    state: PinLock.State,
    lockedTitle: String,
    onBack: () -> Unit,
    onForgot: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!open) {
        LockPage(title = lockedTitle, onBack = onBack) { SalaryLockScreen(lock = lock, state = state, onForgot = onForgot) }
        return
    }
    HideFromRecents(active = state.enabled)
    content()
}

/**
 * PIN 정하기(월급·앱 잠금). 처음 켤 때와 바꿀 때 같이 쓴다.
 * 처음 켤 때 다른 잠금도 켜져 있으면 PIN 을 정한 직후 월급을 한 번 더 잠글지(2중 잠금) 묻는다.
 * @param shouldAskDouble 처음 켰을 때 2중 잠금을 물을지
 * @param onDoubleChosen 2중 잠금을 쓸지 골랐다
 */
@Composable
private fun PinSetupPage(
    lock: PinLock,
    texts: PinLockTexts,
    onBack: () -> Unit,
    shouldAskDouble: () -> Boolean,
    onDoubleChosen: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    // 처음 켜는지 바꾸는지는 들어올 때 정해진다. PIN 을 정한 뒤 제목이 바뀌지 않게 붙잡아 둔다.
    val changing = remember { lock.state.value.enabled }
    var askDouble by rememberSaveable { mutableStateOf(false) }
    LockPage(title = if (changing) "PIN 바꾸기" else "PIN 정하기", onBack = onBack) {
        PinSetupScreen(
            texts = texts,
            onDone = { pin, biometric ->
                lock.setPin(pin, biometric)
                if (!changing && shouldAskDouble()) askDouble = true else onClose()
            },
        )
    }
    if (askDouble) {
        DoubleLockQuestion { double ->
            onDoubleChosen(double)
            askDouble = false
            onClose()
        }
    }
}

private fun salaryViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { SalaryViewModel(container.salaryRepository, container.transactionRepository) }
}

// ── 고정지출 탭 화면 모델 ──
// 고정지출 탭의 화면 모델 공장을 여기에 둔다(카드실적 것은 파일 맨 끝).
private fun fixedExpenseViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { FixedExpenseViewModel(container.transactionRepository, container.categoryRepository) }
}

private fun salarySettingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { SalarySettingsViewModel(container.salaryRepository) { container.clearSalary(SalaryLockReset.KEEP) } }
}

private fun transactionDetailViewModelFactory(container: AppContainer, transactionId: Long) = viewModelFactory {
    initializer { TransactionDetailViewModel(container.transactionRepository, transactionId) }
}

private fun settingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            updateChecker = container.updateChecker,
            backupRepository = container.backupRepository,
            backupStorage = container.backupStorage,
            backupExporter = container.backupExporter,
            salaryRepository = container.salaryRepository,
            appThemeMode = container.themeMode,
        )
    }
}

private fun appInfoViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { AppInfoViewModel(container.apkInstaller, container.updateChecker) }
}

private fun advancedSettingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        AdvancedSettingsViewModel(
            container.settingsRepository,
            container.backupRepository,
            container.paymentCapture,
            container.backupExporter,
            { container.clearSalary(SalaryLockReset.ALL) },
        )
    }
}

/** 화면 모델이 한 번 알리는 글(복사·저장·복원·초기화 결과)을 토스트로 띄운다 */
@Composable
private fun ShowToasts(messages: Flow<String>) {
    val context = LocalContext.current
    LaunchedEffect(messages) { messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
}

private fun inboxViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { InboxViewModel(container.transactionRepository, container.paymentCapture) }
}

private fun patchNotesViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        PatchNotesViewModel(container.updateChecker) { container.settingsRepository.autoSettings.first()[AutoOption.UPDATE_CHECK] }
    }
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

// ── 카드실적 화면 모델 ──
// 카드실적 탭·상세·편집의 화면 모델 공장을 여기에 둔다.

private fun cardPerformanceViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { CardPerformanceViewModel(container.paymentMethodRepository, container.transactionRepository) }
}

private fun cardPerformanceDetailViewModelFactory(container: AppContainer, key: CardPerformanceDetailKey) = viewModelFactory {
    initializer { CardPerformanceDetailViewModel(container.paymentMethodRepository, container.transactionRepository, key) }
}

private fun cardPerformanceEditViewModelFactory(container: AppContainer, key: CardPerformanceEditKey) = viewModelFactory {
    initializer { CardPerformanceEditViewModel(container.paymentMethodRepository, key.paymentMethodId) }
}
