package com.dong.budget.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 분류 색 한 벌. 옅은 원 배경(container) 위에 진한 아이콘(content)을 올린다.
 *
 * 아이콘은 그래픽이라 WCAG 기준 3:1 이상이 필요하다. 모든 쌍을 그 기준으로 맞췄다.
 * 분류 이름은 항상 글자로 함께 나오므로 색만으로 구분하는 곳은 없다.
 */
@Immutable
data class CategorySwatch(val content: Color, val container: Color)

@Immutable
class CategoryPalette(private val swatches: Map<String, CategorySwatch>) {
    /** 모르는 이름이면 회색으로 떨어진다. DB 에 예전 이름이 남아 있어도 화면이 깨지지 않는다. */
    operator fun get(key: String?): CategorySwatch = swatches[key] ?: swatches.getValue("gray")
}

// 라이트: 진한 아이콘(대략 600~700 단계) + 옅은 배경(100 단계)
// 괄호 안은 아이콘과 배경 사이 대비비
internal val LightCategoryPalette =
    CategoryPalette(
        mapOf(
            "red" to CategorySwatch(Color(0xFFE5484D), Color(0xFFFDE8E8)), // 3.3
            "orange" to CategorySwatch(Color(0xFFE8590C), Color(0xFFFFEFE3)), // 3.2
            "amber" to CategorySwatch(Color(0xFFB7791F), Color(0xFFFEF3D7)), // 3.3
            "green" to CategorySwatch(Color(0xFF2F9E44), Color(0xFFE3F6E8)), // 3.1
            "teal" to CategorySwatch(Color(0xFF0C8599), Color(0xFFE0F4F6)), // 3.8
            "blue" to CategorySwatch(Color(0xFF1C7ED6), Color(0xFFE3F0FC)), // 3.6
            "indigo" to CategorySwatch(Indigo500, IndigoContainerLight), // 4.4
            "purple" to CategorySwatch(Color(0xFF7950F2), Color(0xFFEFEAFE)), // 4.2
            "pink" to CategorySwatch(Color(0xFFD6336C), Color(0xFFFCE7EF)), // 3.9
            "gray" to CategorySwatch(Gray600, Gray100), // 4.1
        ),
    )

// 다크: 밝은 아이콘(300~400 단계) + 어두운 배경. 모두 대비 5:1 이상
internal val DarkCategoryPalette =
    CategoryPalette(
        mapOf(
            "red" to CategorySwatch(Color(0xFFFF8589), Color(0xFF3D1F22)),
            "orange" to CategorySwatch(Color(0xFFFFA266), Color(0xFF3D2717)),
            "amber" to CategorySwatch(Color(0xFFF5C04A), Color(0xFF3A3016)),
            "green" to CategorySwatch(Color(0xFF6BD08A), Color(0xFF1B3325)),
            "teal" to CategorySwatch(Color(0xFF4FC3D4), Color(0xFF143339)),
            "blue" to CategorySwatch(Color(0xFF6CB4F5), Color(0xFF172C42)),
            "indigo" to CategorySwatch(Color(0xFF8A97F6), IndigoContainerDark),
            "purple" to CategorySwatch(Color(0xFFB197FC), Color(0xFF2C2447)),
            "pink" to CategorySwatch(Color(0xFFF783AC), Color(0xFF3D1E2B)),
            "gray" to CategorySwatch(Gray400, Dark600),
        ),
    )
