package com.dong.budget.ui.stats.tab.monthly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.ui.components.BudgetDivider
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.stats.MonthPoint
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.TrendAverage
import com.dong.budget.ui.stats.calc.trendAverageText
import com.dong.budget.ui.stats.chart.AxisLabel
import com.dong.budget.ui.stats.chart.AxisLabelStyle
import com.dong.budget.ui.stats.chart.ChartLegend
import com.dong.budget.ui.stats.chart.ColumnChart
import com.dong.budget.ui.stats.chart.ColumnLayout
import com.dong.budget.ui.stats.chart.ColumnSlot
import com.dong.budget.ui.stats.chart.LegendEntry
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.LocalDate
import java.time.YearMonth

/**
 * ⑤ 최근 6개월. 지출·수입 묶음 막대와 표, 앞선 달 평균.
 *
 * - 차트는 정적이다(누를 수 없다). 값은 표가 모두 보여 주고, 화면 읽기도 표를 줄마다 읽는다.
 * - 표는 기록을 시작한 달부터 고른 달까지다. 고른 달 줄은 굵게 적고, 다른 달 줄을 누르면 통계 전체가 그 달로 바뀐다.
 * - 창 안에 기록한 달이 두 달보다 적으면 막대 한 달짜리 비교가 되므로 차트와 표 대신 안내만 둔다.
 *
 * 기록을 시작하기 전 달을 보고 있어 표에 줄이 없으면 부르는 쪽이 섹션을 숨긴다([trendTableRows]).
 *
 * @param month 지금 보는 달
 * @param average 앞선 달 평균. 넣을 달이 없으면 null
 * @param onShowMonth 표의 다른 달 줄을 눌렀을 때
 */
@Composable
internal fun TrendSection(
    trend: List<MonthPoint>,
    month: YearMonth,
    today: LocalDate,
    average: TrendAverage?,
    onShowMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BudgetTheme.colors
    StatsSection(modifier = modifier, title = TREND_TITLE) {
        if (!canCompareMonths(trend)) {
            HintText(text = TREND_FALLBACK)
        } else {
            ChartLegend(entries = listOf(LegendEntry("지출", colors.chartExpense), LegendEntry("수입", colors.chartIncome)))
            ColumnChart(
                slots =
                trend.map { point ->
                    ColumnSlot(
                        // 기록을 시작하기 전 달은 막대 없이 달 글자만 둔다. 음수 지출은 차트가 0 으로 그리고 실제 값은 표에 있다.
                        values = if (point.beforeFirstRecord) emptyList() else listOf(point.totals.expense, point.totals.income),
                        label =
                        AxisLabel(
                            text = "${point.month.monthValue}월",
                            style = if (point.month == month) AxisLabelStyle.STRONG else AxisLabelStyle.NORMAL,
                        ),
                    )
                },
                seriesColors = listOf(colors.chartExpense, colors.chartIncome),
                layout = ColumnLayout.GROUPED,
                contentDescription = TREND_CHART_DESCRIPTION,
            )
            TrendTable(rows = trendTableRows(trend), month = month, onShowMonth = onShowMonth)
            if (trend.any { it.month == YearMonth.from(today) }) HintText(text = TREND_CURRENT_HINT)
            average?.let {
                Text(
                    text = trendAverageText(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 달 | 지출 | 수입 | 남은 돈 표. 금액은 오른쪽 정렬이고 단위는 뺀다.
 * 화면 읽기는 머리줄을 건너뛰고, 줄마다 열 이름과 전체 금액을 붙인 한 문장으로 읽는다.
 */
@Composable
private fun TrendTable(rows: List<MonthPoint>, month: YearMonth, onShowMonth: (YearMonth) -> Unit) {
    val cellStyle = BudgetTheme.amount.tableCell
    Column {
        TableRow(
            cells = TREND_COLUMNS,
            style = MaterialTheme.typography.labelMedium,
            color = BudgetTheme.colors.textSecondary,
            // 줄마다 읽는 문장에 열 이름이 들어 있다
            modifier = Modifier.clearAndSetSemantics {}.padding(bottom = BudgetTheme.spacing.tightGap),
        )
        BudgetDivider()
        rows.forEach { point ->
            key(point.month) {
                val isShown = point.month == month
                TableRow(
                    cells = trendCells(point),
                    style = if (isShown) cellStyle.copy(fontWeight = FontWeight.Bold) else cellStyle,
                    color = BudgetTheme.colors.textPrimary,
                    modifier =
                    Modifier
                        .then(
                            if (isShown) {
                                Modifier
                            } else {
                                Modifier.pressScaleClickable(
                                    shape = RoundedCornerShape(BudgetTheme.radius.chip),
                                    onClick = { onShowMonth(point.month) },
                                )
                            },
                        )
                        // 누르기(버튼)는 남기고 칸 글자 대신 한 문장으로 읽는다
                        .clearAndSetSemantics { contentDescription = trendRowDescription(point, isShown) }
                        // 줄을 눌러 그 달로 가므로 줄마다 최소 터치 높이를 지킨다
                        .heightIn(min = BudgetTheme.size.minTouchTarget),
                )
            }
        }
    }
}

/** 표 한 줄. 달 칸은 좁게 왼쪽 정렬, 금액 칸 셋은 같은 폭에 오른쪽 정렬 */
@Composable
private fun TableRow(cells: List<String>, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, text ->
            val isMonth = index == 0
            Text(
                text = text,
                style = style,
                color = color,
                textAlign = if (isMonth) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                // 칸보다 긴 금액을 글자 단위로 끊으면 "+1,500,00" 처럼 다른 금액으로 읽힌다. 줄여서 한 줄에 맞춘다.
                autoSize =
                if (isMonth) {
                    null
                } else {
                    TextAutoSize.StepBased(
                        minFontSize = BudgetTheme.amount.chartAxis.fontSize,
                        maxFontSize = style.fontSize,
                    )
                },
                modifier = Modifier.weight(if (isMonth) MONTH_COLUMN_WEIGHT else 1f),
            )
        }
    }
}

/** 달 칸의 폭(금액 칸 하나에 견준 비). '12월' 이면 충분하고, 남는 폭을 금액 칸에 준다(360dp 에서 금액 칸 하나가 약 84dp). */
private const val MONTH_COLUMN_WEIGHT = 0.7f
