package com.dong.budget.data.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dong.budget.BudgetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var rescanJob: Job? = null

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
        // 연결돼 있는 동안 앱이 다시 훑어 달라고 하면(알림을 막 허용했을 때 등) 알림창의 토스 알림을 다시 살핀다
        rescanJob?.cancel()
        rescanJob =
            scope.launch {
                capture.rescanRequests.collect {
                    runCatching { activeNotifications }.getOrNull().orEmpty().forEach(::handle)
                }
            }
    }

    override fun onListenerDisconnected() {
        // 연결이 끊긴 뒤에는 알림창을 읽을 수 없다
        rescanJob?.cancel()
        rescanJob = null
    }

    /** 사용자가 우리 알림을 지우면 그 결제는 다시 묻지 않는다 */
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null || !isOurPrompt(sbn)) return
        if (reason !in USER_DISMISS_REASONS) return
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

    private companion object {
        /**
         * 사용자가 지운 것으로 보는 이유.
         * - 한 건을 밀기(CANCEL), '모두 지우기'(CANCEL_ALL)
         * - 쌓여서 시스템이 하나로 묶은 알림을 통째로 밀기. 묶음 안의 알림은 GROUP_SUMMARY_CANCELED 로 온다.
         * - 워치 같은 다른 기기나 다른 앱의 알림 읽기에서 지우기(LISTENER_CANCEL, LISTENER_CANCEL_ALL)
         * 앱이 등록 뒤 치운 것(APP_CANCEL), 업데이트(PACKAGE_CHANGED), 보관 기간 끝(TIMEOUT), 다시 알림(SNOOZED)은 뺀다.
         */
        val USER_DISMISS_REASONS =
            setOf(REASON_CANCEL, REASON_CANCEL_ALL, REASON_GROUP_SUMMARY_CANCELED, REASON_LISTENER_CANCEL, REASON_LISTENER_CANCEL_ALL)
    }
}
