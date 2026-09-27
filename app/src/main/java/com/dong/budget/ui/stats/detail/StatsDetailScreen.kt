package com.dong.budget.ui.stats.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.DayHeader
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.MonthStepper
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.stats.SectionNote
import com.dong.budget.ui.stats.SectionTitle
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.changeText
import com.dong.budget.ui.stats.chart.AxisLabel
import com.dong.budget.ui.stats.chart.AxisLabelStyle
import com.dong.budget.ui.stats.chart.ColumnChart
import com.dong.budget.ui.stats.chart.ColumnSlot
import com.dong.budget.ui.stats.chart.formatAxisWon
import com.dong.budget.ui.stats.netExpenseColor
import com.dong.budget.ui.stats.tab.breakdown.BreakdownRow
import com.dong.budget.ui.stats.tab.breakdown.MerchantRow
import com.dong.budget.ui.stats.tab.breakdown.MerchantsSection
import com.dong.budget.ui.stats.tab.breakdown.groupNote
import com.dong.budget.ui.stats.tab.monthly.TREND_TITLE
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 통계 상세. 분류 탭이나 결제수단 탭의 줄을 눌러 들어온다. 분류 하나(또는 결제수단 하나)를 모아 본다.
 *
 * 위에서부터
 * - 상단 바(뒤로, 이름)와 이 화면만의 달 줄. 통계 본 화면의 달은 바꾸지 않는다.
 * - ① 머리: 뱃지, 금액, "지출의 42% · 12건 · 한 번에 평균 2만원", 지난달 대비
 * - ② 최근 6개월: 한 계열 막대. 고른 달만 그 분류 색이고 나머지는 흐린 색이다. 막대마다 금액을 적는다.
 * - ③ 교차 비중: 분류 상세는 결제수단별, 결제수단 상세는 분류별(누를 수 없다). 수입 분류에는 없다.
 * - ④ 많이 쓴 곳 5곳(지출 쪽만)
 * - ⑤ 이 달 거래. 날짜별로 묶고, 누르면 등록창이 열린다.
 *
 * @param onOpenTransaction 거래 줄을 누르면 그 거래의 등록창
 */
@Composable
fun StatsDetailScreen(
    state: StatsDetailUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 이름을 알기 전에는 제목을 비워 둔다
            BudgetTopAppBar(onNavigationClick = onBack, title = state.entity.name.takeIf { state.loaded })
            MonthStepper(month = state.month, onPreviousMonth = onPreviousMonth, onNextMonth = onNextMonth)
            // 첫 계산이 끝나기 전에는 비워 둔다. 빈 상태 안내가 잠깐 보였다 사라지지 않게 한다.
            if (state.loaded) DetailContent(state = state, onOpenTransaction = onOpenTransaction)
        }
    }
}

@Composable
private fun DetailContent(state: StatsDetailUiState, onOpenTransaction: (Long) -> Unit) {
    val label = monthLabel(state.month, state.today)
    val cross = state.cross
    val crossDimension = state.dimension.crossDimension
    val crossTitle = crossTitle(state.dimension)
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.sectionGap),
    ) {
        // 섹션 key 를 고정해 두어 달을 넘겨 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않는다
        item(key = HEADER_KEY) { DetailHeader(state = state, monthLabel = label) }
        item(key = TREND_KEY) { DetailTrend(trend = state.trend, highlight = highlightColor(state)) }

        if (cross != null && crossDimension != null && crossTitle != null && cross.entries.isNotEmpty()) {
            item(key = CROSS_KEY) {
                Column(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap)) {
                    // 줄은 스스로 좌우 여백을 가진다(누를 수 있는 탭의 줄과 같은 부품)
                    SectionTitle(text = crossTitle, modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal))
                    cross.entries.forEach { entry -> BreakdownRow(entry = entry, dimension = crossDimension, onClick = null) }
                }
            }
        }

        if (state.merchants.isNotEmpty()) {
            item(key = MERCHANTS_KEY) {
                MerchantsSection(merchants = state.merchants)
            }
        }

        item(key = TRANSACTIONS_KEY) {
            StatsSection(
                modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap),
                title = transactionsTitle(state.count).takeIf { state.days.isNotEmpty() },
            ) {
                if (state.days.isEmpty()) SectionNote(noTransactionsText(label, state.dimension))
            }
        }
        state.days.forEach { group ->
            item(key = "day-${group.date}") { DayHeader(date = group.date, today = state.today) }
            items(items = group.items, key = { "tx-${it.id}" }) { item ->
                TransactionRow(item = item, onClick = { onOpenTransaction(item.id) })
            }
        }
    }
}

