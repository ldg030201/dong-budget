package com.dong.budget.data.salary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class SalaryTest {
    // 월 300만 원, 월~금 9~18시, 점심 12~13시는 빼서 하루 8시간, 월급날 25일
    private val salary = SalarySettings(amount = 3_000_000)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    // 9월 25일(금)이 추석 연휴(24~26일)라 9월 월급날은 23일(수)로 당겨진다. 그래서 10월 월급 기간은
    // 9월 24일(목)부터 10월 23일(금, 25일이 일요일이라 당김)까지이고 평일이 22일이다(쌓이는 날은 추석도 평일로 센다)
    private val daily = 3_000_000.0 / 22

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun assertWon(expected: Double, actual: Double) = assertEquals(expected, actual, 0.001)

    @Test
    fun `월급 기간은 앞 달 월급날 다음 날부터 그달 월급날까지다`() {
        assertEquals(LocalDate.of(2026, 9, 24)..LocalDate.of(2026, 10, 23), salary.payPeriod(october))
        // 8월 25일(화)은 영업일, 9월 월급날은 추석으로 23일(수)
        assertEquals(LocalDate.of(2026, 8, 26)..LocalDate.of(2026, 9, 23), salary.payPeriod(september))
        assertEquals(september, salary.payMonthFor(LocalDate.of(2026, 9, 23)))
        assertEquals(october, salary.payMonthFor(LocalDate.of(2026, 9, 24)))
        // 11월 1일 월급이 10월 30일로 당겨지면 10월 31일은 12월 월급 기간이다
        val first = salary.copy(payday = 1)
        assertEquals(YearMonth.of(2026, 11), first.payMonthFor(LocalDate.of(2026, 10, 30)))
        assertEquals(YearMonth.of(2026, 12), first.payMonthFor(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `하루치는 월급 기간의 평일 수로 나누고, 초당 버는 돈은 하루 일하는 초로 나눈다`() {
        // 10월: 9/24~10/23 평일 22일. 9월: 8/26~9/23 평일 21일(8/26~28 사흘, 8/31~9/18 세 주 15일, 9/21~23 사흘)
        assertEquals(22, salary.workdaysIn(october))
        assertEquals(21, salary.workdaysIn(september))
        assertEquals(8 * 3600L, salary.workSecondsPerDay)
        assertWon(daily, salary.dailyAmount(october))
        assertWon(daily / (8 * 3600), salary.perSecond(october))
    }

    @Test
    fun `일하는 중에는 출근부터 지금까지만큼 쌓인다`() {
        val now = salary.earningsAt(at("2026-09-29T10:30:00"))
        assertEquals(WorkStatus.WORKING, now.status)
        assertWon(daily * 1.5 / 8, now.today)
        // 월급 기간(9월 24일~)에서 오늘 전의 평일 사흘(24·25일은 추석이지만 일하는 요일이라 센다, 28일) + 오늘
        assertWon(daily * 3 + daily * 1.5 / 8, now.period)
        assertEquals(october, now.payMonth)
        assertEquals(LocalDate.of(2026, 9, 24), now.periodStart)
        assertEquals(1.5f / 8, now.dayProgress, 0.0001f)
    }

    @Test
    fun `점심시간에는 멈추고, 끄면 점심시간도 쌓인다`() {
        val lunch = salary.earningsAt(at("2026-09-29T12:40:00"))
        assertEquals(WorkStatus.LUNCH, lunch.status)
        assertWon(daily * 3 / 8, lunch.today)
        assertWon(daily * 3 / 8, salary.earningsAt(at("2026-09-29T13:00:00")).today)

        val noLunch = salary.copy(skipLunch = false)
        assertEquals(9 * 3600L, noLunch.workSecondsPerDay)
        assertEquals(WorkStatus.WORKING, noLunch.statusAt(at("2026-09-29T12:40:00")))
        // 3시간 40분 / 9시간
        assertWon(daily * 13_200 / 32_400, noLunch.earningsAt(at("2026-09-29T12:40:00")).today)
    }

    @Test
    fun `출근 전은 0, 퇴근 뒤는 하루치다`() {
        val before = salary.earningsAt(at("2026-09-29T08:59:59"))
        assertEquals(WorkStatus.BEFORE_WORK, before.status)
        assertWon(0.0, before.today)
        val after = salary.earningsAt(at("2026-09-29T21:00:00"))
        assertEquals(WorkStatus.AFTER_WORK, after.status)
        assertWon(daily, after.today)
        assertEquals(1f, after.dayProgress, 0.0001f)
    }

    @Test
    fun `쉬는 요일은 오늘 0 이고 월급날부터는 그 전 평일만큼이다`() {
        val saturday = salary.earningsAt(at("2026-10-03T11:00:00"))
        assertEquals(WorkStatus.DAY_OFF, saturday.status)
        assertWon(0.0, saturday.today)
        assertEquals(0f, saturday.dayProgress)
        // 9월 24일~10월 2일의 평일 7일(24·25일 + 28일~10월 2일)
        assertWon(daily * 7, saturday.period)
    }

    @Test
    fun `월급날 퇴근 뒤에는 딱 월급이고, 다음 날 0원부터 다시 쌓인다`() {
        assertWon(3_000_000.0, salary.earningsAt(at("2026-10-23T18:00:00")).period)
        // 9월 월급날은 추석으로 당겨진 23일이다
        assertWon(3_000_000.0, salary.earningsAt(at("2026-09-23T19:00:00")).period)
        val next = salary.earningsAt(at("2026-10-24T10:00:00"))
        assertEquals(YearMonth.of(2026, 11), next.payMonth)
        assertWon(0.0, next.period)
    }

    @Test
    fun `올해는 1월 1일부터 지나간 날을 그날이 든 월급 기간의 하루치로 더한다`() {
        // 2025년 12월 25일(목)이 성탄절이라 12월 월급날은 24일(수)이다. 그래서 1월 월급 기간은 2025-12-25~2026-01-23(평일 22일)이고
        // 1월 1일~23일 몫(평일 17일)만 올해다.
        // 2월 월급(1월 24일~2월 25일)은 통째로, 3월 월급 기간(2월 26일~3월 25일, 평일 20일)은 2월 26·27일 이틀 몫이다.
        val march = salary.earningsAt(at("2026-03-02T08:00:00"))
        assertWon(3_000_000.0 * 17 / 22 + 3_000_000 + 3_000_000.0 / 20 * 2, march.year)
        assertWon(3_000_000.0 / 20 * 2, march.period)
    }

    @Test
    fun `해가 바뀌면 올해는 1월 1일부터 다시 센다`() {
        // 2026년 12월 25일(금)이 성탄절이라 12월 월급날은 24일(목)이다. 그래서 2027년 1월 월급 기간은 2026-12-25~2027-01-25(평일 22일).
        // 1월 5일 오전 10시는 1일(금, 신정이지만 일하는 요일이라 센다)·4일(월) 이틀과 오늘 1시간
        val daily2027 = 3_000_000.0 / 22
        val now = salary.earningsAt(at("2027-01-05T10:00:00"))
        assertWon(daily2027 * (2 + 1.0 / 8), now.year)
        // 월급날부터는 해를 넘겨 12월 25일부터 센다(25일, 28~31일, 1일, 4일 평일 7일)
        assertWon(daily2027 * (7 + 1.0 / 8), now.period)
    }

    @Test
    fun `연봉은 12로 나눠 한 달 월급으로 본다`() {
        val yearly = SalarySettings(basis = PayBasis.YEARLY, amount = 36_000_000)
        assertWon(3_000_000.0, yearly.monthly)
        assertWon(daily, yearly.dailyAmount(october))
    }

    @Test
    fun `시작일 전에는 벌지 않고, 시작한 월급 기간은 그날부터 일한 날만큼이다`() {
        val joined = salary.copy(startDate = LocalDate.of(2026, 10, 5))
        assertEquals(WorkStatus.NOT_STARTED, joined.statusAt(at("2026-10-02T10:00:00")))
        assertWon(0.0, joined.earningsAt(at("2026-10-02T10:00:00")).period)
        // 10월 5일~23일의 평일 15일
        assertWon(daily * 15, joined.earnedInPeriod(october))
        val end = joined.earningsAt(at("2026-10-23T18:00:00"))
        assertWon(daily * 15, end.period)
        assertWon(daily * 15, end.year)
        assertWon(daily * 15, end.periodTotal)
    }

    @Test
    fun `일하는 요일을 바꾸면 그 요일로 센다`() {
        val weekend = salary.copy(workdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        // 10월 월급 기간(9월 24일~10월 23일)의 토·일은 8일
        assertEquals(8, weekend.workdaysIn(october))
        assertEquals(WorkStatus.WORKING, weekend.statusAt(at("2026-09-26T10:00:00")))
        assertEquals(WorkStatus.DAY_OFF, weekend.statusAt(at("2026-09-29T10:00:00")))
    }

    @Test
    fun `정하지 않았거나 말이 안 되면 계산하지 않는다`() {
        assertFalse(SalarySettings().isReady)
        assertEquals(WorkStatus.NOT_SET, SalarySettings().earningsAt(at("2026-09-29T10:00:00")).status)
        assertFalse(salary.copy(workdays = emptySet()).isReady)
        assertFalse(salary.copy(workStart = LocalTime.of(18, 0), workEnd = LocalTime.of(9, 0)).isReady)
        assertTrue(salary.isReady)
    }

    @Test
    fun `근무 밖에 잡은 점심은 빼지 않는다`() {
        val odd = salary.copy(lunchStart = LocalTime.of(19, 0), lunchEnd = LocalTime.of(20, 0))
        assertEquals(9 * 3600L, odd.workSecondsPerDay)
        // 반쯤 걸친 점심은 걸친 만큼만 뺀다
        val overlap = salary.copy(lunchStart = LocalTime.of(8, 30), lunchEnd = LocalTime.of(9, 30))
        assertEquals(8 * 3600L + 1800, overlap.workSecondsPerDay)
    }

    @Test
    fun `월급날은 없는 날이면 말일, 주말 · 공휴일이면 앞 영업일이다`() {
        // 2026년 10월 25일은 일요일
        assertEquals(LocalDate.of(2026, 10, 23), salary.paydayIn(YearMonth.of(2026, 10)))
        // 2026년 11월 25일(수)은 영업일이라 그대로
        assertEquals(LocalDate.of(2026, 11, 25), salary.paydayIn(YearMonth.of(2026, 11)))
        // 2026년 2월 28일은 토요일
        assertEquals(LocalDate.of(2026, 2, 27), salary.copy(payday = 31).paydayIn(YearMonth.of(2026, 2)))
        // 2026년 11월 1일은 일요일이라 10월 30일로 당겨진다
        assertEquals(LocalDate.of(2026, 10, 30), salary.copy(payday = 1).paydayIn(YearMonth.of(2026, 11)))
    }

    @Test
    fun `월급날이 공휴일이면 앞 영업일로 당긴다`() {
        // 2026년 9월 25일(금)은 추석 연휴(24~26일)라 24일(목)도 건너뛰고 23일(수)
        assertEquals(LocalDate.of(2026, 9, 23), salary.paydayIn(september))
        // 2027년 1월 1일(금)은 신정이라 앞 달 2026년 12월 31일(목)
        val first = salary.copy(payday = 1)
        assertEquals(LocalDate.of(2026, 12, 31), first.paydayIn(YearMonth.of(2027, 1)))
        // 2027년 3월 1일(월)은 삼일절, 2월 28일(일)·27일(토)도 쉬어서 2월 26일(금)
        assertEquals(LocalDate.of(2027, 2, 26), first.paydayIn(YearMonth.of(2027, 3)))
        // 2025년 10월은 3일(금) 개천절, 5~7일 추석, 8일 대체공휴일, 9일(목) 한글날까지 쉬고 10일(금)은 영업일이라 10일 월급은 그대로
        val october2025 = YearMonth.of(2025, 10)
        assertEquals(LocalDate.of(2025, 10, 10), salary.copy(payday = 10).paydayIn(october2025))
        // 5일 월급은 5일(일, 추석)·4일(토)·3일(금, 개천절)을 건너뛰고 2일(목)
        assertEquals(LocalDate.of(2025, 10, 2), salary.copy(payday = 5).paydayIn(october2025))
    }

    @Test
    fun `공휴일로 앞 달로 넘어간 월급날은 다음 달 월급날이다`() {
        // 1일 월급: 2027년 1월 1일(금, 신정) 월급은 2026년 12월 31일(목)에, 12월 월급은 12월 1일(화)에 받는다
        val first = salary.copy(payday = 1)
        val january = YearMonth.of(2027, 1)
        val newYearsEve = LocalDate.of(2026, 12, 31)
        val newYearsDay = LocalDate.of(2027, 1, 1)
        assertEquals(january, first.payMonthOn(newYearsEve))
        // 1월 1일은 월급날이 아니다
        assertEquals(null, first.payMonthOn(newYearsDay))
        assertEquals(newYearsEve, first.nextPayday(LocalDate.of(2026, 12, 2)))
        assertEquals(newYearsEve, first.nextPayday(newYearsEve))
        // 그 다음은 2027년 2월 1일(월)
        assertEquals(LocalDate.of(2027, 2, 1), first.nextPayday(newYearsDay))
        assertEquals(YearMonth.of(2026, 12), first.latestPayMonth(LocalDate.of(2026, 12, 30)))
        assertEquals(january, first.latestPayMonth(newYearsEve))
        assertEquals(january, first.latestPayMonth(newYearsDay))
        // 1월 월급 기간은 12월 2일~31일이고, 1월 1일은 2월 월급 기간(1월 1일~2월 1일)의 첫날이다
        assertEquals(LocalDate.of(2026, 12, 2)..newYearsEve, first.payPeriod(january))
        assertEquals(january, first.payMonthFor(newYearsEve))
        assertEquals(YearMonth.of(2027, 2), first.payMonthFor(newYearsDay))
        // 1일 월급: 2027년 3월 월급은 2월 26일(금)에 받는다
        assertEquals(YearMonth.of(2027, 3), first.payMonthOn(LocalDate.of(2027, 2, 26)))
        assertEquals(YearMonth.of(2027, 3), first.payMonthFor(LocalDate.of(2027, 2, 26)))
        assertEquals(YearMonth.of(2027, 4), first.payMonthFor(LocalDate.of(2027, 2, 27)))
    }

    @Test
    fun `다음 월급날은 오늘이 월급날이면 오늘이고, 지났으면 다음 달이다`() {
        // 9월 월급날은 추석으로 당겨진 23일이라 24일(추석)부터는 10월 23일이다
        assertEquals(LocalDate.of(2026, 9, 23), salary.nextPayday(LocalDate.of(2026, 9, 23)))
        assertEquals(LocalDate.of(2026, 10, 23), salary.nextPayday(LocalDate.of(2026, 9, 24)))
        assertEquals(LocalDate.of(2026, 11, 25), salary.nextPayday(LocalDate.of(2026, 10, 24)))
        // 11월 월급날이 10월 30일로 당겨진 뒤, 10월 31일의 다음 월급날은 12월 1일이다
        val first = salary.copy(payday = 1)
        assertEquals(LocalDate.of(2026, 10, 30), first.nextPayday(LocalDate.of(2026, 10, 2)))
        assertEquals(LocalDate.of(2026, 12, 1), first.nextPayday(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `결제 금액을 시간으로 바꿀 때는 달마다 다르지 않은 평균 시급을 쓴다`() {
        // 평일 5일 × 한 달 평균 4.348주 × 하루 8시간
        val perSecond = 3_000_000 / (5 * (365.2425 / 7 / 12) * 8 * 3600)
        assertEquals(perSecond, salary.averagePerSecond, 1e-9)
        assertEquals(9_800 / perSecond, salary.secondsToEarn(9_800)!!, 0.001)
        assertEquals(null, SalarySettings().secondsToEarn(9_800))
    }

    @Test
    fun `어느 달 월급을 받는 날인지`() {
        // 9월 월급은 추석(24~26일)으로 당겨진 23일에 받는다. 원래 날짜 25일은 월급날이 아니다
        assertEquals(september, salary.payMonthOn(LocalDate.of(2026, 9, 23)))
        assertEquals(null, salary.payMonthOn(LocalDate.of(2026, 9, 22)))
        assertEquals(null, salary.payMonthOn(LocalDate.of(2026, 9, 25)))
        // 11월 1일 월급이 10월 30일로 당겨지면 그날은 11월 월급날이다
        assertEquals(YearMonth.of(2026, 11), salary.copy(payday = 1).payMonthOn(LocalDate.of(2026, 10, 30)))
        assertEquals(september, salary.latestPayMonth(LocalDate.of(2026, 10, 22)))
        assertEquals(YearMonth.of(2026, 10), salary.latestPayMonth(LocalDate.of(2026, 10, 23)))
        assertEquals(YearMonth.of(2026, 11), salary.copy(payday = 1).latestPayMonth(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `월급날 알림은 월급날 출근 시각이고, 놓쳤으면 그날 안에 바로 띄운다`() {
        // 9월 월급날은 25일이 추석이라 23일(수)이다
        val nineOClock = LocalDateTime.of(2026, 9, 23, 9, 0)
        assertEquals(september to nineOClock, salary.nextPaydayAlarm(at("2026-09-20T12:00:00"), notified = null))
        // 월급날 오후에 앱을 열었는데 아직 알리지 않았다
        assertEquals(september to at("2026-09-23T15:00:00"), salary.nextPaydayAlarm(at("2026-09-23T15:00:00"), notified = null))
        // 이미 알렸으면 다음 달(10월 25일은 일요일이라 23일)
        assertEquals(
            YearMonth.of(2026, 10) to LocalDateTime.of(2026, 10, 23, 9, 0),
            salary.nextPaydayAlarm(at("2026-09-23T15:00:00"), notified = september),
        )
        // 월급날이 지난 날에는 다음 달
        assertEquals(YearMonth.of(2026, 10), salary.nextPaydayAlarm(at("2026-09-24T08:00:00"), notified = null)?.first)
        assertEquals(null, salary.copy(paydayNotice = false).nextPaydayAlarm(at("2026-09-20T12:00:00"), notified = null))
        assertEquals(null, SalarySettings().nextPaydayAlarm(at("2026-09-20T12:00:00"), notified = null))
    }

    @Test
    fun `통상시급은 월 소정근로시간(주휴 포함)으로 나눈다`() {
        assertEquals(209, salary.standardMonthlyHours)
        assertEquals(3_000_000.0 / 209, salary.ordinaryHourlyWage, 1e-6)
        // 주 40시간을 넘는 몫은 연장근로라 넣지 않는다
        assertEquals(209, salary.copy(workdays = salary.workdays + DayOfWeek.SATURDAY).standardMonthlyHours)
        // 주 3일 8시간: (24 + 4.8) × 365 ÷ 7 ÷ 12 = 125.1 → 126
        assertEquals(126, salary.copy(workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)).standardMonthlyHours)
        // 주 15시간이 안 되면 주휴가 없다: 주 2일 7시간 = 14 × 365 ÷ 7 ÷ 12 = 60.8 → 61
        val short = salary.copy(workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), workEnd = LocalTime.of(17, 0))
        assertEquals(61, short.standardMonthlyHours)
        assertEquals(0.0, SalarySettings().ordinaryHourlyWage, 0.0)
    }

    @Test
    fun `실수령을 적으면 쌓이는 돈은 실수령으로, 통상시급은 세전으로 센다`() {
        val both = salary.copy(takeHome = 2_600_000)
        assertTrue(both.usesTakeHome)
        assertWon(2_600_000.0, both.monthly)
        // 10월 월급 기간(9월 24일~10월 23일) 평일 22일
        assertWon(2_600_000.0 / 22, both.dailyAmount(october))
        assertWon(2_600_000.0, both.earningsAt(at("2026-10-23T18:00:00")).period)
        assertEquals(3_000_000.0 / 209, both.ordinaryHourlyWage, 1e-6)
        // 실수령만 적어도 쌓인다. 통상시급은 셀 수 없다.
        val onlyTakeHome = SalarySettings(takeHome = 2_600_000)
        assertTrue(onlyTakeHome.isReady)
        assertEquals(0.0, onlyTakeHome.ordinaryHourlyWage, 0.0)
    }

    @Test
    fun `월급날 퇴근 뒤에는 소수 오차 없이 딱 월급이다`() {
        // 하루치를 먼저 구해 곱하면(하루치 × 앞선 21일 + 하루치 × 오늘) 2,999,999.9999999995 가 된다(평일 22일인 10월 월급 기간)
        assertEquals(3_000_000.0, salary.earningsAt(at("2026-10-23T18:00:00")).period, 0.0)
        assertEquals(3_000_000.0, salary.earnedInPeriod(october), 0.0)
        assertEquals(3_000_000L, salary.payFor(october))
        // 3,330,000 원도 같은 셈이면 3,329,999.9999999995 가 된다
        assertEquals(3_330_000.0, salary.copy(amount = 3_330_000).earningsAt(at("2026-10-23T18:00:00")).period, 0.0)
        assertEquals(3_330_000L, salary.copy(amount = 3_330_000).payFor(october))
        // 기간 전체로 셀 때도 하루치를 먼저 곱하면 평일 23일인 11월 월급 기간(10/24~11/25)에서 3,329,999.9999999995 가 된다
        assertEquals(3_330_000.0, salary.copy(amount = 3_330_000).earnedInPeriod(YearMonth.of(2026, 11)), 0.0)
        // 추석으로 짧아진 9월 월급 기간(평일 21일)도 딱 월급이다
        assertEquals(3_000_000L, salary.payFor(september))
    }

    @Test
    fun `입사 전 달은 받을 월급이 없어 알림도 띄우지 않고, 입사한 달은 일할로 받는다`() {
        val joined = salary.copy(startDate = LocalDate.of(2026, 11, 2))
        assertEquals(0L, joined.payFor(october))
        // 11월 월급 기간(10월 24일~11월 25일)에 입사 11월 2일이라 그날부터 일한 만큼
        val november = YearMonth.of(2026, 11)
        val novemberDays = generateSequence(LocalDate.of(2026, 10, 24)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.of(2026, 11, 25)) }
        val total = novemberDays.count { it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        val worked = novemberDays.count {
            it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
                !it.isBefore(LocalDate.of(2026, 11, 2))
        }
        assertEquals((3_000_000.0 * worked / total).toLong(), joined.payFor(november))
        // 9월 29일에 맞추면 10월(입사 전)을 건너뛰고 11월 월급날(25일, 수) 알림을 맞춘다
        assertEquals(november to LocalDateTime.of(2026, 11, 25, 9, 0), joined.nextPaydayAlarm(at("2026-09-29T10:00:00"), notified = null))
    }
}
