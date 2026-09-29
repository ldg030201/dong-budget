package com.dong.budget.data

import android.content.Context
import com.dong.budget.data.backup.BackupRepository
import com.dong.budget.data.backup.BackupStorage
import com.dong.budget.data.capture.CaptureNotifier
import com.dong.budget.data.capture.CaptureStore
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.UpdateChecker
import com.dong.budget.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * 수동 의존성 컨테이너.
 *
 * Hilt 를 쓰지 않는다. 이 규모에서 얻는 건 없고 빌드 구성에 움직이는 부품만 늘어난다.
 * 의존성이 늘어 감당이 안 되는 시점이 오면 그때 도입한다.
 */
class AppContainer(context: Context) {
    private val database by lazy { BudgetDatabase.build(context) }

    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }

    val categoryRepository by lazy { CategoryRepository(database.categoryDao()) }

    val paymentMethodRepository by lazy { PaymentMethodRepository(database.paymentMethodDao()) }

    val settingsRepository by lazy { SettingsRepository(context) }

    val backupRepository by lazy { BackupRepository(database) }

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
