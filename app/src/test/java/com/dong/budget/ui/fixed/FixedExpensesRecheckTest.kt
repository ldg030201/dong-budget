package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 해지한 달 · 하루 차이 두 청구 고침을 다시 검증하다(2026-10-04) 찾은 결함 회귀 테스트. 읽는 범위와 오늘은 화면 모델처럼 준다. */
class FixedExpensesRecheckTest {
    /** 화면 모델처럼 [month] 를 [today] 에 볼 때 읽는 행으로 계산한 판. [morning] 이면 그날 결제가 나가기 전(앞 날짜까지)이다. */
    private fun board(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate, morning: Boolean = false): FixedExpenseBoard {
        val read = rows.filter {
            val date = it.localDate()
            (if (morning) date.isBefore(today) else !date.isAfter(today)) &&
                YearMonth.from(date) in fixedHistoryStart(month)..fixedHistoryEnd(month, today)
        }
        return buildFixedExpenses(month, today, read.sortedByDescending { it.occurredAt })
    }

    /** [board] 의 가게 하나 */
    private fun view(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate, morning: Boolean = false): FixedExpenseItem =
        board(rows, month, today, morning).let { it.due + it.paid + it.notThisMonth + it.stopped }.single()

    /** [view] 의 덧붙임 글 */
    private fun note(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate, morning: Boolean = false): String? =
        rowNote(view(rows, month, today, morning), month, today)?.text

    private fun paid(merchant: String, amount: Long, vararg dates: String) =
        dates.map { tx(it, amount, categoryId = 4, paymentId = 10, merchant = merchant) }

    /** [from] ~ [to] 의 [step] 달마다 [day] 일(없으면 말일)에 [amount] 를 낸 기록. [card] 면 그날, 아니면 쉬는 날이면 다음 영업일에 나간다. */
    private fun bill(merchant: String, amount: Long, day: Int, from: YearMonth, to: YearMonth, card: Boolean = false, step: Long = 1) =
        generateSequence(from) { it.plusMonths(step) }.takeWhile { it <= to }.toList().flatMap {
            val usual = usualDateOf(it, day)
            paid(merchant, amount, (if (card) usual else KoreanCalendar.nextBusinessDay(usual)).toString())
        }

    @Test
    fun `2달마다 내는 두 회선 가게는 사이 달을 건수에 세지 않아 해지한 달이 2번 중 1번만 냈어요이고 평소 달은 두 건이다`() {
        // 홀수 달 10일 카드 45,000원 · 33,000원 두 회선 가운데 33,000원을 2026년 9월부터 해지했다
        val rows = bill("정수기", 45_000, 10, YearMonth.of(2025, 1), YearMonth.of(2026, 11), card = true, step = 2) +
            bill("정수기", 33_000, 10, YearMonth.of(2025, 1), YearMonth.of(2026, 7), card = true, step = 2)
        val july = view(rows, YearMonth.of(2026, 7), day("2026-07-11"))
        assertEquals(FixedStatus.PAID, july.status)
        assertEquals(2, july.paidCount)
        assertEquals(2, july.requiredCount)
        val september = YearMonth.of(2026, 9)
        assertEquals("2번 중 1번 냈고, 남은 건 오늘 낼 차례예요", note(rows, september, day("2026-09-10")))
        val cancelled = view(rows, september, day("2026-09-11"))
        assertEquals(FixedStatus.PAID, cancelled.status)
        assertEquals(1, cancelled.paidCount)
        assertEquals(2, cancelled.requiredCount)
        assertEquals("2번 중 1번만 냈어요", rowNote(cancelled, september, day("2026-09-11"))?.text)
        // 다음 차례(11월)는 남은 회선 한 건이다
        val november = view(rows, YearMonth.of(2026, 11), day("2026-11-02"))
        assertEquals(1, november.requiredCount)
        assertEquals(45_000L, november.amount)
    }

    @Test
    fun `앞 달 뒤 청구가 쉬는 날로 이번 달 초에 밀려 아직 안 나갔으면 이번 달 건수와 낼 돈은 두 청구다`() {
        // 25일 200,000원 · 28일 30,000원 자동이체. 2026년 2월 28일(토) 것은 3월 3일(3월 2일 대체공휴일)에 나간다.
        val apartment = bill("아파트", 200_000, 25, YearMonth.of(2025, 6), YearMonth.of(2026, 6)) +
            bill("아파트", 30_000, 28, YearMonth.of(2025, 6), YearMonth.of(2026, 6))
        val march1 = day("2026-03-01")
        assertEquals("2번 중 1번 냈고, 남은 건 3월 3일에 낼 차례예요", note(apartment, YearMonth.of(2026, 2), march1))
        for (today in listOf(march1, day("2026-03-02"), day("2026-03-03"))) {
            val march = view(apartment, YearMonth.of(2026, 3), today, morning = true)
            assertEquals("$today", 2, march.requiredCount)
            assertEquals("$today", 230_000L, march.amount)
            assertEquals("$today", 230_000L, board(apartment, YearMonth.of(2026, 3), today, morning = true).dueTotal)
        }
        // 3일 50,000원 · 28일 30,000원 자동이체도 같다
        val insurance = bill("보험", 50_000, 3, YearMonth.of(2025, 1), YearMonth.of(2026, 12)) +
            bill("보험", 30_000, 28, YearMonth.of(2025, 1), YearMonth.of(2026, 12))
        assertEquals(80_000L, view(insurance, YearMonth.of(2026, 3), march1).amount)
        // 15일 50,000원 · 말일 30,000원: 10월 31일(토) 것은 11월 2일에 나간다
        val endOfMonth = bill("보험사", 50_000, 15, YearMonth.of(2026, 1), YearMonth.of(2026, 12)) +
            bill("보험사", 30_000, LAST_DAY, YearMonth.of(2026, 1), YearMonth.of(2026, 12))
        assertEquals(80_000L, view(endOfMonth, YearMonth.of(2026, 11), day("2026-11-01")).amount)
        // 같은 날 말일 두 회선(카드 45,000원은 그날, 자동이체 33,000원은 다음 영업일): 2025년 5월 31일(토) 자동이체분은 6월 2일에 나간다
        val lines = bill("통신사", 45_000, LAST_DAY, YearMonth.of(2024, 1), YearMonth.of(2025, 12), card = true) +
            bill("통신사", 33_000, LAST_DAY, YearMonth.of(2024, 1), YearMonth.of(2025, 12))
        val june = view(lines, YearMonth.of(2025, 6), day("2025-06-01"))
        assertEquals(2, june.requiredCount)
        assertEquals(78_000L, june.amount)
    }
}
