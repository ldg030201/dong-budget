package com.dong.budget.ui.stats.chart

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable

// ─────────────────────────────────────────────────────────────────────
// 범례. 계열은 색 견본이 알려주고 글자는 늘 글자색이다(계열 색 글자는 옅은 색에서 읽히지 않는다).
// 계열이 둘 이상이면 범례를 늘 두어 색만으로 구분하지 않게 한다. 계열이 하나면 제목이 대신한다.
// ─────────────────────────────────────────────────────────────────────

/** 네모 색 견본(chart.legendSwatch, 모서리 radius.swatch). 막대·도넛 계열에 쓴다. 뜻은 옆 글자가 전하므로 읽지 않는다. */
@Composable
fun LegendSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(BudgetTheme.chart.legendSwatch)
            .background(color, RoundedCornerShape(BudgetTheme.radius.swatch)),
    )
}

/** 선 모양 견본(견본 두 칸 길이, chart.line 굵기). 선 차트 계열에 쓴다. */
@Composable
fun LegendLineKey(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(BudgetTheme.chart.legendSwatch * 2)
            .height(BudgetTheme.chart.line)
            .background(color, RoundedCornerShape(BudgetTheme.radius.full)),
    )
}

/** 견본 모양 */
enum class LegendMark { SWATCH, LINE }

/** 범례 한 칸. 예: LegendEntry("지출", chartExpense) / LegendEntry("9월", chartExpense, LegendMark.LINE) */
@Immutable
data class LegendEntry(val label: String, val color: Color, val mark: LegendMark = LegendMark.SWATCH)

/**
 * 한 줄 범례(■ 지출 ■ 수입 / ─ 9월 ─ 8월). 차트 위에 둔다. 누를 수 없고, 칸마다 글자만 읽는다.
 * 칸이 많아 한 줄에 안 들어가는 곳에는 [LegendChip] 을 FlowRow 로 쓴다.
 */
@Composable
fun ChartLegend(entries: List<LegendEntry>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (entry.mark) {
                    LegendMark.SWATCH -> LegendSwatch(entry.color)
                    LegendMark.LINE -> LegendLineKey(entry.color)
                }
                Spacer(Modifier.width(swatchTextGap()))
                Text(text = entry.label, style = MaterialTheme.typography.labelMedium, color = BudgetTheme.colors.textSecondary)
            }
        }
    }
}

/**
 * 도넛 옆 범례 한 줄: 견본 + 이름(길면 말줄임) + 값("42%"). 이름이 남은 폭을 다 쓰고 값은 오른쪽 끝에 붙는다.
 *
 * @param contentDescription 줄 전체를 읽을 문장(예: "식비 42퍼센트")
 */
@Composable
fun LegendValueRow(color: Color, label: String, value: String, contentDescription: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(color)
        Spacer(Modifier.width(swatchTextGap()))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BudgetTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(text = value, style = BudgetTheme.amount.tableCell, color = BudgetTheme.colors.textSecondary, maxLines = 1)
    }
}

/**
 * 범례를 겸하는 필터 칩(날마다 쓴 돈의 [전체] [■식비] … [■그 외 3개]). 여럿 중 하나를 고르는 라디오 버튼으로 읽힌다.
 *
 * - 보이는 칩은 BudgetChip 과 같은 높이지만, 위아래 여백까지 누를 수 있어 터치 영역은 size.minTouchTarget(48dp) 이상이다.
 * - 고른 칩: primaryContainer 채움 + onPrimaryContainer Bold 글자 + primary 굵은 테두리.
 *   안 고른 칩: divider 가는 테두리 + textSecondary 글자. 색만이 아니라 채움·굵기·테두리로 함께 갈린다.
 * - 칩들은 부르는 쪽이 `FlowRow(Modifier.selectableGroup())` 에 늘어놓는다.
 *
 * @param color 견본 색. null 이면 견본 없이 글자만([전체])
 * @param contentDescription 화면 읽기 문장. 예: "식비만 보기" / "모든 분류 보기". '선택됨' 은 화면 읽기가 붙인다.
 */
@Composable
fun LegendChip(
    label: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    val spacing = BudgetTheme.spacing
    val size = BudgetTheme.size
    // 고르면 채움·테두리·글자가 바로 뒤바뀌지 않고 번지듯 바뀐다(BudgetChip 과 같다)
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else BudgetTheme.colors.divider,
        Motion.quick(),
        label = "legendChipBorder",
    )
    val container by animateColorAsState(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (selected) 1f else 0f),
        Motion.quick(),
        label = "legendChipContainer",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else BudgetTheme.colors.textSecondary,
        Motion.quick(),
        label = "legendChipContent",
    )
    val borderWidth by animateDpAsState(
        if (selected) size.underlineActive else size.underline,
        Motion.quick(),
        label = "legendChipBorderWidth",
    )

    Box(
        modifier =
        modifier
            .defaultMinSize(minHeight = size.minTouchTarget)
            .pressScaleClickable(shape = shape, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
            Modifier
                .padding(vertical = spacing.tightGap)
                .heightIn(min = size.minTouchTarget - spacing.tightGap * 2)
                .background(container, shape)
                .border(borderWidth, border, shape)
                .padding(horizontal = spacing.itemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (color != null) {
                LegendSwatch(color)
                Spacer(Modifier.width(swatchTextGap()))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = if (selected) FontWeight.Bold else null,
                maxLines = 1,
            )
        }
    }
}

/** 견본과 글자 사이. 칩의 아이콘과 글자 사이(BudgetChip)와 같다. */
@Composable
private fun swatchTextGap(): Dp = BudgetTheme.spacing.tightGap * 1.5f

/**
 * 분류·결제수단 색 이름을 차트 색으로 푼다. 색이 없는 것('그 외', '분류 없음')은 chartOther 다.
 * 도넛 조각, 범례 견본, 비율 막대가 모두 이것을 써서 한 항목이 어디서나 같은 색이다.
 */
@Composable
fun entityColor(color: String?): Color = color?.let { BudgetTheme.categoryPalette[it].content } ?: BudgetTheme.colors.chartOther

/**
 * 색 이름 목록을 차트 색 목록으로 푼다([entityColor] 와 같은 규칙). 같은 목록이면 같은 List 를 돌려줘서,
 * 이걸 받는 차트가 다시 그릴 때마다 막대를 다시 재지 않는다.
 */
@Composable
fun rememberEntityColors(colors: List<String?>): List<Color> {
    val palette = BudgetTheme.categoryPalette
    val other = BudgetTheme.colors.chartOther
    return remember(colors, palette, other) { colors.map { name -> name?.let { palette[it].content } ?: other } }
}
