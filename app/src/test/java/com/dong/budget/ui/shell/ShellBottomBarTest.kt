package com.dong.budget.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellBottomBarTest {
    private fun fullyShown(first: Int, offset: Int, count: Int) = (0 until count).filter { isFullyShown(it, first, offset, count) }

    @Test
    fun `칸 경계에 멈추면 첫 칸부터 네 칸이 다 보이고 다음 칸은 걸쳐 있다`() {
        assertEquals(listOf(0, 1, 2, 3), fullyShown(first = 0, offset = 0, count = 5))
        assertEquals(listOf(1, 2, 3, 4), fullyShown(first = 1, offset = 0, count = 6))
    }

    @Test
    fun `끝까지 밀면 마지막 네 칸이 다 보이고 그 앞 칸이 왼쪽에 걸친다`() {
        // 다섯 칸: 끝은 첫 칸 0 이 0.6칸 밀린 자리
        assertEquals(listOf(1, 2, 3, 4), fullyShown(first = 0, offset = 60, count = 5))
        // 여섯 칸: 끝은 첫 칸 1 이 밀린 자리
        assertEquals(listOf(2, 3, 4, 5), fullyShown(first = 1, offset = 60, count = 6))
    }

    @Test
    fun `미는 중이면 양 끝 칸은 잘려 있을 수 있어 가운데 세 칸만 다 보인다`() {
        assertEquals(listOf(1, 2, 3), fullyShown(first = 0, offset = 30, count = 7))
    }
}
