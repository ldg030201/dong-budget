package com.dong.budget.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

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
}
