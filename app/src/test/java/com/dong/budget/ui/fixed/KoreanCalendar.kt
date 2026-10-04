package com.dong.budget.ui.fixed

import com.dong.budget.data.holiday.KoreanHolidays
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 시뮬레이션 테스트용 한국 달력. 앱의 공휴일 달력([KoreanHolidays])과 같은 날을 쉰다(토 · 일, 설 · 추석 연휴, 대체공휴일,
 * 선거일, 임시공휴일, 2024 · 2025년 근로자의 날, 2026년부터 노동절 · 제헌절). 표가 맞는지는 KoreanHolidaysTest 가 본다.
 * 앱과 같은 달력을 쓰므로 결제가 연휴로 밀린 날을 앱도 안다(밀린 자동이체를 '지났어요' 로 먼저 알리지 않는지 본다).
 */
internal object KoreanCalendar {
    /** 은행이 쉬는 날(토 · 일 · 공휴일)인지 */
    fun isHoliday(date: LocalDate): Boolean = !KoreanHolidays.isBusinessDay(date)

    /** 주말이 아닌데 공휴일(설 · 추석 · 대체공휴일 등)이라 쉬는 날인지 */
    fun isWeekdayHoliday(date: LocalDate): Boolean =
        KoreanHolidays.isHoliday(date) && date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    /** [date] 가 쉬는 날이면 그 뒤 첫 영업일, 아니면 그날 */
    fun nextBusinessDay(date: LocalDate): LocalDate = KoreanHolidays.nextBusinessDay(date)

    /** [date] 가 쉬는 날이면 그 앞 마지막 영업일, 아니면 그날 */
    fun previousBusinessDay(date: LocalDate): LocalDate = KoreanHolidays.previousBusinessDay(date)
}
