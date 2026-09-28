package com.dong.budget.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.dong.budget.ui.theme.Motion

// ─────────────────────────────────────────────────────────────────────
// 화면을 오갈 때 두 화면의 같은 요소를 잇는 연출(공유 요소).
// 아래 메뉴의 '통계' 칸과 통계 하위 메뉴의 첫 칸 '통계' 가 쓴다. 통계를 열면 아래 메뉴의 '통계' 가 하위 메뉴 첫 칸으로 옮겨 가고,
// 닫으면 거꾸로 아래 메뉴로 내려온다.
// ─────────────────────────────────────────────────────────────────────

/** NavDisplay 를 감싼 공유 전환 범위. DongBudgetApp 이 채운다. 없으면(미리보기) 이어 주지 않고 제자리에 그린다. */
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> = staticCompositionLocalOf { null }

/** 아래 메뉴의 '통계' 와 통계 하위 메뉴의 '통계' 칸을 잇는 열쇠. 아이콘과 글자에 각각 뒤를 붙여 쓴다. */
const val STATS_ENTRY_SHARED_KEY = "stats-entry"

/**
 * 화면이 바뀌는 동안 [key] 가 같은 다른 화면의 요소와 잇는다. 이 요소는 저쪽 자리와 크기에서 이쪽 자리와 크기로 옮겨 가고,
 * 그동안 두 화면의 모습이 겹쳐 바뀐다(색·굵기가 달라도 자연스럽다). 크기가 다르면 비율을 지킨 채 늘고 줄어든다.
 * 옮겨 가는 동안에는 화면 위 맨 앞 층에 그려서, 흐려지거나 밀려 나는 화면에 가려지지 않는다.
 *
 * 한 화면에만 있는 동안(짝이 없을 때)은 아무 연출 없이 제자리에 그린다. NavDisplay 의 화면 안에서만 쓴다.
 */
@Composable
fun Modifier.sharedNavElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val navScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedNavElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = navScope,
            enter = fadeIn(Motion.screen()),
            exit = fadeOut(Motion.screen()),
            boundsTransform = SharedNavBounds,
            resizeMode = scaleToBounds(),
        )
    }
}

/** 옮겨 가는 빠르기는 화면 이동과 같다 */
private val SharedNavBounds = BoundsTransform { _, _ -> Motion.screen() }
