package com.dong.budget.ui.stats.tab.daily

import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.stats.calc.buildStatistics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class DayRecordsTest {
    private val today = day("2026-09-28")
    private val rows = listOf(tx("2026-08-10", 10_000, categoryId = 1), tx("2026-09-05", 20_000, categoryId = 1))

    @Test
    fun `기록을 시작하기 전의 지나간 달은 하루 기록과 요일별을 보이지 않는다`() {
        val state = buildStatistics(YearMonth.of(2026, 7), today, rows, day("2026-08-10"))
        assertFalse(state.showsDayRecords())
    }

    @Test
    fun `센 날이 있는 달은 하루 기록과 요일별을 보인다`() {
        assertTrue(buildStatistics(YearMonth.of(2026, 9), today, rows, day("2026-08-10")).showsDayRecords())
        assertTrue(buildStatistics(YearMonth.of(2026, 8), today, rows, day("2026-08-10")).showsDayRecords())
    }

    @Test
    fun `아직 오지 않은 달은 셀 날이 없어도 보인다('아직 지나간 날이 없어요')`() {
        assertTrue(buildStatistics(YearMonth.of(2026, 10), today, rows, day("2026-08-10")).showsDayRecords())
    }
}
