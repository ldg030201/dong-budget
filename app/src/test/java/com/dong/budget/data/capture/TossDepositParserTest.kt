package com.dong.budget.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TossDepositParserTest {
    private val postedAt = 1_760_279_340_000L

    @Test
    fun `실제로 받은 입금 알림을 읽는다`() {
        // 2026-10-12 사용자 스크린샷. 알림 머리에 '토스뱅크' 가 보인다.
        val deposit = TossDepositParser.parse("200,000원 입금", "이동건 → 내 토스뱅크 통장", postedAt)!!
        assertEquals(CaptureKind.DEPOSIT, deposit.kind)
        assertTrue(deposit.isDeposit)
        assertEquals(200_000L, deposit.amount)
        assertEquals("이동건", deposit.merchant)
        // 통장 앞의 '내 ' 는 뗀다
        assertEquals("토스뱅크 통장", deposit.paymentName)
        assertNull(deposit.installmentMonths)
        assertEquals(postedAt, deposit.occurredAtMillis)
        assertEquals("toss-deposit:$postedAt:200000:이동건", deposit.dedupKey)
    }

    @Test
    fun `화살표 모양과 빈칸이 조금 달라도 읽는다`() {
        val bodies =
            listOf(
                "이동건 -> 내 토스뱅크 통장",
                "이동건→내 토스뱅크 통장",
                "이동건->내 토스뱅크 통장",
                // 줄 바꿈 없는 공백·폭 없는 공백이 섞여 와도 읽는다
                "이동건 → 내 토스뱅크​ 통장",
                // 둘째 줄이 붙어도 첫 줄만 읽는다
                "이동건 → 내 토스뱅크 통장\n잔액 1,234,567원",
            )
        bodies.forEach { body ->
            val deposit = TossDepositParser.parse("200,000원 입금", body, postedAt)
            assertEquals("[$body]", "이동건", deposit?.merchant)
            assertEquals("[$body]", "토스뱅크 통장", deposit?.paymentName)
        }
        assertEquals(5_000L, TossDepositParser.parse("5000원 입금", "홍길동 → 내 토스뱅크 통장", postedAt)?.amount)
        assertEquals(5_000L, TossDepositParser.parse(" 5,000 원 입금 ", "홍길동 → 내 토스뱅크 통장", postedAt)?.amount)
        // '내 ' 가 없으면 그대로 둔다
        assertEquals("모임통장", TossDepositParser.parse("5,000원 입금", "홍길동 → 모임통장", postedAt)?.paymentName)
    }

    @Test
    fun `아는 모양과 다르면 읽지 않는다`() {
        val cases =
            listOf(
                "200,000원 입금 취소" to "이동건 → 내 토스뱅크 통장",
                "200,000원 출금" to "내 토스뱅크 통장 → 이동건",
                "입금 200,000원" to "이동건 → 내 토스뱅크 통장",
                "0원 입금" to "이동건 → 내 토스뱅크 통장",
                "1,000,000,000,000원 입금" to "이동건 → 내 토스뱅크 통장",
                "200,000원 결제" to "이동건 → 내 토스뱅크 통장",
                // 화살표가 없다
                "200,000원 입금" to "이동건 님이 보냈어요",
                // 보낸 사람이나 통장이 비었다
                "200,000원 입금" to "→ 내 토스뱅크 통장",
                "200,000원 입금" to "이동건 → 내 ",
                "200,000원 입금" to "",
            )
        cases.forEach { (title, body) -> assertNull("[$title / $body]", TossDepositParser.parse(title, body, postedAt)) }
        assertNull(TossDepositParser.parse(null, "이동건 → 내 토스뱅크 통장", postedAt))
        assertNull(TossDepositParser.parse("200,000원 입금", null, postedAt))
        // 결제 알림 읽기는 입금 알림을 읽지 않는다
        assertNull(TossPaymentParser.parse("200,000원 입금", "이동건 → 내 토스뱅크 통장", postedAt))
    }

    @Test
    fun `같은 금액이 같은 때 와도 결제와 입금은 다른 거래다`() {
        val deposit = TossDepositParser.parse("15,000원 입금", "이동건 → 내 토스뱅크 통장", postedAt)!!
        val payment = TossPaymentParser.parse("15,000원 결제", "하나카드 | 가게", postedAt)!!
        assertFalse(deposit.isSamePaymentAs(payment))
        assertFalse(payment.isSamePaymentAs(deposit))
        // 입금끼리는 결제와 같은 규칙이다(금액이 같고 5초 안)
        val again = TossDepositParser.parse("15,000원 입금", "다른 이름 → 내 토스뱅크 통장", postedAt + 1_000)!!
        assertTrue(deposit.isSamePaymentAs(again))
    }
}
