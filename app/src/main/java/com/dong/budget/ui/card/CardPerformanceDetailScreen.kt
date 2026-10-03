package com.dong.budget.ui.card

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetSmallButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.DayHeader
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.components.animatedItems
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.stats.SectionNote
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.chart.AxisLabel
import com.dong.budget.ui.stats.chart.AxisLabelStyle
import com.dong.budget.ui.stats.chart.ColumnChart
import com.dong.budget.ui.stats.chart.ColumnSlot
import com.dong.budget.ui.stats.chart.formatAxisWon
import com.dong.budget.ui.stats.detail.transactionsTitle
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.slideByDirection
import java.time.LocalDate

/**
 * 카드 하나의 실적 상세. 카드실적 탭의 판을 눌러 들어온다.
 *
 * 위에서부터
 * - 상단 바(뒤로, 카드 이름, '수정')와 이 화면만의 기간 줄("10월 실적" · "10월 1일 ~ 10월 31일")
 * - ① 머리: 뱃지, 쓴 돈, 구간 막대, "첫 구간 30만원까지 176,550원 남았어요", 이번 기간이면 남은 날과 하루에 쓸 돈.
 *   실적을 지웠으면 막대 대신 '실적 추가'
 * - ② 최근 6개월: 한 계열 막대. 고른 기간만 카드 색이고 나머지는 흐린 색이다. 가장 높은 구간을 몇 번 채웠는지 글로 적는다.
 * - ③ 구간: 구간마다 채웠는지, 얼마 남았는지(지난 기간은 얼마 모자랐는지)
 * - ④ 그 기간 거래. 날짜별로 묶고, 누르면 거래 상세가 열린다.
 *
 * @param onEdit 상단 바 '수정'(실적을 지웠으면 머리의 '실적 추가'). 실적 구간·시작일을 고치는 화면
 * @param onThisPeriod 지난 기간을 보고 있을 때 기간 줄 오른쪽에 나오는 '이번 달'(시작일이 1일이 아니면 '이번 기간')
 * @param onOpenTransaction 거래 줄을 누르면 그 거래의 상세
 */
@Composable
fun CardPerformanceDetailScreen(
    state: CardPerformanceDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onThisPeriod: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 이름을 알기 전에는 제목과 '수정' 을 비워 둔다
            BudgetTopAppBar(
                onNavigationClick = onBack,
                title = state.card?.name,
                actions = { if (state.loaded) BudgetTextButton(text = EDIT_LABEL, onClick = onEdit) },
            )
            // 첫 계산이 끝나기 전에는 비워 둔다. 기간은 카드의 시작일을 읽어야 정해진다.
            val period = state.period
            if (!state.loaded || period == null) return@Column
            PeriodStepper(
                period = period,
                today = state.today,
                isCurrent = state.isCurrent,
                onPrevious = onPreviousPeriod,
                onNext = onNextPeriod,
                onThisPeriod = onThisPeriod,
            )
            DetailContent(state = state, onEdit = onEdit, onOpenTransaction = onOpenTransaction)
        }
    }
}

/**
 * 기간 고르기. ‹ 10월 실적 › 와 그 아래 날짜, 지난 기간을 보고 있으면 오른쪽 끝에 '이번 달'(시작일이 1일이 아니면 '이번 기간').
 * 달 줄(MonthStepper)과 같은 모양이고, 앞으로의 기간은 볼 수 없어 이번 기간에서는 › 가 막힌다.
 */
