package com.dong.budget.data.salary

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dong.budget.MainActivity
import com.dong.budget.R
import java.time.YearMonth
import java.util.Locale

/**
 * 월급날 '월급 들어왔나요?' 알림. 누르면 그달 월급으로 채운 수입 등록창이 열린다(MainActivity → DongBudgetApp).
 * 결제 알림과 채널을 나눈다. 알림 읽기(PaymentNotificationListener)가 채널로 자기 결제 알림을 가려내기 때문이다.
 * 눌러도 알림은 남는다. 등록창을 열었다가 그냥 닫으면 아직 등록하지 않은 것이다. 저장하면 [dismiss] 로 치운다.
 */
class SalaryNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** 알림 채널을 만든다. 이미 있으면 그대로 둔다(사용자가 바꾼 설정도 유지된다). */
    fun ensureChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "월급날", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "월급날에 월급이 들어왔는지 묻고, 누르면 수입으로 등록해요"
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 알림을 보낼 수 있는지. 앱 알림 전체와 '월급날' 채널을 본다. 꺼진 채로 보내면 시스템이 말없이 버린다. */
    fun canNotify(): Boolean {
        if (!hasPermission() || !manager.areNotificationsEnabled()) return false
        val importance = manager.getNotificationChannel(CHANNEL_ID)?.importance ?: return true
        return importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * [month] 월급날 알림을 띄운다. 잠금 화면에는 금액을 빼고 보인다.
     * @param amount 설정의 한 달 월급
     */
    fun show(month: YearMonth, amount: Long) {
        if (!hasPermission()) return
        ensureChannel()
        val title = "월급 들어왔나요?"
        val detail = "${month.monthValue}월 월급 ${String.format(Locale.KOREA, "%,d", amount)}원 · 누르면 수입으로 등록해요"
        val public =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sym_payments)
                .setContentTitle(title)
                .setContentText("오늘은 월급날이에요")
                .build()
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sym_payments)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(openIntent(month))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setOnlyAlertOnce(true)
                .build()
        try {
            manager.notify(salaryKey(month), NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 권한은 위에서 확인했지만 그사이 꺼질 수 있다. 이번 달은 묻지 못하고 넘어간다.
        }
    }

    /** 그달 월급을 등록했다. 알림을 치운다. [key] 는 [salaryKey] 다. */
    fun dismiss(key: String) {
        manager.cancel(key, NOTIFICATION_ID)
    }

    /** 떠 있는 월급날 알림을 모두 치운다(데이터 초기화, 월급 설정을 지웠을 때) */
    fun dismissAll() {
        manager.activeNotifications
            .filter { it.id == NOTIFICATION_ID && it.tag?.let(::salaryMonthOf) != null }
            .forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
    }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * 누르면 동계부를 여는 Intent. 달만 identifier 에 담는다(금액은 열 때의 설정에서 다시 읽는다).
     * 결제 알림과 action 이 달라서 PendingIntent 가 겹치지 않는다.
     */
    private fun openIntent(month: YearMonth): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_SALARY)
                .setIdentifier(salaryKey(month))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_ID = "salary_day"
        const val ACTION_OPEN_SALARY = "com.dong.budget.action.OPEN_SALARY"

        /** 결제 알림(1)과 겹치지 않게 한다. 태그도 'salary:' 로 달라 한 번 더 갈린다. */
        private const val NOTIFICATION_ID = 2
    }
}
