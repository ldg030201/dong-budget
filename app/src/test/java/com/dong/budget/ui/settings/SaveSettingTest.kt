package com.dong.budget.ui.settings

import com.dong.budget.data.backup.BackupSchedule
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.testing.FailingPreferencesStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.util.Collections

/**
 * 설정 화면 모델의 저장(테마·한 주 시작·자동 백업·자동 기능 스위치)이 거치는 [saveSetting] 을 본다.
 * 화면 모델은 DB · Context 가 있어야 만들어져서 JVM 에서 바로 만들 수 없다. 그래서 화면 모델이 부르는 그대로
 * 쓰지 못하는 저장소(저장 공간이 모자란 폰)의 저장소 함수를 넘긴다.
 */
class SaveSettingTest {
    /** 화면 모델 범위에서 잡히지 않고 빠져나간 오류. 앱에서는 이것이 앱을 죽인다. */
    private val crashes: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> crashes += e })
    private val messages = Channel<String>(Channel.BUFFERED)
    private val repository = SettingsRepository(FailingPreferencesStore())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `설정 파일에 쓰지 못하면 앱을 죽이지 않고 저장하지 못했다고 한 번 알린다`() = runBlocking {
        val saves: List<suspend () -> Unit> =
            listOf(
                { repository.setThemeMode(ThemeMode.DARK) },
                { repository.setWeekStart(DayOfWeek.MONDAY) },
                { repository.setBackupSchedule(BackupSchedule(on = false)) },
                { repository.setAuto(AutoOption.CAPTURE_DEPOSIT, false) },
            )
        saves.forEach { save ->
            scope.saveSetting(messages, save).join()
            // 저장이 끝났으니(join) 알림은 이미 들어와 있다. 오지 않으면 기다리지 않고 실패한다.
            assertEquals(SETTING_SAVE_FAILED_MESSAGE, messages.tryReceive().getOrNull())
        }
        assertTrue(crashes.toString(), crashes.isEmpty())
        assertTrue(messages.tryReceive().isFailure)
    }
}
