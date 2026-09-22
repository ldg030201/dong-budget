package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

private val SelectedBorderWidth = 1.5.dp
private val UnselectedBorderWidth = 1.dp

/**
 * 선택할 수 있는 칩.
 *
 * 선택 상태를 채움 색 하나로만 표시하지 않고 테두리와 글자색으로 이중화한다.
 * 채움 색만으로 구분하면 색을 구분하기 어려운 사용자에게 선택 여부가 보이지 않는다.
 */
@Composable
fun BudgetChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else BudgetTheme.colors.divider
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else BudgetTheme.colors.textSecondary

    Box(
        modifier =
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .pressScaleClickable(shape = shape, role = Role.RadioButton, onClick = onClick)
            .background(containerColor, shape)
            .border(
                width = if (selected) SelectedBorderWidth else UnselectedBorderWidth,
                color = borderColor,
                shape = shape,
            ).padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.inlineGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
