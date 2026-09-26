package com.dong.budget.ui.stats.chart

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.dong.budget.ui.theme.BudgetTheme
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 차트 공용 부품: 축 글자, 격자, 그림 영역 좌표, 칸 고르기 조작.
//
// 모든 차트가 같은 규칙을 따른다.
// - y축은 하나만, 왼쪽 axisGutter 에 오른쪽 정렬로 적는다. 격자와 기준선(0)은 같은 가는 실선이다.
// - x축 글자 띠(axisBand)는 차트 높이에 들어간다.
// - 글자에는 계열 색을 쓰지 않는다. 계열은 옆의 견본이 알려준다.
// ─────────────────────────────────────────────────────────────────────

/** x축 글자 모양. 색만이 아니라 굵기로도 갈린다. 글자가 겹치면 강한 쪽이 남는다(BRAND > STRONG > NORMAL). */
enum class AxisLabelStyle {
    /** 보통 눈금 글자. 보조 글자색 */
    NORMAL,

    /** 고른 칸, 지금 보는 달. 본문색 Bold */
    STRONG,

    /** 오늘. 브랜드 글자색 Bold. 고른 날과 겹쳐도 오늘이 이긴다 */
    BRAND,
}

/** x축 한 칸 아래 적는 글자 */
@Immutable
data class AxisLabel(val text: String, val style: AxisLabelStyle = AxisLabelStyle.NORMAL)

/**
 * 한 달(1일~[lastDay]일) 차트의 x축 글자. 돌려주는 목록의 i 번째가 (i+1)일 칸이고, 적지 않는 칸은 null 이다.
 *
 * - 고정 눈금: 1, 5, 10, 15, 20, 25일, 그리고 말일이 25일보다 3일 이상 뒤면 말일
 * - 오늘은 늘 적는다(BRAND). 고른 날도 늘 적는다(STRONG). 둘이 같은 날이면 오늘 모양이다.
 * - 오늘이나 고른 날에서 ±2일 안의 고정 눈금은 뺀다. 붙어 적히면 글자가 겹친다.
 *
 * 오늘과 고른 날이 1~2일 차이로 붙으면 그리는 쪽([ColumnChart])이 겹침을 보고 오늘만 남긴다.
 *
 * @param today 오늘의 일. 오늘이 이 달에 없으면 null
 * @param selected 고른 날의 일. 없으면 null
 */
fun dailyAxisLabels(lastDay: Int, today: Int? = null, selected: Int? = null): List<AxisLabel?> {
    val fixed = FIXED_DAY_TICKS + listOfNotNull(lastDay.takeIf { it - FIXED_DAY_TICKS.last() >= LAST_DAY_MIN_GAP })
    val anchors = listOfNotNull(today, selected)
    return (1..lastDay).map { day ->
        when {
            day == today -> AxisLabel(day.toString(), AxisLabelStyle.BRAND)
            day == selected -> AxisLabel(day.toString(), AxisLabelStyle.STRONG)
            day in fixed && anchors.none { abs(it - day) <= ANCHOR_CLEARANCE } -> AxisLabel(day.toString())
            else -> null
        }
    }
}

private val FIXED_DAY_TICKS = listOf(1, 5, 10, 15, 20, 25)

/** 말일은 마지막 고정 눈금(25일)과 이만큼 떨어져야 적는다. 28일부터 적힌다 */
private const val LAST_DAY_MIN_GAP = 3

/** 오늘·고른 날 둘레에서 고정 눈금을 빼는 폭(일) */
private const val ANCHOR_CLEARANCE = 2

// ── 그릴 때 쓰는 색과 글자 ──────────────────────────────────────────────

/**
 * 차트가 그릴 때 쓰는 색과 글자 모양. 그리기 블록은 @Composable 이 아니라 토큰을 직접 못 읽어서,
 * 한 번에 읽어 두었다가 넘긴다. 라이트·다크가 바뀌면 새로 만들어져 다시 그린다.
 */
