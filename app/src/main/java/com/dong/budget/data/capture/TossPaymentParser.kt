package com.dong.budget.data.capture

import com.dong.budget.data.MAX_AMOUNT_DIGITS
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.abs

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
) {
    /** 할부면 '3개월 할부'. 묻는 알림과 등록창 메모가 같은 말을 쓴다. */
    val installmentLabel: String? get() = installmentMonths?.let { "${it}개월 할부" }

    /**
     * 다른 알림으로 온 같은 결제인지. 토스는 결제 하나를 모양이 다른 알림 두 개로 보내기도 한다.
     *   '46,500원 결제' + '하나카드 | 가게(일시불)', '46,500원 결제 완료' + '원더카드2.0 Life ・ 가게(일시불)'
     * 카드 이름은 알림마다 달라서 보지 않는다. 금액과 가게가 같고 시각이 [SAME_PAYMENT_WINDOW_MS] 안이면 같은 결제다.
     */
    fun isSamePaymentAs(other: CapturedPayment): Boolean = amount == other.amount &&
        merchant == other.merchant &&
        abs(occurredAtMillis - other.occurredAtMillis) <= SAME_PAYMENT_WINDOW_MS

    companion object {
        /** 같은 결제의 알림 두 개가 이만큼 안에 온다. 같은 가게에서 같은 금액을 이 안에 두 번 결제하면 하나로 본다. */
        const val SAME_PAYMENT_WINDOW_MS = 3_000L
    }
}

/**
 * 토스 결제 알림을 읽는다.
 *
 * 지금 아는 모양은 세 가지다.
 *   제목: "133,500원 결제"       본문: "하나카드 | 비비큐 강동밀레니얼점(일시불)"
 *   제목: "15,000원 결제 완료"   본문: "토스뱅크 ・ 구글페이먼트코리아 유한회사" (토스뱅크 카드. 둘째 줄에 혜택 안내가 붙는다)
 *   제목: "6원 캐시백 🎉"        본문: "2,300원 결제 | 세븐일레븐 강동열린점" + 둘째 줄 "잔액 146,658원(토스뱅크 체크카드)"
 *     (토스뱅크 체크카드. 캐시백이 붙으면 제목에 캐시백이, 본문에 결제 금액과 가게가, 둘째 줄 괄호에 카드 이름이 온다)
 * 이 모양과 딱 맞을 때만 읽고, 조금이라도 다르면 null 을 준다. 결제 취소나 입금처럼 모르는 알림을
 * 결제로 잘못 읽으면 가계부가 틀어지므로, 새 모양은 실제 문구를 받은 뒤에 추가한다.
 */
object TossPaymentParser {
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
     * 빈칸은 normalize 가 보통 공백 하나로 바꿔 두므로 그대로 적는다.
     */
    private val dotSeparator = Regex(" [\u00B7\u2022\u2027\u2219\u22C5\u30FB\uFF65] ")

    /** 가게 이름 끝의 할부 표시. '(일시불)', '(3개월)', '(3개월 할부)', '(할부 3개월)' */
    private val installmentPattern = Regex("""\(\s*(?:일시불|(\d{1,2})\s*개월(?:\s*할부)?|할부\s*(\d{1,2})\s*개월)\s*\)\s*$""")

    /** @param postedAtMillis 결제 시각. 알림에 적힌 시각이다. */
    fun parse(title: CharSequence?, text: CharSequence?, postedAtMillis: Long): CapturedPayment? {
        val cleanTitle = normalize(title) ?: return null
        // 결제 내용은 본문 첫 줄에 있다. 체크카드는 둘째 줄에 잔액이 붙는다('잔액 1,865,204원').
        val lines = text?.lineSequence()?.mapNotNull(::normalize)?.toList().orEmpty()
        val parts = paymentParts(cleanTitle, lines) ?: cashbackParts(cleanTitle, lines) ?: return null

        val amountDigits = parts.amountDigits.replace(",", "")
        if (amountDigits.length > MAX_AMOUNT_DIGITS) return null
        val amount = amountDigits.toLongOrNull()?.takeIf { it > 0 } ?: return null

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
