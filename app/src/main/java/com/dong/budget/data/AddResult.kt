package com.dong.budget.data

/** 분류·결제수단 이름의 최대 길이. 표에서 한 줄에 들어가는 정도 */
const val MAX_NAME_LENGTH = 10

/** 분류나 결제수단을 새로 만든 결과 */
sealed interface AddResult {
    data class Added(val id: Long) : AddResult

    data object BlankName : AddResult

    data object NameTooLong : AddResult

    data object DuplicateName : AddResult
}
