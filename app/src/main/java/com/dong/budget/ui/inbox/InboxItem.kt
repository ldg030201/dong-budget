package com.dong.budget.ui.inbox

import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.capture.CaptureRecord
import com.dong.budget.data.capture.CapturedPayment
import com.dong.budget.data.capture.PaymentCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 알림 화면의 한 줄. 지금은 결제 등록 알림뿐이다.
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

/** 알림 목록. 기록이 바뀌거나 등록·삭제로 등록 여부가 바뀌면 새로 내보낸다. 홈의 종과 알림 화면이 같이 쓴다. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeInbox(capture: PaymentCapture, repository: TransactionRepository): Flow<List<InboxItem>> = capture.records
    // 기록은 SharedPreferences 에서 JSON 을 풀어 읽는다. 메인 스레드 밖에서 한다.
    .flowOn(Dispatchers.IO)
    .flatMapLatest { records ->
        repository.observeRegisteredKeys(records.map { it.payment.dedupKey }).map { registered -> inboxItems(records, registered) }
    }
