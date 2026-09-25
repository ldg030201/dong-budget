package com.dong.budget.ui.format

import com.dong.budget.data.db.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class FormattersTest {
    @Test
    fun `만 원 아래는 그대로 쓴다`() {
        assertEquals("0원", formatCompactWon(0))
        assertEquals("8,500원", formatCompactWon(8_500))
        assertEquals("9,999원", formatCompactWon(9_999))
    }

    @Test
    fun `만 원부터는 만 단위로 반올림한다`() {
        assertEquals("1만원", formatCompactWon(10_000))
        assertEquals("9만원", formatCompactWon(93_000))
        assertEquals("10만원", formatCompactWon(95_000))
        assertEquals("1,000만원", formatCompactWon(9_999_500))
    }

    @Test
    fun `억 단위`() {
        assertEquals("1억원", formatCompactWon(100_000_000))
        assertEquals("1억 2,346만원", formatCompactWon(123_456_789))
        // 반올림으로 1억이 되면 억으로 올린다
        assertEquals("1억원", formatCompactWon(99_995_000))
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
}
