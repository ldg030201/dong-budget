package com.dong.budget.ui.stats.tab.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.ui.components.BudgetDivider
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.stats.DailyStats
import com.dong.budget.ui.stats.DayStack
import com.dong.budget.ui.stats.StatSeries
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.calc.dayNotes
import com.dong.budget.ui.stats.calc.daySpentText
import com.dong.budget.ui.stats.calc.nothingText
import com.dong.budget.ui.stats.chart.ColumnChart
import com.dong.budget.ui.stats.chart.ColumnSlot
import com.dong.budget.ui.stats.chart.LegendChip
import com.dong.budget.ui.stats.chart.LegendSwatch
import com.dong.budget.ui.stats.chart.dailyAxisLabels
import com.dong.budget.ui.stats.chart.entityColor
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * ② 날마다 쓴 돈. 범례를 겸하는 필터 칩, 분류별로 쌓은 막대, 고른 날의 읽기 판과 그날 거래를 차례로 둔다.
 * 이 달에 쌓을 지출이 하나도 없으면 셋 대신 "{달}에는 쓴 돈이 없어요" 한 줄만 둔다.
 *
 * 색은 분류 색을 따르고 '그 외'·'분류 없음' 만 chartOther 다. 분류 팔레트는 이웃 색끼리 가려 보기 어려워서
 * 색에만 기대지 않게 이름 있는 칩, 하나만 보기, 칸별 화면 읽기 문장, 읽기 판의 분류별 금액을 함께 둔다.
 *
 * @param selectedDay 고른 날(일). 1일부터 말일까지
 * @param onSelectDay 차트를 누르거나 끌어서, 또는 읽기 판의 ‹ › 로 다른 날(일)을 고를 때
 * @param focused '하나만 보기' 로 고른 계열 번호. null 이면 모든 계열을 쌓는다.
 * @param onFocus 칩을 누르면 그 계열 번호, '전체' 를 누르거나 고른 칩을 다시 누르면 null
 * @param onOpenTransaction 읽기 판 아래 거래 줄을 누르면 등록창
 */
@Composable
internal fun DailySpendingSection(
    state: StatsUiState,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    focused: Int?,
    onFocus: (Int?) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = state.daily
    // 쌓을 지출이 하나도 없는 달은 고른 날도 읽기 판도 없다
    val day = daily.days.getOrNull(selectedDay - 1)?.takeIf { daily.stackMax > 0 }
    Column(modifier = modifier.fillMaxWidth()) {
        StatsSection(title = "날마다 쓴 돈") {
            if (day == null) {
                SectionNote(nothingText(monthLabel(state.month, state.today)))
            } else {
                val colors = seriesColors(daily.series)
                SeriesFilter(series = daily.series, colors = colors, focused = focused, onFocus = onFocus)
                // 계열이 하나뿐이면 골라도 그림이 같아서 안내는 빼고, 칩은 범례 몫으로 둔다.
                if (daily.series.size > 1) HintText("분류를 누르면 그 분류만 볼 수 있어요")
                DailyChart(
                    daily = daily,
                    month = state.month,
                    today = state.today,
                    colors = colors,
                    selectedDay = selectedDay,
                    focused = focused,
                    onSelectDay = onSelectDay,
                )
                DayReading(day = day, today = state.today, lastDay = daily.days.size, colors = colors, onSelectDay = onSelectDay)
            }
        }
        // 거래 줄은 화면 끝까지 눌리는 목록 줄이라 섹션의 좌우 여백 밖에 둔다
        if (day != null) DayTransactions(day = day, onOpenTransaction = onOpenTransaction)
    }
}

/** 계열 색. 분류 색의 진한 쪽(content)이고, 색 이름이 없는 '그 외'·'분류 없음' 은 chartOther 다. */
@Composable
private fun seriesColors(series: List<StatSeries>): List<Color> = series.map { entityColor(it.color) }

/**
 * 범례를 겸하는 필터 칩. [전체] [■식비] … [■그 외 3개]
 * 칩을 누르면 그 계열만 그리고 y축을 그 계열에 맞춘다('그 외' 도 고를 수 있다). 같은 칩을 다시 누르거나 '전체' 를 누르면 돌아간다.
 */
@Composable
private fun SeriesFilter(series: List<StatSeries>, colors: List<Color>, focused: Int?, onFocus: (Int?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        LegendChip(
            label = "전체",
            color = null,
            selected = focused == null,
            onClick = { onFocus(null) },
            contentDescription = filterChipDescription(null),
        )
        series.forEachIndexed { index, s ->
            LegendChip(
                label = s.name,
                color = colors[index],
                selected = focused == index,
                onClick = { onFocus(if (focused == index) null else index) },
                contentDescription = filterChipDescription(s),
            )
        }
    }
}

/**
 * 1일부터 말일까지 분류별로 쌓은 막대. 누르거나 가로로 끌어서 날을 고른다.
 * x축에는 고정 눈금과 함께 오늘(브랜드색 Bold)과 고른 날(Bold)을 늘 적는다.
 */
