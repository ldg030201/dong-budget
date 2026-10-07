package com.dong.budget.ui.shell

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.sp
import com.dong.budget.R
import com.dong.budget.data.settings.MenuItem

// 아래 메뉴 칸의 이름·아이콘·색·설명. 이름·아이콘은 아래 메뉴와 설정의 하단 메뉴 편집기가, 설명·색은 전체 목록이 쓴다.

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

/** 전체 목록에서 이름 아래 적는 한 줄. 홈·전체는 목록에 없지만 빠짐없이 적어 둔다. */
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

/** 전체 목록의 둥근 아이콘 바탕색(분류 색 팔레트 이름). 항목마다 달리해 한눈에 구분되게 한다. 내역의 빈 화면도 쓴다. */
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

/**
 * 칸 이름 글자. 아래 메뉴 칸과 하단 메뉴 편집기의 칸이 같이 쓴다.
 * 칸이 많으면 좁은 폰(320dp, 칸 너비 약 50dp)에서 네 글자('고정지출')가 빠듯해, 두 줄로 꺾이거나 잘리지 않게 한 줄에 들 때까지 줄인다
 * (아래 떠 있는 메뉴와 같은 방식).
 * @param color 없으면 둘러싼 칸의 글자색(아래 메뉴는 고른 칸이면 브랜드색)을 따른다
 */
@Composable
fun MenuItemLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val style = MaterialTheme.typography.labelSmall
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

/** 좁은 화면에서 칸 이름을 줄이는 하한. 이보다 작으면 읽기 어렵다. */
private val MIN_LABEL_SIZE = 10.sp
