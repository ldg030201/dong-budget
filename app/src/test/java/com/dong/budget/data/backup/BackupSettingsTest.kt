package com.dong.budget.data.backup

import com.dong.budget.data.settings.AppSettings
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class BackupSettingsTest {
    @Test
    fun `설정을 저장소에 적는 글 그대로 담고 켠 스위치도 모두 적는다`() {
        val settings =
            AppSettings(
                themeMode = ThemeMode.LIGHT,
                weekStart = DayOfWeek.MONDAY,
                bottomMenu = BottomMenu.of(listOf(MenuItem.FIXED_EXPENSE)),
                auto = AutoSettings(setOf(AutoOption.FILL_PAYMENT)),
                backupSchedule = BackupSchedule(on = true, interval = BackupInterval.EVERY_3_DAYS),
            )
        assertEquals(
            BackupSettings(
                themeMode = "LIGHT",
                weekStart = "MONDAY",
                bottomMenu = settings.bottomMenu.encode(),
                // 켠 스위치도 켜라고 적는다. 기대는 스위치(결제수단 고르기)를 꺼도 새 카드 추가는 고른 대로(켜짐) 남는다.
                autoOptions = AutoOption.entries.associate { it.key to (it != AutoOption.FILL_PAYMENT) },
                autoBackup = BackupAutoBackup(on = true, interval = BackupInterval.EVERY_3_DAYS.key),
            ),
            backupSettingsOf(settings),
        )
    }

    @Test
    fun `처음 설치한 설정도 빠짐없이 담는다`() {
        val backup = backupSettingsOf(AppSettings())
        assertEquals("SYSTEM", backup.themeMode)
        assertEquals("SUNDAY", backup.weekStart)
        assertEquals(BottomMenu.DEFAULT.encode(), backup.bottomMenu)
        assertEquals(AutoOption.entries.associate { it.key to true }, backup.autoOptions)
        assertEquals(BackupAutoBackup(on = BackupSchedule.DEFAULT.on, interval = BackupSchedule.DEFAULT.interval.key), backup.autoBackup)
    }
}
