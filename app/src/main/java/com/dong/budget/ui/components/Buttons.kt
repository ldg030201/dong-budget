package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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

/**
 * 줄 옆에 붙이는 작은 버튼. 화면 폭을 채우지 않고 글자만큼만 차지한다.
 * 보이는 높이는 테마 칩과 같게 두고, 누를 수 있는 범위는 최소 터치 크기를 지킨다.
 */
@Composable
fun BudgetSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    Box(
        modifier =
        modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressScaleClickable(shape = shape, enabled = enabled, onClick = onClick)
            .background(BudgetTheme.colors.sectionBackground, shape)
            .padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.inlineGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = BudgetTheme.colors.textPrimary,
        )
    }
}

/** 글자만 있는 버튼. 자주 쓰지 않는 보조 동작을 한 줄에 여러 개 늘어놓을 때 쓴다. */
@Composable
fun BudgetTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    Box(
        modifier =
        modifier
            .minimumInteractiveComponentSize()
            .pressScaleClickable(shape = shape, onClick = onClick)
            .padding(horizontal = BudgetTheme.spacing.tightGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
