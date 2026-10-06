package com.dong.budget.data.capture

/**
 * 카카오페이 결제 알림을 읽는다.
 *
 * 지금 아는 모양은 하나다(2026-10-06 사용자 스크린샷).
 *   제목: "결제가 완료되었어요"   본문: "주식회사 카카오에서 2,500원을 결제했어요."
 * 알림에 카드 이름이 없어서 결제수단은 '카카오페이' 로 둔다. 페이머니로 냈는지 연결한 카드로 냈는지는 알 수 없다.
 * 이 모양과 딱 맞을 때만 읽는다([PaymentParser]). 송금·충전·결제 취소 알림은 모양이 달라 읽지 않는다.
 */
object KakaoPayPaymentParser : PaymentParser {
    /** 결제수단 이름. 알림에 카드 이름이 없다. */
    const val PAYMENT_NAME = "카카오페이"

    private const val TITLE = "결제가 완료되었어요"

    /** 본문 첫 줄. 가게 이름에 '에서' 가 들어 있어도 금액 바로 앞의 '에서' 로 나눈다. */
    private val detailPattern = Regex("""^(.+)에서\s*([0-9][0-9,]*)\s*원을\s*결제했어요\.?$""")

    override fun parse(title: CharSequence?, text: CharSequence?, postedAtMillis: Long): CapturedPayment? {
        if (normalizeNotificationText(title) != TITLE) return null
        val detail = text?.lineSequence()?.firstNotNullOfOrNull(::normalizeNotificationText) ?: return null
        val match = detailPattern.matchEntire(detail) ?: return null
        val merchant = match.groupValues[1].trim().ifEmpty { return null }
        val amount = parseAmount(match.groupValues[2]) ?: return null
        return CapturedPayment(
            amount = amount,
            paymentName = PAYMENT_NAME,
            merchant = merchant,
            installmentMonths = null,
            occurredAtMillis = postedAtMillis,
            dedupKey = "kakaopay:$postedAtMillis:$amount:$merchant",
        )
    }
}
