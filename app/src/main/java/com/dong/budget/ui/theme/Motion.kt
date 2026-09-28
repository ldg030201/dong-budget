package com.dong.budget.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

// ─────────────────────────────────────────────────────────────────────
// 움직임 토큰.
//
// 화면 코드가 시간과 곡선을 제각각 정하면 같은 종류의 움직임이 화면마다 빠르기가 달라진다.
// 치수 토큰처럼 의미로만 고른다. 화면 이동 설정(NavDisplay)처럼 @Composable 밖에서도 써야 해서 상수로 둔다.
// ─────────────────────────────────────────────────────────────────────

object Motion {
    /** 선택 표시·글자 색·꼬리표처럼 작은 상태 변화 */
    const val QUICK_MS = 150

    /** 내용이 바뀌거나, 나타나고 사라지거나, 펼치고 접힐 때 */
    const val STANDARD_MS = 250

    /** 화면 이동 */
    const val SCREEN_MS = 320

    /** 빠르게 출발해 부드럽게 멈춘다. 앱의 움직임은 모두 이 곡선을 쓴다. */
    val Easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK_MS, easing = Easing)

    fun <T> standard(): FiniteAnimationSpec<T> = tween(STANDARD_MS, easing = Easing)

    fun <T> screen(): FiniteAnimationSpec<T> = tween(SCREEN_MS, easing = Easing)

    /** 고른 칸 표시가 옆 칸으로 미끄러질 때. 살짝 탄력 있게 멈춘다(떠 있는 메뉴, 지출/수입 토글). */
    fun <T> indicator(): FiniteAnimationSpec<T> = spring(dampingRatio = INDICATOR_DAMPING, stiffness = Spring.StiffnessMediumLow)

    private const val INDICATOR_DAMPING = 0.8f
}

/**
 * 앞뒤가 있는 값(달)이 바뀔 때의 움직임. 다음으로 가면 새 내용이 오른쪽에서, 이전으로 가면 왼쪽에서 들어오며 흐려진다.
 * @param shift 옮기는 거리. 내용 폭을 받아 픽셀로 돌려준다. 화면 한 판처럼 큰 내용은 조금만 옮겨야 어지럽지 않다.
 */
fun <S : Comparable<S>> AnimatedContentTransitionScope<S>.slideByDirection(shift: (fullWidth: Int) -> Int = { it / 2 }): ContentTransform =
    slideByDirection(forward = targetState > initialState, shift = shift)

/** [slideByDirection] 에서 앞뒤를 직접 알려줄 때. [forward] 면 다음으로 간 것이다. */
fun slideByDirection(forward: Boolean, shift: (fullWidth: Int) -> Int = { it / 2 }): ContentTransform {
    val sign = if (forward) 1 else -1
    return (slideInHorizontally(Motion.standard()) { sign * shift(it) } + fadeIn(Motion.standard())) togetherWith
        (slideOutHorizontally(Motion.standard()) { -sign * shift(it) } + fadeOut(Motion.quick()))
}
