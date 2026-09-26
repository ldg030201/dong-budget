package com.dong.budget.ui.stats.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.rememberTextMeasurer
import com.dong.budget.ui.theme.BudgetTheme

/** 한 칸 안 여러 값을 놓는 방법 */
enum class ColumnLayout {
    /** 아래부터 계열 순서대로 쌓는다. 전체 높이가 합계다(날마다 쓴 돈) */
    STACKED,

    /** 나란히 세운다. 사이는 칠하지 않은 틈이다(최근 6개월 지출·수입) */
    GROUPED,
}

/**
 * 막대 차트의 칸 하나.
 *
 * @property values 계열 순서의 값(원). 음수는 0 으로 그린다(실제 값은 표나 읽기 판이 전한다).
 *   비워 두면 막대 없이 x축 글자만 선다(기록을 시작하기 전 달 등).
 * @property label 칸 아래 x축 글자. null 이면 적지 않는다. 한 달 차트는 [dailyAxisLabels] 로 만든다.
 * @property description 칸별 화면 읽기 문장(예: "9월 3일 수요일, 지출 32,000원, 식비 20,000원").
 *   차트 전체 설명([ColumnChart] 의 contentDescription)을 주면 쓰지 않는다.
 * @property valueLabel 막대 위에 적을 줄인 금액(보통 [formatAxisWon]). 한 칸이라도 있으면 y축 글자와 격자를 빼고 기준선만 둔다.
 *   값이 모두 보이므로 축이 할 일이 없고, 그 폭만큼 막대 자리가 넓어진다.
 * @property colors 이 칸만 다른 색으로 칠할 때(상세의 이 달 막대만 분류 색, 나머지는 chartContext). null 이면 계열 색을 쓴다.
 */
@Immutable
data class ColumnSlot(
    val values: List<Long>,
    val label: AxisLabel? = null,
    val description: String = "",
    val valueLabel: String? = null,
    val colors: List<Color>? = null,
)

/**
 * 세로 막대 차트. 날마다 쓴 돈(쌓은 막대, 고를 수 있음), 최근 6개월(나란히, 정적), 상세의 6개월(한 계열, 값 글자)이 같이 쓴다.
 *
 * 그리는 규칙
 * - 높이 = 그림 영역(chart.plotHeight) + x축 글자 띠(chart.axisBand) + 위쪽 여유(맨 위 눈금 글자의 반, 값 글자가 있으면 한 줄).
 * - 막대 굵기는 쌓기면 칸의 60%, 나란히면 묶음이 칸의 64% 이고 모두 chart.barMaxWidth 를 넘지 않는다.
 * - 데이터 쪽 끝만 min(chart.barCorner, 폭/2, 높이)로 둥글고 바닥은 각지다. 쌓기는 맨 위 조각만 둥글다.
 * - 조각 사이와 나란한 막대 사이는 chart.gap 만큼 칠하지 않고 비운다(어느 바탕 위에서도 맞는다). 틈은 위 조각에서 떼어 내서 전체 높이는 값 그대로다.
 * - 1px 보다 낮은 조각은 그리지 않는다. 값은 읽기 판이나 표에 있다.
 * - y축은 niceAxis(가장 높은 칸)로 잡는다. 모두 0 이면 기준선과 "0" 만 있다.
 *
 * 화면 읽기
 * - [contentDescription] 을 주면 차트 전체가 그 한 문장으로 읽힌다(정적 차트).
 * - 주지 않으면 칸마다 [ColumnSlot.description] 을 읽는 노드를 둔다. [onSelect] 가 있으면 노드마다 '선택됨' 과
 *   '자세히 보기' 동작이 붙어 두 번 탭으로 그 칸을 고른다. 칸 노드에는 포인터 입력이 없어 손가락 조작은 차트가 받는다.
 *
 * @param slots 왼쪽부터 칸. 칸 폭은 그림 폭을 칸 수로 똑같이 나눈다.
 * @param seriesColors 계열 색. [ColumnSlot.values] 와 같은 순서다. 분류 색은 `BudgetTheme.categoryPalette[이름].content`,
 *   '그 외'(색 이름 null)는 `BudgetTheme.colors.chartOther` 로 풀어서 넘긴다.
 * @param focusedSeries 이 계열만 바닥부터 제 색으로 그리고 y축을 그 계열의 최댓값으로 다시 맞춘다('하나만 보기'). null 이면 모두 그린다.
 * @param selectedIndex 고른 칸. 그 칸 뒤에 chartSelection 띠를 깐다. 글자 굵기는 [ColumnSlot.label] 로 따로 준다.
 * @param onSelect 누르거나 가로로 끌어서 칸을 고를 때. null 이면 누를 수 없는 정적 차트다.
 * @param contentDescription 정적 차트의 요약 설명. null 이면 칸별 노드를 둔다.
 */
