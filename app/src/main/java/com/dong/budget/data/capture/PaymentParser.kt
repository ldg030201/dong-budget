package com.dong.budget.data.capture

import com.dong.budget.data.MAX_AMOUNT_DIGITS
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.abs

/**
 * 결제 알림에서 읽어낸 한 건. 아무것도 저장하지 않은 '제안' 이다.
 * 사용자가 등록창에서 확인하고 저장해야 거래가 된다.
 *
 * @property paymentName 카드 이름(예: 하나카드). 카카오페이 알림은 '카카오페이'. 알림에 없으면 null
 * @property installmentMonths 할부 개월 수. 일시불이거나 표시가 없으면 null
 * @property occurredAtMillis 결제 시각. 결제 알림에 적힌 시각을 쓴다(PaymentCapture.paymentTime).
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
     *   (페이스페이) '하나카드 · 씨유(CU) 과천디엠점', '페이스페이(원더카드2.0 Life) · CU 과천디엠점'
     * 카드 이름과 가게 이름은 알림마다 다르게 올 수 있어 보지 않는다. 금액이 같고 시각이 [SAME_PAYMENT_WINDOW_MS] 안이면 같은 결제다.
     * 다른 앱의 알림끼리도 똑같이 본다(카카오페이로 낸 결제의 카드 알림이 토스로도 오는 경우).
     */
    fun isSamePaymentAs(other: CapturedPayment): Boolean = amount == other.amount &&
        abs(occurredAtMillis - other.occurredAtMillis) <= SAME_PAYMENT_WINDOW_MS

    companion object {
        /** 같은 결제의 알림 두 개가 이만큼 안에 온다. 같은 금액을 이 안에 두 번 결제하면 가게가 달라도 하나로 본다. */
        const val SAME_PAYMENT_WINDOW_MS = 3_000L
    }
}

/**
 * 결제 알림 하나를 읽는다. 앱마다 알림 모양이 달라 앱마다 따로 둔다(PaymentCapture.parsersOf).
 *
 * 아는 모양과 딱 맞을 때만 읽고, 조금이라도 다르면 null 을 준다. 결제 취소나 입금처럼 모르는 알림을
 * 결제로 잘못 읽으면 가계부가 틀어지므로, 새 모양은 실제 문구를 받은 뒤에 추가한다.
 */
fun interface PaymentParser {
    /** @param postedAtMillis 결제 시각. 알림에 적힌 시각이다. */
    fun parse(title: CharSequence?, text: CharSequence?, postedAtMillis: Long): CapturedPayment?
}

/** 알림의 금액 글자('133,500')를 숫자로. 0원이거나 너무 길면 null */
internal fun parseAmount(digits: String): Long? {
    val plain = digits.replace(",", "")
    if (plain.length > MAX_AMOUNT_DIGITS) return null
    return plain.toLongOrNull()?.takeIf { it > 0 }
}

/**
 * 알림 글자에 섞여 오는 특수 문자를 보통 문자로 바꾸고, 공백 여러 개를 하나로 줄인다. 비면 null.
 *
 * NFKC 는 줄 바꿈 없는 공백(NBSP)·좁은 공백·전각 공백을 보통 공백으로, 전각 '｜'·숫자·쉼표를 보통 문자로 바꾼다.
 * 폭 없는 공백과 글자 방향 표시는 눈에 안 보여서 정규식이 조용히 어긋나므로 지운다.
 * 공백은 직접 나열해서 줄인다. 정규식의 \s 가 NBSP 를 포함하는지는 JVM 과 Android 가 달라서
 * 단위 테스트와 실제 기기의 결과가 어긋날 수 있다.
 */
internal fun normalizeNotificationText(value: CharSequence?): String? {
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
