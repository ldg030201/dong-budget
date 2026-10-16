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

/**
 * 편집기가 저장한 차림과 밖에서 바뀐 차림(백업 복원 등)을 가른다.
 * 저장한 차림은 저장소를 한 바퀴 돌아 늦게 돌아온다. 그 사이 또 끌었으면 돌아온 옛 차림으로 되돌아가면 안 된다(튄다).
 * 밖에서 바뀐 차림이면 편집기도 그 차림을 보여야 한다. 옛 차림을 보인 채 끌면 바뀐 차림을 덮어쓴다.
 *
 * 저장소는 같은 값이 이어지면 다시 내보내지 않아서(끌었다가 원래대로 되돌린 때 등) 저장한 차림이 끝내 안 돌아오기도 한다.
 * 그런 것이 남아 있으면 나중에 밖에서 그 차림으로 바뀐 것을 제 저장으로 잘못 보므로, 저장한 지 [TTL_MS] 가 지난 것은 잊는다.
 * 저장소 왕복은 그보다 훨씬 빠르다.
 *
 * @param now 지금(밀리초, 단조 증가). 테스트에서 바꾼다.
 */
internal class MenuSaves(private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }) {
    /** 저장했지만 아직 돌아오지 않은 차림과 저장한 때. 저장한 차례대로 */
    private val pending = ArrayDeque<Pair<BottomMenu, Long>>()

    /** 편집기가 [menu] 를 저장했다 */
    fun saved(menu: BottomMenu) {
        pending.addLast(menu to now())
    }

    /**
     * 저장소에서 [menu] 가 왔다. 저장소는 마지막 값만 주기도 해서, 저장한 차림이 돌아오면 그보다 먼저 저장한 것도 돌아온 것으로 본다.
     * @return 편집기가 따라 보여야 할 차림. 편집기가 저장한 것이 돌아왔으면(편집기가 이미 앞서 있으면) null
     */
    fun arrived(menu: BottomMenu): BottomMenu? {
        val expired = now() - TTL_MS
        while (pending.isNotEmpty() && pending.first().second < expired) pending.removeFirst()
        val index = pending.indexOfFirst { it.first == menu }
        if (index < 0) {
            pending.clear()
            return menu
        }
        repeat(index + 1) { pending.removeFirst() }
        return null
    }

    private companion object {
        /** 저장한 차림이 이만큼 지나도 안 돌아오면 잊는다 */
        const val TTL_MS = 3_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
