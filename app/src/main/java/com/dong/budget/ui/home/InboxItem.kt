package com.dong.budget.ui.home

import com.dong.budget.data.capture.CaptureRecord
import com.dong.budget.data.capture.CapturedPayment

/**
 * 홈 알림 목록의 한 줄. 지금은 결제 등록 알림뿐이다.
 *
 * @property isNew 아직 눌러 보지 않은 알림. 연한 남색 바탕과 오른쪽 빨간 점으로 보인다. 등록한 결제는 새 알림이 아니다.
 * @property registered 가계부에 등록을 마친 결제. '등록함' 을 붙인다.
 */
data class InboxItem(val payment: CapturedPayment, val isNew: Boolean, val registered: Boolean)

/**
 * 물어본 결제 기록에 등록 여부를 붙인다. 순서는 기록 그대로(최근 결제부터) 둔다.
 * 등록한 결제는 읽음 표시가 없어도 새 알림으로 보지 않는다. 1.1.0 전에 등록한 결제는 읽음 표시 없이 남아 있다.
 */
fun inboxItems(records: List<CaptureRecord>, registered: Set<String>): List<InboxItem> = records.map { record ->
    val done = record.payment.dedupKey in registered
    InboxItem(payment = record.payment, isNew = !record.read && !done, registered = done)
}
