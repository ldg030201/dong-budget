package com.dong.budget.ui.salary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dong.budget.ui.format.formatWeekday
import com.dong.budget.ui.format.formatWeekdayFull
import com.dong.budget.ui.home.weekOrder
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressFeedback
import java.time.DayOfWeek

/** 일하는 요일 칸 순서. 출근 요일이라 월요일부터 둔다. */
private val WORK_WEEK = weekOrder(DayOfWeek.MONDAY)

private val SelectedBorderWidth = 1.5.dp
private val UnselectedBorderWidth = 1.dp

/**
 * 일하는 요일 고르기. 일곱 칸을 한 줄에 나눠 두고 여러 개를 고른다.
 * 화면 읽기에는 칸마다 '월요일, 체크됨' 처럼 읽힌다(하나만 고르는 라디오 버튼이 아니다).
 * 고른 칸은 채움·테두리·글자 굵기로 함께 보인다. 색만으로 가르면 색을 구분하기 어려운 사람에게 안 보인다.
 */
@Composable
internal fun WeekdayToggleRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = BudgetTheme.spacing.inlineGap),
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        WORK_WEEK.forEach { day ->
            val on = day in selected
            val shape = RoundedCornerShape(BudgetTheme.radius.chip)
            val source = remember { MutableInteractionSource() }
            val container by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primaryContainer else BudgetTheme.colors.raised,
                Motion.quick(),
                label = "weekdayContainer",
            )
            val border by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else BudgetTheme.colors.divider,
                Motion.quick(),
                label = "weekdayBorder",
            )
            val borderWidth by animateDpAsState(
                if (on) SelectedBorderWidth else UnselectedBorderWidth,
                Motion.quick(),
                label = "weekdayBorderWidth",
            )
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .height(BudgetTheme.size.minTouchTarget)
                    .pressFeedback(source, shape)
                    .toggleable(value = on, interactionSource = source, indication = null, role = Role.Checkbox, onValueChange = {
                        onToggle(day)
                    })
                    .semantics { contentDescription = formatWeekdayFull(day) }
                    .background(container, shape)
                    .border(borderWidth, border, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatWeekday(day),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else BudgetTheme.colors.textSecondary,
                )
            }
        }
    }
}
