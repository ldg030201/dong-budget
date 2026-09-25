package com.dong.budget.ui.permission

import com.dong.budget.testing.FakePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptHistoryTest {
    private val prefs = FakePreferences()
    private val history = SystemPromptHistory(prefs)
    private val permission = "android.permission.POST_NOTIFICATIONS"

    @Test
    fun `처음에는 시스템 창을 띄운다`() {
        assertTrue(history.canPrompt(permission, rationaleNow = false))
    }

    @Test
    fun `창을 그냥 한 번 닫은 것으로는 설정으로 보내지 않는다`() {
        history.record(permission, granted = false, rationaleBefore = false, rationaleAfter = false)
        assertTrue(history.canPrompt(permission, rationaleNow = false))
        // 두 번째도 답 없이 끝나면 시스템이 창을 안 띄우는 것으로 본다
        history.record(permission, granted = false, rationaleBefore = false, rationaleAfter = false)
        assertFalse(history.canPrompt(permission, rationaleNow = false))
    }

    @Test
    fun `한 번 거절하면 다시 띄우고, 또 거절하면 설정으로 보낸다`() {
        history.record(permission, granted = false, rationaleBefore = false, rationaleAfter = true)
        assertTrue(history.canPrompt(permission, rationaleNow = true))
        history.record(permission, granted = false, rationaleBefore = true, rationaleAfter = false)
        assertFalse(history.canPrompt(permission, rationaleNow = false))
    }

    @Test
    fun `허용하면 기록을 비운다`() {
        history.record(permission, granted = false, rationaleBefore = false, rationaleAfter = false)
        history.record(permission, granted = true, rationaleBefore = false, rationaleAfter = false)
        history.record(permission, granted = false, rationaleBefore = false, rationaleAfter = false)
        assertTrue(history.canPrompt(permission, rationaleNow = false))
    }

    @Test
    fun `0_1_6 에서 물어봤다고만 적힌 기록은 한 번으로 본다`() {
        prefs.edit().putBoolean(permission, true).apply()
        assertTrue(history.canPrompt(permission, rationaleNow = false))
        history.record(permission, granted = false, rationaleBefore = false, rationaleAfter = false)
        assertFalse(history.canPrompt(permission, rationaleNow = false))
    }
}
