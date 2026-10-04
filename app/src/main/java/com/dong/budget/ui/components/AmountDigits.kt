package com.dong.budget.ui.components

import com.dong.budget.data.MAX_AMOUNT_DIGITS

// 금액 키패드(NumberKeypad 의 '00' 칸을 둔 판)를 누를 때의 숫자 규칙.
// 등록창·월급 설정·카드 실적 편집이 함께 써서, 한 곳만 고쳐 화면마다 다르게 받는 일이 없게 한다.

/**
 * 금액 키패드 한 번 누름. 앞의 0 은 떼고, 12자리(MAX_AMOUNT_DIGITS)를 넘으면 받지 않는다(null).
 * @param now 지금 금액. 0 이면 아직 적지 않은 것으로 본다.
 * @param digit 키패드가 주는 글자("0"~"9", "00")
 */
fun appendAmountDigit(now: Long, digit: String): Long? {
    val digits = (now.takeIf { it > 0 }?.toString().orEmpty() + digit).trimStart('0')
    if (digits.length > MAX_AMOUNT_DIGITS) return null
    return digits.toLongOrNull() ?: 0
}

/** 금액 키패드 지우기. 맨 끝 자리 하나를 뗀다. */
fun deleteAmountDigit(now: Long): Long = now / DECIMAL

private const val DECIMAL = 10L