@Composable
fun ColumnChart(
    slots: List<ColumnSlot>,
    seriesColors: List<Color>,
    modifier: Modifier = Modifier,
    layout: ColumnLayout = ColumnLayout.STACKED,
    focusedSeries: Int? = null,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    contentDescription: String? = null,
) {
    val chart = BudgetTheme.chart
    val spacing = BudgetTheme.spacing
    val underline = BudgetTheme.size.underline
    val selectionCorner = BudgetTheme.radius.chip
    val ink = rememberChartInk()
    val measurer = rememberTextMeasurer()
    val axisLine = with(LocalDensity.current) { BudgetTheme.amount.chartAxis.lineHeight.toDp() }

    val showAxis = slots.none { it.valueLabel != null }
    // y축 글자 자리. 값 글자를 적는 차트는 축이 없으니 칸이 왼쪽 끝부터 선다.
    val gutter = chart.axisGutter.takeIf { showAxis }
    // 맨 위 눈금 글자는 격자선에 가운데를 맞추므로 반 줄이 그림 영역 위로 나간다. 값 글자는 가장 높은 막대 위에 한 줄이 선다.
    val topInset = if (showAxis) axisLine / 2 else axisLine + spacing.tightGap
    val scale = remember(slots, layout, focusedSeries) { niceAxis(slots.maxOfOrNull { it.extent(layout, focusedSeries) } ?: 0) }
    // 고른 칸의 띠는 그리기 단계에서만 읽는다. 칸을 바꿔도 막대와 글자를 다시 재지 않는다.
    val selected = rememberUpdatedState(selectedIndex)

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(topInset + chart.plotHeight + chart.axisBand)
            // 정적 차트는 요약 한 문장으로 읽는다. 아니면 아래 칸 노드들이 읽는다.
            .then(contentDescription?.let { Modifier.clearAndSetSemantics { this.contentDescription = it } } ?: Modifier)
            .slotSelection(slots.size, gutter, selectedIndex, onSelect),
    ) {
        Spacer(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .drawWithCache {
                    val plot =
                        Plot(
                            left = gutter?.toPx() ?: 0f,
                            top = topInset.toPx(),
                            right = size.width,
                            bottom = (topInset + chart.plotHeight).toPx(),
                            scale = scale,
                            count = slots.size,
                        )
                    val bars =
                        buildBars(
                            slots = slots,
                            seriesColors = seriesColors,
                            layout = layout,
                            focusedSeries = focusedSeries,
                            plot = plot,
                            metrics =
                            BarMetrics(
                                maxWidth = chart.barMaxWidth.toPx(),
                                corner = chart.barCorner.toPx(),
                                gap = chart.gap.toPx(),
                                minHeight = MIN_SEGMENT_PX,
                            ),
                        )
                    val grid = if (showAxis) gridLines(plot) else listOf(plot.bottom)
                    val yLabels = if (showAxis) measureYLabels(measurer, ink, plot, spacing.tightGap.toPx()) else emptyList()
                    val xLabels =
                        measureXLabels(
                            measurer = measurer,
                            ink = ink,
                            labels = slots.map { it.label },
                            plot = plot,
                            bandTop = plot.bottom,
                            bandHeight = chart.axisBand.toPx(),
                            canvasWidth = size.width,
                            minGap = spacing.tightGap.toPx(),
                        )
                    val valueLabels =
                        slots.mapIndexedNotNull { index, slot ->
                            val text = slot.valueLabel ?: return@mapIndexedNotNull null
                            val emphasized = slot.label?.style?.let { it != AxisLabelStyle.NORMAL } == true
                            val layoutResult = measurer.measure(text, if (emphasized) ink.axisStrongText else ink.axisText)
                            val barTop = plot.y(slot.extent(layout, focusedSeries))
                            PlacedText(
                                layoutResult,
                                Offset(
                                    centeredLeft(plot.slotCenter(index), layoutResult.size.width.toFloat(), size.width),
                                    barTop - spacing.tightGap.toPx() - layoutResult.size.height,
                                ),
                            )
                        }
                    val bandCorner = CornerRadius(minOf(selectionCorner.toPx(), plot.slotWidth / 2f))
                    val gridThickness = underline.toPx()

                    onDrawBehind {
                        selected.value?.takeIf { it in slots.indices }?.let { index ->
                            drawRoundRect(
                                color = ink.selection,
                                topLeft = Offset(plot.slotLeft(index), plot.top),
                                size = Size(plot.slotWidth, plot.height),
                                cornerRadius = bandCorner,
                            )
                        }
                        drawGrid(grid, plot.left, plot.right, ink.grid, gridThickness)
                        bars.forEach { (color, path) -> drawPath(path, color) }
                        drawPlaced(yLabels)
                        drawPlaced(xLabels)
                        drawPlaced(valueLabels)
                    }
                },
        )
        if (contentDescription == null) {
            SlotNodes(
                slots = slots,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                modifier = Modifier.matchParentSize().then(gutter?.let { Modifier.padding(start = it) } ?: Modifier),
            )
        }
    }
}

