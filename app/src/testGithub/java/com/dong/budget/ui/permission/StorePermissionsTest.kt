package com.dong.budget.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class StorePermissionsTest {
    @Test
    fun `설치 허용은 알림 권한보다 먼저 묻는다`() {
        assertEquals(
            listOf(InstallUpdatesPermission, NotificationPermission.POST_NOTIFICATIONS, NotificationPermission.READ_NOTIFICATIONS),
            APP_PERMISSIONS,
        )
    }

    @Test
    fun `설치 허용은 예전 이름 그대로 적어 다시 안 보기 기록을 잇는다`() {
        assertEquals("INSTALL_UPDATES", InstallUpdatesPermission.name)
    }
}
