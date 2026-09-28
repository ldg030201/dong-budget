package com.dong.budget.ui.settings

import com.dong.budget.data.settings.AutoOption

// ─────────────────────────────────────────────────────────────────────
// 설정의 자동 기능 스위치 글자와 묶음. 켜면 무엇을 하고, 끄면 어떻게 되는지 쓰는 사람 눈높이로 적는다.
// ─────────────────────────────────────────────────────────────────────

/** 스위치 이름 */
internal val AutoOption.title: String
    get() = when (this) {
        AutoOption.CAPTURE_PROMPT -> "결제 알림으로 등록할지 묻기"
        AutoOption.CAPTURE_DEDUPE -> "같은 결제 알림은 한 번만 묻기"
        AutoOption.CAPTURE_RESCAN -> "앱을 열 때 놓친 알림 다시 살피기"
        AutoOption.FILL_CATEGORY -> "같은 가게면 지난 분류 고르기"
        AutoOption.FILL_PAYMENT -> "카드 이름으로 결제수단 고르기"
        AutoOption.FILL_NEW_CARD -> "없는 카드는 새로 추가하기"
        AutoOption.FILL_INSTALLMENT -> "할부는 메모에 적기"
        AutoOption.EDITOR_KEYPAD -> "금액 키패드 바로 열기"
        AutoOption.EDITOR_SELECT_ADDED -> "새로 만든 분류·결제수단 바로 고르기"
        AutoOption.NEW_ITEM_COLOR -> "새 분류·결제수단은 안 쓴 색으로"
        AutoOption.UPDATE_CHECK -> "앱을 열 때 새 버전 확인하기"
        AutoOption.STATS_DAY -> "일별에서 볼 날 자동으로 고르기"
    }

/** 스위치 이름 아래 설명 */
internal val AutoOption.description: String
    get() = when (this) {
        AutoOption.CAPTURE_PROMPT -> "토스 결제 알림이 오면 '가계부에 등록할까요?' 알림을 띄워요"
        AutoOption.CAPTURE_DEDUPE -> "토스가 결제 한 건을 알림 두 개로 보내면 먼저 온 것만 물어요"
        AutoOption.CAPTURE_RESCAN -> "알림창에 남은 토스 알림 중 아직 묻지 못한 결제를 앱을 열 때 물어요"
        AutoOption.FILL_CATEGORY -> "전에 같은 가게로 등록한 지출의 분류를 미리 골라 둬요"
        AutoOption.FILL_PAYMENT -> "알림의 카드와 이름이 같은 결제수단을 미리 골라 둬요"
        AutoOption.FILL_NEW_CARD -> "같은 이름의 결제수단이 없으면 저장할 때 그 카드로 새로 만들어요"
        AutoOption.FILL_INSTALLMENT -> "할부 결제면 메모에 '3개월 할부'처럼 채워요"
        AutoOption.EDITOR_KEYPAD -> "새로 등록할 때 금액 키패드를 열어 둔 채 시작해요"
        AutoOption.EDITOR_SELECT_ADDED -> "등록하다가 새로 추가하면 바로 그것으로 골라 둬요"
        AutoOption.NEW_ITEM_COLOR -> "추가할 때 아직 안 쓴 색을 미리 골라 둬요. 끄면 회색으로 시작해요"
        AutoOption.UPDATE_CHECK -> "새 버전이 있으면 홈 위에 알려 줘요. 끄면 여기서 직접 확인해요"
        AutoOption.STATS_DAY -> "이번 달은 오늘, 지나간 달은 가장 많이 쓴 날을 먼저 보여 줘요. 끄면 1일부터 보여요"
    }

/** 설정 화면에서 스위치를 모아 두는 묶음. 화면 위에서부터 이 순서다. 새 버전 확인([AutoOption.UPDATE_CHECK])은 '앱 정보' 에 있다. */
internal enum class AutoGroup(val label: String, val options: List<AutoOption>) {
    CAPTURE("결제 알림", listOf(AutoOption.CAPTURE_PROMPT, AutoOption.CAPTURE_DEDUPE, AutoOption.CAPTURE_RESCAN)),
    FILL(
        "알림으로 등록할 때",
        listOf(AutoOption.FILL_CATEGORY, AutoOption.FILL_PAYMENT, AutoOption.FILL_NEW_CARD, AutoOption.FILL_INSTALLMENT),
    ),
    EDITOR("거래 등록", listOf(AutoOption.EDITOR_KEYPAD, AutoOption.EDITOR_SELECT_ADDED, AutoOption.NEW_ITEM_COLOR)),
    STATISTICS("통계", listOf(AutoOption.STATS_DAY)),
}