@Composable
private fun PeriodStepper(
    period: PerformancePeriod,
    today: LocalDate,
    isCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisPeriod: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BudgetIconButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 기간", onClick = onPrevious)
        // 제목과 › 는 한 덩어리로 ‹ 옆에 붙는다. 다른 해의 기간("2025년 12월 실적")처럼 제목이 길어도 좁은 화면(320dp)에서
        // › 와 '이번 달'·'이번 기간' 이 밀려나지 않게, 제목은 남는 폭 안에서 한 줄을 지키며 글자를 줄인다.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // 기간을 넘기면 제목이 넘긴 방향으로 밀려 바뀐다. 폭이 달라져도 오른쪽 화살표가 따라 움직인다.
            AnimatedContent(
                targetState = period,
                modifier = Modifier.weight(1f, fill = false),
                transitionSpec = { slideByDirection(forward = targetState.month > initialState.month).using(SizeTransform(clip = false)) },
                label = "periodTitle",
            ) { shown ->
                PeriodTitle(period = shown, today = today)
            }
            BudgetIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "다음 기간",
                onClick = onNext,
                enabled = !isCurrent,
            )
        }
        AnimatedVisibility(
            visible = !isCurrent,
            enter = fadeIn(Motion.quick()) + scaleIn(Motion.standard(), initialScale = HIDDEN_BUTTON_SCALE),
            exit = fadeOut(Motion.quick()) + scaleOut(Motion.standard(), targetScale = HIDDEN_BUTTON_SCALE),
        ) {
            BudgetTextButton(text = thisPeriodLabel(period), onClick = onThisPeriod)
        }
    }
}

/** 기간 줄 가운데. "10월 실적" 아래 "10월 1일 ~ 10월 31일". 화면 읽기는 제목으로 건너뛸 수 있고 "~" 대신 말로 읽는다. */
@Composable
private fun PeriodTitle(period: PerformancePeriod, today: LocalDate) {
    val title = periodName(period.month, today)
    val spokenTitle = "$title, ${spokenPeriodRange(period)}"
    val titleStyle = MaterialTheme.typography.titleLarge
    Column(
        modifier =
        Modifier
            .padding(horizontal = BudgetTheme.spacing.tightGap)
            .clearAndSetSemantics {
                heading()
                contentDescription = spokenTitle
            },
    ) {
        BasicText(
            text = title,
            style = titleStyle.copy(color = BudgetTheme.colors.textPrimary),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SIZE, maxFontSize = titleStyle.fontSize),
        )
        Text(text = periodRange(period), style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
    }
}

@Composable
private fun DetailContent(state: CardPerformanceDetailUiState, onEdit: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.sectionGap),
    ) {
        // 섹션 key 를 고정해 두어 기간을 넘겨 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않는다
        animatedItem(key = HEADER_KEY) { DetailHeader(state = state, onEdit = onEdit) }
        animatedItem(key = HISTORY_KEY) { HistorySection(state = state) }
        if (state.tiers.isNotEmpty()) {
            animatedItem(key = TIERS_KEY) { TierSection(state = state) }
        }
        animatedItem(key = TRANSACTIONS_KEY) {
            StatsSection(
                modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap),
                title = transactionsTitle(state.count).takeIf { state.days.isNotEmpty() },
            ) {
                if (state.days.isEmpty()) SectionNote(NO_TRANSACTIONS_TEXT)
            }
        }
        state.days.forEach { group ->
            animatedItem(key = "day-${group.date}") { DayHeader(date = group.date, today = state.today) }
            animatedItems(items = group.items, key = { "tx-${it.id}" }) { item ->
                TransactionRow(item = item, onClick = { onOpenTransaction(item.id) }, subtitle = rowSubtitle(item), colorExpense = true)
            }
        }
    }
}

/** 거래 줄 부제. 모두 이 카드라 결제수단은 빼고 '오후 2:22 · 식비' 만 적는다. */
private fun rowSubtitle(item: TransactionListItem): String =
    listOfNotNull(formatTime(item.occurredAt), item.categoryName).joinToString(" · ")

/**
 * ① 머리. 큰 뱃지 옆에 기간 이름과 쓴 돈, 아래에 구간 막대와 문장. 이번 기간이면 남은 날과 하루에 쓸 돈을 덧붙인다.
 * 화면 읽기는 금액을 줄이지 않은 한 문장으로 읽는다. 실적을 지웠으면 막대 대신 '실적 추가' 를 둔다(버튼은 따로 읽힌다).
 */
