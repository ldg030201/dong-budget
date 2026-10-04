package com.dong.budget.ui.card

import com.dong.budget.testing.day
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPerformanceEditTest {
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
        assertFalse(panelAccepts(panelAfterRemoval(0, 0), 0))
        // 위 줄을 지워 열린 키패드가 2구간(1)에서 1구간(0)으로 당겨지면, 사라지는 옛 키패드(1)는 받지 않고 새 키패드(0)만 받는다
        val moved = panelAfterRemoval(1, 0)
        assertFalse(panelAccepts(moved, 1))
        assertTrue(panelAccepts(moved, 0))
        assertFalse(panelAccepts(START_DAY_PANEL, 0))
    }

    @Test
    fun `닫히며 내려가거나 키패드로 바뀌며 사라지는 날짜판의 누름은 받지 않는다`() {
        // 전에는 날짜판만 이 확인이 없어, 닫은 날짜판이나 키패드 틈 아래 남은 날짜 칸이 눌려 시작일이 바뀌어 저장됐다
        assertFalse(panelAccepts(null, START_DAY_PANEL))
        assertFalse(panelAccepts(0, START_DAY_PANEL))
        assertTrue(panelAccepts(START_DAY_PANEL, START_DAY_PANEL))
    }

    @Test
    fun `실적 추가로 처음 열면 화면이 다 들어와 누름을 받을 수 있게 된 뒤에 키패드를 연다`() = runBlocking {
        // 전에는 값을 읽자마자 키패드를 열어, 화면이 밀려 들어오는 동안 친 숫자가 말없이 버려졌다
        val ready = CompletableDeferred<Unit>()
        val opening = async { startPanel({ null }, started = false, recreated = true, startWithKeypad = true) { ready.await() } }
        yield()
        assertFalse(opening.isCompleted)
        ready.complete(Unit)
        assertEquals(0, opening.await())

        val now: suspend () -> Unit = {}
        // 들어오는 동안 사용자가 시작일 판을 열었으면 그대로 둔다
        assertEquals(
            START_DAY_PANEL,
            startPanel({
                START_DAY_PANEL
            }, started = false, recreated = true, startWithKeypad = true, awaitReady = now),
        )
        // 실적이 있는 카드면 아무것도 열지 않는다
        assertNull(startPanel({ null }, started = false, recreated = true, startWithKeypad = false, awaitReady = now))
    }

    @Test
    fun `프로세스가 되살아나면 구간 키패드는 닫고, 화면을 돌렸으면 그대로 둔다`() = runBlocking {
        val never: suspend () -> Unit = { error("이미 시작한 화면은 기다리지 않는다") }
        // 친 순서 [700,000, 300,000] 의 2구간 키패드(1)가 열린 채 되살아나면 줄이 [300,000, 700,000] 으로 다시 읽혀
        // 전에는 키패드가 700,000 줄에 붙었다
        assertNull(startPanel({ 1 }, started = true, recreated = true, startWithKeypad = false, awaitReady = never))
        assertEquals(
            START_DAY_PANEL,
            startPanel({
                START_DAY_PANEL
            }, started = true, recreated = true, startWithKeypad = false, awaitReady = never),
        )
        // 화면을 돌리면 화면 모델이 남아 줄 순서도 그대로라 열린 키패드를 둔다
        assertEquals(1, startPanel({ 1 }, started = true, recreated = false, startWithKeypad = false, awaitReady = never))
        assertNull(startPanel({ null }, started = true, recreated = false, startWithKeypad = true, awaitReady = never))
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
