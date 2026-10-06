package com.dong.budget.ui.settings

import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem

// ─────────────────────────────────────────────────────────────────────
// 설정 > 하단 메뉴의 목록과 옮기기 규칙. 화면과 떼어 단위 테스트로 확인한다.
// 목록은 '하단 메뉴' 머리 · 홈 · 넣은 메뉴 · 전체 · '넣을 수 있는 메뉴' 머리 · 뺀 메뉴 순이다.
// 홈과 전체는 끌 수 없지만, 끄는 줄이 닿으면 경계로 쓰인다(전체에 닿으면 아래 구역으로 넘어간다).
// ─────────────────────────────────────────────────────────────────────

/** 하단 메뉴 설정 목록의 한 줄 */
internal sealed interface MenuEditRow {
    /** 목록 줄의 열쇠. 끌기 라이브러리가 줄을 이것으로 알아본다. */
    val key: String

    /** 구역 머리. [shown] 이면 '하단 메뉴', 아니면 '넣을 수 있는 메뉴' */
    data class Header(val shown: Boolean) : MenuEditRow {
        override val key: String get() = if (shown) "header-shown" else "header-hidden"
    }

    data class Item(val item: MenuItem) : MenuEditRow {
        override val key: String get() = "item-${item.key}"
    }

    /** 뺀 메뉴가 없을 때 아래 구역에 두는 안내 줄. 끌어서 빼려면 닿을 줄이 있어야 한다. */
    data object AllShown : MenuEditRow {
        override val key: String get() = "all-shown"
    }
}

/** 목록 줄 전부, 위에서부터 */
internal fun BottomMenu.editRows(): List<MenuEditRow> = buildList {
    add(MenuEditRow.Header(shown = true))
    items.forEach { add(MenuEditRow.Item(it)) }
    add(MenuEditRow.Header(shown = false))
    if (hidden.isEmpty()) add(MenuEditRow.AllShown) else hidden.forEach { add(MenuEditRow.Item(it)) }
}

/**
 * 끄는 [dragged] 가 [target] 줄에 닿았을 때의 새 차림. 옮길 수 없거나 그대로면 null.
 * - 같은 구역의 메뉴에 닿으면 그 자리로 간다(닿은 메뉴는 한 칸 비킨다). 다른 구역의 메뉴에 닿아도 그 자리로 간다.
 * - 홈이나 위 머리에 닿으면 넣은 메뉴의 맨 앞으로.
 * - 전체나 아래 머리에 닿으면 구역을 넘는다. 넣은 메뉴는 뺀 메뉴의 맨 앞으로, 뺀 메뉴는 넣은 메뉴의 맨 뒤로.
 */
internal fun BottomMenu.dropOn(dragged: MenuItem, target: MenuEditRow): BottomMenu? {
    if (dragged.fixed) return null
    val inShown = dragged in shown
    val next =
        when (target) {
            is MenuEditRow.Header ->
                when {
                    target.shown -> toShown(dragged, 0)
                    inShown -> toHidden(dragged, 0)
                    else -> toShown(dragged, Int.MAX_VALUE)
                }

            MenuEditRow.AllShown -> if (inShown) toHidden(dragged, 0) else null

            is MenuEditRow.Item ->
                when (val item = target.item) {
                    dragged -> null
                    MenuItem.HOME -> toShown(dragged, 0)
                    MenuItem.MORE -> if (inShown) toHidden(dragged, 0) else toShown(dragged, Int.MAX_VALUE)
                    in shown -> toShown(dragged, shown.indexOf(item))
                    else -> toHidden(dragged, hidden.indexOf(item))
                }
        }
    return next?.takeIf { it != this }
}

/** '−' 단추. 뺀 메뉴의 맨 앞(경계 바로 아래)으로 뺀다. */
internal fun BottomMenu.remove(item: MenuItem): BottomMenu = if (item in shown) toHidden(item, 0) else this

/** '+' 단추. 넣은 메뉴의 맨 뒤(전체 바로 앞)에 넣는다. */
internal fun BottomMenu.add(item: MenuItem): BottomMenu = if (item.fixed || item in shown) this else toShown(item, Int.MAX_VALUE)

/** 화면 읽기의 '위로/아래로 옮기기'. 같은 구역 안에서 [delta] 칸 옮긴다. 끝이면 null */
internal fun BottomMenu.moveBy(item: MenuItem, delta: Int): BottomMenu? {
    val list = if (item in shown) shown else hidden
    val index = list.indexOf(item)
    val target = index + delta
    if (index < 0 || target !in list.indices) return null
    val moved = list.toMutableList().apply { add(target, removeAt(index)) }
    return if (item in shown) BottomMenu.of(moved, hidden) else BottomMenu.of(shown, moved)
}

/** [item] 을 넣은 메뉴의 [index] 자리로. 자리를 넘으면 맨 뒤다. */
private fun BottomMenu.toShown(item: MenuItem, index: Int): BottomMenu {
    val rest = shown - item
    val moved = rest.toMutableList().apply { add(index.coerceIn(0, rest.size), item) }
    return BottomMenu.of(moved, hidden - item)
}

/** [item] 을 뺀 메뉴의 [index] 자리로. 자리를 넘으면 맨 뒤다. */
private fun BottomMenu.toHidden(item: MenuItem, index: Int): BottomMenu {
    val rest = hidden - item
    val moved = rest.toMutableList().apply { add(index.coerceIn(0, rest.size), item) }
    return BottomMenu.of(shown - item, moved)
}
