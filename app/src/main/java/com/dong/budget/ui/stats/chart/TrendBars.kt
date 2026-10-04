package com.dong.budget.ui.stats.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

/**
 * 최근 몇 달(기간) 막대의 한 칸.
 * @param month x축에 적을 달
 * @param amount 막대 높이이자 위에 적을 금액. 지출은 순지출이라 환불받은 돈이 더 많으면 음수다(막대는 0 높이, 글자는 "+1만").
 * @param recorded false 면 기록(카드 사용)을 시작하기 전이라 막대와 금액 없이 달 이름만 둔다.
 * @param description 화면 읽기가 이 칸에서 읽을 문장
 */
data class TrendBar(val month: YearMonth, val amount: Long, val recorded: Boolean, val description: String)

/**
 * 최근 몇 달 한 계열 막대. 통계 상세(분류·결제수단)와 카드 실적 상세가 함께 쓴다.
 * 마지막 칸(고른 달)만 [highlight] 색, 나머지는 chartContext 다. 고른 달은 x축 글자도 굵게 적어 색만으로 가리지 않는다.
 * 막대마다 줄인 금액([trendValueLabel])을 적으므로 y축은 없다. 누를 수 없고, 화면 읽기는 칸마다 [TrendBar.description] 을 읽는다.
 */
@Composable
fun TrendBars(bars: List<TrendBar>, highlight: Color, modifier: Modifier = Modifier) {
    val context = BudgetTheme.colors.chartContext
    ColumnChart(slots = trendSlots(bars, highlight = highlight, context = context), seriesColors = listOf(context), modifier = modifier)
}

/** [TrendBars] 의 칸들. 마지막 칸만 [highlight] 색과 굵은 x축 글자다. */
internal fun trendSlots(bars: List<TrendBar>, highlight: Color, context: Color): List<ColumnSlot> = bars.mapIndexed { index, bar ->
    val selected = index == bars.lastIndex
    ColumnSlot(
        values = if (bar.recorded) listOf(bar.amount) else emptyList(),
        label = AxisLabel("${bar.month.monthValue}월", if (selected) AxisLabelStyle.STRONG else AxisLabelStyle.NORMAL),
        description = bar.description,
        valueLabel = if (bar.recorded) trendValueLabel(bar.amount) else null,
        colors = listOf(if (selected) highlight else context),
    )
}

/**
 * 막대 위에 적는 줄인 금액. "12.3만".
 * 환불받은 돈이 더 많아 음수면 앱의 다른 곳(머리 금액·화면 읽기 문장)처럼 돌려받은 쪽 부호로 "+1만" 이다.
 * '-' 를 쓰면 같은 화면의 결제 줄(-)과 부호가 겹친다. 수입은 음수가 되지 않는다.
 */
fun trendValueLabel(amount: Long): String = if (amount < 0) "+${formatAxisWon(-amount)}" else formatAxisWon(amount)
