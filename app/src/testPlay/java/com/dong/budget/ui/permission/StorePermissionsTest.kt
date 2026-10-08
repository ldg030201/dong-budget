package com.dong.budget.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class StorePermissionsTest {
    @Test
    fun `Play 배포는 설치 허용을 묻지 않는다`() {
        assertEquals(listOf(NotificationPermission.POST_NOTIFICATIONS, NotificationPermission.READ_NOTIFICATIONS), APP_PERMISSIONS)
    }
}
