package com.dong.budget.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TossPaymentParserTest {
    private val postedAt = 1_758_783_600_000L

    @Test
    fun `실제로 받은 결제 알림을 읽는다`() {
        val payment = TossPaymentParser.parse("133,500원 결제", "하나카드 | 비비큐 강동밀레니얼점(일시불)", postedAt)!!
        assertEquals(133_500L, payment.amount)
        assertEquals("하나카드", payment.paymentName)
        assertEquals("비비큐 강동밀레니얼점", payment.merchant)
        assertNull(payment.installmentMonths)
        assertEquals(postedAt, payment.occurredAtMillis)
    }

    @Test
    fun `할부는 개월 수를 따로 빼낸다`() {
        val payment = TossPaymentParser.parse("1,200,000원 결제", "하나카드 | 전자랜드(12개월)", postedAt)!!
        assertEquals("전자랜드", payment.merchant)
        assertEquals(12, payment.installmentMonths)
        assertEquals(3, TossPaymentParser.parse("30,000원 결제", "하나카드 | 가게(할부 3개월)", postedAt)!!.installmentMonths)
    }

    @Test
    fun `눈에 안 보이는 특수 공백이 섞여도 읽는다`() {
        val payment = TossPaymentParser.parse("133,500 원 결제", "하나카드 | 비비큐​ 강동점 (일시불)", postedAt)!!
        assertEquals(133_500L, payment.amount)
        assertEquals("하나카드", payment.paymentName)
        assertEquals("비비큐 강동점", payment.merchant)
    }

    @Test
    fun `체크카드 알림의 둘째 줄 잔액은 읽지 않는다`() {
        val payment = TossPaymentParser.parse("1,417원 결제", "토스뱅크 체크카드 | 토스페이_게임_TOSS\n잔액 1,865,204원", postedAt)!!
        assertEquals(1_417L, payment.amount)
        assertEquals("토스뱅크 체크카드", payment.paymentName)
        assertEquals("토스페이_게임_TOSS", payment.merchant)
    }

    @Test
    fun `가게 이름 가운데 괄호는 남기고 끝의 결제 구분만 뗀다`() {
        assertEquals("씨유(CU)화곡시원점", TossPaymentParser.parse("1,000원 결제", "비씨체크 | 씨유(CU)화곡시원점(일시불)", postedAt)!!.merchant)
        assertEquals("쿠팡(쿠페이)", TossPaymentParser.parse("19,800원 결제", "KB국민체크 | 쿠팡(쿠페이)(일시불)", postedAt)!!.merchant)
    }

    @Test
    fun `전각 문자와 글자 방향 표시가 섞여도 읽는다`() {
        val payment = TossPaymentParser.parse("１３３，５００원\u00A0결제", "\u200E하나카드\u3000｜\u2009비비큐(일시불)", postedAt)!!
        assertEquals(133_500L, payment.amount)
        assertEquals("하나카드", payment.paymentName)
        assertEquals("비비큐", payment.merchant)
    }

    @Test
    fun `카드 이름이 없으면 가게만 읽는다`() {
        val payment = TossPaymentParser.parse("5,000원 결제", "스타벅스 강남점", postedAt)!!
        assertNull(payment.paymentName)
        assertEquals("스타벅스 강남점", payment.merchant)
    }

    @Test
    fun `모르는 모양의 알림은 읽지 않는다`() {
        // 결제 취소, 입금, 다른 문구가 붙은 제목은 결제로 잘못 읽으면 안 된다
        assertNull(TossPaymentParser.parse("133,500원 결제 취소", "하나카드 | 비비큐", postedAt))
        assertNull(TossPaymentParser.parse("50,000원 입금", "홍길동", postedAt))
        assertNull(TossPaymentParser.parse("토스뱅크", "133,500원 결제", postedAt))
        assertNull(TossPaymentParser.parse("0원 결제", "하나카드 | 가게", postedAt))
        assertNull(TossPaymentParser.parse(null, "하나카드 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("1,000원 결제", null, postedAt))
    }

    @Test
    fun `너무 큰 금액은 읽지 않는다`() {
        assertNull(TossPaymentParser.parse("1,000,000,000,000원 결제", "카드 | 가게", postedAt))
    }

    @Test
    fun `같은 알림은 같은 열쇠를 만든다`() {
        val a = TossPaymentParser.parse("133,500원 결제", "하나카드 | 비비큐(일시불)", postedAt)!!
        val b = TossPaymentParser.parse("133,500원 결제", "하나카드 | 비비큐(일시불)", postedAt)!!
        assertEquals(a.dedupKey, b.dedupKey)
    }
}