/**
 * 칸마다 화면 읽기 노드. 그림과 같은 크기로 겹쳐 두고, 그리기는 하지 않는다.
 * 포인터 입력이 없어서 손가락 조작은 뒤의 차트가 받는다.
 */
@Composable
private fun SlotNodes(slots: List<ColumnSlot>, selectedIndex: Int?, onSelect: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        slots.forEachIndexed { index, slot ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clearAndSetSemantics {
                        contentDescription = slot.description
                        if (onSelect != null) {
                            selected = index == selectedIndex
                            onClick(label = "자세히 보기") {
                                onSelect(index)
                                true
                            }
                        }
                    },
            )
        }
    }
}

/** 칸의 높이를 정하는 값. 쌓기는 양수 조각의 합, 나란히는 가장 큰 막대, 하나만 보기는 그 계열 값 */
private fun ColumnSlot.extent(layout: ColumnLayout, focusedSeries: Int?): Long = when {
    focusedSeries != null -> values.getOrNull(focusedSeries)?.coerceAtLeast(0) ?: 0
    layout == ColumnLayout.STACKED -> values.sumOf { it.coerceAtLeast(0) }
    else -> values.maxOfOrNull { it.coerceAtLeast(0) } ?: 0
}

/** 막대 치수(픽셀) */
private class BarMetrics(val maxWidth: Float, val corner: Float, val gap: Float, val minHeight: Float)

/** 쌓은 조각 하나의 세로 자리(픽셀). [bottom] 은 틈을 떼어 낸 뒤의 값이다. */
private class Segment(val color: Color, val top: Float, val bottom: Float)

