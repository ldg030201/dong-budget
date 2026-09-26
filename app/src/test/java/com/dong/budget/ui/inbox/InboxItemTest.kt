package com.dong.budget.ui.inbox

import com.dong.budget.data.capture.CaptureRecord
import com.dong.budget.data.capture.CapturedPayment
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxItemTest {
    private fun record(key: String, read: Boolean) = CaptureRecord(
        CapturedPayment(
            amount = 1_000,
            paymentName = null,
            merchant = key,
            installmentMonths = null,
            occurredAtMillis = 0,
            dedupKey = key,
        ),
        read,
    )

    @Test
    fun `읽지 않은 결제만 새 알림이고, 등록한 결제는 읽음 표시가 없어도 새 알림이 아니다`() {
        val items =
            inboxItems(listOf(record("a", read = false), record("b", read = true), record("c", read = false)), registered = setOf("c"))
        assertEquals(listOf("a", "b", "c"), items.map { it.payment.dedupKey })
        assertEquals(listOf(true, false, false), items.map { it.isNew })
        assertEquals(listOf(false, false, true), items.map { it.registered })
    }
}
