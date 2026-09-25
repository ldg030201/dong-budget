package com.dong.budget.ui.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedItemMoveTest {
    private fun item(id: Long, movable: Boolean = true) =
        ManagedItem(id = id, name = "분류$id", icon = "restaurant", color = "blue", transactionCount = 0, deletable = movable)

    // 식비, 교통, 편의점, 기타(맨 뒤, 못 옮김)
    private val list = listOf(item(1), item(2), item(3), item(9, movable = false))

    @Test
    fun `끌어다 놓은 자리로 옮긴다`() {
        assertEquals(listOf(3L, 1L, 2L, 9L), list.moved(fromId = 3, toId = 1)!!.map { it.id })
        assertEquals(listOf(2L, 3L, 1L, 9L), list.moved(fromId = 1, toId = 3)!!.map { it.id })
    }

    @Test
    fun `기타는 옮길 수 없고 다른 분류도 기타 아래로 내려가지 않는다`() {
        assertNull(list.moved(fromId = 9, toId = 1))
        assertNull(list.moved(fromId = 1, toId = 9))
    }

    @Test
    fun `제자리나 없는 항목은 옮기지 않는다`() {
        assertNull(list.moved(fromId = 1, toId = 1))
        assertNull(list.moved(fromId = 1, toId = 42))
    }
}
