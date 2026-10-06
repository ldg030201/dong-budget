package com.dong.budget.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KakaoPayPaymentParserTest {
    private val postedAt = 1_758_783_600_000L

    @Test
    fun `실제로 받은 결제 알림을 읽는다`() {
        // 2026-10-06 에 받은 실제 알림
        val payment = KakaoPayPaymentParser.parse("결제가 완료되었어요", "주식회사 카카오에서 2,500원을 결제했어요.", postedAt)!!
        assertEquals(2_500L, payment.amount)
        assertEquals("카카오페이", payment.paymentName)
        assertEquals("주식회사 카카오", payment.merchant)
        assertNull(payment.installmentMonths)
        assertEquals(postedAt, payment.occurredAtMillis)
        assertEquals("kakaopay:$postedAt:2500:주식회사 카카오", payment.dedupKey)
    }

    @Test
    fun `가게 이름에 '에서' 가 들어 있어도 금액 바로 앞에서 나눈다`() {
        val payment = KakaoPayPaymentParser.parse("결제가 완료되었어요", "바다에서 온 횟집에서 38,000원을 결제했어요.", postedAt)!!
        assertEquals(38_000L, payment.amount)
        assertEquals("바다에서 온 횟집", payment.merchant)
    }

    @Test
    fun `마침표가 없거나 특수 공백이 섞여도 읽고, 둘째 줄은 보지 않는다`() {
        val payment =
            KakaoPayPaymentParser.parse("결제가 완료되었어요", "GS25 강남점에서 1,200원을 결제했어요\n쿠폰을 확인해 보세요", postedAt)!!
        assertEquals(1_200L, payment.amount)
        assertEquals("GS25 강남점", payment.merchant)
    }

    @Test
    fun `모르는 모양의 알림은 읽지 않는다`() {
        // 결제 취소·송금·충전처럼 다른 알림을 결제로 잘못 읽으면 안 된다
        assertNull(KakaoPayPaymentParser.parse("결제가 취소되었어요", "주식회사 카카오에서 2,500원을 결제했어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse("결제가 완료되었어요", "주식회사 카카오에서 2,500원을 결제 취소했어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse("송금 완료", "홍길동님에게 2,500원을 보냈어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse("결제가 완료되었어요", "2,500원을 결제했어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse("결제가 완료되었어요", "주식회사 카카오에서 0원을 결제했어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse("결제가 완료되었어요", "주식회사 카카오에서 1,000,000,000,000원을 결제했어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse(null, "주식회사 카카오에서 2,500원을 결제했어요.", postedAt))
        assertNull(KakaoPayPaymentParser.parse("결제가 완료되었어요", null, postedAt))
        // 토스 모양도 읽지 않는다
        assertNull(KakaoPayPaymentParser.parse("2,500원 결제", "하나카드 | 카카오", postedAt))
    }
}
