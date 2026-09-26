package com.dong.budget.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 리플 대신 살짝 눌리는 느낌으로 터치에 반응한다.
 *
 * 주의: indication 을 null 로 두면 누름 표시뿐 아니라 포커스와 호버 표시까지 전부 사라진다.
 * 물리 키보드나 스위치 접근성으로 조작할 때 지금 어디에 있는지 보이지 않게 되므로
 * (WCAG 2.4.7 위반) 포커스 테두리를 여기서 직접 그린다.
 *
 * @param onLongClick 길게 눌렀을 때 할 보조 동작(키패드의 전체 지우기 등). 없으면 null
 */
@Composable
fun Modifier.pressScaleClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = PRESS_DURATION_MS),
        label = "pressScale",
    )
    val focusColor = MaterialTheme.colorScheme.primary

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.clip(shape)
        .then(
            if (focused) {
                Modifier.border(BorderStroke(2.dp, focusColor), shape)
            } else {
                Modifier
            },
        ).combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

private const val PRESSED_SCALE = 0.97f
private const val PRESS_DURATION_MS = 90
