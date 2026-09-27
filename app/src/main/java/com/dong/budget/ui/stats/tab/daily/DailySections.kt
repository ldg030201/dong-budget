package com.dong.budget.ui.stats.tab.daily

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.format.formatDayShort
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.stats.DailyStats
import com.dong.budget.ui.stats.SectionNote
import com.dong.budget.ui.stats.StatRow
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.WeekdayStats
import com.dong.budget.ui.stats.calc.countedStartHint
import com.dong.budget.ui.stats.calc.noSpendCaption
import com.dong.budget.ui.stats.calc.weekdayHeadline
import com.dong.budget.ui.stats.calc.weekdayPatternSentence
import com.dong.budget.ui.stats.calc.weekdayRange
import com.dong.budget.ui.stats.chart.WeekdayBars
import com.dong.budget.ui.stats.netExpenseColor
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

/**
 * ① 하루 기록. 센 날(기록 시작일부터 오늘까지) 안의 하루 평균, 쓴 날 평균, 가장 많이 쓴 날, 돈 안 쓴 날을 줄로 쌓는다.
 * 센 날이 없으면(오지 않은 달, 오늘 뒤에 기록을 시작한 달) 네 줄 대신 한 줄로 알린다.
 * 기록을 1일보다 늦게 시작한 달은 어디서부터 셌는지 아래에 적는다.
 *
 * @param onShowPeak 가장 많이 쓴 날 줄을 누르면 날마다 쓴 돈에서 그날(일)을 고른다. null 이면 누를 수 없다.
 */
@Composable
internal fun DayRecordsSection(month: YearMonth, daily: DailyStats, onShowPeak: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    StatsSection(modifier = modifier, title = "하루 기록", block = true) {
        if (daily.counted.isEmpty()) {
            SectionNote("아직 지나간 날이 없어요")
        } else {
            val peak = daily.peak
            StatRow(
                label = "하루 평균",
                value = averageValue(daily.average),
                valueColor = daily.average?.let { netExpenseColor(it) } ?: BudgetTheme.colors.textPrimary,
                caption = countedCaption(daily.counted),
            )
            StatRow(
                label = "쓴 날 평균",
                value = averageValue(daily.spentDayAverage),
                valueColor = daily.spentDayAverage?.let { netExpenseColor(it) } ?: BudgetTheme.colors.textPrimary,
                // 쓴 날이 없으면 '돈을 쓴 0일 기준' 이 어색해서 뺀다
                caption = daily.spentDays.takeIf { it > 0 }?.let(::spentDaysCaption),
            )
            StatRow(
                label = "가장 많이 쓴 날",
                value = peak?.let { formatNetExpense(it.amount) } ?: "없어요",
                valueColor = peak?.let { netExpenseColor(it.amount) } ?: BudgetTheme.colors.textPrimary,
                caption = peak?.let { formatDayShort(it.date) },
                onClick = if (peak != null && onShowPeak != null) ({ onShowPeak(peak.date.dayOfMonth) }) else null,
            )
            StatRow(
                label = "돈 안 쓴 날",
                value = "${daily.noSpendDays ?: 0}일",
                caption = noSpendCaption(daily.counted, daily.longestNoSpend),
            )
            countedStartHint(month, daily.counted)?.let { HintText(it) }
        }
    }
}

/**
 * ③ 요일별 하루 평균. 최근 3달을 기록 시작일과 오늘로 자른 창에서 요일마다 하루 평균을 가로 막대로 늘어놓는다.
 * 값은 모두 글자로 보여서 누를 필요가 없다. 창이 2주보다 짧으면 막대 대신 안내만 둔다(요일마다 한 번꼴이라 평균이 흔들린다).
 *
 * @param weekday 창이 2주보다 짧으면 null
 */
@Composable
internal fun WeekdaySection(weekday: WeekdayStats?, noPastDays: Boolean, modifier: Modifier = Modifier) {
    StatsSection(modifier = modifier, title = "요일별 하루 평균", subtitle = weekday?.let(::weekdayRange)) {
        if (weekday == null) {
            // 오지 않은 달은 셀 날이 아직 없고, 지나간 날이 있으면 창이 2주보다 짧은 것이다
            if (noPastDays) SectionNote("아직 지나간 날이 없어요") else HintText("2주 넘게 기록하면 요일별로 보여 드려요")
        } else {
            // 요일별 평균은 일요일부터 토요일까지(WEEK_ORDER) 온다
            val topIndex = weekday.averages.indexOfFirst { it.day == weekday.top }.takeIf { it >= 0 }
            val bars =
                remember(weekday, topIndex) { weekday.averages.mapIndexed { index, average -> weekdayBar(average, index == topIndex) } }
            weekdayHeadline(weekday)?.let {
                Text(text = it, style = MaterialTheme.typography.titleSmall, color = BudgetTheme.colors.textPrimary)
            }
            WeekdayBars(bars = bars, topIndex = topIndex)
            weekdayPatternSentence(weekday)?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textPrimary)
            }
        }
    }
}
