package com.dong.budget.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.dong.budget.R
import com.dong.budget.data.settings.MenuItem

// 아래 메뉴 칸의 이름·아이콘·색·설명. 아래 메뉴, 전체 목록, 설정의 하단 메뉴 화면이 같이 쓴다.

/** 칸 이름. 아래 메뉴 글자와 화면 제목에 쓴다. */
val MenuItem.label: String
    get() = when (this) {
        MenuItem.HOME -> "홈"
        MenuItem.SALARY -> "월급"
        MenuItem.STATISTICS -> "통계"
        MenuItem.FIXED_EXPENSE -> "고정지출"
        MenuItem.CARD_PERFORMANCE -> "카드실적"
        MenuItem.HISTORY -> "내역"
        MenuItem.MORE -> "전체"
    }

/** 전체 목록과 하단 메뉴 설정에서 이름 아래 적는 한 줄 */
val MenuItem.description: String
    get() = when (this) {
        MenuItem.HOME -> "이번 달 요약과 달력, 날짜별 내역을 봐요"
        MenuItem.SALARY -> "일하는 동안 번 돈이 초마다 쌓여요"
        MenuItem.STATISTICS -> "한눈에 보고, 월별·일별·분류·결제수단으로 나눠 봐요"
        MenuItem.FIXED_EXPENSE -> "매달 나가는 돈을 냈는지 봐요"
        MenuItem.CARD_PERFORMANCE -> "카드마다 실적을 얼마나 채웠는지 봐요"
        MenuItem.HISTORY -> "모든 거래를 모아 보고 가게·금액으로 찾아요"
        MenuItem.MORE -> "모든 메뉴와 설정을 모아 봐요"
    }

/** 전체 목록과 하단 메뉴 설정의 둥근 아이콘 바탕색(분류 색 팔레트 이름). 항목마다 달리해 한눈에 구분되게 한다. */
val MenuItem.color: String
    get() = when (this) {
        MenuItem.HOME -> "indigo"
        MenuItem.SALARY -> "teal"
        MenuItem.STATISTICS -> "orange"
        MenuItem.FIXED_EXPENSE -> "green"
        MenuItem.CARD_PERFORMANCE -> "blue"
        MenuItem.HISTORY -> "pink"
        MenuItem.MORE -> "gray"
    }

/**
 * 칸 아이콘. 월급은 기본 수입 분류 '급여', 고정지출은 기본 지출 분류 '고정지출', 카드실적은 기본 카드, 내역은 결제 등록 알림과 같은 아이콘이다
 * (Material Symbols 는 리소스라 여기서 읽는다).
 */
@Composable
fun MenuItem.icon(): ImageVector = when (this) {
    MenuItem.HOME -> Icons.Filled.Home
    MenuItem.SALARY -> ImageVector.vectorResource(R.drawable.ic_sym_payments)
    MenuItem.STATISTICS -> ImageVector.vectorResource(R.drawable.ic_sym_bar_chart)
    MenuItem.FIXED_EXPENSE -> ImageVector.vectorResource(R.drawable.ic_sym_event_repeat)
    MenuItem.CARD_PERFORMANCE -> ImageVector.vectorResource(R.drawable.ic_sym_credit_card)
    MenuItem.HISTORY -> ImageVector.vectorResource(R.drawable.ic_sym_receipt_long)
    MenuItem.MORE -> Icons.Filled.Menu
}
