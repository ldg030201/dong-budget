package com.dong.budget.data.backup

import com.dong.budget.data.settings.AppSettings
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.data.settings.SettingsRestore
import com.dong.budget.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class BackupSettingsTest {
    @Test
    fun `담은 설정을 되살리면 모든 칸과 모든 스위치를 바꾼다`() {
        val settings =
            AppSettings(
                themeMode = ThemeMode.LIGHT,
                weekStart = DayOfWeek.MONDAY,
                bottomMenu = BottomMenu.of(listOf(MenuItem.FIXED_EXPENSE)),
                auto = AutoSettings(setOf(AutoOption.FILL_PAYMENT)),
                backupSchedule = BackupSchedule(on = true, interval = BackupInterval.EVERY_3_DAYS),
            )
        assertEquals(
            SettingsRestore(
                themeMode = ThemeMode.LIGHT,
                weekStart = DayOfWeek.MONDAY,
                bottomMenu = settings.bottomMenu,
                // 켠 스위치도 켜라고 적는다. 기대는 스위치(결제수단 고르기)를 꺼도 새 카드 추가는 고른 대로(켜짐) 남는다.
                auto = AutoOption.entries.associateWith { it != AutoOption.FILL_PAYMENT },
                backupOn = true,
                backupInterval = BackupInterval.EVERY_3_DAYS,
            ),
            backupSettingsOf(settings).toRestore(),
        )
    }

    @Test
    fun `처음 설치한 설정도 빠짐없이 담는다`() {
        val restore = backupSettingsOf(AppSettings()).toRestore()
        assertEquals(ThemeMode.SYSTEM, restore.themeMode)
        assertEquals(DayOfWeek.SUNDAY, restore.weekStart)
        assertEquals(BottomMenu.DEFAULT, restore.bottomMenu)
        assertEquals(AutoOption.entries.associateWith { true }, restore.auto)
        assertEquals(BackupSchedule.DEFAULT.on, restore.backupOn)
        assertEquals(BackupSchedule.DEFAULT.interval, restore.backupInterval)
    }

    @Test
    fun `비어 있는 칸은 지금 값을 그대로 두게 한다`() {
        assertEquals(SettingsRestore(), BackupSettings().toRestore())
        // 주기만 빠졌으면 켜고 끄기만 바꾼다
        val onOnly = BackupSettings(autoBackup = BackupAutoBackup(on = false)).toRestore()
        assertEquals(false, onOnly.backupOn)
        assertNull(onOnly.backupInterval)
    }

    @Test
    fun `읽을 수 없는 값은 건너뛴다`() {
        // 한 주는 일요일·월요일에만 시작하고, 이름은 저장소와 같은 대문자다
        assertNull(BackupSettings(weekStart = "SATURDAY").toRestore().weekStart)
        assertNull(BackupSettings(weekStart = "monday").toRestore().weekStart)
        assertNull(BackupSettings(themeMode = "dark").toRestore().themeMode)
        // 아는 칸이 하나도 없는 하단 메뉴는 가운데를 비우지 않고 지금 차림을 둔다
        listOf("", "|", "budget|wallet", "아무 글").forEach { assertNull(it, BackupSettings(bottomMenu = it).toRestore().bottomMenu) }
        // 가운데를 모두 뺀 차림은 그대로 받는다
        val empty = BottomMenu.of(emptyList())
        assertEquals(empty, BackupSettings(bottomMenu = empty.encode()).toRestore().bottomMenu)
    }
}
