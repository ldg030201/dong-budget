package com.dong.budget.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupScheduleTest {
    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `처음에는 켜져 있고 1주마다다`() {
        assertEquals(BackupSchedule(on = true, interval = BackupInterval.WEEKLY), BackupSchedule.DEFAULT)
    }

    @Test
    fun `백업한 적 없으면 바로 하고, 끄면 하지 않는다`() {
        assertTrue(BackupSchedule().isDue(null, today))
        assertFalse(BackupSchedule(on = false).isDue(null, today))
    }

    @Test
    fun `마지막 파일 백업에서 주기만큼 지난 날부터 한다`() {
        val weekly = BackupSchedule(interval = BackupInterval.WEEKLY)
        assertTrue(weekly.isDue(LocalDate.of(2026, 9, 24), today))
        assertFalse(weekly.isDue(LocalDate.of(2026, 9, 25), today))

        val daily = BackupSchedule(interval = BackupInterval.DAILY)
        assertFalse(daily.isDue(today, today))
        assertTrue(daily.isDue(today.minusDays(1), today))

        assertFalse(BackupSchedule(interval = BackupInterval.EVERY_3_DAYS).isDue(LocalDate.of(2026, 9, 29), today))
        assertTrue(BackupSchedule(interval = BackupInterval.EVERY_3_DAYS).isDue(LocalDate.of(2026, 9, 28), today))

        // 8월 31일의 한 달 뒤는 9월 30일이다
        assertTrue(BackupSchedule(interval = BackupInterval.MONTHLY).isDue(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30)))
        assertFalse(BackupSchedule(interval = BackupInterval.MONTHLY).isDue(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `모르는 주기 값은 기본 주기로 읽는다`() {
        assertEquals(BackupInterval.WEEKLY, BackupInterval.fromKey(null))
        assertEquals(BackupInterval.WEEKLY, BackupInterval.fromKey("hourly"))
        BackupInterval.entries.forEach { assertEquals(it, BackupInterval.fromKey(it.key)) }
    }

    @Test
    fun `자동 백업 파일 이름은 정해진 앞부분으로 시작한다`() {
        assertEquals("동계부-자동백업-2026-10-01.json", BackupSchedule.fileName(today))
        assertTrue(BackupSchedule.fileName(today).startsWith(BackupSchedule.FILE_PREFIX))
    }
}
