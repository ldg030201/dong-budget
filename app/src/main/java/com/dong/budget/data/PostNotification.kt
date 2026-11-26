package com.dong.budget.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Android 13 부터 알림을 보내려면 사용자 허락이 필요하다. 그 전에는 따로 허락받지 않는다. */
internal fun hasPostPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * [channelId] 채널로 알림을 보낼 수 있는지. 결제 묻는 알림(CaptureNotifier)과 월급날 알림(SalaryNotifier)이 같이 쓴다.
 * 알림 권한([hasPostPermission])과 앱 알림 전체뿐 아니라 그 채널만 꺼 둔 경우도 본다.
 * 채널이 꺼진 채로 보내면 시스템이 예외 없이 말없이 버려서, 보낸 것으로 여겨도 사용자는 알림을 못 본다.
 */
internal fun canPostNotification(context: Context, manager: NotificationManagerCompat, channelId: String): Boolean {
    if (!hasPostPermission(context) || !manager.areNotificationsEnabled()) return false
    // 채널이 아직 없으면 보낼 때 만든다
    val importance = manager.getNotificationChannel(channelId)?.importance ?: return true
    return importance != NotificationManager.IMPORTANCE_NONE
}
