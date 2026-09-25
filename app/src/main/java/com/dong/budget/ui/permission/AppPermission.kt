package com.dong.budget.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dong.budget.ui.components.ConfirmDialog

/**
 * 사용자가 기기 설정 화면에서 직접 켜야 하는 권한.
 *
 * 일반 권한처럼 앱 안에서 '허용' 창을 띄울 수 없고, 설정 화면으로 보내 켜게 해야 한다.
 * 새 기능이 이런 권한을 쓰게 되면(예: 알림 읽기) 여기에 추가한다.
 * 앱을 켤 때 꺼져 있는 것이 있으면 [PermissionGate] 가 안내창을 띄운다.
 */
enum class AppPermission(val title: String, val message: String) {
    /** '출처를 알 수 없는 앱 설치'. 앱 안에서 새 버전을 설치할 때 필요하다. */
    INSTALL_UPDATES(
        title = "업데이트 설치를 허용해 주세요",
        message =
        "새 버전을 앱 안에서 바로 설치하려면 '출처를 알 수 없는 앱 설치'에서 동계부를 허용해야 해요.\n" +
            "설정 화면에서 허용을 켜고 돌아와 주세요.",
    ),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        INSTALL_UPDATES -> context.packageManager.canRequestPackageInstalls()
    }

    /** 이 권한을 켜는 설정 화면. 동계부 항목이 바로 열린다. */
    fun settingsIntent(context: Context): Intent = when (this) {
        INSTALL_UPDATES ->
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    }
}

/**
 * 권한 안내창. [설정으로 가기] 를 누르면 그 권한을 켜는 설정 화면으로 바로 간다.
 * 제조사가 해당 화면을 막아둔 기기에서는 동계부의 앱 정보 화면으로 대신 간다.
 */
@Composable
fun PermissionDialog(permission: AppPermission, onGoToSettings: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    ConfirmDialog(
        title = permission.title,
        message = permission.message,
        confirmLabel = "설정으로 가기",
        dismissLabel = "나중에",
        destructive = false,
        onConfirm = {
            val opened = runCatching { context.startActivity(permission.settingsIntent(context)) }.isSuccess
            if (!opened) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
            onGoToSettings()
        },
        onDismiss = onLater,
    )
}

/**
 * 앱을 켤 때(그리고 설정 화면에서 돌아올 때마다) 꺼진 권한이 있는지 보고 안내창을 띄운다.
 *
 * [나중에] 를 누른 권한은 앱을 다시 켤 때까지 묻지 않는다. 업데이트할 때만 쓰는 권한을
 * 화면을 오갈 때마다 물으면 성가시다. 설정 화면에 다녀왔는데도 여전히 꺼져 있으면 한 번 더 묻는다.
 */
@Composable
fun PermissionGate() {
    val context = LocalContext.current
    // enum 이름으로 저장한다. 화면이 다시 만들어져도 '나중에' 가 유지되게 한다.
    var postponed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var missing by remember { mutableStateOf<AppPermission?>(null) }

    LifecycleResumeEffect(postponed) {
        missing = AppPermission.entries.firstOrNull { it.name !in postponed && !it.isGranted(context) }
        onPauseOrDispose {}
    }

    missing?.let { permission ->
        PermissionDialog(
            permission = permission,
            // 설정 화면에서 돌아오면 다시 확인한다(LifecycleResumeEffect)
            onGoToSettings = { missing = null },
            onLater = {
                postponed = postponed + permission.name
                missing = null
            },
        )
    }
}
