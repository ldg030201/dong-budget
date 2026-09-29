package com.dong.budget.data

import android.content.Context
import com.dong.budget.data.backup.BackupRepository
import com.dong.budget.data.backup.BackupStorage
import com.dong.budget.data.capture.CaptureNotifier
import com.dong.budget.data.capture.CaptureStore
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.salary.SalaryLock
import com.dong.budget.data.salary.SalaryLockReset
import com.dong.budget.data.salary.SalaryNotifier
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.salary.SalaryScheduler
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.UpdateChecker
import com.dong.budget.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

/**
 * 수동 의존성 컨테이너.
 *
 * Hilt 를 쓰지 않는다. 이 규모에서 얻는 건 없고 빌드 구성에 움직이는 부품만 늘어난다.
 * 의존성이 늘어 감당이 안 되는 시점이 오면 그때 도입한다.
 */
class AppContainer(context: Context) {
    private companion object {
        /** 월급 설정을 고친 뒤 월급날 알림을 다시 맞추기까지 기다리는 시간 */
        const val SALARY_SETTLE_MS = 30_000L
    }

    private val database by lazy { BudgetDatabase.build(context) }

    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }

    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }

    val paymentMethodRepository by lazy { PaymentMethodRepository(database.paymentMethodDao()) }

    val settingsRepository by lazy { SettingsRepository(context) }

    val backupRepository by lazy { BackupRepository(database) }

    val salaryRepository by lazy { SalaryRepository(context) }

    val salaryNotifier by lazy { SalaryNotifier(context) }

    /** 월급 탭 잠금(PIN·지문)과 처음 안내를 확인했는지 */
    val salaryLock by lazy { SalaryLock(context.getSharedPreferences(SalaryLock.PREFS_NAME, Context.MODE_PRIVATE)) }

    val salaryScheduler by lazy {
        SalaryScheduler(context, context.getSharedPreferences(SalaryScheduler.PREFS_NAME, Context.MODE_PRIVATE))
    }

    val backupStorage by lazy { BackupStorage(context) }

    /** 앱이 살아 있는 동안 도는 일(설정 따라가기 등). 화면이나 서비스보다 오래 산다. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 자동 기능 스위치의 지금 값. 화면이 처음 그려질 때(등록창의 키패드 등) 기다리지 않고 바로 읽으려고 앱이 켜질 때부터 따라간다.
     * 저장소를 다 읽기 전에는 모두 켜진 값이다(처음 설치와 같다). 기다려도 되는 곳은 [SettingsRepository.autoSettings] 를 읽는다.
     */
    val autoSettings: StateFlow<AutoSettings> by lazy {
        settingsRepository.autoSettings.stateIn(appScope, SharingStarted.Eagerly, AutoSettings())
    }

    /**
     * 월급 설정을 따라가며 월급날 알림을 맞춘다. 앱이 켜질 때(강제 종료로 알람이 사라진 뒤 포함)와 설정을 바꿀 때마다 다시 맞춘다.
     * 복원·데이터 초기화로 설정이 바뀌어도 여기서 따라간다.
     */
    @OptIn(FlowPreview::class)
    fun startSalaryAlarm() {
        appScope.launch {
            salaryRepository.settings
                .withIndex()
                // 켤 때는 바로 맞춘다. 월급 설정 화면은 숫자 하나 누를 때마다 저장하므로, 고치는 동안에는 손을 멈출 때까지 기다린다.
                // 기다리지 않으면 월급날 오후에 설정하다 잠깐 멈춘 사이, 반쯤 친 금액으로 '월급 들어왔나요?' 가 뜨고 그달을 알린 달로 적는다.
                .debounce { (index, _) -> if (index == 0) 0L else SALARY_SETTLE_MS }
                .collect { (_, settings) -> salaryScheduler.schedule(settings) }
        }
    }

    /**
     * 월급 설정을 지운다. 월급날 알림 기록과 떠 있는 월급날 알림도 치운다.
     * @param lock 잠금을 어떻게 할지. 월급 설정 초기화는 그대로 두고, PIN 을 잊었을 때는 PIN 만, 데이터 초기화는 처음 안내까지 지운다.
     */
    suspend fun clearSalary(lock: SalaryLockReset = SalaryLockReset.KEEP) {
        salaryRepository.clear()
        salaryScheduler.reset()
        salaryNotifier.dismissAll()
        when (lock) {
            SalaryLockReset.KEEP -> Unit
            SalaryLockReset.PIN -> salaryLock.reset(keepIntro = true)
            SalaryLockReset.ALL -> salaryLock.reset(keepIntro = false)
        }
    }

    /**
     * 화면 테마의 지금 값. 설정을 열 때 한 줄 토글이 '기기 설정' 칸에서 시작했다가 저장된 칸으로 미끄러져 가지 않게,
     * 앱이 켜질 때부터 따라가 두고 처음 그릴 때 이 값을 쓴다.
     */
    val themeMode: StateFlow<ThemeMode> by lazy {
        settingsRepository.themeMode.stateIn(appScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    }

    val updateRepository by lazy { UpdateRepository() }

    val apkInstaller by lazy { ApkInstaller(context) }

    val updateChecker by lazy {
        UpdateChecker(
            fetch = updateRepository::newerReleases,
            prefs = context.getSharedPreferences(UpdateChecker.PREFS_NAME, Context.MODE_PRIVATE),
        )
    }

    val captureNotifier by lazy { CaptureNotifier(context) }

    /** 토스 결제 알림을 읽어 등록할지 묻는다 */
    val paymentCapture by lazy {
        PaymentCapture(
            store = CaptureStore(context.getSharedPreferences(CaptureStore.PREFS_NAME, Context.MODE_PRIVATE)),
            prompt = captureNotifier,
            isRegistered = transactionRepository::isRegistered,
            // 알림 읽기는 앱 화면 없이 켜지기도 해서, 저장소를 다 읽을 때까지 기다린 값으로 판단한다
            settings = { settingsRepository.autoSettings.first() },
        )
    }
}
