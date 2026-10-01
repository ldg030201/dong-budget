package com.dong.budget.data.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

class ScheduledBackupTest {
    private var schedule = BackupSchedule.DEFAULT
    private var lastFile: Instant? = null
    private var hasData = true
    private var saveResult: Result<String>? = null
    private val saved = mutableListOf<String>()
    private var pruned = 0

    private val backup =
        ScheduledBackup(
            schedule = { schedule },
            lastFileBackup = { lastFile },
            hasData = { hasData },
            save = { name ->
                saved += name
                saveResult ?: Result.success(name)
            },
            deleteOld = {
                pruned++
                1
            },
            today = { LocalDate.of(2026, 10, 1) },
        )

    @Test
    fun `차례가 되면 오늘 날짜 이름으로 저장하고 오래된 자동 백업을 지운다`() = runBlocking {
        // 서울로 9월 24일 오전
        lastFile = Instant.parse("2026-09-24T01:00:00Z")
        assertEquals("동계부-자동백업-2026-10-01.json", backup.runIfDue())
        assertEquals(listOf("동계부-자동백업-2026-10-01.json"), saved)
        assertEquals(1, pruned)
    }

    @Test
    fun `차례가 아니거나 꺼져 있거나 백업할 것이 없으면 저장하지 않는다`() = runBlocking {
        lastFile = Instant.parse("2026-09-25T01:00:00Z")
        assertNull(backup.runIfDue())

        lastFile = null
        schedule = BackupSchedule(on = false)
        assertNull(backup.runIfDue())

        schedule = BackupSchedule.DEFAULT
        hasData = false
        assertNull(backup.runIfDue())

        assertTrue(saved.isEmpty())
        assertEquals(0, pruned)
    }

    @Test
    fun `저장하지 못하면 오래된 파일을 지우지 않는다`() = runBlocking {
        saveResult = Result.failure(IOException("저장 공간 없음"))
        assertNull(backup.runIfDue())
        assertEquals(1, saved.size)
        assertEquals(0, pruned)
    }
}
