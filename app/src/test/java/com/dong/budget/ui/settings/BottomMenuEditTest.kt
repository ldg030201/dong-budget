package com.dong.budget.ui.settings

import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem.CARD_PERFORMANCE
import com.dong.budget.data.settings.MenuItem.FIXED_EXPENSE
import com.dong.budget.data.settings.MenuItem.HISTORY
import com.dong.budget.data.settings.MenuItem.HOME
import com.dong.budget.data.settings.MenuItem.MORE
import com.dong.budget.data.settings.MenuItem.SALARY
import com.dong.budget.data.settings.MenuItem.STATISTICS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomMenuEditTest {
    private val menu = BottomMenu.DEFAULT // 홈 · 월급 · 통계 · 고정지출 · 전체 | 카드실적 · 내역

    @Test
    fun `하단 메뉴 안에서 옮기면 그 자리에 서고 나머지가 비킨다`() {
        assertEquals(listOf(STATISTICS, FIXED_EXPENSE, SALARY), menu.moveTo(SALARY, MenuSpot.Shown(2)).shown)
        assertEquals(listOf(FIXED_EXPENSE, SALARY, STATISTICS), menu.moveTo(FIXED_EXPENSE, MenuSpot.Shown(0)).shown)
        // 자리를 넘으면 맨 뒤(전체 바로 앞)
        assertEquals(listOf(STATISTICS, FIXED_EXPENSE, SALARY), menu.moveTo(SALARY, MenuSpot.Shown(99)).shown)
    }

    @Test
    fun `아래 칸으로 옮기면 빠지고, 위 판으로 옮기면 들어간다`() {
        val removed = menu.moveTo(STATISTICS, MenuSpot.Hidden(1))
        assertEquals(listOf(SALARY, FIXED_EXPENSE), removed.shown)
        assertEquals(listOf(CARD_PERFORMANCE, STATISTICS, HISTORY), removed.hidden)
        val added = menu.moveTo(HISTORY, MenuSpot.Shown(1))
        assertEquals(listOf(SALARY, HISTORY, STATISTICS, FIXED_EXPENSE), added.shown)
        assertEquals(listOf(CARD_PERFORMANCE), added.hidden)
    }

    @Test
    fun `홈과 전체는 옮기지 않는다`() {
        assertEquals(menu, menu.moveTo(HOME, MenuSpot.Shown(2)))
        assertEquals(menu, menu.moveTo(MORE, MenuSpot.Hidden(0)))
    }

    @Test
    fun `손가락보다 왼쪽에 있는 다른 칸의 수가 자리 번호다`() {
        // 다른 칸 가운데가 50, 150, 250 일 때
        val centers = listOf(50f, 150f, 250f)
        assertEquals(0, spotIndex(centers, 10f))
        assertEquals(1, spotIndex(centers, 51f))
        assertEquals(2, spotIndex(centers, 200f))
        assertEquals(3, spotIndex(centers, 300f))
        assertEquals(0, spotIndex(emptyList(), 300f))
    }

    @Test
    fun `끄는 칸 자신은 빼고 세서 옆 칸 가운데를 넘어야 자리가 바뀐다`() {
        // 칸 너비 100: 월급(50) · 통계(150) · 고정지출(250). 월급을 끌고 있다.
        val others = listOf(150f, 250f) // 통계, 고정지출
        // 통계 가운데(150)를 넘기 전에는 그대로 첫째 자리
        assertEquals(0, spotIndex(others, 140f))
        // 넘으면 둘째 자리로. 그러면 통계가 왼쪽 칸(가운데 50)으로 비켜서고, 손가락을 조금 되돌려도(140) 50 을 넘은 채라 그대로다.
        assertEquals(1, spotIndex(others, 160f))
        assertEquals(1, spotIndex(listOf(50f, 250f), 140f))
    }

    @Test
    fun `화면 읽기의 옮기기는 같은 줄 안에서 한 칸씩, 끝이면 없다`() {
        assertEquals(listOf(SALARY, FIXED_EXPENSE, STATISTICS), menu.moveBy(STATISTICS, 1)!!.shown)
        assertNull(menu.moveBy(SALARY, -1))
        assertNull(menu.moveBy(FIXED_EXPENSE, 1))
        assertEquals(listOf(HISTORY, CARD_PERFORMANCE), menu.moveBy(CARD_PERFORMANCE, 1)!!.hidden)
        assertNull(menu.moveBy(HISTORY, 1))
        assertNull(menu.moveBy(HOME, 1))
    }
}