@Composable
private fun DailyChart(
    daily: DailyStats,
    month: YearMonth,
    today: LocalDate,
    colors: List<Color>,
    selectedDay: Int,
    focused: Int?,
    onSelectDay: (Int) -> Unit,
) {
    // 오늘은 이 달에 있을 때만 적는다
    val todayDay = today.takeIf { YearMonth.from(it) == month }?.dayOfMonth
    // 칸 설명은 날을 골라도 바뀌지 않는다. x축 글자만 고른 날을 따라 다시 만든다.
    val descriptions = remember(daily.days, today) { daily.days.map { daySlotDescription(it, today) } }
    val slots =
        remember(daily.days, descriptions, todayDay, selectedDay) {
            val labels = dailyAxisLabels(lastDay = daily.days.size, today = todayDay, selected = selectedDay)
            daily.days.mapIndexed { index, day ->
                ColumnSlot(values = day.segments, label = labels[index], description = descriptions[index])
            }
        }
    ColumnChart(
        slots = slots,
        seriesColors = colors,
        focusedSeries = focused,
        selectedIndex = selectedDay - 1,
        onSelect = { index -> onSelectDay(index + 1) },
    )
}

/**
 * 차트 바로 아래 늘 보이는 읽기 판. 고른 날의 날짜와 쓴 돈, 그날 금액이 있는 분류 전부(접힌 분류도 제 이름으로),
 * 빠진 환불이나 수입이 있으면 그 안내를 둔다.
 * ‹ › 는 차트를 끌지 않고도 하루씩 옮기는 대체 조작이다. 1일과 말일에서는 흐리게 막힌다.
 */
@Composable
private fun DayReading(day: DayStack, today: LocalDate, lastDay: Int, colors: List<Color>, onSelectDay: (Int) -> Unit) {
    val dayOfMonth = day.date.dayOfMonth
    val other = BudgetTheme.colors.chartOther
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
        // 날짜와 쓴 돈을 한 덩어리로 읽고, 고른 날이 바뀌면 화면 읽기가 새 날을 알려준다. ‹ › 는 따로 누르는 버튼으로 남는다.
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BudgetIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "이전 날",
                    onClick = { onSelectDay(dayOfMonth - 1) },
                    shape = CircleShape,
                    enabled = dayOfMonth > 1,
                )
                Text(
                    text = dayTitle(day.date, today),
                    style = MaterialTheme.typography.titleSmall,
                    color = BudgetTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                BudgetIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "다음 날",
                    onClick = { onSelectDay(dayOfMonth + 1) },
                    shape = CircleShape,
                    enabled = dayOfMonth < lastDay,
                )
            }
            Text(text = daySpentText(day), style = BudgetTheme.amount.medium, color = BudgetTheme.colors.textPrimary)
        }
        day.details.forEach { detail ->
            // 접힌 분류와 계열이 없는 분류는 '그 외' 견본(chartOther)이다
            DetailRow(color = detail.seriesIndex?.let { colors.getOrNull(it) } ?: other, name = detail.name, amount = detail.amount)
        }
        dayNotes(day).forEach { HintText(it) }
    }
}

/** 읽기 판의 분류 한 줄: 견본 + 이름(길면 말줄임) + 금액. 환불이 더 많은 분류는 "+2,000원" 을 수입 색으로 적는다. */
@Composable
private fun DetailRow(color: Color, name: String, amount: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(color)
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = BudgetTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(
            text = spentAmount(amount),
            style = BudgetTheme.amount.tableCell,
            color = if (amount < 0) BudgetTheme.colors.income else BudgetTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * 고른 날의 거래. 처음에는 [PREVIEW_ITEMS] 건만 보이고 나머지는 'n건 더 보기' 로 펼친다.
 * 펼침은 날마다 따로이고(날짜가 달을 담고 있어 달마다도 따로다), 다른 날을 고르면 다시 접힌다.
 */
@Composable
private fun DayTransactions(day: DayStack, onOpenTransaction: (Long) -> Unit) {
    if (day.items.isEmpty()) return
    var expanded by rememberSaveable(day.date) { mutableStateOf(false) }
    val shown = if (expanded) day.items else day.items.take(PREVIEW_ITEMS)
    val hidden = day.items.size - shown.size
    Column(modifier = Modifier.fillMaxWidth().padding(top = BudgetTheme.spacing.itemGap)) {
        BudgetDivider(Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal))
        shown.forEach { item ->
            key(item.id) { TransactionRow(item = item, onClick = { onOpenTransaction(item.id) }) }
        }
        if (hidden > 0) {
            BudgetTextButton(
                text = moreItemsText(hidden),
                onClick = { expanded = true },
                // 버튼 안쪽 여백만큼 당겨서 글자가 거래 줄의 뱃지와 줄을 맞춘다
                modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal - BudgetTheme.spacing.tightGap),
            )
        }
    }
}

/** 읽기 판 아래 처음 보이는 거래 수 */
private const val PREVIEW_ITEMS = 3
