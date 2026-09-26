package com.dong.budget.ui.stats.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 요일 가로 막대 한 줄.
 *
 * @property label 요일 한 글자("일", "월" …)
 * @property value 막대 길이를 정하는 값(하루 평균, 원). 0 이하는 막대가 없다.
 * @property valueText 줄 끝에 적는 값(예: "32,000원")
 * @property description 줄 전체를 합쳐 읽는 문장(예: "토요일, 하루 평균 32,000원, 가장 많음")
 */
@Immutable
data class WeekdayBar(val label: String, val value: Long, val valueText: String, val description: String)

/**
 * 요일별 하루 평균 가로 막대. 캔버스 없이 Box 로 그린다.
 *
 * - 한 줄 = 요일 글자(chart.weekdayLabelWidth 고정 폭) + 막대(가장 큰 값이 남은 폭을 다 쓴다) + 값 글자.
 * - 값 글자 칸은 가장 긴 값에 맞춰 모든 줄이 같은 폭이라, 막대 길이를 줄끼리 그대로 견줄 수 있다.
 * - 막대는 높이 chart.weekdayBarHeight, 오른쪽(데이터 쪽) 끝만 chart.barCorner 로 둥글다.
 * - [topIndex] 줄만 chartExpense 에 글자 Bold 이고, 나머지 막대는 chartContext 다.
 * - 모든 값이 글자로 보이므로 누를 수 없다. 줄마다 [WeekdayBar.description] 한 문장으로 읽는다.
 *
 * @param bars 위에서 아래로 놓을 줄(보통 일요일부터 토요일까지, WEEK_ORDER)
 * @param topIndex 가장 많이 쓴 줄. 모두 0 이면 null
 */
@Composable
fun WeekdayBars(bars: List<WeekdayBar>, topIndex: Int?, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val chart = BudgetTheme.chart
    val colors = BudgetTheme.colors
    val valueStyle = BudgetTheme.amount.tableCell
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val topStyle = valueStyle.copy(fontWeight = FontWeight.Bold)
    val valueWidth =
        remember(bars, topIndex, valueStyle, density) {
            val widest =
                bars.withIndex().maxOf { (index, bar) ->
                    val style = if (index == topIndex) topStyle else valueStyle
                    measurer.measure(bar.valueText, style).size.width
                }
            with(density) { widest.toDp() }
        }
    val max = bars.maxOf { it.value }
    val barShape = RoundedCornerShape(topEnd = chart.barCorner, bottomEnd = chart.barCorner)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap)) {
        bars.forEachIndexed { index, bar ->
            val top = index == topIndex
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = bar.description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (top) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (top) FontWeight.Bold else null,
                    maxLines = 1,
                    modifier = Modifier.width(chart.weekdayLabelWidth),
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = BudgetTheme.spacing.inlineGap)) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth(if (max > 0) (bar.value.toFloat() / max).coerceIn(0f, 1f) else 0f)
                            .height(chart.weekdayBarHeight)
                            .background(if (top) colors.chartExpense else colors.chartContext, barShape),
                    )
                }
                Text(
                    text = bar.valueText,
                    style = valueStyle,
                    color = colors.textPrimary,
                    fontWeight = if (top) FontWeight.Bold else null,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(valueWidth),
                )
            }
        }
    }
}
