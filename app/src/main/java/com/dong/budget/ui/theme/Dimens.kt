package com.dong.budget.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────
// 치수 토큰.
//
// 의미 토큰만 공개한다. sm/md/lg 같은 크기 등급은 일부러 노출하지 않는다.
// 둘 다 노출하면 "화면 좌우 여백에 lg 를 쓸까 screenHorizontal 을 쓸까"가 매번 판단 문제가 되고,
// 나중에 값을 바꿀 때 절반만 바뀐다. 화면 코드가 고를 수 있는 건 의미 토큰뿐이어야 한다.
// ─────────────────────────────────────────────────────────────────────

@Immutable
data class BudgetSpacing(
    /** 화면 좌우 기본 여백 */
    val screenHorizontal: Dp = 20.dp,
    /** 리스트 한 줄의 위아래 여백 */
    val listItemVertical: Dp = 14.dp,
    /** 서로 다른 섹션 사이 간격 */
    val sectionGap: Dp = 32.dp,
    /** 섹션 블록 내부 여백 */
    val sectionPadding: Dp = 16.dp,
    /** 같은 그룹 안 항목 사이 간격 */
    val itemGap: Dp = 12.dp,
    /** 아이콘과 글자처럼 한 줄 안에서 붙는 간격 */
    val inlineGap: Dp = 8.dp,
    /** 제목과 부제처럼 아주 가까운 요소 사이 */
    val tightGap: Dp = 4.dp,
    /** 하단 고정 버튼과 본문 사이 */
    val ctaTopGap: Dp = 12.dp,
)

@Immutable
data class BudgetRadius(
    /** 버튼, 입력 필드 */
    val control: Dp = 14.dp,
    /** 섹션 블록, 카드 */
    val block: Dp = 18.dp,
    /** 바텀시트 상단 */
    val sheet: Dp = 24.dp,
    /** 칩, 작은 태그 */
    val chip: Dp = 10.dp,
    /** 완전한 원형 */
    val full: Dp = 999.dp,
)

@Immutable
data class BudgetElevation(
    /** 기본값. 계층은 그림자가 아니라 배경색 차이로 표현한다 */
    val none: Dp = 0.dp,
    val fab: Dp = 6.dp,
)

@Immutable
data class BudgetSize(
    /** 주 CTA 버튼 높이 */
    val ctaHeight: Dp = 56.dp,
    /** 터치 영역 최소 크기. 접근성 기준 */
    val minTouchTarget: Dp = 48.dp,
    val iconSmall: Dp = 18.dp,
    val icon: Dp = 24.dp,
    val topBarHeight: Dp = 56.dp,
    /** 목록 줄 앞의 아이콘 원 */
    val badge: Dp = 40.dp,
    /** 입력칸 안처럼 좁은 곳의 아이콘 원 */
    val badgeSmall: Dp = 28.dp,
    /** 분류 표의 아이콘 원 */
    val badgeLarge: Dp = 48.dp,
    /** 입력칸 한 줄의 최소 높이 */
    val formFieldMinHeight: Dp = 64.dp,
    /** 입력칸 밑줄 두께. 선택된 칸은 굵게 */
    val underline: Dp = 1.dp,
    val underlineActive: Dp = 2.dp,
    /** 아래에서 올라오는 입력판(키패드, 분류 표)의 높이. 판을 바꿔도 화면이 출렁이지 않게 맞춘다 */
    val inputPanelHeight: Dp = 264.dp,
    /**
     * 달력 한 칸의 높이. 날짜 숫자와 수입·지출 두 줄이 들어간다.
     * 첫 화면에 달력 아래 거래가 두세 개 보이도록 빠듯하게 잡았다. 키우면 목록이 밀려난다.
     */
    val calendarDayHeight: Dp = 52.dp,
    /** 달력 날짜 숫자를 감싸는 동그라미. 고른 날을 표시한다 */
    val calendarDayMark: Dp = 26.dp,
    /** 달력 위 요일 머리줄 높이 */
    val weekdayRowHeight: Dp = 24.dp,
    /** 떠 있는 추가 버튼 */
    val fab: Dp = 56.dp,
    /** 패치노트 종류 꼬리표 너비. 가장 긴 '오류수정' 이 들어가는 폭으로 모두 맞춘다 */
    val patchTagWidth: Dp = 64.dp,
)

/** 화면 코드에서 참조하는 전체 토큰 묶음 */
@Immutable
data class BudgetTokens(
    val colors: BudgetColorTokens,
    val spacing: BudgetSpacing = BudgetSpacing(),
    val radius: BudgetRadius = BudgetRadius(),
    val elevation: BudgetElevation = BudgetElevation(),
    val size: BudgetSize = BudgetSize(),
    val amount: BudgetAmountTypography = DefaultAmountTypography,
    val categoryPalette: CategoryPalette = LightCategoryPalette,
)

internal val BudgetShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