@Immutable
internal data class ChartInk(
    /** 격자와 기준선 */
    val grid: Color,
    /** y축 글자와 보통 x축 글자 */
    val axisText: TextStyle,
    val axisStrongText: TextStyle,
    val axisBrandText: TextStyle,
    /** 선 끝 직접 라벨 */
    val directLabel: TextStyle,
    /** 고른 칸 뒤 띠 */
    val selection: Color,
    /** 끌기 기준선 */
    val crosshair: Color,
    /** 차트가 올라가는 바탕. 점 둘레 고리를 이 색으로 칠한다(조각 사이 틈은 칠하지 않고 비운다) */
    val surface: Color,
) {
    fun xLabelStyle(style: AxisLabelStyle): TextStyle = when (style) {
        AxisLabelStyle.NORMAL -> axisText
        AxisLabelStyle.STRONG -> axisStrongText
        AxisLabelStyle.BRAND -> axisBrandText
    }
}

@Composable
internal fun rememberChartInk(): ChartInk {
    val colors = BudgetTheme.colors
    val axis = BudgetTheme.amount.chartAxis
    val label = MaterialTheme.typography.labelMedium
    val surface = MaterialTheme.colorScheme.background
    return remember(colors, axis, label, surface) {
        ChartInk(
            grid = colors.divider,
            axisText = axis.copy(color = colors.textSecondary),
            axisStrongText = axis.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            axisBrandText = axis.copy(color = colors.brandText, fontWeight = FontWeight.Bold),
            directLabel = label.copy(color = colors.textPrimary),
            selection = colors.chartSelection,
            crosshair = colors.chartContext,
            surface = surface,
        )
    }
}

// ── 그림 영역 ─────────────────────────────────────────────────────────

/**
 * 막대·선이 그려지는 영역(픽셀)과 값 → y 변환. 칸은 영역 폭을 [count] 로 똑같이 나눈다.
 * [scale] 이 null 이면(모든 값이 0) 모든 값이 바닥(기준선)에 붙는다.
 */
internal class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float, val scale: AxisScale?, val count: Int) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val slotWidth: Float get() = if (count > 0) width / count else width

    fun y(value: Long): Float {
        val axis = scale ?: return bottom
        return bottom - (value - axis.bottom).toFloat() / (axis.top - axis.bottom) * height
    }

    fun slotLeft(index: Int): Float = left + index * slotWidth

    fun slotCenter(index: Int): Float = left + (index + 0.5f) * slotWidth
}

/** 미리 재 둔 글자와 그 왼쪽 위 자리. drawWithCache 안에서 만들고 그리기마다 [drawPlaced] 로 찍는다. */
internal class PlacedText(val layout: TextLayoutResult, val topLeft: Offset)

internal fun DrawScope.drawPlaced(texts: List<PlacedText>) {
    texts.forEach { drawText(it.layout, topLeft = it.topLeft) }
}

/**
 * y축 글자를 잰다. 눈금마다 [Plot.left] 에서 [gap] 만큼 떨어진 곳에 오른쪽 정렬하고, 격자선에 세로 가운데를 맞춘다.
 * 눈금이 없으면(scale 이 null) 기준선 옆에 "0" 하나만 적는다.
 */
internal fun measureYLabels(measurer: TextMeasurer, ink: ChartInk, plot: Plot, gap: Float): List<PlacedText> {
    val ticks = plot.scale?.ticks ?: listOf(0L)
    return ticks.map { tick ->
        val layout = measurer.measure(formatAxisWon(tick), ink.axisText)
        PlacedText(layout, Offset(plot.left - gap - layout.size.width, plot.y(tick) - layout.size.height / 2f))
    }
}

/** 격자선의 y 자리. 눈금이 없으면 기준선 하나 */
internal fun gridLines(plot: Plot): List<Float> = (plot.scale?.ticks ?: listOf(0L)).map(plot::y)

internal fun DrawScope.drawGrid(ys: List<Float>, left: Float, right: Float, color: Color, thickness: Float) {
    ys.forEach { y -> drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = thickness) }
}

/**
 * x축 글자를 잰다. 칸 가운데 아래, [bandTop] 부터 [bandHeight] 띠 안 세로 가운데에 둔다.
 * 캔버스 밖으로 나가지 않게 좌우를 당기고, 이웃 글자와 [minGap] 보다 가까우면 약한 쪽을 뺀다(BRAND > STRONG > NORMAL, 같으면 앞 칸).
 */
