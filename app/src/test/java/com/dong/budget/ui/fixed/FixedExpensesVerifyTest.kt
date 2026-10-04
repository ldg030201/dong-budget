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
    fun `6 - 따로 낸 결제는 앞뒤 금액의 4분의 1이 안 되는 금액이고 바로 앞이나 뒤 결제와 같은 금액이면 값을 바꾼 것이다`() {
        // 금액이 크게 다른 두 청구가 앞뒤 13건에 7건 · 6건으로 걸려도 적은 쪽은 따로 낸 것이 아니다(절반과 견주면 따로 낸 것이었다)
        val bills = List(13) { if (it % 2 == 0) 200_000L else 30_000L }
        assertFalse(isExtraAt(bills, 11))
        assertTrue(isExtraAt(List(11) { 9_900L } + 99_000L, 11))
        assertTrue(isExtraAt(List(11) { 9_900L } + 99_000L + 9_900L, 11))
        assertTrue(isExtraAt(List(11) { 5_500L } + 17_000L, 11))
        val changed = List(10) { 5_500L } + List(12) { 17_000L }
        assertFalse(isExtraAt(changed, 10))
        // 금액을 바꾸고 오래 지나도 바꾸기 전 결제는 그때의 평소 금액이다
        assertFalse((0..9).any { isExtraAt(changed, it) })
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

    @Test
    fun `5 - 평소 날 가까이 나간 연간 결제는 그 달 차례를 채우지 않는다`() {
        val google = card("구글", 9_900, 12, YearMonth.of(2026, 1), YearMonth.of(2026, 11)) + paid("구글", 99_000, "2026-09-10")
        val september = YearMonth.of(2026, 9)
        // 매달 것이 아직 안 나간 9월 11일은 아직 안 냈어요이고, 낸 돈에는 연간 결제가 들며 낼 돈은 매달 것이다
        val before = view(google, september, day("2026-09-11"))
        assertEquals(FixedStatus.DUE, before.status)
        assertEquals(9_900L, before.amount)
        assertEquals(99_000L, before.paidAmount)
        val after = view(google, september, day("2026-09-13"))
        assertEquals(FixedStatus.PAID, after.status)
        assertEquals(108_900L, after.amount)
        assertNull(rowNote(after, september, day("2026-09-13")))
        assertEquals(9_900L, view(google, YearMonth.of(2026, 10), day("2026-10-11")).amount)
        // 매달 10일 것을 9월에만 11일에 내고 연간 결제를 10일에 내도 10일엔 아직 안 냈어요다
        val late = card("구글2", 9_900, 10, YearMonth.of(2026, 1), YearMonth.of(2026, 8)) + paid("구글2", 9_900, "2026-09-11") +
            paid("구글2", 99_000, "2026-09-10")
        assertEquals(FixedStatus.DUE, view(late, september, day("2026-09-10")).status)
        assertEquals(FixedStatus.PAID, view(late, september, day("2026-09-11")).status)
    }

    @Test
    fun `7 - 매년 내는 것을 앞 달에 미리 내면 낸 달 화면이 그 몫을 미리 냈다고 알리고 다음 차례는 그다음 해다`() {
        val car = paid("자동차보험", 800_000, "2024-03-15", "2025-03-14", "2026-02-22")
        val february = YearMonth.of(2026, 2)
        val early = view(car, february, day("2026-02-23"))
        assertEquals(FixedStatus.NOT_THIS_MONTH, early.status)
        assertEquals(YearMonth.of(2026, 3), early.prepaidMonth)
        assertEquals(YearMonth.of(2027, 3), early.nextMonth)
        assertEquals("3월 몫을 2월 22일에 미리 냈어요", rowNote(early, february, day("2026-02-23"))?.text)
        // 그 결제는 3월 몫이라 2월 낸 돈에는 들지 않고, 3월 화면은 냈어요다
        assertEquals(0L, early.paidAmount)
        val march = view(car, YearMonth.of(2026, 3), day("2026-03-20"))
        assertEquals(FixedStatus.PAID, march.status)
        assertEquals(day("2026-02-22"), march.lastPaidOn)
        // 아직 미리 내지 않았으면 2월 화면이 다음 차례를 알린다
        val onTime = view(paid("자동차보험", 800_000, "2024-03-15", "2025-03-14"), february, day("2026-02-20"))
        assertNull(onTime.prepaidMonth)
        assertEquals("다음은 3월에 내요", rowNote(onTime, february, day("2026-02-20"))?.text)
    }

    @Test
    fun `c32 - 한 번 계산하는 모든 가게의 짐작 · 짝짓기 · 정렬이 낼 날 표 하나를 함께 쓴다`() {
        val today = day("2026-10-04")
        fun rows(count: Int) = (0 until count).flatMap { monthly("가게$it", 10_000L + it, 25, YearMonth.of(2025, 1), YearMonth.of(2026, 9)) }
        val one = DueDates().also { buildFixedExpenses(YearMonth.of(2026, 10), today, rows(1), it) }
        val many = DueDates().also { buildFixedExpenses(YearMonth.of(2026, 10), today, rows(15), it) }
        // 같은 날 내는 가게가 늘어도 새로 구할 낼 날이 없고, 짐작이 묻는 앞뒤 달의 1~31일도 그 표에 있다
        assertEquals(one.size, many.size)
        assertTrue(one.size >= LAST_DAY * 3)
    }
}
