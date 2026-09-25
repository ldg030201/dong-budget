package com.dong.budget.data.capture

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
import java.util.Locale

/**
 * '가계부에 등록할까요?' 알림.
 *
 * 누르면 동계부가 열리고 결제 내용이 채워진 등록창이 뜬다(MainActivity → DongBudgetApp).
 * 눌러도 알림은 남는다. 등록창을 열었다가 그냥 닫으면 아직 등록하지 않은 것이므로 다시 누를 수 있어야 한다.
 * 저장하면 [dismiss] 로 치운다. 등록하지 않을 결제는 사용자가 밀어서 지운다.
 */
class CaptureNotifier(private val context: Context) : CapturePrompt {
    private val manager = NotificationManagerCompat.from(context)

    /** 알림 채널을 만든다. 이미 있으면 그대로 둔다(사용자가 바꾼 설정도 유지된다). */
    fun ensureChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "결제 등록", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "토스 결제 알림을 읽고 가계부에 등록할지 물어봐요"
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun canAsk(): Boolean = hasPermission() && manager.areNotificationsEnabled()

    override fun ask(payment: CapturedPayment, quietly: Boolean) {
        if (!hasPermission()) return
        ensureChannel()
        // 제목은 짧게 두고, 금액과 가게를 본문 앞에 둔다. 알림을 접은 상태에서는 한 줄만 보여 뒤가 잘린다.
        val detail =
            listOfNotNull(
                "${String.format(Locale.KOREA, "%,d", payment.amount)}원",
                payment.merchant.ifBlank { null },
                payment.paymentName,
                payment.installmentMonths?.let { "${it}개월 할부" },
            ).joinToString(" · ")
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sym_receipt_long)
                .setContentTitle("가계부에 등록할까요?")
                .setContentText(detail)
                // 펼치면 잘린 부분까지 다 보인다
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setWhen(payment.occurredAtMillis)
                .setShowWhen(true)
                .setContentIntent(openIntent(payment.dedupKey))
                .setOnlyAlertOnce(true)
                .setSilent(quietly)
                // 기록(CaptureStore)이 지워지는 때에 맞춰 알림도 사라진다. 그 뒤에는 눌러도 채울 내용이 없다.
                .setTimeoutAfter((payment.occurredAtMillis + CaptureStore.RETENTION_MS - System.currentTimeMillis()).coerceAtLeast(1))
                .build()
        try {
            manager.notify(payment.dedupKey, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 권한은 위에서 확인했지만 그사이 꺼질 수 있다. 이번 결제는 묻지 못하고 넘어간다.
        }
    }

    override fun dismiss(dedupKey: String) {
        manager.cancel(dedupKey, NOTIFICATION_ID)
    }

    /** Android 13 부터 알림을 보내려면 사용자 허락이 필요하다. 그 전에는 따로 허락받지 않는다. */
    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * 누르면 동계부를 여는 Intent. 결제 내용은 담지 않고 열쇠만 identifier 에 담는다(CaptureStore 참고).
     * 결제마다 identifier 가 달라서 서로 다른 PendingIntent 가 된다. 같으면 뒤의 알림이 앞 알림의 열쇠를 덮어쓴다.
     * extras 는 쓰지 않는다. 받는 쪽(MainActivity)이 extras 를 풀지 않아도 되게 하기 위함이다.
     */
    private fun openIntent(dedupKey: String): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_CAPTURED)
                .setIdentifier(dedupKey)
                // 동계부 위에 다른 화면(설정 앱 등)이 떠 있으면 걷어내고, 떠 있는 동계부 화면으로 보낸다(onNewIntent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_ID = "payment_capture"
        const val ACTION_OPEN_CAPTURED = "com.dong.budget.action.OPEN_CAPTURED"
        private const val NOTIFICATION_ID = 1
    }
}