@Composable
private fun DetailHeader(state: CardPerformanceDetailUiState, onEdit: () -> Unit) {
    val card = state.card ?: return
    val period = state.period ?: return
    val progress = state.progress
    val description = detailHeaderDescription(state)
    StatsSection(block = true) {
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(icon = card.icon, color = card.color, size = BudgetTheme.size.badgeLarge)
                Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
                Column(verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
                    Text(
                        text = periodName(period.month, state.today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BudgetTheme.colors.textSecondary,
                    )
                    Text(text = spentText(progress.spent), style = BudgetTheme.amount.large, color = BudgetTheme.colors.textPrimary)
                }
            }
            if (state.tiers.isEmpty()) {
                Text(text = NO_TIERS_TEXT, style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
            } else {
                TierBar(
                    progress = progress,
                    color = card.color,
                    modifier = Modifier.padding(vertical = BudgetTheme.spacing.tightGap),
                )
                Text(
                    text = tierSentence(progress, past = !state.isCurrent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textPrimary,
                )
                if (state.isCurrent) HintText(daysLeftLine(state.daysLeft, dailyNeed(progress, state.daysLeft)))
            }
        }
        if (state.tiers.isEmpty()) {
            // 회색 판 위라 판과 같은 바탕이면 안 보여 떠 있는 색을 깐다
            BudgetSmallButton(text = ADD_PERFORMANCE, onClick = onEdit, container = BudgetTheme.colors.raised)
        }
    }
}

/**
 * ② 최근 6개월. 한 계열 막대이고 마지막 칸(고른 기간)만 카드 색, 나머지는 chartContext 다.
 * 고른 기간은 x축 글자도 굵게 적어 색만으로 가리지 않는다. 막대마다 줄인 금액을 적으므로 y축은 없다.
 * 가장 높은 구간을 채운 기간은 제목 아래 글로 몇 번인지 알리고, 칸마다 읽는 문장에도 넣는다.
 */
@Composable
private fun HistorySection(state: CardPerformanceDetailUiState) {
    val card = state.card ?: return
    val context = BudgetTheme.colors.chartContext
    val highlight = BudgetTheme.categoryPalette[card.color].content
    val slots =
        state.history.mapIndexed { index, entry ->
            val selected = index == state.history.lastIndex
            ColumnSlot(
                values = listOf(entry.progress.spent),
                label = AxisLabel("${entry.period.month.monthValue}월", if (selected) AxisLabelStyle.STRONG else AxisLabelStyle.NORMAL),
                description = historySlotDescription(entry, past = entry.period.month != state.currentMonth),
                valueLabel = formatAxisWon(entry.progress.spent),
                colors = listOf(if (selected) highlight else context),
            )
        }
    StatsSection(
        modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap),
        title = HISTORY_TITLE,
        subtitle = historySummary(state.history, state.tiers),
    ) {
        ColumnChart(slots = slots, seriesColors = listOf(context))
    }
}

/** ③ 구간. 구간마다 차례와 금액, 오른쪽에 채웠는지(브랜드색) 아니면 얼마 남았는지. 지난 기간은 얼마 모자랐는지 */
@Composable
private fun TierSection(state: CardPerformanceDetailUiState) {
    val spent = state.progress.spent
    val past = !state.isCurrent
    StatsSection(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap), title = TIERS_TITLE) {
        state.tiers.forEachIndexed { index, tier ->
            TierRow(order = tierOrder(index), amount = tierName(tier), status = tierStatus(tier, spent, past), reached = spent >= tier)
        }
    }
}

/** 구간 한 줄. 화면 읽기는 "1구간, 30만원, 채웠어요" 로 한 번에 읽는다. 채웠는지는 색만이 아니라 글로도 적는다. */
@Composable
private fun TierRow(order: String, amount: String, status: String, reached: Boolean) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
            Text(text = order, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
            Text(text = amount, style = MaterialTheme.typography.bodyLarge, color = BudgetTheme.colors.textPrimary)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (reached) BudgetTheme.colors.brandText else BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap),
        )
    }
}

private const val EDIT_LABEL = "수정"

/** 기간 줄 제목이 좁은 화면에서 줄어드는 가장 작은 글자 크기 */
private val MIN_TITLE_SIZE = 14.sp

/** '이번 달'·'이번 기간' 버튼이 나타나고 사라질 때 이 크기에서 커지고 여기까지 줄어든다(통계와 같다) */
private const val HIDDEN_BUTTON_SCALE = 0.8f

private const val HEADER_KEY = "header"
private const val HISTORY_KEY = "history"
private const val TIERS_KEY = "tiers"
private const val TRANSACTIONS_KEY = "transactions"
