package com.dong.budget.ui.stats.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.dong.budget.ui.theme.Motion
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────
// 차트의 움직임.
//
// 차트는 캔버스에 직접 그린다. 움직이는 값은 그리기 단계(onDrawBehind)에서 읽어야 한다.
// 캐시(drawWithCache) 안에서 읽으면 한 장면마다 막대 모양과 글자를 다시 재서 버벅인다.
// 기기 설정에서 애니메이션을 끄면 Compose 가 곧바로 끝 값으로 보낸다.
// ─────────────────────────────────────────────────────────────────────

/** 차트가 차오르는 시간. 화면 전환보다 조금 길게 두어 들어온 뒤에도 자라는 게 보인다. */
internal const val CHART_REVEAL_MS = 600

/**
 * 차트가 처음 그려지거나 [key](그리는 값)가 바뀔 때 0 에서 1 로 차오르는 진행도.
 * 막대 높이·선 길이·도넛 각도에 곱한다. 고른 칸처럼 그리는 값이 아닌 것은 [key] 에 넣지 않는다(끌 때마다 다시 자란다).
 */
@Composable
internal fun rememberChartReveal(key: Any?): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(CHART_REVEAL_MS, easing = Motion.Easing))
    }
    return progress.asState()
}

/**
 * 막대 길이처럼 한 값으로 된 비율. 처음에는 0 에서 자라고, 값이 바뀌면 지금 길이에서 새 길이로 늘거나 준다.
 */
@Composable
internal fun rememberGrowingFraction(target: Float): State<Float> {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target, tween(CHART_REVEAL_MS, easing = Motion.Easing)) }
    return value.asState()
}

/**
 * 고른 칸 번호를 부드럽게 따라가는 값(칸 번호 사이의 소수). 처음 고를 때는 그 자리에 바로 나타나고,
 * 다른 칸으로 옮기면 미끄러진다. 끌어서 고를 때도 손가락을 늦게 따라오지 않게 빠르게 멈춘다. 고른 칸이 없으면 null.
 */
@Composable
internal fun rememberSlidingIndex(selectedIndex: Int?): State<Float?> {
    val position = remember { Animatable(selectedIndex?.toFloat() ?: 0f) }
    val shown = remember { mutableStateOf(selectedIndex != null) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex == null) {
            shown.value = false
        } else if (!shown.value) {
            position.snapTo(selectedIndex.toFloat())
            shown.value = true
        } else {
            position.animateTo(
                selectedIndex.toFloat(),
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    return remember { derivedStateOf { if (shown.value) position.value else null } }
}

/**
 * 칸 번호 사이의 소수 [position] 에 맞는 값. 두 칸 사이면 두 칸의 값을 그 비율로 섞는다.
 * @param at 칸 번호의 값
 */
internal inline fun lerpSlots(position: Float, count: Int, at: (Int) -> Float): Float {
    val clamped = position.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
    val lower = floor(clamped).toInt()
    val upper = minOf(lower + 1, count - 1)
    val fraction = clamped - lower
    return at(lower) + (at(upper) - at(lower)) * fraction
}

/** 진행도가 [from] 을 지난 뒤에 0 → 1 로 오르는 값. 막대가 다 자랄 즈음 값 글자·끝점이 나타나게 할 때 쓴다. */
internal fun tailAlpha(progress: Float, from: Float = TAIL_START): Float = ((progress - from) / (1f - from)).coerceIn(0f, 1f)

private const val TAIL_START = 0.7f
