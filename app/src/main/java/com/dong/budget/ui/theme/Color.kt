package com.dong.budget.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────
// 원시 팔레트.
// 이 파일 밖에서는 Color(0x...) 리터럴을 쓰지 않는다. 화면 코드는 항상 토큰만 참조한다.
//
// 브랜드색은 인디고 계열(hue 약 230도)이다. 특정 금융 앱의 파랑(hue 약 217도)과
// 의도적으로 분리해서 고유 색을 쓴다.
//
// 모든 대비비는 흰색이 아니라 '실제로 그 색이 올라가는 가장 밝은 배경' 기준으로 계산했다.
// 이 앱은 섹션을 Gray50/Gray100 블록으로 나누기 때문에 흰색 기준 계산은 과대평가가 된다.
// ─────────────────────────────────────────────────────────────────────

internal val Indigo100 = Color(0xFFDDE1FD)
internal val Indigo200 = Color(0xFFBFC7FA)
internal val Indigo400 = Color(0xFF6C7DF4)
internal val Indigo500 = Color(0xFF4658EC) // 라이트 primary. 흰색 대비 5.43:1
internal val Indigo550 = Color(0xFF5566F0) // 다크 primary. 흰색 대비 4.62:1
internal val Indigo700 = Color(0xFF2F3DB8)
internal val Indigo900 = Color(0xFF1A2270)
internal val IndigoContainerLight = Color(0xFFE4E7FE)
internal val IndigoContainerDark = Color(0xFF2A3160)

// 약간 차가운 회색 램프
internal val Gray50 = Color(0xFFF7F8FA)
internal val Gray100 = Color(0xFFEFF1F5)
internal val Gray200 = Color(0xFFE3E6ED)
internal val Gray300 = Color(0xFFCDD3DE)
internal val Gray400 = Color(0xFFAAB2C0)
internal val Gray500 = Color(0xFF8B94A3) // 흰색 대비 3.06:1. 본문 텍스트에 쓰면 안 된다
internal val Gray600 = Color(0xFF6B7484)
internal val Gray700 = Color(0xFF5F6878) // 라이트 보조 텍스트. Gray200 위에서도 4.45:1
internal val Gray800 = Color(0xFF3A4150)
internal val Gray900 = Color(0xFF1F2430) // 라이트 본문 텍스트

// 다크 배경 램프. 순수 검정 대신 약간 푸른 기가 도는 어두운 회색
internal val Dark900 = Color(0xFF14161C)
internal val Dark800 = Color(0xFF1B1E26)
internal val Dark700 = Color(0xFF232732)
internal val Dark600 = Color(0xFF2D323F)
internal val DarkText = Color(0xFFECEFF4)
internal val DarkTextSecondary = Color(0xFF9AA3B2)

// 수입/지출.
// 지출은 색을 쓰지 않고 무채색 본문색으로 둔다. 가계부는 지출 항목이 압도적으로 많아서
// 지출에 색을 주면 화면 전체가 빨개지고 강조의 의미가 사라진다.
internal val IncomeLight = Color(0xFF077A47) // 흰색 5.41 / Gray50 5.09
internal val IncomeDark = Color(0xFF109E57) // Dark900 5.21 / 본문 DarkText 대비 3.01
internal val DangerLight = Color(0xFFC42638) // 흰색 5.70. 삭제 확인과 error 공용
internal val DangerDark = Color(0xFFFF7A85)
internal val DangerContainerLight = Color(0xFFFDE7EA)
internal val OnDangerContainerLight = Color(0xFF6E0F1B)
internal val DangerContainerDark = Color(0xFF5C1A22)
internal val OnDangerContainerDark = Color(0xFFFFD9DD)
internal val DarkSurfaceLowest = Color(0xFF0F1116)

// ─────────────────────────────────────────────────────────────────────
// 의미 토큰.
// Material3 ColorScheme 에 넣지 않는 도메인 색들. ColorScheme 에 넣으면
// M3 컴포넌트가 엉뚱한 곳에서 집어다 쓸 수 있다.
// ─────────────────────────────────────────────────────────────────────

@Immutable
data class BudgetColorTokens(
    val textPrimary: Color,
    val textSecondary: Color,
    /** 대비 3.06:1 로 AA 미달이다. 18sp 이상 또는 14sp Bold 이상에서만 쓴다. */
    val textTertiary: Color,
    val income: Color,
    val danger: Color,
    /**
     * 배경 위에 올리는 브랜드색 글자(오늘 날짜, 강조 금액 등).
     * 다크의 primary(Indigo550)는 흰 글자를 올리는 채움용이라 어두운 배경 위 글자로는 대비가 모자라다(3.61:1).
     */
    val brandText: Color,
    val sectionBackground: Color,
    val divider: Color,
    /** 통계 차트용. 빨강-초록 조합은 적녹색맹에서 구분이 불가능해서 쓰지 않는다. */
    val chartExpense: Color,
    val chartIncome: Color,
)

internal val LightColorTokens =
    BudgetColorTokens(
        textPrimary = Gray900,
        textSecondary = Gray700,
        textTertiary = Gray500,
        income = IncomeLight,
        danger = DangerLight,
        brandText = Indigo500, // Gray50 5.11
        sectionBackground = Gray50,
        divider = Gray200,
        chartExpense = Indigo500,
        chartIncome = IncomeLight,
    )

internal val DarkColorTokens =
    BudgetColorTokens(
        textPrimary = DarkText,
        textSecondary = DarkTextSecondary,
        textTertiary = Gray600,
        income = IncomeDark,
        danger = DangerDark,
        brandText = Indigo400, // Dark800 4.66 / Dark900 5.06
        sectionBackground = Dark800,
        divider = Dark600,
        chartExpense = Indigo550,
        chartIncome = IncomeDark,
    )
