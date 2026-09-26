package com.dong.budget.ui.stats.tab.monthly

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dong.budget.ui.stats.CumulativeFlow
import com.dong.budget.ui.stats.Pace
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.flowDescription
import com.dong.budget.ui.stats.calc.flowReading
import com.dong.budget.ui.stats.calc.paceSentence
import com.dong.budget.ui.stats.chart.ChartLegend
import com.dong.budget.ui.stats.chart.LegendEntry
import com.dong.budget.ui.stats.chart.LegendMark
import com.dong.budget.ui.stats.chart.LineChart
import com.dong.budget.ui.stats.chart.LineSeries
import com.dong.budget.ui.stats.chart.dailyAxisLabels
import com.dong.budget.ui.stats.chart.formatAxisWon
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * ④ 이번 달 흐름. 1일부터 쌓은 지출을 지난달 선과 같은 '일' 에 겹쳐 본다.
 *
 * - 지난달 선(chartContext)을 먼저 그려 뒤에 깔고, 이번 달 선(chartExpense) 끝에 점과 줄인 금액을 단다.
 *   지난달 기록이 없으면 이번 달 선만 두고 범례도 뺀다(계열이 하나면 제목이 대신한다).
 * - 차트를 누르거나 끌면 그날까지를 아래 읽기 줄이 적는다. 고르기 전에는 이번 달 선의 마지막 날(이번 달이면 오늘)이다.
 * - 이번 달이면 '지난달만큼 쓰려면 하루 얼마' 문장을 붙인다.
 * - 화면 읽기는 차트를 요약 한 문장으로 읽는다(전체 금액).
 *
 * @param title "이번 달 흐름" / "8월 흐름"
 * @param selectedDay 차트에서 고른 날(일). null 이면 고르지 않았다.
 * @param pace 이번 달이 아니거나 견줄 지난달이 없으면 null
 */
@Composable
internal fun FlowSection(
    title: String,
    flow: CumulativeFlow,
    pace: Pace?,
    today: LocalDate,
    selectedDay: Int?,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BudgetTheme.colors
    val lastDay = flow.month.lengthOfMonth()
    // 오늘이 이 달에 있을 때만 x축에 오늘을 적는다
    val todayDay = today.dayOfMonth.takeIf { YearMonth.from(today) == flow.month }
    val lines =
        remember(flow, colors.chartContext, colors.chartExpense) {
            listOfNotNull(
                flow.previous?.let { LineSeries(values = it, color = colors.chartContext) },
                LineSeries(
                    values = flow.thisMonth,
                    color = colors.chartExpense,
                    endMarker = true,
                    endLabel = formatAxisWon(flow.thisMonth.last()),
                ),
            )
        }
    val xLabels = remember(lastDay, todayDay, selectedDay) { dailyAxisLabels(lastDay, today = todayDay, selected = selectedDay) }

    StatsSection(modifier = modifier, title = title) {
        if (flow.previous != null) {
            ChartLegend(
                entries =
                listOf(
                    LegendEntry("${flow.month.monthValue}월", colors.chartExpense, LegendMark.LINE),
                    LegendEntry("${flow.month.minusMonths(1).monthValue}월", colors.chartContext, LegendMark.LINE),
                ),
            )
        }
        LineChart(
            lines = lines,
            slotCount = lastDay,
            contentDescription = flowDescription(flow),
            xLabels = xLabels,
            selectedIndex = selectedDay?.minus(1),
            onSelect = { index -> onSelectDay(index + 1) },
        )
        // 늘 보이는 읽기 줄. 고른 날이 오늘 뒤면 이번 달 값은 빠지고 지난달 값만 적힌다.
        Text(
            text = flowReading(flow, selectedDay ?: flow.thisMonth.size),
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        pace?.let {
            Text(
                text = paceSentence(it),
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textPrimary,
            )
        }
    }
}
