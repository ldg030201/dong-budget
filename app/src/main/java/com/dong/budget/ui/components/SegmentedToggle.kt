package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/** 지출/수입처럼 둘 이상 중 하나를 고르는 토글. 회색 판 위에 고른 칸만 떠 보인다. */
@Composable
fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .background(BudgetTheme.colors.sectionBackground, shape)
            .padding(BudgetTheme.spacing.tightGap),
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val cellShape = RoundedCornerShape(BudgetTheme.radius.chip)
            Row(
                modifier =
                Modifier
                    .weight(1f)
                    .pressScaleClickable(shape = cellShape, role = Role.Tab, onClick = { onSelect(index) })
                    // 화면 읽기가 지금 고른 칸을 알려준다(예: '지출, 선택됨, 탭')
                    .semantics { this.selected = selected }
                    .background(if (selected) MaterialTheme.colorScheme.background else Color.Transparent, cellShape)
                    .height(BudgetTheme.size.minTouchTarget),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) BudgetTheme.colors.textPrimary else BudgetTheme.colors.textSecondary,
                )
            }
        }
    }
}
