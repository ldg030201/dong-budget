package com.dong.budget.data.capture

import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * 결제 알림에서 읽어낸 한 건. 아무것도 저장하지 않은 '제안' 이다.
 * 사용자가 등록창에서 확인하고 저장해야 거래가 된다.
 *
 * @property paymentName 카드 이름(예: 하나카드). 알림에 없으면 null
 * @property installmentMonths 할부 개월 수. 일시불이거나 표시가 없으면 null
 * @property occurredAtMillis 결제 시각. 토스 알림에 적힌 시각을 쓴다(PaymentCapture.paymentTime).
 * @property dedupKey 같은 결제를 두 번 등록하지 않기 위한 열쇠. 거래의 dedupKey 칸에 저장된다.
 */
@Serializable
data class CapturedPayment(
    val amount: Long,
    val paymentName: String?,
    val merchant: String,
    val installmentMonths: Int?,
    val occurredAtMillis: Long,
    val dedupKey: String,
)

/**
 * 토스 결제 알림을 읽는다.
 *
 * 지금 아는 모양은 하나뿐이다.
 *   제목: "133,500원 결제"
 *   본문: "하나카드 | 비비큐 강동밀레니얼점(일시불)"
 * 이 모양과 딱 맞을 때만 읽고, 조금이라도 다르면 null 을 준다. 결제 취소나 입금처럼 모르는 알림을
 * 결제로 잘못 읽으면 가계부가 틀어지므로, 새 모양은 실제 문구를 받은 뒤에 추가한다.
 */
object TossPaymentParser {
    /** 제목 전체가 '금액원 결제' 여야 한다. '결제 취소' 처럼 뒤에 무엇이 붙으면 맞지 않는다. */
    private val titlePattern = Regex("""^([0-9][0-9,]*)\s*원\s*결제$""")

    /** 가게 이름 끝의 할부 표시. '(일시불)', '(3개월)', '(3개월 할부)', '(할부 3개월)' */
    private val installmentPattern = Regex("""\(\s*(?:일시불|(\d{1,2})\s*개월(?:\s*할부)?|할부\s*(\d{1,2})\s*개월)\s*\)\s*$""")

    /** 원 단위 금액 자리수 상한. 등록 화면과 같게 둔다(조 단위까지). */
    private const val MAX_AMOUNT_DIGITS = 12

    /** @param postedAtMillis 결제 시각. 알림에 적힌 시각이다. */
    fun parse(title: CharSequence?, text: CharSequence?, postedAtMillis: Long): CapturedPayment? {
        val cleanTitle = normalize(title) ?: return null
        // 체크카드는 둘째 줄에 잔액이 붙는다('잔액 1,865,204원'). 결제 내용은 첫 줄에만 있다.
        val cleanText = normalize(text?.lineSequence()?.firstOrNull { it.isNotBlank() }) ?: return null

        val amountDigits = titlePattern.matchEntire(cleanTitle)?.groupValues?.get(1)?.replace(",", "") ?: return null
        if (amountDigits.length > MAX_AMOUNT_DIGITS) return null
        val amount = amountDigits.toLongOrNull()?.takeIf { it > 0 } ?: return null

        // 카드 이름과 가게 사이는 '|' 로 나뉜다. 가게 이름에 '|' 가 들어갈 수도 있으니 첫 번째 것만 나눈다.
        val card = cleanText.substringBefore('|', missingDelimiterValue = "").trim().ifEmpty { null }
        var merchant = if ('|' in cleanText) cleanText.substringAfter('|').trim() else cleanText
        var months: Int? = null
        installmentPattern.find(merchant)?.let { match ->
            months = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()?.takeIf { it > 1 }
            merchant = merchant.removeRange(match.range).trim()
        }

        return CapturedPayment(
            amount = amount,
            paymentName = card,
            merchant = merchant,
            installmentMonths = months,
            occurredAtMillis = postedAtMillis,
            dedupKey = "toss:$postedAtMillis:$amount:$merchant",
        )
    }

    /**
     * 알림 글자에 섞여 오는 특수 문자를 보통 문자로 바꾸고, 공백 여러 개를 하나로 줄인다.
     *
     * NFKC 는 줄 바꿈 없는 공백(NBSP)·좁은 공백·전각 공백을 보통 공백으로, 전각 '｜'·숫자·쉼표를 보통 문자로 바꾼다.
     * 폭 없는 공백과 글자 방향 표시는 눈에 안 보여서 정규식이 조용히 어긋나므로 지운다.
     * 공백은 직접 나열해서 줄인다. 정규식의 \s 가 NBSP 를 포함하는지는 JVM 과 Android 가 달라서
     * 단위 테스트와 실제 기기의 결과가 어긋날 수 있다.
     */
    private fun normalize(value: CharSequence?): String? {
        if (value == null) return null
        return Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .replace(invisible, "")
            .replace(spaces, " ")
            .trim()
            .ifEmpty { null }
    }

    private val invisible = Regex("[\\u200B-\\u200F\\u2060\\uFEFF]")
    private val spaces = Regex("[ \\t\\n\\r\\f\\u000B\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+")
}
