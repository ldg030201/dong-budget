package com.dong.budget.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDateSpoken
import com.dong.budget.ui.format.formatWeekday
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.LocalDate

/**
 * 한 달 달력. 칸마다 그날 들어온 돈과 쓴 돈을 적는다.
 * 거래가 있는 날을 누르면 [onDayClick] 이 불린다. 거래가 없는 날은 누를 수 없다.
 *
 * @param highlighted 눌러서 고른 날. 동그라미로 표시한다.
 */
@Composable
fun MonthCalendar(
    weeks: List<List<LocalDate?>>,
    days: Map<LocalDate, Totals>,
    today: LocalDate,
    highlighted: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = BudgetTheme.spacing.inlineGap)) {
        WeekdayHeader()
        weeks.forEach { week ->
            WeekRow(week = week, days = days, today = today, highlighted = highlighted, onDayClick = onDayClick)
        }
    }
}

/**
 * 목록을 내렸을 때 화면 위에 붙어 있는 한 주 줄.
 * 달력이 스크롤로 사라져도 날짜를 바로 오갈 수 있게 한다. 아래 화살표를 누르면 달력이 있는 맨 위로 돌아간다.
 */
@Composable
fun WeekStrip(
    week: List<LocalDate?>,
    days: Map<LocalDate, Totals>,
    today: LocalDate,
    highlighted: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .height(weekStripHeight())
            // 이 줄은 목록 위에 겹쳐 그려진다. 요일 글자나 거래 없는 날처럼 누를 수 없는 곳을 눌러도
            // 탭이 밑에 가려진 거래 줄로 새서 보이지 않는 거래가 열리지 않도록, 줄 전체가 터치를 받는다.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            // 화면 읽기도 마찬가지로 이 줄에 가려진 목록 줄을 건너뛰게 한다.
            // 접근성에 의미 있는 속성이 있어야 가린 영역으로 계산되는데, 날짜 칸 묶음이라 selectableGroup 이 맞다.
            .selectableGroup()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.padding(horizontal = BudgetTheme.spacing.inlineGap)) {
            WeekdayHeader()
            WeekRow(week = week, days = days, today = today, highlighted = highlighted, onDayClick = onDayClick)
        }
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(BudgetTheme.size.minTouchTarget)
                .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onExpand)
                .semantics { contentDescription = "달력 펼치기" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = BudgetTheme.colors.textTertiary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BudgetTheme.size.underline)
                .background(BudgetTheme.colors.divider),
        )
    }
}

/** 한 주 줄 전체 높이. 목록이 이 높이만큼 가려지므로 스크롤 위치를 맞출 때도 쓴다. */
@Composable
fun weekStripHeight(): Dp = BudgetTheme.size.weekdayRowHeight +
    BudgetTheme.size.calendarDayHeight +
    BudgetTheme.size.minTouchTarget +
    BudgetTheme.size.underline

@Composable
private fun WeekdayHeader() {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.weekdayRowHeight)
            // 요일 글자는 날짜 칸이 이미 읽어주므로 화면 읽기에서 뺀다
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WEEK_ORDER.forEach { day ->
            Text(
                text = formatWeekday(day),
                style = MaterialTheme.typography.labelSmall,
                color = BudgetTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekRow(
    week: List<LocalDate?>,
    days: Map<LocalDate, Totals>,
    today: LocalDate,
    highlighted: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        week.forEach { date ->
            val cellModifier = Modifier.weight(1f).height(BudgetTheme.size.calendarDayHeight)
            if (date == null) {
                Spacer(cellModifier)
            } else {
                DayCell(
                    date = date,
                    totals = days[date],
                    today = today,
                    highlighted = date == highlighted,
                    onClick = { onDayClick(date) },
                    modifier = cellModifier,
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    totals: Totals?,
    today: LocalDate,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == today
    val clickable =
        if (totals != null) {
            Modifier.pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onClick)
        } else {
            Modifier
        }
    Column(
        modifier =
        modifier
            .then(clickable)
            .clearAndSetSemantics {
                contentDescription = describeDay(date, totals, isToday)
                selected = highlighted
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
            Modifier
                .size(BudgetTheme.size.calendarDayMark)
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || highlighted) FontWeight.Bold else FontWeight.Normal,
                color =
                when {
                    // 동그라미 위에서는 오늘이어도 동그라미 전용 글자색을 쓴다. 브랜드색 글자는 동그라미 위에서 대비가 모자라다.
                    highlighted -> MaterialTheme.colorScheme.onPrimaryContainer

                    isToday -> BudgetTheme.colors.brandText

                    // 아직 오지 않은 날은 한 단계 흐리게 둔다. 가장 흐린 글자색은 이 크기에서 대비가 모자라다.
                    date.isAfter(today) -> BudgetTheme.colors.textSecondary

                    else -> BudgetTheme.colors.textPrimary
                },
            )
        }
        if (totals != null) {
            // 들어온 돈은 초록 +, 쓴 돈은 빨강 - 로 한눈에 구분한다
            if (totals.income > 0) DayAmount("+${formatAmount(totals.income)}", BudgetTheme.colors.income)
            when {
                totals.expense > 0 -> DayAmount("-${formatAmount(totals.expense)}", BudgetTheme.colors.expense)

                // 환불이 그날 쓴 돈보다 많으면 돈이 돌아온 날이다. 들어온 돈처럼 + 로 적는다.
                totals.expense < 0 -> DayAmount("+${formatAmount(-totals.expense)}", BudgetTheme.colors.income)
            }
        }
    }
}

/**
 * 칸 안의 작은 금액. 칸 너비가 좁아서 큰 금액은 글자를 줄여 한 줄에 맞춘다.
 * 잘라서 "1,234,…" 처럼 보이면 금액을 잘못 읽을 수 있다.
 */
@Composable
private fun DayAmount(text: String, color: Color) {
    val style = BudgetTheme.amount.calendar
    BasicText(
        text = text,
        style = style.copy(color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_AMOUNT_SIZE, maxFontSize = style.fontSize, stepSize = AMOUNT_SIZE_STEP),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun describeDay(date: LocalDate, totals: Totals?, isToday: Boolean): String = buildList {
    add(formatDateSpoken(date))
    if (isToday) add("오늘")
    if (totals != null) {
        if (totals.income > 0) add("수입 ${formatAmount(totals.income)}원")
        if (totals.expense > 0) add("지출 ${formatAmount(totals.expense)}원")
        if (totals.expense < 0) add("환불 ${formatAmount(-totals.expense)}원")
    }
}.joinToString(", ")

private val MIN_AMOUNT_SIZE = 8.sp
private val AMOUNT_SIZE_STEP = 0.5.sp