/**
 * ① 머리. 큰 뱃지 옆에 라벨과 금액, 아래에 비율·건수·한 번에 평균과 지난달 대비.
 * 화면 읽기는 금액을 줄이지 않은 한 문장으로 읽는다. 지운 분류라는 안내는 따로 읽는다.
 */
@Composable
private fun DetailHeader(state: StatsDetailUiState, monthLabel: String) {
    val description = detailDescription(state, monthLabel)
    StatsSection(block = true) {
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(icon = state.entity.icon, color = state.entity.color, size = BudgetTheme.size.badgeLarge)
                Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
                Column(verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
                    Text(
                        text = detailLabel(monthLabel, state.dimension),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BudgetTheme.colors.textSecondary,
                    )
                    Text(
                        text = formatNetExpense(state.amount),
                        style = BudgetTheme.amount.large,
                        color = netExpenseColor(state.amount),
                    )
                }
            }
            state.entry?.let { entry ->
                Text(
                    text = detailSummary(entry, state.dimension),
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
            state.change?.let { change ->
                Text(text = changeText(change), style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
            }
        }
        if (state.entity.missing) HintText(missingHint(state.dimension))
        groupNote(state.key, state.dimension)?.let { HintText(it) }
    }
}

/**
 * ② 최근 6개월. 한 계열 막대이고 마지막 칸(고른 달)만 [highlight] 색, 나머지는 chartContext 다.
 * 고른 달은 x축 글자도 굵게 적어 색만으로 가리지 않는다. 막대마다 줄인 금액을 적으므로 y축은 없다.
 * 누를 수 없고, 화면 읽기는 칸마다 "2026년 7월, 83,000원, 5건" 을 읽는다.
 */
@Composable
private fun DetailTrend(trend: List<DetailMonth>, highlight: Color) {
    val context = BudgetTheme.colors.chartContext
    val slots =
        trend.mapIndexed { index, point ->
            val selected = index == trend.lastIndex
            ColumnSlot(
                // 기록을 시작하기 전 달은 막대 없이 달 이름만 둔다
                values = if (point.beforeFirstRecord) emptyList() else listOf(point.amount),
                label = AxisLabel("${point.month.monthValue}월", if (selected) AxisLabelStyle.STRONG else AxisLabelStyle.NORMAL),
                description = trendSlotDescription(point),
                valueLabel = if (point.beforeFirstRecord) null else formatAxisWon(point.amount),
                colors = listOf(if (selected) highlight else context),
            )
        }
    StatsSection(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap), title = TREND_TITLE) {
        ColumnChart(slots = slots, seriesColors = listOf(context))
    }
}

/** 고른 달 막대의 색. 분류(결제수단) 색이고, 색이 없으면('분류 없음', 지운 것) 지출·수입 차트 색이다. */
@Composable
private fun highlightColor(state: StatsDetailUiState): Color {
    val color = state.entity.color ?: return if (state.dimension == StatsDimension.INCOME_CATEGORY) {
        BudgetTheme.colors.chartIncome
    } else {
        BudgetTheme.colors.chartExpense
    }
    return BudgetTheme.categoryPalette[color].content
}

private const val HEADER_KEY = "header"
private const val TREND_KEY = "trend"
private const val CROSS_KEY = "cross"
private const val MERCHANTS_KEY = "merchants"
private const val TRANSACTIONS_KEY = "transactions"
