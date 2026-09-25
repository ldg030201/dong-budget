package com.dong.budget.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.NavIconButton
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDayHeader
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.format.formatSignedAmount
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 홈. 위에서부터 이번 달 수입·지출 요약, 달력, 날짜별 거래 목록이 한 화면에 이어진다.
 *
 * 첫 화면에 달력과 함께 최근 거래가 두세 개 보이고, 아래로 내리면 나머지 거래가 이어진다.
 * 달력이 스크롤로 사라지면 한 주 줄([WeekStrip])이 위에 붙는다.
 * 달력이나 한 주 줄에서 날짜를 누르면 목록이 그날로 스크롤된다.
 *
 * 상단 인셋은 이 화면이 직접 처리한다. 셸의 Scaffold 는 인셋을 비워둔다.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            // 가로 화면에서 옆에 붙는 시스템 버튼 줄이나 카메라 구멍 밑으로 금액과 추가 버튼이 들어가지 않게 한다
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            MonthSelector(month = state.month, onPreviousMonth = onPreviousMonth, onNextMonth = onNextMonth)
            // 달이 바뀌면 스크롤 위치와 고른 날짜를 처음부터 다시 시작한다
            key(state.month) {
                MonthBody(state = state, onEditTransaction = onEditTransaction, modifier = Modifier.weight(1f))
            }
        }
        FloatingActionButton(
            onClick = onAddTransaction,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = BudgetTheme.elevation.fab),
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = BudgetTheme.spacing.screenHorizontal, bottom = BudgetTheme.spacing.sectionPadding),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "거래 등록")
        }
    }
}

// 목록 맨 앞의 고정 줄. 날짜별 거래는 그 뒤에 온다.
private const val SUMMARY_KEY = "summary"
private const val CALENDAR_KEY = "calendar"
private const val CALENDAR_INDEX = 1
private const val FIRST_DAY_INDEX = 2

/**
 * 목록 안에서 줄 번호와 날짜를 오가는 표.
 * 달력에서 날짜를 누르면 그날 구분선의 줄 번호가 필요하고,
 * 스크롤 중에는 화면 위쪽 줄이 며칠인지 알아야 한 주 줄을 맞출 수 있다.
 */
private class DayIndex(groups: List<DayGroup>) {
    val headerIndex: Map<LocalDate, Int>
    private val dateByIndex: List<LocalDate>

    init {
        val headers = mutableMapOf<LocalDate, Int>()
        val dates = mutableListOf<LocalDate>()
        groups.forEach { group ->
            headers[group.date] = FIRST_DAY_INDEX + dates.size
            repeat(1 + group.items.size) { dates += group.date }
        }
        headerIndex = headers
        dateByIndex = dates
    }

    fun dateAt(index: Int): LocalDate? = dateByIndex.getOrNull(index - FIRST_DAY_INDEX)
}

@Composable
private fun MonthBody(state: HomeUiState, onEditTransaction: (Long) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    // 누른 날의 거래를 지우거나 다른 날로 옮기면 그날은 더 이상 누를 수 없는 칸이다. 그런 날은 표시하지 않는다.
    val highlight = selected?.takeIf { it in state.days }
    val weeks = remember(state.month) { calendarWeeks(state.month) }
    val dayIndex = remember(state.groups) { DayIndex(state.groups) }
    val density = LocalDensity.current
    val stripHeightPx = with(density) { weekStripHeight().roundToPx() }
    // 날짜 구분선 줄의 윗여백과 선. 스크롤할 때 이만큼은 한 주 줄 밑으로 숨겨서 선이 두 겹으로 보이지 않게 한다.
    val headerLeadPx = with(density) { (BudgetTheme.spacing.inlineGap + BudgetTheme.size.underline).roundToPx() }

    val showStrip by remember(listState, stripHeightPx) {
        derivedStateOf { isCalendarScrolledAway(listState, stripHeightPx) }
    }
    // 한 주 줄에 보여줄 날. 방금 누른 날이 보이면 그날, 아니면 한 주 줄 바로 아래에 걸친 줄의 날짜.
    val anchor by remember(listState, dayIndex, stripHeightPx) {
        derivedStateOf {
            val visible =
                listState.layoutInfo.visibleItemsInfo
                    .filter { it.offset + it.size > stripHeightPx }
                    .mapNotNull { dayIndex.dateAt(it.index) }
            selected?.takeIf { it in visible } ?: visible.firstOrNull()
        }
    }

    fun scrollTo(date: LocalDate) {
        val index = dayIndex.headerIndex[date] ?: return
        selected = date
        // 한 주 줄이 목록 위를 덮으므로 그 높이만큼 내려서 날짜가 가려지지 않게 한다
        scope.launch { listState.animateScrollToItem(index, scrollOffset = -(stripHeightPx - headerLeadPx)) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 마지막 거래가 떠 있는 추가 버튼에 가리지 않게 아래를 비운다
            contentPadding = PaddingValues(bottom = BudgetTheme.size.fab + BudgetTheme.spacing.sectionGap),
        ) {
            item(key = SUMMARY_KEY) {
                SummaryBlock(totals = state.totals, comparison = state.comparison)
            }
            item(key = CALENDAR_KEY) {
                MonthCalendar(
                    weeks = weeks,
                    days = state.days,
                    today = state.today,
                    highlighted = highlight,
                    onDayClick = ::scrollTo,
                    modifier = Modifier.padding(top = BudgetTheme.spacing.itemGap),
                )
            }
            if (state.groups.isEmpty()) {
                item(key = "empty") { EmptyMonth() }
            }
            state.groups.forEach { group ->
                item(key = "day-${group.date}") { DayHeader(date = group.date, today = state.today) }
                items(items = group.items, key = { "tx-${it.id}" }) { item ->
                    TransactionRow(item = item, onClick = { onEditTransaction(item.id) })
                }
            }
        }

        AnimatedVisibility(
            visible = showStrip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val shown = anchor ?: highlight ?: state.today
            WeekStrip(
                week = weekOf(shown, state.month),
                days = state.days,
                today = state.today,
                highlighted = anchor,
                onDayClick = ::scrollTo,
                onExpand = { scope.launch { listState.animateScrollToItem(0) } },
            )
        }
    }
}

