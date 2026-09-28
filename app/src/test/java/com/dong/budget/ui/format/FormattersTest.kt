package com.dong.budget.ui.format

import com.dong.budget.data.db.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class FormattersTest {
    @Test
    fun `알림이 온 때는 오늘과 어제를 말로 적는다`() {
        val today = LocalDate.of(2026, 9, 25)
        fun at(text: String) = java.time.LocalDateTime.parse(text).atZone(com.dong.budget.data.db.BudgetTime.ZONE).toInstant()
        assertEquals("오늘 오후 2:22", formatNoticeTime(at("2026-09-25T14:22"), today))
        assertEquals("어제 오전 8:40", formatNoticeTime(at("2026-09-24T08:40"), today))
        assertEquals("9월 23일 오후 3:00", formatNoticeTime(at("2026-09-23T15:00"), today))
    }

    @Test
    fun `지출은 -, 수입은 + 를 붙이고, 화면 읽기용 지출은 부호 없이 적는다`() {
        assertEquals("-12,000원", formatNetExpense(12_000))
        assertEquals("+12,000원", formatNetExpense(-12_000))
        assertEquals("0원", formatNetExpense(0))
        assertEquals("12,000원", formatSpentAmount(12_000))
        assertEquals("+12,000원", formatSpentAmount(-12_000))
        assertEquals("+30,000원", formatDirected(30_000, income = true))
        assertEquals("-30,000원", formatDirected(30_000, income = false))
    }

    @Test
    fun `만 원 아래는 그대로 쓴다`() {
        assertEquals("0원", formatCompactWon(0))
        assertEquals("8,500원", formatCompactWon(8_500))
        assertEquals("9,999원", formatCompactWon(9_999))
    }

    @Test
    fun `만 원부터는 만 단위 아래를 버린다`() {
        assertEquals("1만원", formatCompactWon(10_000))
        assertEquals("9만원", formatCompactWon(93_000))
        assertEquals("9만원", formatCompactWon(95_000))
        assertEquals("999만원", formatCompactWon(9_999_500))
        // 통계 머리의 전체 금액(2,076,048원)보다 커 보이지 않는다
        assertEquals("207만원", formatCompactWon(2_076_048))
    }

    @Test
    fun `억 단위`() {
        assertEquals("1억원", formatCompactWon(100_000_000))
        assertEquals("1억 2,345만원", formatCompactWon(123_456_789))
        // 1억에 모자라면 억으로 올리지 않는다
        assertEquals("9,999만원", formatCompactWon(99_995_000))
    }

    @Test
    fun `한국어 단위 표기는 줄이지 않고 1원까지 적는다`() {
        assertEquals("8,500원", formatKoreanWon(8_500))
        assertEquals("1만원", formatKoreanWon(10_000))
        assertEquals("1만 2,345원", formatKoreanWon(12_345))
        assertEquals("1억 2,345만 6,789원", formatKoreanWon(123_456_789))
        assertEquals("9,999억 9,999만 9,999원", formatKoreanWon(999_999_999_999))
    }

    @Test
    fun `한국어 단위 표기는 빈 단위를 건너뛰고 조까지 쓴다`() {
        assertEquals("1억 2,000만원", formatKoreanWon(120_000_000))
        assertEquals("1억 5,000원", formatKoreanWon(100_005_000))
        assertEquals("1억 1원", formatKoreanWon(100_000_001))
        assertEquals("1조 2,345억원", formatKoreanWon(1_234_500_000_000))
        assertEquals("0원", formatKoreanWon(0))
    }

    @Test
    fun `부호는 떼고 크기만 쓴다`() {
        assertEquals("9만원", formatCompactWon(-90_400))
    }

    @Test
    fun `거래 금액은 들어오면 +, 나가면 -, 이체는 부호가 없다`() {
        assertEquals("+3,000원", formatSignedAmount(TransactionType.INCOME, 3_000))
        assertEquals("+3,000원", formatSignedAmount(TransactionType.REFUND, 3_000))
        assertEquals("-55,000원", formatSignedAmount(TransactionType.EXPENSE, 55_000))
        assertEquals("10,000원", formatSignedAmount(TransactionType.TRANSFER, 10_000))
    }

    @Test
    fun `합계는 값의 방향대로 부호를 붙이고 0 은 부호가 없다`() {
        assertEquals("+2,976,087원", formatSignedTotal(2_976_087))
        assertEquals("-1,303,998원", formatSignedTotal(-1_303_998))
        assertEquals("0원", formatSignedTotal(0))
    }

    @Test
    fun `날짜 구분선은 오늘과 어제를 알려준다`() {
        val today = LocalDate.of(2026, 9, 25)
        assertEquals("25일 금요일 · 오늘", formatDayHeader(today, today))
        assertEquals("24일 목요일 · 어제", formatDayHeader(today.minusDays(1), today))
        assertEquals("20일 일요일", formatDayHeader(LocalDate.of(2026, 9, 20), today))
    }

    @Test
    fun `요일과 읽기용 날짜는 기기 언어와 상관없이 한국어다`() {
        assertEquals("일", formatWeekday(DayOfWeek.SUNDAY))
        assertEquals("9월 25일 금요일", formatDateSpoken(LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun `올해가 아니면 연도를 붙인다`() {
        val today = LocalDate.of(2026, 9, 25)
        // 2025-09-10 12:00 KST
        val lastYear = Instant.parse("2025-09-10T03:00:00Z")
        val thisYear = Instant.parse("2026-09-10T03:00:00Z")
        assertEquals("2025년 9월 10일 (수)", formatDate(lastYear, today))
        assertEquals("9월 10일 (목)", formatDate(thisYear, today))
    }

    @Test
    fun `비율은 정수 % 로 반올림하고 반올림하면 0% 가 되는 작은 값은 1% 미만이다`() {
        assertEquals("42%", formatShare(0.42))
        assertEquals("43%", formatShare(0.4251))
        assertEquals("100%", formatShare(1.0))
        // 딱 28.5% 는 소수로 28.4999… 가 되지만 눈에 띄는 점(정수 반올림)과 같게 29% 로 올린다
        assertEquals("29%", formatShare(57_000.0 / 200_000))
        assertEquals("0%", formatShare(0.0))
        assertEquals("1% 미만", formatShare(0.004))
        assertEquals("1%", formatShare(0.005))
    }

    @Test
    fun `배수는 소수 첫째 자리까지 쓴다`() {
        assertEquals("1.8배", formatRatio(1.8))
        assertEquals("1.3배", formatRatio(1.25))
        assertEquals("2.0배", formatRatio(2.0))
    }

    @Test
    fun `짧은 날짜와 요일 이름`() {
        assertEquals("9월 3일 (목)", formatDayShort(LocalDate.of(2026, 9, 3)))
        assertEquals("토요일", formatWeekdayFull(DayOfWeek.SATURDAY))
    }

    @Test
    fun `달 이름은 이번 달, 올해, 다른 해를 가린다`() {
        val today = LocalDate.of(2026, 9, 27)
        assertEquals("이번 달", monthLabel(YearMonth.of(2026, 9), today))
        assertEquals("8월", monthLabel(YearMonth.of(2026, 8), today))
        assertEquals("2025년 12월", monthLabel(YearMonth.of(2025, 12), today))
        assertEquals("2027년 1월", monthLabel(YearMonth.of(2027, 1), today))
    }
}
