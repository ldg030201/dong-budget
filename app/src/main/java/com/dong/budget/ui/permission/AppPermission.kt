package com.dong.budget.ui.permission

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dong.budget.data.capture.PaymentNotificationListener
import com.dong.budget.data.update.GalaxyAutoBlocker
import com.dong.budget.ui.components.ConfirmDialog

/**
 * 사용자가 켜 줘야 하는 권한.
 *
 * 대부분은 앱 안에서 '허용' 창을 띄울 수 없어 기기 설정 화면으로 보내 켜게 한다.
 * [runtimePermission] 이 있는 것은 처음 한 번은 앱 안에서 시스템 '허용' 창을 띄울 수 있다.
 * 앱을 켤 때 꺼져 있는 것이 있으면 [PermissionGate] 가 안내창을 띄운다. 안내 순서는 여기 적은 순서다.
 */
enum class AppPermission(val title: String, val message: String) {
    /** '출처를 알 수 없는 앱 설치'. 앱 안에서 새 버전을 설치할 때 필요하다. */
    INSTALL_UPDATES(
        title = "업데이트 설치를 허용해 주세요",
        // 갤럭시의 자동 차단은 앱에서 켜짐 여부를 알 수 없고 설정 화면으로 바로 보낼 수도 없어서 글로만 안내한다
        message =
        "새 버전을 앱 안에서 바로 설치하려면 '출처를 알 수 없는 앱 설치'에서 동계부를 허용해야 해요.\n" +
            "설정 화면에서 허용을 켜고 돌아와 주세요.\n\n" +
            "갤럭시라면 '설정 > 보안 및 개인정보 보호 > 보안 위험 자동 차단'도 꺼져 있어야 업데이트가 설치돼요.",
    ),

    /**
     * 알림 보내기. '가계부에 등록할까요?' 알림을 띄우는 데 필요하다.
     * 알림 읽기보다 먼저 묻는다. 알림 읽기가 처음 연결될 때 알림창에 남은 토스 알림을 훑는데,
     * 그때 알림을 보낼 수 있어야 바로 물을 수 있다.
     */
    POST_NOTIFICATIONS(
        title = "알림을 허용해 주세요",
        message = "토스 결제 알림을 읽으면 '가계부에 등록할까요?' 알림을 보내요. 알림을 누르면 결제 내용이 채워진 등록창이 열려요.",
    ),

    /** '알림 읽기'. 토스 결제 알림을 읽는 데 필요하다. */
    READ_NOTIFICATIONS(
        title = "알림 읽기를 허용해 주세요",
        // 플레이 스토어 밖에서 설치한 앱은 Android 13 부터 이 권한이 '제한된 설정' 으로 막혀 있다.
        // 스위치를 한 번 눌러 막힌 것을 확인해야 앱 정보에 '제한된 설정 허용' 메뉴가 생기는 기기가 있어 그 순서로 적는다.
        message =
        "토스 결제 알림이 오면 가계부에 등록할지 물어보려면 동계부의 '알림 읽기'를 허용해야 해요.\n" +
            "토스 결제 알림만 골라 쓰고, 다른 앱의 알림은 저장하거나 어디로 보내지 않아요.\n\n" +
            "스위치를 눌렀는데 '제한된 설정' 창이 뜨면, 아래 '앱 정보 열기'를 눌러 오른쪽 위 ⋮ 에서 " +
            "'제한된 설정 허용'을 누른 뒤 다시 켜 주세요.",
    ),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        INSTALL_UPDATES -> context.packageManager.canRequestPackageInstalls()

        READ_NOTIFICATIONS ->
            context
                .getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(context, PaymentNotificationListener::class.java))

        // 알림 권한뿐 아니라 사용자가 기기 설정에서 동계부 알림을 통째로 끈 경우도 본다
        POST_NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 앱 안에서 시스템 '허용' 창으로 받을 수 있는 권한. 없으면 null */
    val runtimePermission: String?
        get() {
            // Android 12 까지는 알림 권한을 따로 묻지 않는다. 꺼져 있으면 설정 화면으로 보낸다.
            if (this != POST_NOTIFICATIONS || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return Manifest.permission.POST_NOTIFICATIONS
        }

    /**
     * 안내창 아래에 둘 보조 버튼의 글. 없으면 null
     * - 알림 읽기: 플레이 스토어 밖에서 설치한 앱은 '제한된 설정' 으로 막혀 있고, 푸는 메뉴가 앱 정보 화면에 있다.
     * - 설치 허용(갤럭시): '보안 위험 자동 차단' 이 켜져 있으면 설치 허용을 켜도 업데이트가 막힌다. 끄는 화면으로 보낸다.
     */
    val extraLabel: String?
        get() = when (this) {
            READ_NOTIFICATIONS -> "앱 정보 열기"
            INSTALL_UPDATES -> if (GalaxyAutoBlocker.isAvailable) "보안 위험 자동 차단 열기" else null
            POST_NOTIFICATIONS -> null
        }

    /** [extraLabel] 버튼을 눌렀을 때 */
    fun openExtra(context: Context) {
        when (this) {
            READ_NOTIFICATIONS -> runCatching { context.startActivity(appInfoIntent(context)) }
            INSTALL_UPDATES -> GalaxyAutoBlocker.open(context)
            POST_NOTIFICATIONS -> Unit
        }
    }

    /** 이 권한을 켜는 설정 화면들. 앞의 것이 안 열리면 다음 것을 연다. 동계부 항목이 바로 열리는 것을 앞에 둔다. */
    fun settingsIntents(context: Context): List<Intent> = when (this) {
        INSTALL_UPDATES ->
            listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))

        READ_NOTIFICATIONS ->
            listOf(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, PaymentNotificationListener::class.java).flattenToString(),
                ),
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )

        POST_NOTIFICATIONS ->
            listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}

