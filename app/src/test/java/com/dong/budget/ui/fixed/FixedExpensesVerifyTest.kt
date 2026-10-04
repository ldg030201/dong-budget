package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
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
    fun `1 - 평일마다 내는 돌봄이나 주 3회 PT 의 금액이 바뀌어도 계산이 죽지 않는다`() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val care = weekly("돌봄", weekdays, "2026-07-01", "2026-10-02", 30_000, "2026-09-14", 33_000)
        val today = day("2026-10-02")
        assertEquals("돌봄", view(care, YearMonth.of(2026, 10), today).name)
        assertEquals("돌봄", view(care, YearMonth.of(2026, 9), today).name)
        val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        for (start in listOf("2026-03-02", "2026-06-01", "2026-07-01")) {
            val pt = weekly("PT", mwf, start, "2026-10-02", 50_000, "2026-09-01", 55_000)
            assertEquals("PT", view(pt, YearMonth.of(2026, 10), today).name)
        }
    }
}
