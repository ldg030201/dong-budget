package com.dong.budget.ui.card

import androidx.lifecycle.SavedStateHandle
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
    fun `닫히며 내려가거나 다른 줄로 바뀐 키패드의 누름은 받지 않는다`() {
        // 1구간(id 0) 키패드가 열린 채 1구간을 지우면 입력판은 닫힌다(null). 내려가는 키패드를 눌러도 받지 않는다.
        assertFalse(panelAccepts(null, 0))
        // 2구간(id 1) 키패드로 바꾸는 동안 사라지는 1구간 키패드는 받지 않고 새 키패드만 받는다
        assertFalse(panelAccepts(1, 0))
        assertTrue(panelAccepts(1, 1))
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
        val opening = async { startPanel({ null }, keypadRow = 0) { ready.await() } }
        yield()
        assertFalse(opening.isCompleted)
        ready.complete(Unit)
        assertEquals(0, opening.await())

        val now: suspend () -> Unit = {}
        // 들어오는 동안 사용자가 시작일 판을 열었으면 그대로 둔다
        assertEquals(START_DAY_PANEL, startPanel({ START_DAY_PANEL }, keypadRow = 0, awaitReady = now))
        // 실적이 있는 카드면 아무것도 열지 않는다
        assertNull(startPanel({ null }, keypadRow = null, awaitReady = now))
    }

    @Test
    fun `줄은 id 로 가리켜 앞 줄을 지워도 열린 키패드가 다른 줄 금액을 바꾸지 않는다`() {
        val rows = state(listOf(700_000, 300_000, 0))
        // 1구간(id 0)을 지우면 2구간이 1구간 자리로 올라오지만 id 는 1 그대로다
        val removed = rows.copy(rows = rows.rows.filter { it.id != 0 })
        assertEquals(TierRow(1, 3_000_001), removed.changeRow(1) { it * 10 + 1 }?.rows?.first())
        // 지운 줄 id 로 오는 누름(내려가는 키패드)은 아무 줄도 바꾸지 않는다
        assertNull(removed.changeRow(0) { it * 10 + 1 })
        // 바뀌지 않는 누름도 null 이다
        assertNull(removed.changeRow(2) { 0 })
    }

    private fun state(rows: List<Long>, startDay: Int = 1) = CardPerformanceEditState(
        card = CardInfo(id = 1, name = "하나카드", icon = "credit_card", color = "blue"),
        rows = rows.mapIndexed { id, amount -> TierRow(id, amount) },
        startDay = startDay,
        startKeypadRow = null,
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

    @Test
    fun `프로세스가 죽었다 돌아와도 적던 순서와 빈 줄과 줄 id 를 그대로 잇는다`() {
        // 전에는 DB 의 정리된 값을 다시 읽어 [700,000, 300,000, 빈 줄] 이 [300,000, 700,000] 이 되고 열린 키패드를 닫았다
        val handle = SavedStateHandle()
        assertNull(handle.readEditBuffer())
        val buffer =
            EditBuffer(
                rows = listOf(TierRow(2, 700_000), TierRow(0, 300_000), TierRow(5, 0)),
                startDay = 15,
                nextRowId = 6,
                opened = SavedPerformance(listOf(300_000), 1),
            )
        handle.writeEditBuffer(buffer)
        assertEquals(buffer, handle.readEditBuffer())
    }
}
