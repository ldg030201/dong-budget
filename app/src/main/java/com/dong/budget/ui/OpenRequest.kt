package com.dong.budget.ui

/**
 * 알림을 눌러 동계부를 열었을 때 열어 줄 것. MainActivity 가 Intent 에서 읽어 DongBudgetApp 에 넘긴다.
 * Intent 의 extras 는 풀지 않고 identifier(문자열)만 읽는다. 동계부 첫 화면은 다른 앱도 열 수 있어서다.
 */
sealed interface OpenRequest {
    /** '가계부에 등록할까요?' 알림. 그 결제로 채운 등록창을 연다. */
    data class Captured(val dedupKey: String) : OpenRequest

    /** '월급 들어왔나요?' 알림. 그달 월급으로 채운 수입 등록창을 연다. [key] 는 'salary:2026-09' 모양이다. */
    data class Salary(val key: String) : OpenRequest
}
