package com.dong.budget.ui.theme

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 리플 대신 살짝 눌리는 느낌으로 터치에 반응한다.
 *
 * 주의: indication 을 null 로 두면 누름 표시뿐 아니라 포커스와 호버 표시까지 전부 사라진다.
 * 물리 키보드나 스위치 접근성으로 조작할 때 지금 어디에 있는지 보이지 않게 되므로
 * (WCAG 2.4.7 위반) 포커스 테두리를 여기서 직접 그린다.
 *
 * 줄여지는 것은 이 modifier 뒤에 붙는 것들이다. 바탕(background)은 이 뒤에 붙여야 바탕까지 같이 눌린다.
 *
 * @param pressedTint 누르는 동안 [shape] 모양으로 옅은 바탕을 깐다. 아이콘이나 글자만 있는 버튼은
 *   줄어드는 것만으로는 눌린 게 거의 안 보여서 켠다.
 * @param onLongClick 길게 눌렀을 때 할 보조 동작(키패드의 전체 지우기 등). 없으면 null
 * @param onClickLabel 누르면 무엇을 하는지 화면 읽기가 알려 줄 말('두 번 탭하여 하루 기록 보기'). 줄 글만으로 알 수 없을 때 준다.
 */
@Composable
fun Modifier.pressScaleClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedTint: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressFeedback(interactionSource, shape, enabled = enabled, pressedTint = pressedTint)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * [pressScaleClickable] 의 눌림 모양만. 누름을 직접 받는 컴포넌트(스위치 줄, Material 의 하단 탭·떠 있는 버튼)에
 * 그 컴포넌트의 [interactionSource] 를 넘겨 같은 반응을 입힌다.
 *
 * @param clip [shape] 로 잘라낸다. 그림자가 모양 밖으로 나가는 컴포넌트(떠 있는 버튼)는 끈다.
 * @param pressedScale 눌렸을 때 크기. 바탕 없이 작은 아이콘만 있는 곳(하단 탭)은 더 줄여야 눌린 게 보인다.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    pressedTint: Boolean = false,
    clip: Boolean = true,
    pressedScale: Float = PRESSED_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedVisibly()
    val focused by interactionSource.collectIsFocusedAsState()
    val down = pressed && enabled
    val scale by animateFloatAsState(
        targetValue = if (down) pressedScale else 1f,
        // 눌릴 때는 바로, 뗄 때는 조금 천천히 돌아온다
        animationSpec = tween(durationMillis = if (down) PRESS_DURATION_MS else RELEASE_DURATION_MS, easing = Motion.Easing),
        label = "pressScale",
    )
    val tintAlpha by animateFloatAsState(
        targetValue = if (down && pressedTint) 1f else 0f,
        animationSpec = tween(durationMillis = if (down) PRESS_DURATION_MS else RELEASE_DURATION_MS),
        label = "pressTint",
    )
    val tint = BudgetTheme.colors.textPrimary.copy(alpha = PRESSED_TINT_ALPHA)
    val focusColor = MaterialTheme.colorScheme.primary

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.then(if (clip) Modifier.clip(shape) else Modifier)
        .then(
            if (focused) {
                Modifier.border(BorderStroke(2.dp, focusColor), shape)
            } else {
                Modifier
            },
        ).then(
            if (pressedTint) {
                Modifier.drawBehind {
                    if (tintAlpha > 0f) {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, tint, alpha = tintAlpha)
                    }
                }
            } else {
                Modifier
            },
        )
}

/**
 * 눌린 모양을 보여줄지. 스크롤 안의 버튼을 짧게 톡 치면 '눌림' 이 늦게 와서 '뗌' 과 거의 함께 오고,
 * 그러면 축소가 보이기도 전에 끝난다. 눌림을 보여주기 시작하면 적어도 [MIN_PRESS_VISIBLE_MS] 는 유지한다.
 */
@Composable
private fun InteractionSource.collectIsPressedVisibly(): State<Boolean> {
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        val scope = this
        var pressedAt = 0L
        var pressCount = 0
        var releaseJob: Job? = null
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    releaseJob?.cancel()
                    if (pressCount++ == 0) pressedAt = SystemClock.uptimeMillis()
                    visible.value = true
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressCount = (pressCount - 1).coerceAtLeast(0)
                    if (pressCount > 0) return@collect
                    val wait = MIN_PRESS_VISIBLE_MS - (SystemClock.uptimeMillis() - pressedAt)
                    releaseJob =
                        scope.launch {
                            if (wait > 0) delay(wait)
                            visible.value = false
                        }
                }
            }
        }
    }
    return visible
}

private const val PRESSED_SCALE = 0.97f
private const val PRESS_DURATION_MS = 90
private const val RELEASE_DURATION_MS = 160

/** 짧게 톡 쳐도 눌린 모양이 보이는 최소 시간 */
private const val MIN_PRESS_VISIBLE_MS = 110L

/** 누르는 동안 까는 바탕의 진하기. 글자색을 이만큼 옅게 깔아 밝은·어두운 화면에서 모두 보인다. */
private const val PRESSED_TINT_ALPHA = 0.08f
