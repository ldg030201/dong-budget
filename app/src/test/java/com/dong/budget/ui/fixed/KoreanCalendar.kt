package com.dong.budget.ui.fixed

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 시뮬레이션 테스트용 한국 달력(2025~2027). 토·일과 주요 공휴일을 쉬는 날로 본다.
 * 신정 · 설 연휴 · 삼일절 · 어린이날 · 부처님오신날 · 현충일 · 광복절 · 추석 연휴 · 개천절 · 한글날 · 성탄절과 대체공휴일,
 * 2025년 1월 27일 임시공휴일을 적었다. 선거일은 뺐다.
 */
internal object KoreanCalendar {
    private val holidays: Set<LocalDate> =
        listOf(
            // 2025
            "2025-01-01",
            "2025-01-27", // 임시공휴일
            "2025-01-28", "2025-01-29", "2025-01-30", // 설
            "2025-03-01", "2025-03-03", // 삼일절(토) → 대체
            "2025-05-05", "2025-05-06", // 어린이날 · 부처님오신날 겹침 → 대체
            "2025-06-06",
            "2025-08-15",
            "2025-10-03",
            "2025-10-05", "2025-10-06", "2025-10-07", "2025-10-08", // 추석(일 겹침 → 대체 8일)
            "2025-10-09",
            "2025-12-25",
            // 2026
            "2026-01-01",
            "2026-02-16", "2026-02-17", "2026-02-18", // 설
            "2026-03-01", "2026-03-02", // 삼일절(일) → 대체
            "2026-05-05",
            "2026-05-24", "2026-05-25", // 부처님오신날(일) → 대체
            "2026-06-06",
            "2026-08-15", "2026-08-17", // 광복절(토) → 대체
            "2026-09-24", "2026-09-25", "2026-09-26", "2026-09-28", // 추석(토 겹침 → 대체 28일)
            "2026-10-03", "2026-10-05", // 개천절(토) → 대체
            "2026-10-09",
            "2026-12-25",
            // 2027
            "2027-01-01",
            "2027-02-06", "2027-02-07", "2027-02-08", "2027-02-09", // 설(일 겹침 → 대체 9일)
            "2027-03-01",
            "2027-05-05",
            "2027-05-13",
            "2027-06-06",
            "2027-08-15", "2027-08-16", // 광복절(일) → 대체
            "2027-09-14", "2027-09-15", "2027-09-16", // 추석
            "2027-10-03", "2027-10-04", // 개천절(일) → 대체
            "2027-10-09", "2027-10-11", // 한글날(토) → 대체
            "2027-12-25", "2027-12-27", // 성탄절(토) → 대체
        ).map(LocalDate::parse).toSet()

    /** 은행이 쉬는 날(토·일·공휴일)인지 */
    fun isHoliday(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY || date in holidays

    /** 주말이 아닌데 공휴일(설 · 추석 · 대체공휴일 등)이라 쉬는 날인지 */
    fun isWeekdayHoliday(date: LocalDate): Boolean =
        date in holidays && date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    /** [date] 가 쉬는 날이면 그 뒤 첫 영업일, 아니면 그날 */
    fun nextBusinessDay(date: LocalDate): LocalDate = generateSequence(date) { it.plusDays(1) }.first { !isHoliday(it) }

    /** [date] 가 쉬는 날이면 그 앞 마지막 영업일, 아니면 그날 */
    fun previousBusinessDay(date: LocalDate): LocalDate = generateSequence(date) { it.minusDays(1) }.first { !isHoliday(it) }
}
