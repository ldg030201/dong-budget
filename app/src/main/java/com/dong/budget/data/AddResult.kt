package com.dong.budget.data

/**
 * 분류·결제수단 이름의 최대 길이. 알림에서 읽는 카드 상품명('원더카드2.0 Life', 'MG새마을금고 체크카드')도 잘리지 않게 넉넉히 둔다.
 * 고르는 표에서는 두 줄까지 보이고, 목록에서는 자리가 모자라면 말줄임표로 줄인다.
 */
const val MAX_NAME_LENGTH = 20

/** 분류나 결제수단을 새로 만든 결과 */
sealed interface AddResult {
    data class Added(val id: Long) : AddResult

    data object BlankName : AddResult

    data object NameTooLong : AddResult

    data object DuplicateName : AddResult
}

/** 앞뒤 빈칸을 뗀 이름이 규칙에 어긋나면 그 이유. 괜찮으면 null. 분류와 결제수단이 같은 규칙을 쓴다. */
internal fun nameProblem(name: String): AddResult? = when {
    name.isEmpty() -> AddResult.BlankName
    name.length > MAX_NAME_LENGTH -> AddResult.NameTooLong
    else -> null
}
