package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

private const val DISABLED_ALPHA = 0.4f

/** 화면 하단에 고정되는 주 실행 버튼 */
@Composable
fun BudgetPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.ctaHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressScaleClickable(shape = shape, enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.primary, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** 보조 동작용 버튼. 배경은 회색 블록으로 둔다. */
@Composable
fun BudgetSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.ctaHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressScaleClickable(shape = shape, enabled = enabled, onClick = onClick)
            .background(BudgetTheme.colors.sectionBackground, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
        )
    }
}
