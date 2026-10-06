package com.dong.budget.data.settings

import com.dong.budget.data.settings.MenuItem.CARD_PERFORMANCE
import com.dong.budget.data.settings.MenuItem.FIXED_EXPENSE
import com.dong.budget.data.settings.MenuItem.HISTORY
import com.dong.budget.data.settings.MenuItem.HOME
import com.dong.budget.data.settings.MenuItem.MORE
import com.dong.budget.data.settings.MenuItem.SALARY
import com.dong.budget.data.settings.MenuItem.STATISTICS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomMenuTest {
    @Test
    fun `처음 차림은 홈 · 월급 · 통계 · 고정지출 · 전체이고 카드실적·내역은 빠져 있다`() {
        assertEquals(listOf(HOME, SALARY, STATISTICS, FIXED_EXPENSE, MORE), BottomMenu.DEFAULT.items)
        assertEquals(listOf(CARD_PERFORMANCE, HISTORY), BottomMenu.DEFAULT.hidden)
        assertTrue(HOME in BottomMenu.DEFAULT)
        assertTrue(MORE in BottomMenu.DEFAULT)
        assertFalse(CARD_PERFORMANCE in BottomMenu.DEFAULT)
    }

    @Test
    fun `적어 둔 차림을 그대로 읽는다`() {
        val menu = BottomMenu.of(listOf(FIXED_EXPENSE, SALARY), listOf(CARD_PERFORMANCE, STATISTICS))
        assertEquals(menu, BottomMenu.decode(menu.encode()))
        // 적지 않은 내역은 뺀 쪽 끝에 붙어 함께 적힌다
        assertEquals("fixed_expense,salary|card_performance,statistics,history", menu.encode())
    }

    @Test
    fun `적힌 적 없으면 처음 차림이고, 다 뺀 차림도 읽는다`() {
        assertEquals(BottomMenu.DEFAULT, BottomMenu.decode(null))
        val empty = BottomMenu.decode("|salary,statistics,fixed_expense,card_performance,history")
        assertEquals(listOf(HOME, MORE), empty.items)
        assertEquals(empty, BottomMenu.decode(empty.encode()))
    }

    @Test
    fun `모르는 이름·겹친 이름·고정 칸은 버리고, 어디에도 없는 칸은 뺀 쪽 끝에 붙인다`() {
        val menu = BottomMenu.decode("salary,unknown,salary,home,more|statistics")
        assertEquals(listOf(SALARY), menu.shown)
        // 적히지 않은 고정지출·카드실적·내역(새로 생긴 칸과 같다)은 뺀 쪽 끝에 기본 순서로
        assertEquals(listOf(STATISTICS, FIXED_EXPENSE, CARD_PERFORMANCE, HISTORY), menu.hidden)
    }

    @Test
    fun `가운데가 네 칸을 넘으면 밀어서 본다`() {
        assertFalse(BottomMenu.of(listOf(SALARY, STATISTICS, FIXED_EXPENSE, CARD_PERFORMANCE)).scrolls)
        assertFalse(BottomMenu.DEFAULT.scrolls)
        assertTrue(BottomMenu.of(listOf(SALARY, STATISTICS, FIXED_EXPENSE, CARD_PERFORMANCE, HISTORY)).scrolls)
    }

    @Test
    fun `1점7점0 처럼 내역이 없던 때 적은 차림을 읽으면 내역은 빠진 쪽에 붙는다`() {
        val menu = BottomMenu.decode("salary,statistics|fixed_expense,card_performance")
        assertEquals(listOf(SALARY, STATISTICS), menu.shown)
        assertEquals(listOf(FIXED_EXPENSE, CARD_PERFORMANCE, HISTORY), menu.hidden)
    }
}
