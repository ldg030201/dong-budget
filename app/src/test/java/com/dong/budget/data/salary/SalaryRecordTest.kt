package com.dong.budget.data.salary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SalaryRecordTest {
    @Test
    fun `저장했다 읽으면 같은 설정이다`() {
        val settings =
            SalarySettings(
                basis = PayBasis.YEARLY,
                amount = 48_000_000,
                takeHome = 3_300_000,
                workStart = LocalTime.of(8, 30),
                workEnd = LocalTime.of(17, 30),
                skipLunch = false,
                lunchStart = LocalTime.of(11, 30),
                lunchEnd = LocalTime.of(12, 30),
                workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
                payday = 10,
                startDate = LocalDate.of(2026, 3, 2),
                paydayNotice = false,
            )
        assertEquals(settings, settings.toRecord().toSettings())
        assertEquals(SalarySettings(), SalarySettings().toRecord().toSettings())
    }

    @Test
    fun `읽을 수 없는 칸은 기본값으로 둔다`() {
        val odd =
            SalaryRecord(
                amount = -5,
                workStart = "아홉시",
                workdays = listOf(0, 1, 8),
                payday = 40,
                startDate = "언젠가",
            ).toSettings()
        assertEquals(0L, odd.amount)
        assertEquals(LocalTime.of(9, 0), odd.workStart)
        assertEquals(setOf(DayOfWeek.MONDAY), odd.workdays)
        assertEquals(MAX_PAYDAY, odd.payday)
        assertEquals(null, odd.startDate)
    }
}
