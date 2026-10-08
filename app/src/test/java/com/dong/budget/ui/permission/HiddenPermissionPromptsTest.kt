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
        APP_PERMISSIONS.forEach { assertFalse(prompts.isHidden(it)) }

        prompts.hide(NotificationPermission.READ_NOTIFICATIONS)

        val reopened = HiddenPermissionPrompts(prefs)
        assertTrue(reopened.isHidden(NotificationPermission.READ_NOTIFICATIONS))
        // 나머지(배포처에만 있는 설치 허용 포함)는 그대로 묻는다
        (APP_PERMISSIONS - NotificationPermission.READ_NOTIFICATIONS).forEach { assertFalse(reopened.isHidden(it)) }
    }
}
