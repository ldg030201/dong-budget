package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 해지한 달 · 하루 차이 두 청구 고침을 교차 검증하다(2026-10-05) 찾은 결함 회귀 테스트. 읽는 범위와 오늘은 화면 모델처럼 준다. */
class FixedExpensesCrossCheckTest {
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
    private fun note(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate, morning: Boolean = false): RowNote? =
        rowNote(view(rows, month, today, morning), month, today)

    /** 상태 · 낸 횟수 / 건수 · 금액을 한 줄로 */
    private fun short(item: FixedExpenseItem): String = "${item.status} ${item.paidCount}/${item.requiredCount} ${item.amount}"

    private fun paid(merchant: String, amount: Long, vararg dates: String) =
        dates.map { tx(it, amount, categoryId = 4, paymentId = 10, merchant = merchant) }

    /** [from] ~ [to] 의 [step] 달마다 [day] 일(없으면 말일)에 [amount] 를 낸 기록. [card] 면 그날, 아니면 쉬는 날이면 다음 영업일에 나간다. */
    private fun bill(merchant: String, amount: Long, day: Int, from: YearMonth, to: YearMonth, card: Boolean = false, step: Long = 1) =
        generateSequence(from) { it.plusMonths(step) }.takeWhile { it <= to }.toList().flatMap {
            val usual = usualDateOf(it, day)
            paid(merchant, amount, (if (card) usual else KoreanCalendar.nextBusinessDay(usual)).toString())
        }

    @Test
    fun `금액으로 가를 수 없는 두 차례에서 뒤 차례를 해지하면 다음 달 앞 차례 결제는 그 달 몫이라 다음 달부터 한 건이다`() {
        // 3일 · 20일 30,000원 자동이체 가운데 20일 것을 2026년 9월부터 해지
        val rows = bill("보장", 30_000, 3, JAN, MAR27) + bill("보장", 30_000, 20, JAN, AUG)
        assertEquals("PAID 1/2 30000", short(view(rows, SEP, day("2026-09-25"))))
        assertEquals("DUE 0/1 30000", short(view(rows, OCT, day("2026-10-02"))))
        for (today in listOf("2026-10-06", "2026-10-15", "2026-10-21", "2026-10-23", "2026-10-28", "2026-11-23")) {
            val month = YearMonth.from(day(today))
            assertEquals(today, "PAID 1/1 30000", short(view(rows, month, day(today))))
            assertNull(today, note(rows, month, day(today)))
        }
        // 지난 달 화면의 9월은 끝까지 2번 중 1번만 냈어요
        assertEquals("PAID 1/2 30000", short(view(rows, SEP, day("2026-10-21"))))
        // 3일 것만 20% 넘게 올라도(50,000원 → 65,000원) 금액으로 차례를 가르지 못해 같다
        val raised = bill("보장", 50_000, 3, JAN, SEP) + bill("보장", 65_000, 3, OCT, MAR27) + bill("보장", 30_000, 20, JAN, AUG)
        for (today in listOf("2026-10-06", "2026-10-22")) assertEquals(today, "PAID 1/1 65000", short(view(raised, OCT, day(today))))
    }

    private companion object {
        val JAN: YearMonth = YearMonth.of(2026, 1)
        val FEB: YearMonth = YearMonth.of(2026, 2)
        val MAR: YearMonth = YearMonth.of(2026, 3)
        val APR: YearMonth = YearMonth.of(2026, 4)
        val MAY: YearMonth = YearMonth.of(2026, 5)
        val JUN: YearMonth = YearMonth.of(2026, 6)
        val JUL: YearMonth = YearMonth.of(2026, 7)
        val AUG: YearMonth = YearMonth.of(2026, 8)
        val DEC: YearMonth = YearMonth.of(2026, 12)
        val SEP: YearMonth = YearMonth.of(2026, 9)
        val OCT: YearMonth = YearMonth.of(2026, 10)
        val NOV: YearMonth = YearMonth.of(2026, 11)
        val MAR27: YearMonth = YearMonth.of(2027, 3)
    }
}
