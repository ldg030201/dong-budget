package com.dong.budget.data.capture

/**
 * 토스 결제 알림을 읽는다.
 *
 * 지금 아는 모양은 세 가지다.
 *   제목: "133,500원 결제"       본문: "하나카드 | 비비큐 강동밀레니얼점(일시불)"
 *   제목: "15,000원 결제 완료"   본문: "토스뱅크 ・ 구글페이먼트코리아 유한회사" (토스뱅크 카드. 둘째 줄에 혜택 안내가 붙는다)
 *   제목: "6원 캐시백 🎉"        본문: "2,300원 결제 | 세븐일레븐 강동열린점" + 둘째 줄 "잔액 146,658원(토스뱅크 체크카드)"
 *     (토스뱅크 체크카드. 캐시백이 붙으면 제목에 캐시백이, 본문에 결제 금액과 가게가, 둘째 줄 괄호에 카드 이름이 온다)
 * 이 모양과 딱 맞을 때만 읽는다([PaymentParser]).
 */
object TossPaymentParser : PaymentParser {
    /** 제목 전체가 '금액원 결제' 나 '금액원 결제 완료' 여야 한다. '결제 취소' 처럼 다른 말이 붙으면 맞지 않는다. */
    private val titlePattern = Regex("""^([0-9][0-9,]*)\s*원\s*결제(?:\s*완료)?$""")

    /**
     * 캐시백 알림의 제목. '금액원 캐시백' 뒤에는 이모지 같은 기호만 올 수 있다. '캐시백 취소' 처럼 말이 붙으면 맞지 않는다.
     * 이 금액은 돌려받은 돈이라 쓰지 않는다. 결제 금액은 본문에 있다.
     */
    private val cashbackTitlePattern = Regex("""^[0-9][0-9,]*\s*원\s*캐시백[^\p{L}\p{N}]*$""")

    /** 캐시백 알림 본문 첫 줄. "2,300원 결제 | 세븐일레븐 강동열린점". 금액과 가게가 여기에 있다. */
    private val cashbackPaidPattern = Regex("""^([0-9][0-9,]*)\s*원\s*결제\s*\|\s*(.+)$""")

    /** 캐시백 알림 본문 둘째 줄. "잔액 146,658원(토스뱅크 체크카드)". 괄호 안이 카드 이름이다. */
    private val balancePattern = Regex("""^잔액\s*[0-9][0-9,]*\s*원\s*\((.+)\)$""")

    /**
     * 카드 이름과 가게 사이의 가운뎃점. 토스뱅크 알림이 쓴다. 화면에서 똑같아 보이는 글자가 여럿이라 모두 받는다.
     * 앞뒤에 빈칸이 있을 때만 나눈다. '스타벅스·강남' 처럼 가게 이름 안에 붙은 가운뎃점은 자르지 않는다.
     * 빈칸은 normalizeNotificationText 가 보통 공백 하나로 바꿔 두므로 그대로 적는다.
     */
    private val dotSeparator = Regex(" [\u00B7\u2022\u2027\u2219\u22C5\u30FB\uFF65] ")

    /** 가게 이름 끝의 할부 표시. '(일시불)', '(3개월)', '(3개월 할부)', '(할부 3개월)' */
    private val installmentPattern = Regex("""\(\s*(?:일시불|(\d{1,2})\s*개월(?:\s*할부)?|할부\s*(\d{1,2})\s*개월)\s*\)\s*$""")

    override fun parse(title: CharSequence?, text: CharSequence?, postedAtMillis: Long): CapturedPayment? {
        val cleanTitle = normalizeNotificationText(title) ?: return null
        // 결제 내용은 본문 첫 줄에 있다. 체크카드는 둘째 줄에 잔액이 붙는다('잔액 1,865,204원').
        val lines = text?.lineSequence()?.mapNotNull(::normalizeNotificationText)?.toList().orEmpty()
        val parts = paymentParts(cleanTitle, lines) ?: cashbackParts(cleanTitle, lines) ?: return null
        val amount = parseAmount(parts.amountDigits) ?: return null

        var merchant = parts.merchant
        var months: Int? = null
        installmentPattern.find(merchant)?.let { match ->
            months = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()?.takeIf { it > 1 }
            merchant = merchant.removeRange(match.range).trim()
        }

        return CapturedPayment(
            amount = amount,
            paymentName = parts.card,
            merchant = merchant,
            installmentMonths = months,
            occurredAtMillis = postedAtMillis,
            dedupKey = "toss:$postedAtMillis:$amount:$merchant",
        )
    }

    /** 알림에서 읽은 결제 조각. 금액은 아직 쉼표가 붙은 글자다. 가게 끝에는 할부 표시가 붙어 있을 수 있다. */
    private class Parts(val amountDigits: String, val card: String?, val merchant: String)

    /** 제목이 '금액원 결제' 인 알림. 본문 첫 줄이 '카드 | 가게' 나 '카드 ・ 가게' 다. 둘째 줄은 읽지 않는다. */
    private fun paymentParts(title: String, lines: List<String>): Parts? {
        val amountDigits = titlePattern.matchEntire(title)?.groupValues?.get(1) ?: return null
        val detail = lines.firstOrNull() ?: return null
        // 카드 이름과 가게 사이는 '|' 로 나뉜다. 없으면 빈칸을 둔 가운뎃점으로 나뉜 것(토스뱅크)을 본다.
        // 가게 이름에 구분자가 또 들어갈 수 있으니 첫 번째 것만 나눈다.
        val separator = if ('|' in detail) detail.indexOf('|').let { it..it } else dotSeparator.find(detail)?.range
        val card = separator?.let { detail.substring(0, it.first).trim().ifEmpty { null } }
        val merchant = separator?.let { detail.substring(it.last + 1).trim() } ?: detail
        return Parts(amountDigits, card, merchant)
    }

    /**
     * 제목이 '금액원 캐시백' 인 알림(토스뱅크 체크카드). 본문 첫 줄이 '금액원 결제 | 가게' 여야 한다.
     * 카드 이름은 둘째 줄 '잔액 N원(카드)' 의 괄호에서 꺼낸다. 둘째 줄이 없거나 모양이 다르면 카드 이름 없이 읽는다.
     */
    private fun cashbackParts(title: String, lines: List<String>): Parts? {
        if (!cashbackTitlePattern.matches(title)) return null
        val paid = cashbackPaidPattern.matchEntire(lines.firstOrNull() ?: return null) ?: return null
        val card = lines.getOrNull(1)?.let { balancePattern.matchEntire(it) }?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        return Parts(paid.groupValues[1], card, paid.groupValues[2].trim())
    }
}
