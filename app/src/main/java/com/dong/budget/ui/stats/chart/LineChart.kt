package com.dong.budget.ui.stats.chart

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 선 하나.
 *
 * @property values 칸 순서의 값(원). i 번째 값은 i 번째 칸 가운데에 찍힌다. 칸보다 짧으면 거기서 끝난다(이번 달은 오늘까지).
 * @property color 선 색. 이번 달은 chartExpense, 비교하는 지난달은 chartContext 처럼 강조하지 않는 색을 쓴다.
 * @property endMarker 마지막 값에 점(chart.marker)과 바탕색 고리(chart.markerRing)를 단다.
 * @property endLabel 끝점 위에 다는 직접 라벨(보통 [formatAxisWon]). 위가 모자라면 아래에 단다. 한 차트에 하나만 둔다.
 */
@Immutable
data class LineSeries(val values: List<Long>, val color: Color, val endMarker: Boolean = false, val endLabel: String? = null)

/**
 * 누적 선 차트(이번 달 흐름). 선은 [lines] 순서대로 그려서 앞의 것이 뒤에 깔린다. 지난달을 먼저 넘긴다.
 *
 * - 높이 = 그림 영역(chart.plotHeight) + x축 글자 띠(chart.axisBand) + 맨 위 눈금 글자의 반.
 * - y 범위는 min(0, 최솟값) ~ niceAxis(최댓값)이다. 환불이 많아 0 아래로 내려가도 잘리지 않는다.
 * - 선은 chart.line 굵기에 끝과 이음새가 둥글다.
 * - 누르거나 가로로 끌면 가장 가까운 칸을 [onSelect] 로 알린다. 고른 칸에는 세로 기준선(chartContext)과
 *   선마다 그 칸의 점을 그린다. 차트 아래 읽기 줄("12일까지 · 9월 31만원 · 8월 40만원")은 부르는 쪽이 이 값으로 적는다.
 * - 화면 읽기는 차트 전체를 [contentDescription] 한 문장으로 읽는다(예: "9월 누적 지출 선 그래프. 27일까지 812,000원, 지난달 같은 날까지 930,000원.").
 *
 * @param slotCount 칸(날) 수. 보통 고른 달의 날수를 넘긴다. 이보다 긴 값은 그리지 않는다.
 * @param xLabels 칸 아래 글자. 한 달이면 [dailyAxisLabels] 로 만든다.
 * @param selectedIndex 고른 칸. null 이면 기준선 없이 끝점만 보인다(읽기 줄은 이번 달 선의 마지막 날을 쓰면 된다).
 * @param onSelect null 이면 누를 수 없다.
 */
@Composable
fun LineChart(
    lines: List<LineSeries>,
    slotCount: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    xLabels: List<AxisLabel?> = emptyList(),
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    val chart = BudgetTheme.chart
    val spacing = BudgetTheme.spacing
    val underline = BudgetTheme.size.underline
    val ink = rememberChartInk()
    val measurer = rememberTextMeasurer()
    val topInset = with(LocalDensity.current) { BudgetTheme.amount.chartAxis.lineHeight.toDp() } / 2
    val scale =
        remember(lines, slotCount) {
            val shown = lines.flatMap { it.values.take(slotCount) }
            niceAxis(max = shown.maxOrNull() ?: 0, min = shown.minOrNull() ?: 0)
        }
    // 고른 칸은 그리기 단계에서만 읽는다. 끄는 동안 선과 글자를 다시 재지 않는다.
    val selected = rememberUpdatedState(selectedIndex)

    Spacer(
        modifier =
        modifier
            .fillMaxWidth()
            .height(topInset + chart.plotHeight + chart.axisBand)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .slotSelection(slotCount, chart.axisGutter, selectedIndex, onSelect)
            .drawWithCache {
                val plot =
                    Plot(
                        left = chart.axisGutter.toPx(),
                        top = topInset.toPx(),
                        right = size.width,
                        bottom = (topInset + chart.plotHeight).toPx(),
                        scale = scale,
                        count = slotCount,
                    )
                val stroke = Stroke(width = chart.line.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val paths =
                    lines.map { line ->
                        line.color to
                            Path().apply {
                                line.values.take(slotCount).forEachIndexed { index, value ->
                                    val point = Offset(plot.slotCenter(index), plot.y(value))
                                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                }
                            }
                    }
                val markerRadius = chart.marker.toPx() / 2f
                val ringRadius = markerRadius + chart.markerRing.toPx()
                val ends =
                    lines.mapNotNull { line ->
                        val last = minOf(line.values.size, slotCount) - 1
                        if (!line.endMarker || last < 0) return@mapNotNull null
                        line.color to Offset(plot.slotCenter(last), plot.y(line.values[last]))
                    }
                val endLabel =
                    lines.firstNotNullOfOrNull { line ->
                        val text = line.endLabel ?: return@firstNotNullOfOrNull null
                        val last = minOf(line.values.size, slotCount) - 1
                        if (last < 0) return@firstNotNullOfOrNull null
                        val layout = measurer.measure(text, ink.directLabel)
                        val point = Offset(plot.slotCenter(last), plot.y(line.values[last]))
                        val clearance = ringRadius + spacing.tightGap.toPx()
                        val above = point.y - clearance - layout.size.height
                        PlacedText(
                            layout,
                            Offset(
                                centeredLeft(point.x, layout.size.width.toFloat(), size.width),
                                // 위가 모자라면 끝점 아래에 단다
                                if (above >= 0f) above else point.y + clearance,
                            ),
                        )
                    }
                val grid = gridLines(plot)
                val yLabels = measureYLabels(measurer, ink, plot, spacing.tightGap.toPx())
                val axisLabels =
                    measureXLabels(
                        measurer = measurer,
                        ink = ink,
                        labels = xLabels,
                        plot = plot,
                        bandTop = plot.bottom,
                        bandHeight = chart.axisBand.toPx(),
                        canvasWidth = size.width,
                        minGap = spacing.tightGap.toPx(),
                    )
                val thin = underline.toPx()

                onDrawBehind {
                    drawGrid(grid, plot.left, plot.right, ink.grid, thin)
                    val picked = selected.value?.takeIf { it in 0 until slotCount }
                    if (picked != null) {
                        val x = plot.slotCenter(picked)
                        drawLine(ink.crosshair, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = thin)
                    }
                    paths.forEach { (color, path) -> drawPath(path, color, style = stroke) }
                    ends.forEach { (color, point) -> drawMarker(point, color, ink.surface, markerRadius, ringRadius) }
                    if (picked != null) {
                        lines.forEach { line ->
                            val value = line.values.getOrNull(picked) ?: return@forEach
                            drawMarker(Offset(plot.slotCenter(picked), plot.y(value)), line.color, ink.surface, markerRadius, ringRadius)
                        }
                    }
                    drawPlaced(yLabels)
                    drawPlaced(axisLabels)
                    endLabel?.let { drawPlaced(listOf(it)) }
                }
            },
    )
}

/** 점과 바탕색 고리. 고리가 선과 겹친 곳을 끊어 점이 또렷하게 보인다. */
private fun DrawScope.drawMarker(center: Offset, color: Color, surface: Color, radius: Float, ringRadius: Float) {
    drawCircle(surface, ringRadius, center)
    drawCircle(color, radius, center)
}
