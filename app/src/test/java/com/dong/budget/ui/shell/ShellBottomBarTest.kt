package com.dong.budget.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellBottomBarTest {
    @Test
    fun `가운데 칸은 첫 칸부터 네 칸이 보이고, 첫 칸이 잘렸으면 그 칸은 다 보이지 않는다`() {
        // 첫 칸이 0, 딱 맞게 멈춤: 0~3 이 다 보인다
        assertEquals(listOf(0, 1, 2, 3), (0..5).filter { isFullyShown(it, first = 0, offset = 0) })
        // 끝까지 밀어 첫 칸이 1: 1~4
        assertEquals(listOf(1, 2, 3, 4), (0..5).filter { isFullyShown(it, first = 1, offset = 0) })
        // 미는 중(첫 칸 0 이 잘림): 1~3 만 다 보인다. 4 는 반쯤 들어와 있다
        assertEquals(listOf(1, 2, 3), (0..5).filter { isFullyShown(it, first = 0, offset = 12) })
    }
}
