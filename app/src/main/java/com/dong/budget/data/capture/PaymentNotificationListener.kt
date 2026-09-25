package com.dong.budget.data.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dong.budget.BudgetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 기기에 올라오는 알림을 받는다. 사용자가 '알림 읽기' 를 허용해야 시스템이 연결해 준다.
 *
 * 시스템은 모든 앱의 알림을 넘겨준다. 토스가 아닌 앱의 알림은 [handle] 첫 줄에서 버리고,
 * 내용을 읽거나 남기지 않는다.
 *
 * 이 클래스의 이름과 패키지 경로는 바꾸지 않는다. 시스템이 사용자의 허용을 이 이름으로 기억해서,
 * 바꾸면 업데이트할 때 허용이 조용히 풀린다.
 */
class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val capture: PaymentCapture get() = (application as BudgetApplication).container.paymentCapture

    /**
     * 연결될 때(허용 직후, 앱 업데이트나 기기 재시작 뒤) 할 일.
     * 1. 업데이트나 재시작으로 지워진 우리 알림 중 아직 답하지 않은 것을 다시 띄운다.
     * 2. 알림창에 남아 있는 토스 알림도 살핀다. 연결이 끊겨 있던 사이 올라온 결제를 놓치지 않기 위함이다.
     *    이미 물어본 결제는 다시 묻지 않는다.
     */
    override fun onListenerConnected() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val showing = active.filter(::isOurPrompt).mapNotNull { it.tag }.toSet()
        scope.launch { runCatching { capture.restorePrompts(showing) } }
        active.forEach(::handle)
    }

    /** 사용자가 우리 알림을 밀어서(또는 '모두 지우기' 로) 지우면 그 결제는 다시 묻지 않는다 */
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null || !isOurPrompt(sbn)) return
        if (reason != REASON_CANCEL && reason != REASON_CANCEL_ALL) return
        val dedupKey = sbn.tag ?: return
        scope.launch { runCatching { capture.onPromptDismissed(dedupKey) } }
    }

    private fun isOurPrompt(sbn: StatusBarNotification) =
        sbn.packageName == packageName && sbn.notification?.channelId == CaptureNotifier.CHANNEL_ID

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let(::handle)
    }

    private fun handle(sbn: StatusBarNotification) {
        // 다른 앱의 알림은 여기서 끝난다
        if (!PaymentCapture.isSource(sbn.packageName)) return
        val notification = sbn.notification ?: return
        // 여러 알림을 묶는 요약 알림은 결제 한 건이 아니다
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // 알림 내용을 꺼내다 실패해도(알 수 없는 형식 등) 동계부가 죽지 않게 한다
        val extras = notification.extras ?: return
        val (titles, texts) =
            runCatching {
                listOf(extras.getCharSequence(Notification.EXTRA_TITLE), extras.getCharSequence(Notification.EXTRA_TITLE_BIG)) to
                    listOf(extras.getCharSequence(Notification.EXTRA_TEXT), extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            }.getOrNull() ?: return
        val occurredAt = PaymentCapture.paymentTime(notification.`when`, sbn.postTime)
        scope.launch { runCatching { capture.onNotification(titles, texts, occurredAt) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
