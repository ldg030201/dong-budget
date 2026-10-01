package com.dong.budget.ui.permission

import com.dong.budget.testing.FakePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenPermissionPromptsTest {
    private val prefs = FakePreferences()

    @Test
    fun `다시 안 보기를 누른 권한만 숨기고, 앱을 다시 켜도 숨긴 채로 둔다`() {
        val prompts = HiddenPermissionPrompts(prefs)
        AppPermission.entries.forEach { assertFalse(prompts.isHidden(it)) }

        prompts.hide(AppPermission.READ_NOTIFICATIONS)

        val reopened = HiddenPermissionPrompts(prefs)
        assertTrue(reopened.isHidden(AppPermission.READ_NOTIFICATIONS))
        assertFalse(reopened.isHidden(AppPermission.POST_NOTIFICATIONS))
        assertFalse(reopened.isHidden(AppPermission.INSTALL_UPDATES))
    }
}
