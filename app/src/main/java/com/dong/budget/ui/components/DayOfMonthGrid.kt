package com.dong.budget.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 매달 며칠(1~31일) 고르기. 달력처럼 일곱 칸씩 늘어놓고, 다섯 줄이 키패드 입력판(InputPanelBox) 높이 안에 들어간다.
 * 한 번 누르면 바로 [onPick] 을 부른다. 월급날과 카드 실적 시작일이 같이 쓴다.
 * 31일은 없는 달이 있어 '말일' 로도 쓴다. 그 뜻은 [describe] 가 화면 읽기에 알린다.
 *
 * @param describe 칸마다 화면 읽기가 읽을 말("매달 25일", "매달 31일, 없는 달은 말일")
 */
@Composable
fun DayOfMonthGrid(selected: Int, onPick: (Int) -> Unit, describe: (Int) -> String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
        (1..LAST_DAY).chunked(DAYS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { day ->
                    DayCell(
                        day = day,
                        selected = day == selected,
                        description = describe(day),
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 마지막 줄도 칸 폭을 맞춘다
                repeat(DAYS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, description: String, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val mark by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
        Motion.quick(),
        label = "dayOfMonthMark",
    )
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    Box(
        modifier =
        modifier
            .height(CELL_HEIGHT)
            .pressScaleClickable(shape = shape, role = Role.RadioButton, onClick = { onPick(day) })
            .semantics {
                this.selected = selected
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(BudgetTheme.size.calendarDayMark).background(mark, CircleShape))
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else BudgetTheme.colors.textPrimary,
        )
    }
}

/** 가장 긴 달의 날 수 */
private const val LAST_DAY = 31

private const val DAYS_PER_ROW = 7

/** 다섯 줄(1~31일)이 키패드 입력판 높이 안에 들어가는 칸 높이 */
private val CELL_HEIGHT = 40.dp
