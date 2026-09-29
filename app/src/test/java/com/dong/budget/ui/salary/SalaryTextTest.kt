package com.dong.budget.ui.salary

import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.ui.format.formatClock
import com.dong.budget.ui.format.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SalaryTextTest {
    private val salary = SalarySettings(amount = 3_000_000)

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `번 돈은 원 아래를 버리고 + 를 붙인다`() {
        assertEquals("+128,340원", formatEarned(128_340.97))
        assertEquals("+0원", formatEarned(0.4))
    }

    @Test
    fun `지금 상태 한 줄`() {
        // 2026-09-29 는 화요일, 2026-09-26 은 토요일
        assertEquals("일하는 중이에요 · 퇴근까지 2시간 14분", statusLine(salary, at("2026-09-29T15:46:00")))
        assertEquals("일하는 중이에요 · 퇴근까지 1분", statusLine(salary, at("2026-09-29T17:59:30")))
        assertEquals("출근 전이에요 · 출근까지 40분", statusLine(salary, at("2026-09-29T08:20:00")))
        assertEquals("점심시간이에요 · 오후 1:00부터 다시 쌓여요", statusLine(salary, at("2026-09-29T12:30:00")))
        assertEquals("퇴근했어요 · 오늘 몫을 다 벌었어요", statusLine(salary, at("2026-09-29T18:00:00")))
        assertEquals("쉬는 날이에요 · 월요일부터 다시 쌓여요", statusLine(salary, at("2026-09-26T11:00:00")))
        assertEquals("쉬는 날이에요 · 내일부터 다시 쌓여요", statusLine(salary, at("2026-09-27T11:00:00")))
        assertEquals(
            "10월 1일 (목)부터 쌓여요",
            statusLine(salary.copy(startDate = LocalDate.of(2026, 10, 1)), at("2026-09-29T11:00:00")),
        )
        assertEquals("월급을 정하면 쌓이기 시작해요", statusLine(SalarySettings(), at("2026-09-29T11:00:00")))
    }

    @Test
    fun `월급날까지 남은 날은 기호 없이 말로 적는다`() {
        val today = LocalDate.of(2026, 9, 22)
        assertEquals("월급날까지 3일 남았어요", paydayLine(LocalDate.of(2026, 9, 25), today))
        assertEquals("내일은 월급날이에요", paydayLine(LocalDate.of(2026, 9, 23), today))
        assertEquals("오늘은 월급날이에요", paydayLine(today, today))
    }

    @Test
    fun `버는 빠르기`() {
        assertEquals("1초에 4.73원 · 1분에 284원 · 1시간에 17,045원", rateLine(4.7348))
    }

    @Test
    fun `오늘 번 돈과 쓴 돈 견주기`() {
        assertEquals("번 돈의 18%를 썼어요", spentLine(128_340.0, 23_400))
        assertEquals("번 돈의 1%를 썼어요", spentLine(128_340.0, 100))
        assertEquals("번 돈보다 12,000원 더 썼어요", spentLine(8_000.9, 20_000))
        assertEquals("오늘은 아직 쓴 돈이 없어요", spentLine(128_340.0, 0))
        assertEquals("아직 번 돈이 없어요", spentLine(0.0, 5_000))
        assertEquals("오늘은 쓴 돈보다 환불받은 돈이 많아요", spentLine(10.0, -3_000))
    }

    @Test
    fun `일한 시간은 하루치를 넘으면 일하는 날로 센다`() {
        val day = 8 * 3600L
        assertEquals("1분도 안 돼요", workValue(20.0, day))
        assertEquals("약 47분", workValue(47 * 60.0 + 10, day))
        assertEquals("약 2시간 5분", workValue(125 * 60.0, day))
        assertEquals("약 3시간", workValue(180 * 60.0, day))
        assertEquals("약 1일", workValue(day.toDouble(), day))
        assertEquals("약 3일 2시간", workValue(3 * day + 2 * 3600.0, day))
    }

    @Test
    fun `화면 읽기는 천 원 단위로 끊는다`() {
        assertEquals("약 128,000원", spokenEarned(128_999.0))
    }

    @Test
    fun `시각과 남은 시간 포맷`() {
        assertEquals("오전 9:00", formatClock(LocalTime.of(9, 0)))
        assertEquals("오후 6:30", formatClock(LocalTime.of(18, 30)))
        assertEquals("1분", formatDuration(1))
        assertEquals("40분", formatDuration(40 * 60))
        assertEquals("41분", formatDuration(40 * 60 + 1))
        assertEquals("3시간", formatDuration(3 * 3600))
        assertEquals("1시간 5분", formatDuration(65 * 60))
    }
}
