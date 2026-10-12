package com.dong.budget.data.capture

/**
 * 토스 입금 알림을 읽는다.
 *
 * 지금 아는 모양은 하나다(2026-10-12 사용자 스크린샷, 알림 머리에 '토스뱅크'. 토스 앱이 올린다고 본다).
 *   제목: "200,000원 입금"   본문: "이동건 → 내 토스뱅크 통장"
 * 본문 첫 줄의 화살표 앞이 보낸 사람, 뒤가 들어온 통장이다. 통장 앞의 '내 ' 는 뗀다('토스뱅크 통장').
 * 화살표는 '→' 와 '->' 를 받는다. 앞뒤 빈칸은 있어도 없어도 된다.
 * 이 모양과 딱 맞을 때만 읽는다([PaymentParser]). '입금 취소' 처럼 제목에 다른 말이 붙거나 화살표가 없으면 읽지 않는다.
 * 내 다른 통장에서 옮긴 돈인지는 가리지 않는다. 사용자가 등록창에서 보고 고르거나 묻는 알림을 지운다(2026-10-12 사용자 결정).
 */
object TossDepositParser : PaymentParser {
    /** 제목 전체가 '금액원 입금' 이어야 한다 */
    private val titlePattern = Regex("""^([0-9][0-9,]*)\s*원\s*입금$""")

    /** 본문 첫 줄. 보낸 사람 이름에 화살표가 또 들어갈 일은 드물어 첫 화살표로 나눈다. */
    private val detailPattern = Regex("""^(.+?)\s*(?:→|->)\s*(.+)$""")

    /** 들어온 통장 앞에 붙는 '내 '. 통장 이름 없이 '내' 만 있으면 모두 지워 빈 이름이 된다. '내통장' 처럼 붙은 이름은 그대로 둔다. */
    private val minePrefix = Regex("""^내(?: |$)""")

    override fun parse(title: CharSequence?, text: CharSequence?, postedAtMillis: Long): CapturedPayment? {
        val cleanTitle = normalizeNotificationText(title) ?: return null
        val amount = titlePattern.matchEntire(cleanTitle)?.groupValues?.get(1)?.let(::parseAmount) ?: return null
        val detail = text?.lineSequence()?.firstNotNullOfOrNull(::normalizeNotificationText) ?: return null
        val match = detailPattern.matchEntire(detail) ?: return null
        val sender = match.groupValues[1].trim().ifEmpty { return null }
        val account = match.groupValues[2].trim().replaceFirst(minePrefix, "").trim().ifEmpty { return null }
        return CapturedPayment(
            amount = amount,
            paymentName = account,
            merchant = sender,
            installmentMonths = null,
            occurredAtMillis = postedAtMillis,
            dedupKey = "toss-deposit:$postedAtMillis:$amount:$sender",
            kind = CaptureKind.DEPOSIT,
        )
    }
}
