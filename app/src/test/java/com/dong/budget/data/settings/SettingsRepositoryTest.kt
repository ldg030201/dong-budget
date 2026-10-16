package com.dong.budget.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dong.budget.data.backup.BackupInterval
import com.dong.budget.data.backup.BackupSchedule
import com.dong.budget.data.backup.backupSettingsOf
import com.dong.budget.data.backup.toRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek

/** 설정 저장소를 임시 파일에 두고, 백업이 쓰는 한 벌 읽기([SettingsRepository.current])와 되살리기([SettingsRepository.restore])를 본다. */
class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    /** 같은 파일을 두 저장소가 함께 열 수 없어서, 폰마다 다른 파일을 쓴다 */
    private fun repository(name: String = "app_settings"): SettingsRepository {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val store = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("$name.preferences_pb") }
        return SettingsRepository(store)
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private val custom =
        AppSettings(
            themeMode = ThemeMode.DARK,
            weekStart = DayOfWeek.MONDAY,
            bottomMenu = BottomMenu.of(listOf(MenuItem.CARD_PERFORMANCE, MenuItem.SALARY)),
            auto = AutoSettings(setOf(AutoOption.CAPTURE_DEPOSIT, AutoOption.EDITOR_KEYPAD)),
            backupSchedule = BackupSchedule(on = false, interval = BackupInterval.MONTHLY),
        )

    private fun everything(settings: AppSettings) = SettingsRestore(
        themeMode = settings.themeMode,
        weekStart = settings.weekStart,
        bottomMenu = settings.bottomMenu,
        auto = AutoOption.entries.associateWith { settings.auto.chosen(it) },
        backupOn = settings.backupSchedule.on,
        backupInterval = settings.backupSchedule.interval,
    )

    @Test
    fun `아무것도 정하지 않았으면 처음 설치한 설정이다`() = runBlocking {
        assertEquals(AppSettings(), repository().current())
    }

    @Test
    fun `하나씩 바꾼 설정을 한 벌로 읽는다`() = runBlocking {
        val repository = repository()
        repository.setThemeMode(ThemeMode.DARK)
        repository.setWeekStart(DayOfWeek.MONDAY)
        repository.setBottomMenu(custom.bottomMenu)
        repository.setAuto(AutoOption.CAPTURE_DEPOSIT, false)
        repository.setAuto(AutoOption.EDITOR_KEYPAD, false)
        repository.setBackupSchedule(custom.backupSchedule)
        assertEquals(custom, repository.current())
    }

    @Test
    fun `되살리면 백업 때 설정이 되고 켠 스위치도 다시 켜진다`() = runBlocking {
        val repository = repository()
        // 이 폰에서는 다른 스위치를 꺼 두고 테마도 밝게 골라 뒀다
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setAuto(AutoOption.UPDATE_CHECK, false)
        repository.restore(everything(custom))
        assertEquals(custom, repository.current())
        assertTrue(repository.autoSettings.first().chosen(AutoOption.UPDATE_CHECK))
        assertEquals(ThemeMode.DARK, repository.themeMode.first())
        assertEquals(custom.backupSchedule, repository.backupSchedule.first())
    }

    @Test
    fun `설정 파일을 읽지 못하면 기본값이 아니라 없음으로 읽어 백업이 기본 설정을 담지 않는다`() = runBlocking {
        // 깨진 설정 파일. 화면은 기본값으로 시작하지만 백업에는 기본값을 담으면 안 된다.
        folder.root.resolve("broken.preferences_pb").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        assertNull(repository("broken").current())
    }

    @Test
    fun `되살릴 설정에 없는 것은 지금 값을 그대로 둔다`() = runBlocking {
        val repository = repository()
        repository.restore(everything(custom))
        repository.restore(SettingsRestore(themeMode = ThemeMode.LIGHT, auto = mapOf(AutoOption.EDITOR_KEYPAD to true), backupOn = true))
        val after = checkNotNull(repository.current())
        assertEquals(ThemeMode.LIGHT, after.themeMode)
        assertTrue(after.auto.chosen(AutoOption.EDITOR_KEYPAD))
        // 나머지는 앞서 되살린 그대로다
        assertEquals(DayOfWeek.MONDAY, after.weekStart)
        assertEquals(custom.bottomMenu, after.bottomMenu)
        assertFalse(after.auto.chosen(AutoOption.CAPTURE_DEPOSIT))
        assertEquals(BackupSchedule(on = true, interval = BackupInterval.MONTHLY), after.backupSchedule)
    }

    @Test
    fun `설정 초기화는 되살린 설정도 처음대로 돌린다`() = runBlocking {
        val repository = repository()
        repository.restore(everything(custom))
        repository.resetAll()
        assertEquals(AppSettings(), repository.current())
    }

    @Test
    fun `백업에 담은 설정을 다른 폰에 되살리면 같은 설정이 된다`() = runBlocking {
        val old = repository("old")
        old.restore(everything(custom))
        // 새 폰은 스위치 하나를 꺼 두고 1일마다 자동 백업한다
        val new = repository("new")
        new.setAuto(AutoOption.STATS_DAY, false)
        new.setBackupSchedule(BackupSchedule(interval = BackupInterval.DAILY))
        new.restore(backupSettingsOf(checkNotNull(old.current())).toRestore())
        assertEquals(custom, new.current())
    }
}
