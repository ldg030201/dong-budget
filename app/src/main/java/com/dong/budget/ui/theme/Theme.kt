package com.dong.budget.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// ─────────────────────────────────────────────────────────────────────
// ColorScheme 구성 원칙
//
// M3 컴포넌트가 어떤 슬롯을 집어가도 브랜드가 깨지지 않도록 모든 슬롯을 명시한다.
// 기본값으로 두면 M3 기본 보라색 팔레트가 새어 나온다.
// primaryFixed 계열 12개 슬롯도 반드시 지정해야 봉인이 완성된다.
//
// secondary/tertiary 는 무채색으로 봉인한다. 브랜드색을 두 번째로 쓸 곳이 없고,
// 컴포넌트가 실수로 집어도 최악이 회색이면 화면이 망가지지 않는다.
//
// surfaceTint 에는 surface 와 '같은 색'을 넣어야 tonal elevation 착색이 꺼진다.
// Color.Transparent 를 넣으면 안 된다. 그건 알파가 0인 검정이라서,
// surfaceColorAtElevation 이 surfaceTint.copy(alpha).compositeOver(surface) 를 할 때
// RGB 성분인 검정이 남아 표면이 오히려 어두워진다.
// 이 앱은 계층을 그림자나 착색이 아니라 배경색 차이로만 표현한다.
// ─────────────────────────────────────────────────────────────────────

private val LightScheme: ColorScheme =
    lightColorScheme(
        primary = Indigo500,
        onPrimary = Color.White,
        primaryContainer = IndigoContainerLight,
        onPrimaryContainer = Indigo900,
        inversePrimary = Indigo200,
        secondary = Gray700,
        onSecondary = Color.White,
        secondaryContainer = Gray100,
        onSecondaryContainer = Gray900,
        tertiary = Gray700,
        onTertiary = Color.White,
        tertiaryContainer = Gray100,
        onTertiaryContainer = Gray900,
        error = DangerLight,
        onError = Color.White,
        errorContainer = DangerContainerLight,
        onErrorContainer = OnDangerContainerLight,
        background = Color.White,
        onBackground = Gray900,
        surface = Color.White,
        onSurface = Gray900,
        surfaceVariant = Gray100,
        onSurfaceVariant = Gray700,
        surfaceTint = Color.White,
        inverseSurface = Gray900,
        inverseOnSurface = Gray50,
        outline = Gray400,
        outlineVariant = Gray200,
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = Gray200,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Gray50,
        surfaceContainer = Gray50,
        surfaceContainerHigh = Gray100,
        surfaceContainerHighest = Gray200,
        primaryFixed = Indigo100,
        primaryFixedDim = Indigo200,
        onPrimaryFixed = Indigo900,
        onPrimaryFixedVariant = Indigo700,
        secondaryFixed = Gray100,
        secondaryFixedDim = Gray200,
        onSecondaryFixed = Gray900,
        onSecondaryFixedVariant = Gray700,
        tertiaryFixed = Gray100,
        tertiaryFixedDim = Gray200,
        onTertiaryFixed = Gray900,
        onTertiaryFixedVariant = Gray700,
    )

private val DarkScheme: ColorScheme =
    darkColorScheme(
        primary = Indigo550,
        onPrimary = Color.White,
        primaryContainer = IndigoContainerDark,
        onPrimaryContainer = Indigo100,
        inversePrimary = Indigo500,
        secondary = Gray300,
        onSecondary = Dark900,
        secondaryContainer = Dark700,
        onSecondaryContainer = Gray200,
        tertiary = Gray300,
        onTertiary = Dark900,
        tertiaryContainer = Dark700,
        onTertiaryContainer = Gray200,
        error = DangerDark,
        onError = Dark900,
        errorContainer = DangerContainerDark,
        onErrorContainer = OnDangerContainerDark,
        background = Dark900,
        onBackground = DarkText,
        surface = Dark900,
        onSurface = DarkText,
        surfaceVariant = Dark700,
        onSurfaceVariant = DarkTextSecondary,
        surfaceTint = Dark900,
        inverseSurface = Gray100,
        inverseOnSurface = Gray900,
        outline = Gray600,
        outlineVariant = Dark600,
        scrim = Color.Black,
        surfaceBright = Dark600,
        surfaceDim = Dark900,
        surfaceContainerLowest = DarkSurfaceLowest,
        surfaceContainerLow = Dark800,
        surfaceContainer = Dark800,
        surfaceContainerHigh = Dark700,
        surfaceContainerHighest = Dark600,
        primaryFixed = Indigo100,
        primaryFixedDim = Indigo200,
        onPrimaryFixed = Indigo900,
        onPrimaryFixedVariant = Indigo700,
        secondaryFixed = Gray100,
        secondaryFixedDim = Gray200,
        onSecondaryFixed = Gray900,
        onSecondaryFixedVariant = Gray700,
        tertiaryFixed = Gray100,
        tertiaryFixedDim = Gray200,
        onTertiaryFixed = Gray900,
        onTertiaryFixedVariant = Gray700,
    )

internal val LocalBudgetTokens =
    staticCompositionLocalOf<BudgetTokens> {
        error("BudgetTheme 밖에서 토큰을 참조했다. 화면을 BudgetTheme 으로 감싸야 한다.")
    }

/** MaterialTheme 과 같은 방식으로 커스텀 토큰에 접근한다. */
object BudgetTheme {
    val colors: BudgetColorTokens
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.colors

    val spacing: BudgetSpacing
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.spacing

    val radius: BudgetRadius
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.radius

    val elevation: BudgetElevation
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.elevation

    val size: BudgetSize
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.size

    val amount: BudgetAmountTypography
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.amount
    val categoryPalette: CategoryPalette
        @Composable @ReadOnlyComposable
        get() = LocalBudgetTokens.current.categoryPalette
}

@Composable
fun BudgetTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val tokens =
        BudgetTokens(
            colors = if (darkTheme) DarkColorTokens else LightColorTokens,
            categoryPalette = if (darkTheme) DarkCategoryPalette else LightCategoryPalette,
        )

    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalBudgetTokens provides tokens,
        // 기기의 글꼴 크기 설정을 따르지 않고 고정한다.
        // 에뮬레이터에서 확인한 화면이 모든 기기에서 그대로 나오게 하기 위함이다.
        // 나중에 앱 안에 자체 글씨 크기 조절을 넣을 때 이 1f 를 설정값으로 바꾸면 된다.
        LocalDensity provides Density(density = density.density, fontScale = 1f),
        // 리플을 끈다. 누름 표현은 Interaction.kt 의 눌림 축소로 통일한다.
        // 포커스 표시는 리플과 별개로 pressScaleClickable 이 직접 그린다.
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = BudgetTypography,
            shapes = BudgetShapes,
            content = content,
        )
    }
}