private fun promptHistory(context: Context) =
    SystemPromptHistory(context.getSharedPreferences(SystemPromptHistory.PREFS_NAME, Context.MODE_PRIVATE))

/** 설정 화면을 연다. 제조사가 해당 화면을 막아둔 기기에서는 동계부의 앱 정보 화면으로 대신 간다. */
private fun openSettings(context: Context, permission: AppPermission) {
    val candidates = permission.settingsIntents(context) + appInfoIntent(context)
    candidates.firstOrNull { runCatching { context.startActivity(it) }.isSuccess }
}

private fun appInfoIntent(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

/**
 * 권한 안내창.
 *
 * 시스템 '허용' 창을 띄울 수 있는 권한이면 [허용하기] 로 그 창을 띄우고([onRequest]),
 * 아니면 [설정으로 가기] 로 그 권한을 켜는 설정 화면으로 바로 간다.
 */
@Composable
fun PermissionDialog(permission: AppPermission, onGoToSettings: () -> Unit, onLater: () -> Unit, onRequest: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val runtime = permission.runtimePermission
    val canPrompt =
        runtime != null &&
            onRequest != null &&
            activity != null &&
            promptHistory(context).canPrompt(runtime, activity.shouldShowRequestPermissionRationale(runtime))
    ConfirmDialog(
        title = permission.title,
        message = permission.message,
        confirmLabel = if (canPrompt) "허용하기" else "설정으로 가기",
        dismissLabel = "나중에",
        destructive = false,
        onConfirm = {
            if (canPrompt && runtime != null && onRequest != null) {
                onRequest(runtime)
            } else {
                openSettings(context, permission)
                onGoToSettings()
            }
        },
        onDismiss = onLater,
        extraLabel = permission.extraLabel,
        onExtra = {
            permission.openExtra(context)
            onGoToSettings()
        },
    )
}

/**
 * 앱을 켤 때(그리고 설정 화면에서 돌아올 때마다) 꺼진 권한이 있는지 보고 안내창을 띄운다.
 *
 * [나중에] 를 누른 권한은 앱을 다시 켤 때까지 묻지 않는다. 쓸 때만 필요한 권한을
 * 화면을 오갈 때마다 물으면 성가시다. 설정 화면에 다녀왔는데도 여전히 꺼져 있으면 한 번 더 묻는다.
 * 시스템 '허용' 창에서 거절한 것도 [나중에] 와 같게 본다.
 */
@Composable
fun PermissionGate() {
    val context = LocalContext.current
    // enum 이름으로 저장한다. 화면이 다시 만들어져도 '나중에' 가 유지되게 한다.
    var postponed by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var missing by remember { mutableStateOf<AppPermission?>(null) }
    val activity = LocalActivity.current
    // 시스템 '허용' 창을 띄운 권한(enum 이름)과 띄우기 직전의 rationale. 결과가 오면 기록하고, 거절이면 '나중에' 로 돌린다.
    var requesting by rememberSaveable { mutableStateOf<String?>(null) }
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permission = requesting?.let { name -> AppPermission.entries.firstOrNull { it.name == name } }
            requesting = null
            val runtime = permission?.runtimePermission ?: return@rememberLauncherForActivityResult
            promptHistory(context).record(
                permission = runtime,
                granted = granted,
                rationaleBefore = rationaleBefore,
                rationaleAfter = activity?.shouldShowRequestPermissionRationale(runtime) ?: false,
            )
            if (!granted) postponed = postponed + permission.name
        }

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
            onRequest = { runtime ->
                requesting = permission.name
                rationaleBefore = activity?.shouldShowRequestPermissionRationale(runtime) ?: false
                missing = null
                launcher.launch(runtime)
            },
        )
    }
}