/**
 * 막대를 색별 Path 로 모은다. 같은 색 막대는 한 번에 그린다.
 * 쌓은 조각 사이의 틈은 위 조각에서 떼어 내고, 그 틈을 빼고 1px 도 안 남는 조각은 건너뛴다.
 */
private fun buildBars(
    slots: List<ColumnSlot>,
    seriesColors: List<Color>,
    layout: ColumnLayout,
    focusedSeries: Int?,
    plot: Plot,
    metrics: BarMetrics,
): List<Pair<Color, Path>> {
    val paths = LinkedHashMap<Color, Path>()
    fun add(color: Color, left: Float, top: Float, right: Float, bottom: Float, rounded: Boolean) {
        val radius = if (rounded) CornerRadius(minOf(metrics.corner, (right - left) / 2f, bottom - top)) else CornerRadius.Zero
        paths.getOrPut(color) { Path() }.addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero,
            ),
        )
    }

    slots.forEachIndexed { index, slot ->
        val colors = slot.colors ?: seriesColors
        val center = plot.slotCenter(index)
        when {
            focusedSeries != null -> {
                val color = colors.getOrNull(focusedSeries) ?: return@forEachIndexed
                val value = slot.values.getOrNull(focusedSeries)?.coerceAtLeast(0) ?: 0
                val half = minOf(plot.slotWidth * STACK_WIDTH_RATIO, metrics.maxWidth) / 2f
                val top = plot.y(value)
                if (plot.bottom - top >= metrics.minHeight) add(color, center - half, top, center + half, plot.bottom, rounded = true)
            }

            layout == ColumnLayout.STACKED -> {
                val half = minOf(plot.slotWidth * STACK_WIDTH_RATIO, metrics.maxWidth) / 2f
                val segments = mutableListOf<Segment>()
                var sum = 0L
                slot.values.forEachIndexed { series, raw ->
                    val value = raw.coerceAtLeast(0)
                    if (value == 0L) return@forEachIndexed
                    val bottom = plot.y(sum)
                    sum += value
                    val top = plot.y(sum)
                    val trimmedBottom = if (segments.isEmpty()) bottom else bottom - metrics.gap
                    val color = colors.getOrNull(series) ?: return@forEachIndexed
                    if (trimmedBottom - top >= metrics.minHeight) segments += Segment(color, top, trimmedBottom)
                }
                segments.forEachIndexed { order, segment ->
                    add(segment.color, center - half, segment.top, center + half, segment.bottom, rounded = order == segments.lastIndex)
                }
            }

            else -> {
                val count = maxOf(seriesColors.size, slot.values.size)
                if (count == 0) return@forEachIndexed
                val groupWidth = minOf(plot.slotWidth * GROUP_WIDTH_RATIO, count * metrics.maxWidth + (count - 1) * metrics.gap)
                val barWidth = (groupWidth - (count - 1) * metrics.gap) / count
                val start = center - groupWidth / 2f
                slot.values.forEachIndexed { series, raw ->
                    val color = colors.getOrNull(series) ?: return@forEachIndexed
                    val top = plot.y(raw.coerceAtLeast(0))
                    val left = start + series * (barWidth + metrics.gap)
                    if (plot.bottom - top >= metrics.minHeight) add(color, left, top, left + barWidth, plot.bottom, rounded = true)
                }
            }
        }
    }
    return paths.toList()
}

/** 이보다 낮은 조각(픽셀)은 그리지 않는다. 둥근 끝과 틈만 남아 얼룩처럼 보인다 */
private const val MIN_SEGMENT_PX = 1f

/** 쌓은 막대(와 한 계열 막대)의 굵기. 칸의 이만큼이고 barMaxWidth 를 넘지 않는다 */
private const val STACK_WIDTH_RATIO = 0.6f

/** 나란한 막대 묶음의 폭. 칸의 이만큼이고 (barMaxWidth × 막대 수 + 틈)을 넘지 않는다 */
private const val GROUP_WIDTH_RATIO = 0.64f
