package com.dong.budget.ui.fixed

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.editor.fixedExpensePrefill
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** [from] ~ [to] 달마다 [day] 일(없으면 말일, 쉬는 날이면 다음 영업일)에 [amount] 를 낸 기록 */
    private fun monthly(merchant: String, amount: Long, day: Int, from: YearMonth, to: YearMonth) =
        generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.toList().flatMap {
            paid(merchant, amount, KoreanCalendar.nextBusinessDay(usualDateOf(it, day)).toString())
        }

    @Test
    fun `4 - 두 청구 가운데 하나를 해지하면 다음 달부터 남은 하나만 내도 냈어요다`() {
        // 21일 45,000원 · 33,000원 두 회선 가운데 33,000원을 9월에 해지했다. 9월은 해지인지 아직 안 낸 것인지 모른다.
        val lines = monthly("통신사", 45_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 12)) +
            monthly("통신사", 33_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        assertEquals(FixedStatus.DUE, view(lines, YearMonth.of(2026, 9), day("2026-09-30")).status)
        val october = view(lines, YearMonth.of(2026, 10), day("2026-10-22"))
        assertEquals(FixedStatus.PAID, october.status)
        assertEquals(45_000L, october.amount)
        assertEquals(1, october.requiredCount)
        // 3일 50,000원 · 20일 30,000원 두 차례 가운데 20일 것을 9월부터 해지했다
        val insurance = monthly("보험", 50_000, 3, YearMonth.of(2026, 1), YearMonth.of(2026, 12)) +
            monthly("보험", 30_000, 20, YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        val later = view(insurance, YearMonth.of(2026, 10), day("2026-10-25"))
        assertEquals(FixedStatus.PAID, later.status)
        assertNull(rowNote(later, YearMonth.of(2026, 10), day("2026-10-25")))
    }

    @Test
    fun `6 - 금액이 3배 넘게 바뀌면서 결제일도 바뀌면 두 번째 새 금액부터 값을 바꾼 것으로 보아 다달이 낸 달이 냈어요다`() {
        val rows = (1..7).flatMap { paid("넷플릭스", 5_500, "2026-0$it-25") } +
            listOf("2026-08-10", "2026-09-10", "2026-10-10", "2026-11-10").flatMap { paid("넷플릭스", 17_000, it) }
        // 첫 새 금액 하나만으로는 연간 결제와 가를 수 없어 따로 낸 것이다(그 달 낸 돈에는 든다)
        val august = view(rows, YearMonth.of(2026, 8), day("2026-08-11"))
        assertEquals(FixedStatus.DUE, august.status)
        assertEquals(17_000L, august.paidAmount)
        for (date in listOf("2026-09-11", "2026-10-11", "2026-10-27", "2026-11-11", "2026-11-26")) {
            val today = day(date)
            val item = view(rows, YearMonth.from(today), today)
            assertEquals(date, FixedStatus.PAID, item.status)
            assertEquals(date, 17_000L, item.amount)
        }
        // 두 번째 새 금액이 나간 뒤에는 첫 달도 그 결제로 냈어요다
        assertEquals(FixedStatus.PAID, view(rows, YearMonth.of(2026, 8), day("2026-09-11")).status)
    }

    @Test
    fun `6 - 따로 낸 결제는 최근 금액의 4분의 1이 안 되는 금액이고 마지막 두 결제가 같은 금액이면 값을 바꾼 것이다`() {
        // 금액이 크게 다른 두 청구가 최근 12건에 7건 · 5건으로 걸려도 적은 쪽은 따로 낸 것이 아니다(절반과 견주면 따로 낸 것이었다)
        assertFalse(isExtra(30_000, List(7) { 200_000L } + List(5) { 30_000L }))
        assertTrue(isExtra(99_000, List(11) { 9_900L } + 99_000L))
        assertTrue(isExtra(17_000, List(11) { 5_500L } + 17_000L))
        assertFalse(isExtra(17_000, List(10) { 5_500L } + 17_000L + 17_000L))
    }

    /** [from] ~ [to] 달마다 [day] 일(없으면 말일)에 그날 나간 카드 결제 */
    private fun card(merchant: String, amount: Long, day: Int, from: YearMonth, to: YearMonth) =
        generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.toList().flatMap {
            paid(merchant, amount, usualDateOf(it, day).toString())
        }

    @Test
    fun `2 - 며칠 사이로 따로 나가는 두 청구는 두 차례라 남은 청구의 날 전에는 지났다고 하지 않는다`() {
        val apple = card("애플", 4_400, 5, YearMonth.of(2026, 1), YearMonth.of(2026, 11)) +
            card("애플", 10_900, 9, YearMonth.of(2026, 1), YearMonth.of(2026, 11))
        val october = YearMonth.of(2026, 10)
        for (date in listOf("2026-10-06", "2026-10-07", "2026-10-08")) {
            val item = view(apple, october, day(date))
            assertEquals(listOf(5, 9), item.usualDays)
            assertEquals(date, 1, item.paidCount)
            assertEquals(date, 10_900L, item.amount)
            assertEquals(date, "2번 중 1번 냈어요", rowNote(item, october, day(date))?.text)
        }
        assertEquals(FixedStatus.PAID, view(apple, october, day("2026-10-09")).status)
        // 자동이체 25일 관리비 · 28일 주차도 두 차례이고, 28일 것의 낼 날 전에는 지났다고 하지 않는다
        val apartment = monthly("아파트", 200_000, 25, YearMonth.of(2026, 1), YearMonth.of(2026, 11)) +
            monthly("아파트", 30_000, 28, YearMonth.of(2026, 1), YearMonth.of(2026, 11))
        assertEquals(listOf(25, 28), view(apartment, october, day("2026-10-24")).usualDays)
        for (date in listOf("2026-10-27", "2026-11-26", "2026-11-27")) {
            val today = day(date)
            val item = view(apartment, YearMonth.from(today), today)
            assertEquals(date, 28, item.dueDay)
            assertTrue(date, (item.daysPastUsual ?: 0) <= 0)
        }
        // 읽은 차례(최신순 · 오랜순)와 상관없이 같다(같은 날 낸 두 청구가 최근 12건의 끝에 걸려도)
        val today = day("2027-03-25")
        val rows = monthly("아파트", 200_000, 25, YearMonth.of(2025, 1), YearMonth.of(2027, 3)) +
            monthly("아파트", 30_000, 28, YearMonth.of(2025, 1), YearMonth.of(2027, 2))
        val read = rows.filter { YearMonth.from(it.localDate()) >= fixedHistoryStart(YearMonth.of(2027, 3)) }
        val newest = buildFixedExpenses(YearMonth.of(2027, 3), today, read.sortedByDescending { it.occurredAt })
        val oldest = buildFixedExpenses(YearMonth.of(2027, 3), today, read.sortedBy { it.occurredAt })
        assertEquals(newest, oldest)
        assertEquals(1, newest.due.single().paidCount)
    }

    @Test
    fun `3 - 1일과 말일 두 청구는 같은 달의 두 차례라 1일 것은 그 달 몫이다`() {
        val insurer = card("보험사", 50_000, 1, YearMonth.of(2026, 1), YearMonth.of(2026, 11)) +
            card("보험사", 30_000, LAST_DAY, YearMonth.of(2026, 1), YearMonth.of(2026, 11))
        val october = view(insurer, YearMonth.of(2026, 10), day("2026-10-15"))
        assertEquals(FixedStatus.DUE, october.status)
        assertEquals(listOf(1, LAST_DAY), october.usualDays)
        assertEquals(1, october.paidCount)
        assertEquals(30_000L, october.amount)
        assertEquals(30_000L, fixedExpensePrefill(october, day("2026-10-15")).amount)
        assertEquals(FixedStatus.PAID, view(insurer, YearMonth.of(2026, 10), day("2026-10-31")).status)
        assertEquals(80_000L, view(insurer, YearMonth.of(2026, 10), day("2026-10-31")).amount)
        assertEquals(30_000L, view(insurer, YearMonth.of(2026, 11), day("2026-11-15")).amount)
        // 같은 날 본 9월 화면은 9월 1일 · 30일 것이다(10월 1일 것을 9월 몫으로 세지 않는다)
        val september = view(insurer, YearMonth.of(2026, 9), day("2026-10-15"))
        assertEquals(FixedStatus.PAID, september.status)
        assertEquals(day("2026-09-01"), september.lastPaidOn)
    }
}
