package com.dong.budget.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * 생기고 없어지고 자리를 옮길 때 움직이는 목록 줄. 새 줄은 흐려졌다 나타나고, 없어지는 줄은 흐려지며 사라지고,
 * 위아래 줄이 바뀌면 제자리로 미끄러진다(거래를 추가·삭제하거나 날짜를 바꿨을 때, 달이 바뀌어 칸이 생기고 없어질 때).
 * 처음 그려질 때 있던 줄은 움직이지 않는다.
 *
 * @param slide false 면 자리 옮김은 움직이지 않고 생기고 없어질 때만 흐려진다. 위 칸의 높이가 손가락을 따라
 *   계속 바뀌는 곳(일별 그래프에서 날을 끌어 고를 때 그 아래 칸)은 미끄러지면 손을 늦게 따라와 끈다.
 */
fun LazyListScope.animatedItem(key: Any, slide: Boolean = true, content: @Composable LazyItemScope.() -> Unit) {
    item(key = key) {
        val scope = this
        Box(Modifier.animateItem(placementSpec = if (slide) placementSpec else null)) { scope.content() }
    }
}

/** [animatedItem] 을 여러 줄에. 줄마다 [key] 가 달라야 어느 줄이 옮겨졌는지 안다. */
fun <T> LazyListScope.animatedItems(items: List<T>, key: (T) -> Any, content: @Composable LazyItemScope.(T) -> Unit) {
    items.forEach { item -> animatedItem(key = key(item)) { content(item) } }
}

/** 제자리로 미끄러지는 움직임. 목록 줄의 기본값과 같다. */
private val placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
