package com.dong.budget.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTabTest {
    @Test
    fun `첫 칸은 아래 메뉴와 같은 이름의 통계다`() {
        // 아래 메뉴의 '통계' 가 이 칸 자리로 옮겨 오는 연출은 이름과 아이콘이 같다는 것에 기댄다
        assertEquals(StatsTab.OVERVIEW, StatsTab.entries.first())
        assertEquals("통계", StatsTab.OVERVIEW.label)
    }

    @Test
    fun `화면 읽기 이름은 겹치지 않고 모두 통계로 끝난다`() {
        assertEquals("한눈에 보는 통계", StatsTab.OVERVIEW.paneTitle)
        assertEquals("월별 통계", StatsTab.MONTHLY.paneTitle)
        StatsTab.entries.forEach { tab ->
            assertTrue(tab.paneTitle, tab.paneTitle.endsWith("통계"))
            assertTrue(tab.paneTitle, "통계 통계" !in tab.paneTitle)
        }
        assertEquals(StatsTab.entries.size, StatsTab.entries.map { it.paneTitle }.toSet().size)
    }

    @Test
    fun `하위 메뉴는 다섯 칸을 넘지 않는다`() {
        // 320dp 폭에서 칸 하나가 약 43dp 라 여섯 칸부터는 '결제수단' 이 잘린다(FloatingSubBar)
        assertTrue(StatsTab.entries.size <= 5)
    }
}
