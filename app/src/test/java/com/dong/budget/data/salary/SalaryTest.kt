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
    // 월 300만 원, 월~금 9~18시, 점심 12~13시는 빼서 하루 8시간
    private val salary = SalarySettings(amount = 3_000_000)
    private val september = YearMonth.of(2026, 9)

    // 2026년 9월은 1일이 화요일이라 평일이 22일이다
    private val daily = 3_000_000.0 / 22

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun assertWon(expected: Double, actual: Double) = assertEquals(expected, actual, 0.001)

    @Test
    fun `하루치는 그 달 평일 수로 나누고, 초당 버는 돈은 하루 일하는 초로 나눈다`() {
        assertEquals(22, salary.workdaysIn(september))
        assertEquals(8 * 3600L, salary.workSecondsPerDay)
        assertWon(daily, salary.dailyAmount(september))
        assertWon(daily / (8 * 3600), salary.perSecond(september))
    }

    @Test
    fun `일하는 중에는 출근부터 지금까지만큼 쌓인다`() {
        val now = salary.earningsAt(at("2026-09-29T10:30:00"))
        assertEquals(WorkStatus.WORKING, now.status)
        assertWon(daily * 1.5 / 8, now.today)
        // 29일 전의 평일 20일 + 오늘
        assertWon(daily * 20 + daily * 1.5 / 8, now.month)
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
    fun `쉬는 요일은 오늘 0 이고 이번 달은 그 전 평일만큼이다`() {
        val saturday = salary.earningsAt(at("2026-09-26T11:00:00"))
        assertEquals(WorkStatus.DAY_OFF, saturday.status)
        assertWon(0.0, saturday.today)
        assertEquals(0f, saturday.dayProgress)
        // 1~25일의 평일 19일
        assertWon(daily * 19, saturday.month)
    }

    @Test
    fun `말일 퇴근 뒤에는 이번 달이 딱 월급이다`() {
        assertWon(3_000_000.0, salary.earningsAt(at("2026-09-30T18:00:00")).month)
        // 평일 수가 다른 달도 마찬가지다(2026년 2월은 20일)
        assertEquals(20, salary.workdaysIn(YearMonth.of(2026, 2)))
        assertWon(3_000_000.0, salary.earningsAt(at("2026-02-27T19:00:00")).month)
    }

    @Test
    fun `올해는 지난 달들의 월급과 이번 달 번 돈을 더한다`() {
        val march = salary.earningsAt(at("2026-03-02T08:00:00"))
        assertWon(6_000_000.0, march.year)
        assertWon(0.0, march.month)
        val september = salary.earningsAt(at("2026-09-29T10:30:00"))
        assertWon(8 * 3_000_000.0 + september.month, september.year)
    }

    @Test
    fun `연봉은 12로 나눠 한 달 월급으로 본다`() {
        val yearly = SalarySettings(basis = PayBasis.YEARLY, amount = 36_000_000)
        assertWon(3_000_000.0, yearly.monthly)
        assertWon(daily, yearly.dailyAmount(september))
    }

    @Test
    fun `시작일 전에는 벌지 않고, 시작한 달은 그날부터 일한 날만큼이다`() {
        val joined = salary.copy(startDate = LocalDate.of(2026, 9, 15))
        assertEquals(WorkStatus.NOT_STARTED, joined.statusAt(at("2026-09-14T10:00:00")))
        assertWon(0.0, joined.earningsAt(at("2026-09-14T10:00:00")).month)
        // 15~30일의 평일 12일
        assertWon(daily * 12, joined.earnedInWholeMonth(september))
        val end = joined.earningsAt(at("2026-09-30T18:00:00"))
        assertWon(daily * 12, end.month)
        assertWon(daily * 12, end.year)
        assertWon(daily * 12, end.monthTotal)
    }

    @Test
    fun `일하는 요일을 바꾸면 그 요일로 센다`() {
        val weekend = salary.copy(workdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        // 2026년 9월의 토·일은 8일
        assertEquals(8, weekend.workdaysIn(september))
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
    fun `월급날은 없는 날이면 말일, 주말이면 앞 금요일이다`() {
        // 2026년 10월 25일은 일요일
        assertEquals(LocalDate.of(2026, 10, 23), salary.paydayIn(YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 9, 25), salary.paydayIn(september))
        // 2026년 2월 28일은 토요일
        assertEquals(LocalDate.of(2026, 2, 27), salary.copy(payday = 31).paydayIn(YearMonth.of(2026, 2)))
        // 2026년 11월 1일은 일요일이라 10월 30일로 당겨진다
        assertEquals(LocalDate.of(2026, 10, 30), salary.copy(payday = 1).paydayIn(YearMonth.of(2026, 11)))
    }

    @Test
    fun `다음 월급날은 오늘이 월급날이면 오늘이고, 지났으면 다음 달이다`() {
        assertEquals(LocalDate.of(2026, 9, 25), salary.nextPayday(LocalDate.of(2026, 9, 25)))
        assertEquals(LocalDate.of(2026, 10, 23), salary.nextPayday(LocalDate.of(2026, 9, 26)))
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
        assertEquals(september, salary.payMonthOn(LocalDate.of(2026, 9, 25)))
        assertEquals(null, salary.payMonthOn(LocalDate.of(2026, 9, 24)))
        // 11월 1일 월급이 10월 30일로 당겨지면 그날은 11월 월급날이다
        assertEquals(YearMonth.of(2026, 11), salary.copy(payday = 1).payMonthOn(LocalDate.of(2026, 10, 30)))
        assertEquals(september, salary.latestPayMonth(LocalDate.of(2026, 10, 22)))
        assertEquals(YearMonth.of(2026, 10), salary.latestPayMonth(LocalDate.of(2026, 10, 23)))
        assertEquals(YearMonth.of(2026, 11), salary.copy(payday = 1).latestPayMonth(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `월급날 알림은 월급날 출근 시각이고, 놓쳤으면 그날 안에 바로 띄운다`() {
        val nineOClock = LocalDateTime.of(2026, 9, 25, 9, 0)
        assertEquals(september to nineOClock, salary.nextPaydayAlarm(at("2026-09-20T12:00:00"), notified = null))
        // 월급날 오후에 앱을 열었는데 아직 알리지 않았다
        assertEquals(september to at("2026-09-25T15:00:00"), salary.nextPaydayAlarm(at("2026-09-25T15:00:00"), notified = null))
        // 이미 알렸으면 다음 달(10월 25일은 일요일이라 23일)
        assertEquals(
            YearMonth.of(2026, 10) to LocalDateTime.of(2026, 10, 23, 9, 0),
            salary.nextPaydayAlarm(at("2026-09-25T15:00:00"), notified = september),
        )
        // 월급날이 지난 날에는 다음 달
        assertEquals(YearMonth.of(2026, 10), salary.nextPaydayAlarm(at("2026-09-26T08:00:00"), notified = null)?.first)
        assertEquals(null, salary.copy(paydayNotice = false).nextPaydayAlarm(at("2026-09-20T12:00:00"), notified = null))
        assertEquals(null, SalarySettings().nextPaydayAlarm(at("2026-09-20T12:00:00"), notified = null))
    }
}
