package com.dong.budget.ui.stats.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 비율 도넛(정적). 지름 chart.donut, 두께 chart.donutStroke, 끝은 각지게(Butt) 자른다.
 *
 * - 12시에서 시계 방향으로 [values] 순서대로 그린다. '그 외' 는 맨 끝에 넘긴다.
 * - 조각 사이는 chart.gap 만큼 칠하지 않고 비운다. 각도로는 gap / r(두께 가운데 반지름)이고 최소 1° 다.
 *   조각이 하나뿐이면 틈 없이 온 고리를 그린다. 틈보다 가는 조각은 그리지 않는다(옆 범례와 목록에 값이 있다).
 * - 누를 수 없고 화면 읽기에서 통째로 뺀다. 옆 범례 줄([LegendValueRow])과 아래 목록이 값을 전한다.
 *
 * @param values 조각 값(원). 0 이하는 건너뛴다.
 * @param colors 조각 색. [values] 와 같은 순서다. 분류 색은 `categoryPalette[이름].content`, '그 외' 는 chartOther.
 * @param center 가운데 구멍에 둘 내용. 보통 [DonutCenterLabel]
 */
@Composable
fun Donut(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier, center: @Composable BoxScope.() -> Unit = {}) {
    val chart = BudgetTheme.chart
    Box(
        modifier =
        modifier
            .size(chart.donut)
            .clearAndSetSemantics {}
            .drawWithCache {
                val thickness = chart.donutStroke.toPx()
                val diameter = size.minDimension
                val radius = (diameter - thickness) / 2f
                val gapDegrees = maxOf(Math.toDegrees((chart.gap.toPx() / radius).toDouble()).toFloat(), MIN_GAP_DEGREES)
                val slices = values.zip(colors).filter { it.first > 0 }
                val total = slices.sumOf { it.first }.toFloat()
                val arcs =
                    buildList {
                        if (total <= 0f) return@buildList
                        if (slices.size == 1) {
                            add(Arc(slices.single().second, START_DEGREES, FULL_DEGREES))
                            return@buildList
                        }
                        var start = START_DEGREES
                        slices.forEach { (value, color) ->
                            val sweep = value / total * FULL_DEGREES
                            // 틈은 조각 양 끝에서 반씩 뗀다
                            if (sweep > gapDegrees) add(Arc(color, start + gapDegrees / 2f, sweep - gapDegrees))
                            start += sweep
                        }
                    }
                val topLeft = Offset((size.width - diameter) / 2f + thickness / 2f, (size.height - diameter) / 2f + thickness / 2f)
                val arcSize = Size(diameter - thickness, diameter - thickness)
                val stroke = Stroke(width = thickness, cap = StrokeCap.Butt)
                onDrawBehind {
                    arcs.forEach { arc ->
                        drawArc(arc.color, arc.start, arc.sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                    }
                }
            },
        contentAlignment = Alignment.Center,
        content = center,
    )
}

/**
 * 도넛 가운데 글자. 위에 무엇의 합인지("지출"/"수입"), 아래에 줄인 합계(formatCompactWon).
 * 구멍보다 긴 금액은 글자를 줄여 한 줄에 맞춘다. 잘려 보이면 금액을 잘못 읽는다.
 */
@Composable
fun DonutCenterLabel(caption: String, value: String, modifier: Modifier = Modifier) {
    val amount = BudgetTheme.amount.medium
    Column(
        modifier = modifier.padding(horizontal = BudgetTheme.chart.donutStroke + BudgetTheme.spacing.tightGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = caption, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary, maxLines = 1)
        BasicText(
            text = value,
            style = amount.copy(color = BudgetTheme.colors.textPrimary, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = BudgetTheme.amount.chartAxis.fontSize, maxFontSize = amount.fontSize),
        )
    }
}

private class Arc(val color: Color, val start: Float, val sweep: Float)

/** 12시 방향 */
private const val START_DEGREES = -90f
private const val FULL_DEGREES = 360f

/** 조각 사이 틈의 최소 각도 */
private const val MIN_GAP_DEGREES = 1f
