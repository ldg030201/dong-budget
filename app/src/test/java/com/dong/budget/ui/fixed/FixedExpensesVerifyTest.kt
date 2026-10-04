package com.dong.budget.ui.fixed

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.editor.fixedExpensePrefill
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** 리뷰 고침을 따로 검증하다(2026-10-04) 찾은, 고친 고정지출 계산의 새 결함 회귀 테스트. 읽는 범위와 오늘은 화면 모델처럼 준다. */
class FixedExpensesVerifyTest {
    /** 화면 모델처럼 [month] 를 [today] 에 볼 때 읽는 행으로 계산한 판 */
    private fun board(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate): FixedExpenseBoard {
        val read = rows.filter {
            !it.localDate().isAfter(today) && YearMonth.from(it.localDate()) in fixedHistoryStart(month)..fixedHistoryEnd(month, today)
        }
        return buildFixedExpenses(month, today, read.sortedByDescending { it.occurredAt })
    }

    /** [board] 의 가게 하나 */
    private fun view(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate): FixedExpenseItem =
        board(rows, month, today).let { it.due + it.paid + it.notThisMonth + it.stopped }.single()

    private fun paid(merchant: String, amount: Long, vararg dates: String) =
        dates.map { tx(it, amount, categoryId = 4, paymentId = 10, merchant = merchant) }

    /** [from] ~ [to] 날 가운데 [days] 요일마다 낸 기록. [changeAt] 부터는 [changed] 원이다. */
    private fun weekly(merchant: String, days: Set<DayOfWeek>, from: String, to: String, amount: Long, changeAt: String, changed: Long) =
        generateSequence(day(from)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(day(to)) }
            .filter { it.dayOfWeek in days }
            .flatMap { paid(merchant, if (it < day(changeAt)) amount else changed, it.toString()) }
            .toList()

    @Test
    fun `1 - 평일마다 내는 돌봄이나 주 3회 PT 의 금액이 바뀌어도 계산이 죽지 않고 그 달에 냈으면 냈어요다`() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val care = weekly("돌봄", weekdays, "2026-07-01", "2026-10-30", 30_000, "2026-09-14", 33_000)
        val today = day("2026-10-02")
        val october = view(care, YearMonth.of(2026, 10), today)
        assertEquals(FixedStatus.PAID, october.status)
        assertEquals(66_000L, october.amount)
        assertEquals("한 달에 21번쯤", scheduleText(october))
        // 지난 달은 그 달에 낸 것 모두(9월 14일부터 33,000원)
        assertEquals(30_000L * 9 + 33_000L * 13, view(care, YearMonth.of(2026, 9), today).amount)
        // 11월 첫 결제 전(일요일)에는 아직 안 냈어요, 낼 돈은 지난 달 합이고 낼 날 알림은 없다. 등록하기는 한 번 낸 금액으로 오늘이다.
        val sunday = day("2026-11-01")
        val november = view(care, YearMonth.of(2026, 11), sunday)
        assertEquals(FixedStatus.DUE, november.status)
        assertEquals(33_000L * 22, november.amount)
        assertNull(rowNote(november, YearMonth.of(2026, 11), sunday))
        val prefill = fixedExpensePrefill(november, sunday)
        assertEquals(33_000L, prefill.amount)
        assertEquals(sunday, BudgetTime.toLocalDate(Instant.ofEpochMilli(prefill.occurredAtMillis)))
        val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        for (start in listOf("2026-03-02", "2026-06-01", "2026-07-01")) {
            val pt = weekly("PT", mwf, start, "2026-10-02", 50_000, "2026-09-01", 55_000)
            assertEquals(FixedStatus.PAID, view(pt, YearMonth.of(2026, 10), today).status)
            assertEquals(55_000L, view(pt, YearMonth.of(2026, 10), today).amount)
        }
    }
}
