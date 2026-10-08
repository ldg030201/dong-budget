package com.dong.budget.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.permission.AppPermission
import com.dong.budget.ui.permission.NotificationPermission
import com.dong.budget.ui.permission.PermissionDialog
import com.dong.budget.ui.permission.STORE_PERMISSIONS
import com.dong.budget.ui.permission.openSettings
import com.dong.budget.ui.theme.BudgetTheme

/** 설정에 보이는 순서. 결제 알림을 읽는 것이 이 앱의 중심이라 맨 위에 두고, 배포처에만 있는 권한(설치 허용)은 아래에 둔다. */
private val SETTINGS_ORDER =
    listOf(NotificationPermission.READ_NOTIFICATIONS, NotificationPermission.POST_NOTIFICATIONS) + STORE_PERMISSIONS

/**
 * 권한이 켜져 있는지 한눈에 본다. 꺼진 줄을 누르면 왜 필요한지와 켜는 길을 알려 주는 안내창을,
 * 켜진 줄을 누르면 그 권한의 기기 설정 화면을 연다. 앱을 켤 때의 안내에서 '다시 안 보기' 를 눌렀어도 여기서 켤 수 있다.
 * 기기 설정에서 바꾸고 돌아오면 다시 읽는다.
 */
@Composable
internal fun PermissionSection() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(SETTINGS_ORDER.associateWith { it.isGranted(context) }) }
    LifecycleResumeEffect(context) {
        granted = SETTINGS_ORDER.associateWith { it.isGranted(context) }
        onPauseOrDispose {}
    }
    var asking by remember { mutableStateOf<AppPermission?>(null) }

    SettingsGroup("권한") {
        SETTINGS_ORDER.forEach { permission ->
            val on = granted[permission] == true
            ActionRow(
                title = permission.label,
                description = permission.summary,
                value = if (on) "켜짐" else "꺼짐",
                valueColor = if (on) BudgetTheme.colors.textSecondary else BudgetTheme.colors.danger,
                onClick = { if (on) openSettings(context, permission) else asking = permission },
            )
        }
    }

    asking?.let { permission ->
        PermissionDialog(
            permission = permission,
            onGoToSettings = { asking = null },
            onLater = { asking = null },
        )
    }
}
