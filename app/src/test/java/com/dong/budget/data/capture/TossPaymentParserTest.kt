package com.dong.budget.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `토스뱅크 카드의 결제 완료 알림도 읽는다`() {
        // 2026-09-27 에 받은 실제 알림. 둘째 줄의 혜택 안내는 읽지 않는다.
        val payment =
            TossPaymentParser.parse("15,000원 결제 완료", "토스뱅크 ・ 구글페이먼트코리아 유한회사\n결제한 돈 일부를 돌려받을 수 있어요.", postedAt)!!
        assertEquals(15_000L, payment.amount)
        assertEquals("토스뱅크", payment.paymentName)
        assertEquals("구글페이먼트코리아 유한회사", payment.merchant)
    }

    @Test
    fun `토스뱅크 체크카드의 캐시백 알림은 본문에서 결제 금액과 가게를, 둘째 줄에서 카드를 읽는다`() {
        // 2026-09-29 에 받은 실제 알림 두 개. 제목의 캐시백 금액은 결제 금액이 아니다.
        val seven =
            TossPaymentParser.parse("6원 캐시백 🎉", "2,300원 결제 | 세븐일레븐 강동열린점\n잔액 146,658원(토스뱅크 체크카드)", postedAt)!!
        assertEquals(2_300L, seven.amount)
        assertEquals("토스뱅크 체크카드", seven.paymentName)
        assertEquals("세븐일레븐 강동열린점", seven.merchant)
        assertNull(seven.installmentMonths)

        val ice =
            TossPaymentParser.parse("14원 캐시백 🎉", "4,800원 결제 | 얼음왕국아이스크림할인점힐스테\n잔액 148,958원(토스뱅크 체크카드)", postedAt)!!
        assertEquals(4_800L, ice.amount)
        assertEquals("토스뱅크 체크카드", ice.paymentName)
        assertEquals("얼음왕국아이스크림할인점힐스테", ice.merchant)

        // 같은 결제가 '금액원 결제' 알림으로도 오면 같은 결제로 본다
        assertTrue(seven.isSamePaymentAs(TossPaymentParser.parse("2,300원 결제", "토스뱅크 체크카드 | 세븐일레븐 강동열린점", postedAt)!!))
    }

    @Test
    fun `캐시백 알림은 제목 끝 기호가 달라도, 둘째 줄이 없어도 읽는다`() {
        listOf("6원 캐시백", "6원 캐시백 🎉", "6원 캐시백🎉", "6원 캐시백 🎁✨", "1,200원 캐시백 🎉\uFE0F", "6원 캐시백!").forEach { title ->
            val payment = TossPaymentParser.parse(title, "2,300원 결제 | 가게\n잔액 1,000원(토스뱅크 체크카드)", postedAt)
            assertEquals("[$title]", 2_300L, payment?.amount)
        }
        // 첫 줄만 담긴 본문이면 카드 이름 없이 읽는다
        val oneLine = TossPaymentParser.parse("6원 캐시백 🎉", "2,300원 결제 | 세븐일레븐 강동열린점", postedAt)!!
        assertNull(oneLine.paymentName)
        assertEquals("세븐일레븐 강동열린점", oneLine.merchant)
        // 둘째 줄이 잔액 모양이 아니면 괄호 안을 카드 이름으로 읽지 않는다
        assertNull(TossPaymentParser.parse("6원 캐시백 🎉", "2,300원 결제 | 가게\n일부를 돌려받았어요(토스뱅크)", postedAt)!!.paymentName)
    }

    @Test
    fun `똑같아 보이는 가운뎃점은 모두 구분자로 읽는다`() {
        listOf("・", "･", "·", "•", "\u00A0・\u00A0").forEach { dot ->
            val payment = TossPaymentParser.parse("15,000원 결제 완료", "토스뱅크${if (dot.length == 1) " $dot " else dot}가게", postedAt)!!
            assertEquals("[$dot]", "토스뱅크", payment.paymentName)
            assertEquals("[$dot]", "가게", payment.merchant)
        }
    }

    @Test
    fun `가게 이름 안에 붙은 가운뎃점은 자르지 않는다`() {
        val payment = TossPaymentParser.parse("15,000원 결제 완료", "토스뱅크 ・ 스타벅스·강남점", postedAt)!!
        assertEquals("토스뱅크", payment.paymentName)
        assertEquals("스타벅스·강남점", payment.merchant)
        // 빈칸 없이 붙은 가운뎃점만 있으면 나누지 않고 가게로 본다
        val noCard = TossPaymentParser.parse("5,000원 결제", "커피·빵", postedAt)!!
        assertNull(noCard.paymentName)
        assertEquals("커피·빵", noCard.merchant)
        // '|' 가 있으면 그쪽으로 나눈다(원래 모양)
        val pipe = TossPaymentParser.parse("5,000원 결제", "하나카드 | 빵 · 커피", postedAt)!!
        assertEquals("하나카드", pipe.paymentName)
        assertEquals("빵 · 커피", pipe.merchant)
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
        assertNull(TossPaymentParser.parse("15,000원 결제 취소 완료", "토스뱅크 ・ 가게", postedAt))
        assertNull(TossPaymentParser.parse("15,000원 결제 완료됨", "토스뱅크 ・ 가게", postedAt))
        assertNull(TossPaymentParser.parse("50,000원 입금", "홍길동", postedAt))
        assertNull(TossPaymentParser.parse("토스뱅크", "133,500원 결제", postedAt))
        assertNull(TossPaymentParser.parse("0원 결제", "하나카드 | 가게", postedAt))
        assertNull(TossPaymentParser.parse(null, "하나카드 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("1,000원 결제", null, postedAt))
        // 캐시백 알림도 제목과 본문 첫 줄이 딱 맞을 때만 읽는다. 제목의 캐시백 금액만으로는 결제로 보지 않는다.
        assertNull(TossPaymentParser.parse("6원 캐시백 취소", "2,300원 결제 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("6원 캐시백 받았어요", "2,300원 결제 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("캐시백 🎉", "2,300원 결제 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("6원 캐시백 🎉", "2,300원 결제 취소 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("6원 캐시백 🎉", "토스뱅크 체크카드 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("6원 캐시백 🎉", "캐시백이 쌓였어요", postedAt))
        assertNull(TossPaymentParser.parse("6원 캐시백 🎉", "0원 결제 | 가게", postedAt))
        assertNull(TossPaymentParser.parse("6원 캐시백 🎉", null, postedAt))
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
