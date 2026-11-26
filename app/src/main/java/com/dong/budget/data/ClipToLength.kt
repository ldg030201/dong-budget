package com.dong.budget.data

/**
 * [text] 를 UTF-16 [maxLength] 단위 안으로 자른다. 이모지처럼 두 단위인 글자가 끝에 걸리면 그 글자를 통째로 뺀다.
 * take 로만 자르면 앞 반쪽(외톨이 서로게이트)만 남아 깨진 글자가 그대로 저장된다.
 * 한도를 UTF-16 단위로 세는 것은 저장할 때의 길이 검사(name.length)와 맞추기 위해서다.
 * 글자 칸(FormTextField)·알림에서 읽은 카드 이름(PaymentMethodRepository.normalizeName)·개발자 로그가 같이 쓴다.
 */
internal fun clipToLength(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    val end = if (maxLength > 0 && text[maxLength - 1].isHighSurrogate()) maxLength - 1 else maxLength
    return text.substring(0, end)
}
