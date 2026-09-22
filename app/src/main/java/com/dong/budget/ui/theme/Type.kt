package com.dong.budget.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.dong.budget.R

// ─────────────────────────────────────────────────────────────────────
// Pretendard 를 앱에 번들해서 기기와 무관하게 같은 글자를 보장한다.
//
// 주의: 번들한 굵기는 400/600/700 세 개뿐이다.
// FontWeight.Medium(500) 을 쓰면 에러 없이 조용히 Regular(400) 로 떨어진다.
// 굵기가 더 필요하면 폰트 파일을 추가하고 이 목록에 등록할 것.
// ─────────────────────────────────────────────────────────────────────

val Pretendard =
    FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )

/**
 * Pretendard 의 기본 숫자는 비례폭이라 글리프마다 너비가 다르다.
 * 실측으로 '0' 과 '1' 의 폭이 약 36% 차이난다.
 * 금액처럼 값이 계속 바뀌는 숫자에 그대로 쓰면 입력 중에 폭이 흔들린다.
 * tnum 피처를 켜서 고정폭 숫자를 쓴다.
 */
private const val TABULAR_NUMBERS = "tnum"

private fun body(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // 여러 줄 본문은 고품질 줄바꿈을 쓴다.
    lineBreak = LineBreak.Paragraph,
)

private fun heading(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // 제목은 어절 단위로 끊어야 한국어가 자연스럽다. Paragraph 는 어절 단위가 아니다.
    lineBreak = LineBreak.Heading,
)

private fun label(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // 한 줄짜리 짧은 라벨은 줄바꿈 계산이 필요 없다.
    lineBreak = LineBreak.Simple,
)

// 본문 기준을 17sp 로 잡는다. M3 기본(16)보다 한 단계 크다.
// 한 화면에 정보를 적게 담고 글자를 크게 쓰는 방향.
val BudgetTypography =
    Typography(
        displayLarge = heading(40, 50),
        displayMedium = heading(34, 44),
        displaySmall = heading(30, 40),
        headlineLarge = heading(26, 36),
        headlineMedium = heading(23, 32),
        headlineSmall = heading(20, 28),
        titleLarge = heading(19, 26, FontWeight.SemiBold),
        titleMedium = heading(17, 24, FontWeight.SemiBold),
        titleSmall = heading(15, 22, FontWeight.SemiBold),
        bodyLarge = body(17, 26),
        bodyMedium = body(15, 23),
        bodySmall = body(13, 20),
        labelLarge = label(15, 20),
        labelMedium = label(13, 18),
        labelSmall = label(12, 16),
    )

/**
 * 금액 전용 타이포.
 * M3 Typography 슬롯에 넣지 않는 이유는 M3 컴포넌트가 이 스타일을 집어가면 안 되기 때문이다.
 */
@Immutable
data class BudgetAmountTypography(
    /** 등록 화면 상단의 큰 금액 */
    val hero: TextStyle,
    /** 카드/요약의 강조 금액 */
    val large: TextStyle,
    /** 리스트 항목의 금액 */
    val medium: TextStyle,
    /** 보조 금액 */
    val small: TextStyle,
    /** 숫자 키패드의 숫자 */
    val keypadDigit: TextStyle,
)

private fun amount(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = TABULAR_NUMBERS,
    lineBreak = LineBreak.Simple,
)

val DefaultAmountTypography =
    BudgetAmountTypography(
        hero = amount(44, 52, FontWeight.Bold),
        large = amount(32, 40, FontWeight.Bold),
        medium = amount(17, 24, FontWeight.SemiBold),
        small = amount(15, 22, FontWeight.SemiBold),
        keypadDigit = amount(24, 30, FontWeight.SemiBold),
    )
