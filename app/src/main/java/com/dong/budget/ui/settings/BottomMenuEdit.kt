package com.dong.budget.ui.settings

import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem

// ─────────────────────────────────────────────────────────────────────
// 설정 > 하단 메뉴 편집기의 옮기기 규칙. 화면과 떼어 단위 테스트로 확인한다.
// 편집기는 위에 하단 메뉴(홈 · 넣은 메뉴 · 전체), 아래에 넣을 수 있는 메뉴를 늘어놓고, 아이콘을 끌어 그 사이를 오간다.
// ─────────────────────────────────────────────────────────────────────

/** 메뉴가 놓일 자리 */
internal sealed interface MenuSpot {
    /** 하단 메뉴 가운데(홈과 전체 사이)의 [index] 번째. 홈 바로 다음이 0 이다. */
    data class Shown(val index: Int) : MenuSpot

    /** 넣을 수 있는 메뉴의 [index] 번째 */
    data class Hidden(val index: Int) : MenuSpot
}

/** [item] 을 [spot] 으로 옮긴 차림. 번호는 옮긴 뒤 [item] 이 서는 자리이고, 넘치면 끝이다. 홈·전체는 옮기지 않는다. */
internal fun BottomMenu.moveTo(item: MenuItem, spot: MenuSpot): BottomMenu {
    if (item.fixed) return this
    val shownRest = shown - item
    val hiddenRest = hidden - item
    return when (spot) {
        is MenuSpot.Shown -> BottomMenu.of(shownRest.inserted(spot.index, item), hiddenRest)
        is MenuSpot.Hidden -> BottomMenu.of(shownRest, hiddenRest.inserted(spot.index, item))
    }
}

/**
 * 끄는 손가락이 가리키는 자리. [others] 는 그 줄에 놓인 다른 메뉴들의 가운데 x 좌표(왼쪽부터)이고, 손가락([x])보다 왼쪽에 있는 수가 곧 자리 번호다.
 * 끄는 메뉴 자신은 빼고 센다. 그래서 끄는 메뉴가 옆 칸의 가운데를 넘어야 자리가 바뀌고, 칸 경계에서 이리저리 튀지 않는다.
 */
internal fun spotIndex(others: List<Float>, x: Float): Int = others.count { it < x }

/** 화면 읽기의 '왼쪽으로/오른쪽으로 옮기기'. 같은 줄 안에서 [delta] 칸 옮긴다. 끝이면 null */
internal fun BottomMenu.moveBy(item: MenuItem, delta: Int): BottomMenu? {
    val inShown = item in shown
    val list = if (inShown) shown else hidden
    val target = list.indexOf(item).takeIf { it >= 0 }?.plus(delta) ?: return null
    if (target !in list.indices) return null
    return moveTo(item, if (inShown) MenuSpot.Shown(target) else MenuSpot.Hidden(target))
}

private fun List<MenuItem>.inserted(index: Int, item: MenuItem): List<MenuItem> = toMutableList().apply {
    add(index.coerceIn(0, size), item)
}
