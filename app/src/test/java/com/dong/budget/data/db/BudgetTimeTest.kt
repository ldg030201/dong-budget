package com.dong.budget.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BudgetTimeTest {
    @Test
    fun `다음 날까지 남은 시간은 서울 자정 기준이다`() {
        // 서울 2026-09-25 23:59:00 = UTC 14:59:00
        assertEquals(60_000L, BudgetTime.millisUntilNextDay(Instant.parse("2026-09-25T14:59:00Z")))
        // 서울 자정 정각이면 하루 뒤까지 남는다
        assertEquals(86_400_000L, BudgetTime.millisUntilNextDay(Instant.parse("2026-09-25T15:00:00Z")))
    }

    @Test
    fun `UTC 로는 같은 날이어도 서울 날짜로 계산한다`() {
        // UTC 2026-09-25 16:00 = 서울 09-26 01:00 → 서울 09-27 0시까지 23시간
        assertEquals(23 * 3_600_000L, BudgetTime.millisUntilNextDay(Instant.parse("2026-09-25T16:00:00Z")))
    }
}
