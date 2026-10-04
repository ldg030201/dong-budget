package com.dong.budget.data.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/**
 * 한국 은행이 쉬는 공휴일 달력. 자동이체는 쉬는 날(주말 · 공휴일)이면 다음 영업일에 나가므로, 고정지출이 연휴로 밀린 결제를
 * '평소보다 N일 지났어요' 로 먼저 알리지 않게 쓴다. 월급날을 앞 영업일로 당기는 데도 쓴다(SalarySettings.paydayIn).
 * 표를 고치면 고정지출의 낼 날뿐 아니라 그 달 월급 기간 · 하루치 · '월급날부터 번 돈' 도 지난 것까지 바뀐다.
 *
 * - 해마다 같은 양력 날: 신정 · 삼일절 · 노동절(5월 1일) · 어린이날 · 현충일 · 광복절 · 개천절 · 한글날 · 성탄절, 2026년부터 제헌절.
 *   5월 1일은 2025년까지 관공서 공휴일이 아닌 근로자의 날이었지만 은행 영업점이 쉬었고, 2026년부터 노동절로 공휴일이다.
 * - 해마다 바뀌는 날([FIRST_KNOWN_YEAR] ~ [LAST_KNOWN_YEAR]): 설 · 부처님오신날 · 추석 연휴, 대체공휴일, 임기만료 선거일, 임시공휴일.
 *   표 밖의 해는 양력 고정 공휴일만 본다(설 · 추석 연휴를 모른다).
 *
 * 출처: 관공서의 공휴일에 관한 규정(대통령령 제36290호) 제2조 · 제3조(대체공휴일), 한국천문연구원 음양력 변환으로 바꾼 설 · 부처님오신날 · 추석
 * (중국 음력과 다른 해가 있다), 우주항공청 2027년 월력요항, 공직선거법 제34조로 센 선거일, 정부가 지정한 임시공휴일
 * (2023년 10월 2일 · 2024년 10월 1일 · 2025년 1월 27일 · 2025년 6월 3일 궐위 대통령 선거). 2028년부터의 대체공휴일은 현행 제3조로 센 값이다.
 *
 * 임시공휴일 · 궐위 선거일은 정부가 지정할 때 생기고, 앞으로의 선거일은 대통령 궐위 · 개헌 · 선거법 개정으로 바뀔 수 있다.
 * 지정되거나 바뀌면 [VARIABLE_DAYS] 에 더하거나 고쳐야 한다(2026년 10월 4일까지 지정된 것을 넣었다). 표를 늘리면 [LAST_KNOWN_YEAR] 도 올린다.
 *
 * 날짜는 서울 기준(com.dong.budget.data.db.BudgetTime)으로 바꾼 날짜다. 시각에 따라 날이 달라지지 않게 [LocalDate] 만 받는다.
 */
object KoreanHolidays {
    /** 해마다 바뀌는 공휴일을 아는 첫 해 */
    const val FIRST_KNOWN_YEAR = 2023

    /** 해마다 바뀌는 공휴일을 아는 마지막 해. 그 뒤 해는 양력 고정 공휴일만 본다. */
    const val LAST_KNOWN_YEAR = 2035

    /**
     * 해마다 바뀌는 공휴일(월일 MMDD). 해마다 같은 양력 공휴일([FIXED_DAYS])과 겹치는 날(2025년 어린이날 · 부처님오신날,
     * 2028년 개천절 · 추석)은 한 번만 적는다. 대체공휴일은 그 해의 것을 그대로 적는다.
     */
    private val VARIABLE_DAYS: Map<Int, String> =
        mapOf(
            2023 to "0121 0122 0123 0124 0527 0529 0928 0929 0930 1002", // 1/24 · 5/29 대체, 10/2 임시
            2024 to "0209 0210 0211 0212 0410 0506 0515 0916 0917 0918 1001", // 4/10 총선, 10/1 임시(국군의날)
            2025 to "0127 0128 0129 0130 0303 0506 0603 1005 1006 1007 1008", // 1/27 임시, 6/3 궐위 대선(임시)
            2026 to "0216 0217 0218 0302 0524 0525 0603 0817 0924 0925 0926 1005", // 6/3 지방선거
            2027 to "0206 0207 0208 0209 0503 0513 0719 0816 0914 0915 0916 1004 1011 1227",
            2028 to "0126 0127 0128 0412 0502 1002 1004 1005", // 4/12 총선
            2029 to "0212 0213 0214 0507 0520 0521 0921 0922 0923 0924",
            2030 to "0202 0203 0204 0205 0327 0506 0509 0612 0911 0912 0913", // 3/27 대선, 6/12 지방선거
            2031 to "0122 0123 0124 0303 0528 0930 1001 1002",
            2032 to "0210 0211 0212 0414 0503 0516 0517 0719 0816 0918 0919 0920 0921 1004 1011 1227", // 4/14 총선
            2033 to "0130 0131 0201 0202 0502 0506 0718 0907 0908 0909 1010 1226",
            2034 to "0218 0219 0220 0221 0525 0531 0926 0927 0928", // 5/31 지방선거
            2035 to "0207 0208 0209 0328 0507 0515 0915 0916 0917 0918", // 3/28 대선
        )

    /** [VARIABLE_DAYS] 를 날짜로 바꾼 것 */
    private val variableDates: Set<LocalDate> =
        VARIABLE_DAYS.flatMap { (year, days) ->
            days.split(" ").map { LocalDate.of(year, it.take(2).toInt(), it.drop(2).toInt()) }
        }.toSet()

    /** 해마다 같은 양력 공휴일. 5월 1일은 근로자의 날(2025년까지, 은행이 쉼) · 노동절(2026년부터)이다. */
    private val FIXED_DAYS: Set<MonthDay> =
        listOf("01-01", "03-01", "05-01", "05-05", "06-06", "08-15", "10-03", "10-09", "12-25")
            .map { MonthDay.parse("--$it") }
            .toSet()

    /** 제헌절. 2008~2025년은 공휴일이 아니었고 2026년부터 다시 공휴일이다. */
    private val CONSTITUTION_DAY: MonthDay = MonthDay.of(7, 17)
    private const val CONSTITUTION_DAY_SINCE = 2026

    /** [date] 가 은행이 쉬는 공휴일인지. 주말은 따로 보지 않는다(주말과 겹친 공휴일도 공휴일이다). */
    fun isHoliday(date: LocalDate): Boolean {
        val day = MonthDay.from(date)
        return date in variableDates || day in FIXED_DAYS || (day == CONSTITUTION_DAY && date.year >= CONSTITUTION_DAY_SINCE)
    }

    /** [date] 가 은행이 여는 날(토 · 일 · 공휴일이 아닌 날)인지 */
    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && !isHoliday(date)

    /** [date] 부터 첫 영업일. 그날이 영업일이면 그날이고, 쉬는 날이면 자동이체가 나갈 다음 영업일이다. */
    fun nextBusinessDay(date: LocalDate): LocalDate = generateSequence(date) { it.plusDays(1) }.first(::isBusinessDay)

    /** [date] 까지 마지막 영업일. 그날이 영업일이면 그날이고, 쉬는 날이면 그 앞 영업일이다(쉬는 날 앞에 미리 빼 가는 자동이체). */
    fun previousBusinessDay(date: LocalDate): LocalDate = generateSequence(date) { it.minusDays(1) }.first(::isBusinessDay)
}
