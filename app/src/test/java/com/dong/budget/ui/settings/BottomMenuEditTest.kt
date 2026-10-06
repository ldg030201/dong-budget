package com.dong.budget.ui.settings

import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem.CARD_PERFORMANCE
import com.dong.budget.data.settings.MenuItem.FIXED_EXPENSE
import com.dong.budget.data.settings.MenuItem.HOME
import com.dong.budget.data.settings.MenuItem.MORE
import com.dong.budget.data.settings.MenuItem.SALARY
import com.dong.budget.data.settings.MenuItem.STATISTICS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomMenuEditTest {
    private val menu = BottomMenu.DEFAULT // 홈 · 월급 · 통계 · 고정지출 · 전체 | 카드실적

    @Test
    fun `목록은 위 머리 · 홈 · 넣은 메뉴 · 전체 · 아래 머리 · 뺀 메뉴 순이다`() {
        assertEquals(
            listOf(
                MenuEditRow.Header(true),
                MenuEditRow.Item(HOME),
                MenuEditRow.Item(SALARY),
                MenuEditRow.Item(STATISTICS),
                MenuEditRow.Item(FIXED_EXPENSE),
                MenuEditRow.Item(MORE),
                MenuEditRow.Header(false),
                MenuEditRow.Item(CARD_PERFORMANCE),
            ),
            menu.editRows(),
        )
        // 다 넣었으면 아래 구역에 끌어 내릴 자리가 남는다
        assertEquals(MenuEditRow.AllShown, menu.add(CARD_PERFORMANCE).editRows().last())
    }

    @Test
    fun `같은 구역의 메뉴에 닿으면 그 자리로 가고 닿은 메뉴가 비킨다`() {
        assertEquals(listOf(STATISTICS, SALARY, FIXED_EXPENSE), menu.dropOn(SALARY, MenuEditRow.Item(STATISTICS))!!.shown)
        assertEquals(listOf(FIXED_EXPENSE, SALARY, STATISTICS), menu.dropOn(FIXED_EXPENSE, MenuEditRow.Item(SALARY))!!.shown)
    }

    @Test
    fun `홈과 전체는 끌 수 없고, 홈에 닿으면 맨 앞, 이미 맨 앞이면 그대로다`() {
        assertNull(menu.dropOn(HOME, MenuEditRow.Item(SALARY)))
        assertNull(menu.dropOn(MORE, MenuEditRow.Item(FIXED_EXPENSE)))
        assertEquals(listOf(STATISTICS, SALARY, FIXED_EXPENSE), menu.dropOn(STATISTICS, MenuEditRow.Item(HOME))!!.shown)
        assertNull(menu.dropOn(SALARY, MenuEditRow.Item(HOME)))
        assertNull(menu.dropOn(SALARY, MenuEditRow.Item(SALARY)))
    }

    @Test
    fun `전체나 아래 머리에 닿으면 구역을 넘는다`() {
        // 넣은 메뉴를 전체 아래로 끌어 내리면 뺀 메뉴의 맨 앞
        val removed = menu.dropOn(FIXED_EXPENSE, MenuEditRow.Item(MORE))!!
        assertEquals(listOf(SALARY, STATISTICS), removed.shown)
        assertEquals(listOf(FIXED_EXPENSE, CARD_PERFORMANCE), removed.hidden)
        // 뺀 메뉴를 아래 머리 위로 끌어 올리면 넣은 메뉴의 맨 뒤(전체 바로 앞)
        val added = menu.dropOn(CARD_PERFORMANCE, MenuEditRow.Header(false))!!
        assertEquals(listOf(SALARY, STATISTICS, FIXED_EXPENSE, CARD_PERFORMANCE), added.shown)
        assertEquals(emptyList<Any>(), added.hidden)
        // 다 넣은 뒤 안내 줄로 끌어 내리면 빠진다
        assertEquals(listOf(STATISTICS), added.dropOn(STATISTICS, MenuEditRow.AllShown)!!.hidden)
    }

    @Test
    fun `다른 구역의 메뉴에 닿으면 그 자리로 간다`() {
        val removed = menu.remove(SALARY) // 뺀 메뉴: 월급, 카드실적
        val back = removed.dropOn(CARD_PERFORMANCE, MenuEditRow.Item(STATISTICS))!!
        assertEquals(listOf(CARD_PERFORMANCE, STATISTICS, FIXED_EXPENSE), back.shown)
        assertEquals(listOf(SALARY), back.hidden)
    }

    @Test
    fun `− 는 뺀 메뉴의 맨 앞으로, + 는 넣은 메뉴의 맨 뒤로`() {
        val removed = menu.remove(STATISTICS)
        assertEquals(listOf(SALARY, FIXED_EXPENSE), removed.shown)
        assertEquals(listOf(STATISTICS, CARD_PERFORMANCE), removed.hidden)
        assertEquals(listOf(SALARY, FIXED_EXPENSE, CARD_PERFORMANCE), removed.add(CARD_PERFORMANCE).shown)
        // 고정 칸은 넣고 빼지 않는다
        assertEquals(menu, menu.remove(HOME))
        assertEquals(menu, menu.add(MORE))
    }

    @Test
    fun `화면 읽기의 옮기기는 같은 구역 안에서 한 칸씩, 끝이면 없다`() {
        assertEquals(listOf(SALARY, FIXED_EXPENSE, STATISTICS), menu.moveBy(STATISTICS, 1)!!.shown)
        assertNull(menu.moveBy(SALARY, -1))
        assertNull(menu.moveBy(FIXED_EXPENSE, 1))
        assertNull(menu.moveBy(CARD_PERFORMANCE, 1))
    }
}
