package com.dong.budget.ui.editor

import com.dong.budget.data.capture.TossDepositParser
import com.dong.budget.data.capture.TossPaymentParser
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.navigation.PrefillSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePrefillTest {
    private val payment = requireNotNull(TossPaymentParser.parse("133,500원 결제", "하나카드 | 비비큐 강동밀레니얼점(3개월)", 1_758_783_600_000L))

    @Test
    fun `스위치가 모두 켜져 있으면 카드와 할부를 채우고 분류와 새 카드도 맡긴다`() {
        val prefill = payment.toPrefill(AutoSettings())
        assertEquals(133_500L, prefill.amount)
        assertEquals("비비큐 강동밀레니얼점", prefill.merchant)
        assertEquals("하나카드", prefill.paymentName)
        assertEquals("3개월 할부", prefill.memo)
        assertTrue(prefill.guessCategory)
        assertTrue(prefill.addMissingCard)
    }

    @Test
    fun `채우기 스위치를 끄면 그 칸은 비워 둔다`() {
        val off =
            AutoSettings()
                .with(AutoOption.FILL_PAYMENT, false)
                .with(AutoOption.FILL_INSTALLMENT, false)
                .with(AutoOption.FILL_CATEGORY, false)
        val prefill = payment.toPrefill(off)
        assertNull(prefill.paymentName)
        assertNull(prefill.memo)
        assertFalse(prefill.guessCategory)
        // 카드 이름으로 고르지 않으면 새 카드도 만들지 않는다
        assertFalse(prefill.addMissingCard)
    }

    @Test
    fun `없는 카드 추가만 끄면 카드 이름은 넘기고 새로 만들지는 않는다`() {
        val prefill = payment.toPrefill(AutoSettings().with(AutoOption.FILL_NEW_CARD, false))
        assertEquals("하나카드", prefill.paymentName)
        assertFalse(prefill.addMissingCard)
    }

    @Test
    fun `입금 알림은 수입으로 열고 보낸 사람을 내용에 통장을 들어온 곳에 채운다`() {
        val deposit = requireNotNull(TossDepositParser.parse("200,000원 입금", "이동건 → 내 토스뱅크 통장", 1_760_279_340_000L))
        val prefill = deposit.toPrefill(AutoSettings())
        assertEquals(PrefillSource.DEPOSIT_ALERT, prefill.source)
        assertEquals(200_000L, prefill.amount)
        assertEquals("이동건", prefill.merchant)
        assertEquals("토스뱅크 통장", prefill.paymentName)
        assertNull(prefill.memo)
        assertEquals(deposit.dedupKey, prefill.dedupKey)
        assertTrue(prefill.guessCategory)
        assertTrue(prefill.addMissingCard)
        // 결제 알림은 지출로 연다
        assertEquals(PrefillSource.PAYMENT_ALERT, payment.toPrefill(AutoSettings()).source)
        // 통장 이름으로 고르기를 끄면 들어온 곳은 비워 둔다
        assertNull(deposit.toPrefill(AutoSettings().with(AutoOption.FILL_PAYMENT, false)).paymentName)
    }
}
