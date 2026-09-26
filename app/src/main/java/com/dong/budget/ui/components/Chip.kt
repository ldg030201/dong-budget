package com.dong.budget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
fun BudgetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 글 앞에 둘 아이콘. 없으면 글만 보인다. 뜻은 글이 전하므로 화면 읽기에는 읽히지 않게 둔다. */
    @DrawableRes icon: Int? = null,
) {
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
            // 화면 읽기가 '선택됨' 여부를 읽어 준다. 색과 테두리만으로는 전해지지 않는다.
            .semantics { this.selected = selected }
            .background(containerColor, shape)
            .border(
                width = if (selected) SelectedBorderWidth else UnselectedBorderWidth,
                color = borderColor,
                shape = shape,
            ).padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.inlineGap),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(BudgetTheme.size.iconSmall),
                )
                Spacer(Modifier.width(BudgetTheme.spacing.tightGap * 1.5f))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
