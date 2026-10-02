package com.dong.budget.ui.card

import com.dong.budget.testing.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPerformanceEditTest {
    @Test
    fun `금액 키패드는 앞의 0 을 떼고 12자리까지만 받는다`() {
        assertEquals(3L, typeDigit(0, "3"))
        assertEquals(300L, typeDigit(3, "00"))
        assertEquals(0L, typeDigit(0, "0"))
        assertEquals(0L, typeDigit(0, "00"))
        assertEquals(999_999_999_999L, typeDigit(99_999_999_999, "9"))
        assertNull(typeDigit(999_999_999_999, "9"))
        assertNull(typeDigit(99_999_999_999, "00"))
    }

    @Test
    fun `구간 줄을 지우면 그 줄의 키패드는 닫고 아래 줄 키패드는 한 칸 당긴다`() {
        assertNull(panelAfterRemoval(1, 1))
        assertEquals(1, panelAfterRemoval(2, 0))
        assertEquals(0, panelAfterRemoval(0, 2))
        assertEquals(START_DAY_PANEL, panelAfterRemoval(START_DAY_PANEL, 0))
        assertNull(panelAfterRemoval(null, 0))
    }

    @Test
    fun `닫히며 내려가거나 다른 줄로 바뀐 키패드의 누름은 받지 않는다`() {
        // 1구간 키패드가 열린 채 1구간을 지우면 입력판은 닫히고(null) 2구간이 1구간 자리로 올라온다.
        // 내려가는 1구간 키패드(번호 0)를 눌러도 올라온 줄 금액이 바뀌면 안 된다.
        assertFalse(keypadAccepts(panelAfterRemoval(0, 0), 0))
        // 위 줄을 지워 열린 키패드가 2구간(1)에서 1구간(0)으로 당겨지면, 사라지는 옛 키패드(1)는 받지 않고 새 키패드(0)만 받는다
        val moved = panelAfterRemoval(1, 0)
        assertFalse(keypadAccepts(moved, 1))
        assertTrue(keypadAccepts(moved, 0))
        assertFalse(keypadAccepts(START_DAY_PANEL, 0))
    }

    private fun state(rows: List<Long>, startDay: Int = 1) = CardPerformanceEditState(
        card = CardInfo(id = 1, name = "하나카드", icon = "credit_card", color = "blue"),
        rows = rows,
        startDay = startDay,
        startWithKeypad = false,
        today = day("2026-10-03"),
    )

    @Test
    fun `저장할 구간은 적은 순서와 상관없이 0원을 빼고 같은 금액은 하나로 오름차순이다`() {
        assertEquals(listOf(300_000L, 700_000L), state(listOf(700_000, 0, 300_000, 700_000)).tiers)
        assertEquals(emptyList<Long>(), state(listOf(0)).tiers)
    }

    @Test
    fun `적은 것이 있어야 실적 지우기를 두고, 다섯 줄이면 더 못 더한다`() {
        assertFalse(state(listOf(0)).hasPerformance)
        assertTrue(state(listOf(0), startDay = 15).hasPerformance)
        assertTrue(state(listOf(300_000)).hasPerformance)
        assertTrue(state(listOf(1, 2, 3, 4)).canAddRow)
        assertFalse(state(listOf(1, 2, 3, 4, 5)).canAddRow)
    }

    @Test
    fun `바꾼 기록은 카드 이름과 금액 없이 구간 개수와 시작일만 남긴다`() {
        val opened = SavedPerformance(listOf(300_000), 1)
        assertNull(performanceChangeLog(opened, opened))
        assertEquals(
            "카드 실적을 바꿨어요 · 구간 2개 · 매달 15일부터",
            performanceChangeLog(opened, SavedPerformance(listOf(300_000, 700_000), 15)),
        )
        assertEquals("카드 실적을 바꿨어요 · 구간 1개 · 매달 말일부터", performanceChangeLog(opened, SavedPerformance(listOf(300_000), 31)))
        assertEquals("카드 실적을 지웠어요", performanceChangeLog(opened, SavedPerformance(emptyList(), 1)))
    }
}
