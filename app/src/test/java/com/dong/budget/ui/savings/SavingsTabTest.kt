package com.dong.budget.ui.savings

import com.dong.budget.ui.stats.StatsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsTabTest {
    @Test
    fun `내역 · 통계 · 고정저축 순서이고 들어오면 내역부터 본다`() {
        assertEquals(listOf("내역", "통계", "고정저축"), SavingsTab.entries.map { it.label })
        assertEquals(SavingsTab.HISTORY, SavingsTab.entries.first())
    }

    @Test
    fun `하위 메뉴는 다섯 칸을 넘지 않는다`() {
        // 320dp 폭에서 칸 하나가 약 43dp 라 여섯 칸부터는 네 글자 이름이 잘린다(FloatingSubBar)
        assertTrue(SavingsTab.entries.size <= 5)
    }

    @Test
    fun `화면 읽기 이름은 겹치지 않고 통계 화면의 탭 이름과도 다르다`() {
        val names = SavingsTab.entries.map { it.paneTitle }
        assertEquals(names.size, names.toSet().size)
        // '통계' 탭을 바꿀 때 통계 메뉴의 탭과 같은 이름이면 어느 화면인지 알 수 없다
        assertTrue(names.none { name -> StatsTab.entries.any { it.paneTitle == name } })
        assertEquals("저축 통계", SavingsTab.STATS.paneTitle)
    }
}