internal fun measureXLabels(
    measurer: TextMeasurer,
    ink: ChartInk,
    labels: List<AxisLabel?>,
    plot: Plot,
    bandTop: Float,
    bandHeight: Float,
    canvasWidth: Float,
    minGap: Float,
): List<PlacedText> {
    val taken = mutableListOf<ClosedFloatingPointRange<Float>>()
    val placed = mutableListOf<PlacedText>()
    labels
        .withIndex()
        .filter { it.value != null && it.index < plot.count }
        .sortedByDescending { it.value!!.style.ordinal }
        .forEach { (index, label) ->
            val layout = measurer.measure(label!!.text, ink.xLabelStyle(label.style))
            val width = layout.size.width.toFloat()
            val left = (plot.slotCenter(index) - width / 2f).coerceIn(0f, maxOf(0f, canvasWidth - width))
            val span = left..(left + width)
            val crowded = taken.any { span.start < it.endInclusive + minGap && it.start < span.endInclusive + minGap }
            if (!crowded) {
                taken += span
                placed += PlacedText(layout, Offset(left, bandTop + (bandHeight - layout.size.height) / 2f))
            }
        }
    return placed
}

/**
 * 가운데를 [centerX] 에 맞춘 글자의 왼쪽 자리. 캔버스 밖으로 나가면 안쪽으로 당긴다.
 * 선 끝 라벨이나 막대 위 값처럼 칸 가운데에 다는 글자에 쓴다.
 */
internal fun centeredLeft(centerX: Float, width: Float, canvasWidth: Float): Float = (centerX - width / 2f).coerceIn(
    0f,
    maxOf(
        0f,
        canvasWidth - width,
    ),
)

// ── 칸 고르기 ─────────────────────────────────────────────────────────

/**
 * 누르거나 가로로 끌어서 칸 하나를 고른다. 손가락에서 가장 가까운 칸(손가락이 놓인 칸)에 붙고,
 * 고른 칸이 바뀔 때마다 짧게 진동한다(분류 순서 바꾸기와 같은 느낌).
 * 세로 끌기는 건드리지 않으므로 차트 위에서 시작해도 목록 스크롤이 된다.
 *
 * @param count 칸 수
 * @param plotLeft 칸이 시작하는 왼쪽 자리(y축 글자 자리 폭). 그 왼쪽을 누르면 첫 칸이다. null 이면 왼쪽 끝부터 칸이다.
 * @param selectedIndex 지금 고른 칸. 같은 칸을 다시 누르면 부르지 않는다.
 * @param onSelect null 이면 고를 수 없는 정적 차트라 아무 조작도 걸지 않는다.
 */
@Composable
internal fun Modifier.slotSelection(count: Int, plotLeft: Dp?, selectedIndex: Int?, onSelect: ((Int) -> Unit)?): Modifier {
    val haptic = LocalHapticFeedback.current
    val selected by rememberUpdatedState(selectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)
    if (onSelect == null || count <= 0) return this
    fun select(index: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        latestOnSelect?.invoke(index)
    }
    return this
        .pointerInput(count, plotLeft) {
            detectTapGestures { position ->
                val index = slotAt(position.x, plotLeft?.toPx() ?: 0f, size.width.toFloat(), count)
                if (index != selected) select(index)
            }
        }.pointerInput(count, plotLeft) {
            // 끄는 동안에는 화면이 다시 그려지기 전에 같은 칸이 여러 번 잡힐 수 있어서, 마지막으로 보낸 칸을 따로 들고 있는다.
            var last: Int? = null
            fun move(x: Float) {
                val index = slotAt(x, plotLeft?.toPx() ?: 0f, size.width.toFloat(), count)
                if (index != last) {
                    last = index
                    select(index)
                }
            }
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    last = selected
                    move(position.x)
                },
            ) { change, _ ->
                change.consume()
                move(change.position.x)
            }
        }
}

private fun slotAt(x: Float, left: Float, width: Float, count: Int): Int {
    val slot = (width - left) / count
    if (slot <= 0f) return 0
    return ((x - left) / slot).toInt().coerceIn(0, count - 1)
}
