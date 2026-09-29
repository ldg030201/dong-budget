package com.dong.budget.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 누르면 무언가를 하거나 다른 화면으로 가는 한 줄. 왼쪽에 이름과 설명, 다른 화면으로 가면 오른쪽에 꺾쇠.
 * 켜고 끄는 줄([SwitchRow])과 높이·여백이 같아서 한 묶음 안에 섞어 둬도 가지런하다.
 *
 * @param description 이름 아래 흐린 설명
 * @param opensScreen 다른 화면으로 가는 줄이면 true. 오른쪽에 꺾쇠를 둔다.
 * @param danger 되돌릴 수 없는 일(데이터 지우기)이면 true. 이름을 빨갛게 쓴다.
 * @param enabled false 면 흐리게 그리고 누를 수 없다(내보내는 중 등)
 * @param value 오른쪽(꺾쇠 앞)에 두는 짧은 값. 앱 정보 줄의 버전 등
 * @param valueColor [value] 의 글자색. 알릴 것이 있으면 브랜드색으로 눈에 띄게 한다.
 */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    opensScreen: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    value: String? = null,
    valueColor: Color = BudgetTheme.colors.textSecondary,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, Motion.quick(), label = "actionRowAlpha")
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), enabled = enabled, onClick = onClick)
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.inlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) BudgetTheme.colors.danger else BudgetTheme.colors.textPrimary,
            )
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                modifier = Modifier.padding(start = BudgetTheme.spacing.itemGap),
            )
        }
        if (opensScreen) {
            // 줄 이름이 곧 갈 곳이라 꺾쇠는 화면 읽기에 읽히지 않게 둔다
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BudgetTheme.colors.textSecondary,
                modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap).size(BudgetTheme.size.icon),
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.4f