/** 달력의 아래 끝이 한 주 줄 높이보다 위로 올라가면 달력이 사라진 것으로 본다. */
private fun isCalendarScrolledAway(listState: LazyListState, stripHeightPx: Int): Boolean {
    val calendar = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == CALENDAR_KEY }
    return if (calendar == null) {
        listState.firstVisibleItemIndex > CALENDAR_INDEX
    } else {
        calendar.offset + calendar.size <= stripHeightPx
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIconButton(
            icon = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "이전 달",
            onClick = onPreviousMonth,
        )
        Text(
            text = formatMonth(month),
            style = MaterialTheme.typography.titleLarge,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.tightGap),
        )
        NavIconButton(
            icon = Icons.Filled.KeyboardArrowRight,
            contentDescription = "다음 달",
            onClick = onNextMonth,
        )
    }
}

@Composable
private fun SummaryBlock(totals: Totals, comparison: SpendingComparison?) {
    Column(
        modifier =
        Modifier
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .fillMaxWidth()
            .background(BudgetTheme.colors.sectionBackground, RoundedCornerShape(BudgetTheme.radius.block))
            .padding(BudgetTheme.spacing.sectionPadding),
    ) {
        Row {
            SummaryCell(
                label = "지출",
                value = "${formatAmount(totals.expense)}원",
                valueColor = BudgetTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            SummaryCell(
                label = "수입",
                value = "${formatAmount(totals.income)}원",
                valueColor = BudgetTheme.colors.income,
                modifier = Modifier.weight(1f),
            )
        }
        if (comparison != null) {
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BudgetTheme.size.underline)
                    .background(BudgetTheme.colors.divider),
            )
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            Text(
                text = comparisonText(comparison),
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BudgetTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        Text(
            text = value,
            style = BudgetTheme.amount.summary,
            color = valueColor,
        )
    }
}

/** 금액 부분만 색을 입힌다. 덜 썼으면 브랜드색, 더 썼으면 경고색. */
@Composable
private fun comparisonText(comparison: SpendingComparison): AnnotatedString {
    val sentence = comparison.sentence()
    val amountColor =
        if (comparison.difference > 0) BudgetTheme.colors.danger else BudgetTheme.colors.brandText
    return buildAnnotatedString {
        append(sentence.prefix)
        if (sentence.amount != null) {
            withStyle(SpanStyle(color = amountColor, fontWeight = FontWeight.SemiBold)) { append(sentence.amount) }
        }
        append(sentence.suffix)
    }
}

/** 날짜 구분선. 가는 선 아래에 날짜를 적는다. */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(top = BudgetTheme.spacing.inlineGap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BudgetTheme.size.underline)
                .background(BudgetTheme.colors.divider),
        )
        Text(
            text = formatDayHeader(date, today),
            style = MaterialTheme.typography.labelMedium,
            color = BudgetTheme.colors.textSecondary,
            modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.tightGap),
        )
    }
}

@Composable
private fun TransactionRow(item: TransactionListItem, onClick: () -> Unit) {
    BudgetListItem(
        title = item.merchant ?: item.categoryName ?: "이름 없는 거래",
        leading = { CategoryBadge(icon = item.categoryIcon, color = item.categoryColor) },
        subtitle =
        listOfNotNull(
            formatTime(item.occurredAt),
            item.categoryName,
            item.paymentMethodName,
        ).joinToString(" · "),
        trailing = {
            Text(
                text = formatSignedAmount(item.type, item.amount),
                style = BudgetTheme.amount.medium,
                color =
                when (item.type) {
                    TransactionType.INCOME, TransactionType.REFUND -> BudgetTheme.colors.income
                    else -> BudgetTheme.colors.textPrimary
                },
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun EmptyMonth() {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal, vertical = BudgetTheme.spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        Text(
            text = "이 달에는 거래가 없어요",
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        Text(
            // 화면 읽기에서는 버튼이 '거래 등록' 으로 읽히므로 모양(+)만이 아니라 이름으로도 가리킨다
            text = "오른쪽 아래 거래 등록 버튼(+)으로 남겨 보세요",
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
        )
    }
}
