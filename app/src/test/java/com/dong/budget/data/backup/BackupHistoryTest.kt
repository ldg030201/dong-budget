package com.dong.budget.data.backup

import com.dong.budget.testing.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BackupHistoryTest {
    private val prefs = FakePreferences()
    private var clock = Instant.parse("2026-10-01T03:00:00Z")

    private fun history() = BackupHistory(prefs) { clock }

    @Test
    fun `백업한 적 없으면 비어 있고, 백업하면 때와 방법을 앱을 다시 켜도 기억한다`() {
        val history = history()
        assertNull(history.last.value)

        history.record(BackupKind.FILE)
        assertEquals(LastBackup(clock, BackupKind.FILE), history.last.value)

        clock = Instant.parse("2026-10-02T09:30:00Z")
        history.record(BackupKind.AUTO)
        assertEquals(LastBackup(clock, BackupKind.AUTO), history().last.value)
    }

    @Test
    fun `종류를 읽을 수 없으면 파일로 본다`() {
        prefs.edit().putLong("last_at", clock.toEpochMilli()).putString("last_kind", "CLOUD").apply()
        assertEquals(LastBackup(clock, BackupKind.FILE), history().last.value)
    }
}
