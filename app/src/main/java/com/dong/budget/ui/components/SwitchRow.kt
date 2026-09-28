package com.dong.budget.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressFeedback

/**
 * 켜고 끄는 한 줄. 왼쪽에 이름과 설명, 오른쪽에 스위치. 줄 전체가 스위치로 읽히고 눌린다.
 *
 * 앱은 리플을 꺼 두었으므로(Theme) 기본 누름 표시로는 아무것도 안 보인다. 다른 버튼과 같은 눌림과 포커스 테두리를 입힌다.
 * 스위치 자체는 누름을 받지 않는다. 줄이 대신 눌린다(스위치만 누르면 줄의 눌림이 안 보이고, 화면 읽기가 두 번 읽는다).
 *
 * @param description 이름 아래 흐린 설명. 켜면 무엇을 하고 끄면 어떻게 되는지 적는다.
 * @param enabled false 면 흐리게 그리고 누를 수 없다. 기대는 다른 스위치가 꺼져 있을 때 쓴다. 화면 읽기는 '사용 중지됨' 으로 읽는다.
 * @param titleStyle 이름 글꼴. 화면 맨 위의 큰 스위치(개발자 모드)는 한 단계 크게 쓴다.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // 기대는 스위치를 끄고 켤 때 흐려짐이 튀지 않고 바뀐다
    val alpha by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, Motion.quick(), label = "switchRowAlpha")
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .pressFeedback(interactionSource, RoundedCornerShape(BudgetTheme.radius.chip), enabled = enabled)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ).heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.inlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = BudgetTheme.spacing.itemGap),
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(text = title, style = titleStyle, color = BudgetTheme.colors.textPrimary)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private const val DISABLED_ALPHA = 0.4f
