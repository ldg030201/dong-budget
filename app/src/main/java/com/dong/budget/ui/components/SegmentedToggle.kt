package com.dong.budget.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 지출/수입처럼 둘 이상 중 하나를 고르는 토글. 회색 판 위에 고른 칸만 떠 보인다.
 * 다른 칸을 고르면 떠 있는 판이 그 칸으로 미끄러져 간다(떠 있는 메뉴와 같은 움직임).
 */
@Composable
fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    val cellShape = RoundedCornerShape(BudgetTheme.radius.chip)
    val gap = BudgetTheme.spacing.tightGap
    val cellHeight = BudgetTheme.size.minTouchTarget
    BoxWithConstraints(
        modifier =
        modifier
            .fillMaxWidth()
            .background(BudgetTheme.colors.sectionBackground, shape)
            .padding(BudgetTheme.spacing.tightGap),
    ) {
        val count = options.size.coerceAtLeast(1)
        // 칸 사이가 떨어져 있어서 판은 '칸 번호 × (칸 폭 + 칸 사이)' 만큼 옮긴다
        val cellWidth = (maxWidth - gap * (count - 1)) / count
        val indicatorOffset =
            animateDpAsState(
                targetValue = (cellWidth + gap) * selectedIndex,
                animationSpec = Motion.indicator(),
                label = "toggleIndicator",
            )
        // 위치는 배치 단계에서 읽는다. 미끄러지는 동안 칸 글자까지 다시 그리지 않게 한다.
        Box(
            modifier =
            Modifier
                .offset { IntOffset(indicatorOffset.value.roundToPx(), 0) }
                .width(cellWidth)
                .height(cellHeight)
                .background(MaterialTheme.colorScheme.background, cellShape),
        )
        Row(
            // 화면 읽기가 칸들을 한 묶음으로 알린다('2개 중 1번째')
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    if (selected) BudgetTheme.colors.textPrimary else BudgetTheme.colors.textSecondary,
                    Motion.standard(),
                    label = "toggleText",
                )
                Row(
                    modifier =
                    Modifier
                        .weight(1f)
                        .pressScaleClickable(shape = cellShape, role = Role.Tab, onClick = { onSelect(index) })
                        // 화면 읽기가 지금 고른 칸을 알려준다(예: '지출, 선택됨, 탭')
                        .semantics { this.selected = selected }
                        .height(cellHeight),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall, color = textColor)
                }
            }
        }
    }
}
