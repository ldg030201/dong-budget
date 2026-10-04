package com.dong.budget.data.holiday

import com.dong.budget.testing.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year

class KoreanHolidaysTest {
    private fun daysOf(year: Int): List<LocalDate> = (1..Year.of(year).length()).map { LocalDate.ofYearDay(year, it) }

    @Test
    fun `표의 해마다 은행이 쉬는 공휴일 수`() {
        // 주말과 겹친 공휴일도 센다. 2024 · 2025년은 공휴일 19일에 은행이 쉰 근로자의 날(5월 1일)을 더했다.
        val expected =
            mapOf(
                2023 to 19, 2024 to 20, 2025 to 20, 2026 to 22, 2027 to 24, 2028 to 18, 2029 to 20,
                2030 to 21, 2031 to 18, 2032 to 26, 2033 to 22, 2034 to 19, 2035 to 20,
            )
        val actual = (KoreanHolidays.FIRST_KNOWN_YEAR..KoreanHolidays.LAST_KNOWN_YEAR).associateWith { year ->
            daysOf(year).count(KoreanHolidays::isHoliday)
        }
        assertEquals(expected, actual)
        assertEquals(2035, KoreanHolidays.LAST_KNOWN_YEAR)
    }

    @Test
    fun `2027년 관공서 공휴일은 월력요항대로 72일이다`() {
        // 우주항공청 2027년 월력요항: 일요일 52일 + 일요일이 아닌 공휴일 20일
        val off = daysOf(2027).count { it.dayOfWeek == DayOfWeek.SUNDAY || KoreanHolidays.isHoliday(it) }
        assertEquals(72, off)
    }

    @Test
    fun `2025년 추석은 개천절부터 한글날까지 이어 쉬고 10일 금요일에 연다`() {
        val closed = generateSequence(day("2025-10-03")) { it.plusDays(1) }.takeWhile { it <= day("2025-10-09") }.toList()
        assertEquals(7, closed.size)
        assertTrue(closed.none(KoreanHolidays::isBusinessDay))
        assertTrue(KoreanHolidays.isBusinessDay(day("2025-10-10")))
        assertEquals(day("2025-10-10"), KoreanHolidays.nextBusinessDay(day("2025-10-03")))
        // 10월 1일(국군의날)은 2025년엔 임시공휴일이 아니었다
        assertTrue(KoreanHolidays.isBusinessDay(day("2025-10-01")))
    }

    @Test
    fun `2026년 추석이 토요일과 겹쳐도 대체공휴일이 없어 9월 28일 월요일에 연다`() {
        listOf("2026-09-24", "2026-09-25", "2026-09-26").forEach { assertTrue(it, KoreanHolidays.isHoliday(day(it))) }
        assertFalse(KoreanHolidays.isHoliday(day("2026-09-28")))
        assertTrue(KoreanHolidays.isBusinessDay(day("2026-09-28")))
        assertEquals(day("2026-09-28"), KoreanHolidays.nextBusinessDay(day("2026-09-24")))
        assertEquals(day("2026-09-23"), KoreanHolidays.nextBusinessDay(day("2026-09-23")))
    }

    @Test
    fun `대체공휴일과 선거일 · 임시공휴일도 쉰다`() {
        val days =
            listOf(
                "2024-02-12", "2024-04-10", "2024-10-01", "2025-01-27", "2025-03-03", "2025-05-06", "2025-06-03", "2025-10-08",
                "2026-03-02", "2026-05-25", "2026-06-03", "2026-08-17", "2026-10-05", "2027-02-09", "2027-05-03",
                "2027-07-19", "2027-10-11", "2027-12-27", "2028-10-05", "2032-09-21", "2033-12-26",
            )
        days.forEach { assertTrue(it, KoreanHolidays.isHoliday(day(it))) }
        // 신정 · 현충일은 대체공휴일이 없다(2028년 1월 1일 토요일, 2026년 6월 6일 토요일)
        assertTrue(KoreanHolidays.isBusinessDay(day("2028-01-03")))
        assertTrue(KoreanHolidays.isBusinessDay(day("2026-06-08")))
    }

    @Test
    fun `근로자의 날은 은행이 쉬고 12월 31일은 연다`() {
        assertFalse(KoreanHolidays.isBusinessDay(day("2024-05-01")))
        assertFalse(KoreanHolidays.isBusinessDay(day("2025-05-01")))
        assertTrue(KoreanHolidays.isBusinessDay(day("2025-12-31")))
        // 제헌절은 2026년부터 다시 공휴일이다
        assertTrue(KoreanHolidays.isBusinessDay(day("2025-07-17")))
        assertFalse(KoreanHolidays.isBusinessDay(day("2026-07-17")))
    }

    @Test
    fun `표 밖의 해는 양력 고정 공휴일만 본다`() {
        val fixed = listOf("01-01", "03-01", "05-01", "05-05", "06-06", "07-17", "08-15", "10-03", "10-09", "12-25")
        fixed.forEach { assertTrue(it, KoreanHolidays.isHoliday(day("2036-$it"))) }
        assertEquals(fixed.size, daysOf(2036).count(KoreanHolidays::isHoliday))
        // 표 안의 2023년은 제헌절이 공휴일이 아니었고, 설 연휴(1/21~23)와 대체공휴일(1/24 · 5/29) · 임시공휴일(10/2)이 있다
        assertTrue(KoreanHolidays.isHoliday(day("2023-05-01")))
        assertFalse(KoreanHolidays.isHoliday(day("2023-07-17")))
        listOf("2023-01-24", "2023-05-29", "2023-10-02").forEach { assertTrue(it, KoreanHolidays.isHoliday(day(it))) }
    }

    @Test
    fun `다음 영업일은 그날이 영업일이면 그날, 쉬는 날이면 주말 · 공휴일을 건너뛴 날이다`() {
        // 2026년 10월 5일(월)은 개천절 대체공휴일
        assertEquals(day("2026-10-06"), KoreanHolidays.nextBusinessDay(day("2026-10-03")))
        // 2026년 2월 14일(토) · 15일(일) · 설 16~18일
        assertEquals(day("2026-02-19"), KoreanHolidays.nextBusinessDay(day("2026-02-14")))
        // 2027년 12월 25일(토) → 27일 대체공휴일 → 28일
        assertEquals(day("2027-12-28"), KoreanHolidays.nextBusinessDay(day("2027-12-25")))
        // 표 밖 해에도 주말 · 고정 공휴일을 건너뛴다(2036년 1월 1일 화요일)
        assertEquals(day("2036-01-02"), KoreanHolidays.nextBusinessDay(day("2036-01-01")))
        assertEquals(day("2026-10-07"), KoreanHolidays.nextBusinessDay(day("2026-10-07")))
    }
}
