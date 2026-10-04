package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.editor.fixedExpensePrefill
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** [from] ~ [to] 날마다 */
    private fun days(from: String, to: String): List<LocalDate> =
        generateSequence(day(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(day(to)) }.toList()

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

    @Test
    fun `같은 금액 카드 두 차례에서 뒤 차례를 해지한 다음 달 앞 차례가 쉬는 날 낼 날 전에 나가도 그 달 몫이다`() {
        // 카드 5일 · 21일 20,000원 가운데 21일 것을 2026년 9월부터 해지. 10월 5일은 대체공휴일이라 카드는 5일, 낼 날은 6일이다.
        val rows = bill("카드", 20_000, 5, JAN, MAR27, card = true) + bill("카드", 20_000, 21, JAN, AUG, card = true)
        assertEquals("PAID 1/2 20000", short(view(rows, SEP, day("2026-10-05"))))
        for (today in listOf("2026-10-05", "2026-10-07", "2026-10-21", "2026-10-25", "2026-10-31")) {
            assertEquals(today, "PAID 1/1 20000", short(view(rows, OCT, day(today))))
            assertNull(today, note(rows, OCT, day(today)))
        }
        assertNull(view(rows, NOV, day("2026-11-02")).missedMonth)
    }

    @Test
    fun `한 차례에 한 건 다른 차례에 두 건 내는 가게는 차례마다 낸 대로 나눠 기다린다`() {
        // 5일 20,000원 · 21일 45,000원 · 21일 33,000원 자동이체
        val normal = bill("통신", 20_000, 5, JAN, MAR27) + bill("통신", 45_000, 21, JAN, MAR27) + bill("통신", 33_000, 21, JAN, MAR27)
        assertEquals("DUE 1/3 78000", short(view(normal, SEP, day("2026-09-10"))))
        // 5일 것을 9월부터 해지하면 다음 달부터 21일 두 회선만 기다린다
        val early = bill("통신", 20_000, 5, JAN, AUG) + bill("통신", 45_000, 21, JAN, MAR27) + bill("통신", 33_000, 21, JAN, MAR27)
        for (today in listOf("2026-10-08", "2026-10-15", "2026-11-07", "2026-12-09")) {
            val item = view(early, YearMonth.from(day(today)), day(today))
            assertEquals(today, "DUE 0/2 78000 21", "${short(item)} ${item.dueDay}")
            assertTrue(today, (item.daysPastUsual ?: 0) <= 0)
        }
        // 21일 33,000원 회선을 9월부터 해지하면 9월은 끝까지 3번 중 2번만 냈어요, 10월은 두 건이다
        val late = bill("통신", 20_000, 5, JAN, MAR27) + bill("통신", 45_000, 21, JAN, MAR27) + bill("통신", 33_000, 21, JAN, AUG)
        for (today in listOf("2026-10-06", "2026-10-25")) assertEquals(today, "PAID 2/3 65000", short(view(late, SEP, day(today))))
        assertEquals(2, view(late, OCT, day("2026-10-07")).requiredCount)
        assertEquals("PAID 2/2 65000", short(view(late, OCT, day("2026-10-22"))))
        assertNull(note(late, OCT, day("2026-10-22")))
    }

    @Test
    fun `같은 날 회선 하나를 해지하고 남은 회선 금액이 바뀐 다음 달 몫을 일찍 내도 해지한 달의 빈 회선을 메우지 않는다`() {
        // 21일 45,000원 · 33,000원 가운데 33,000원을 9월부터 해지, 10월부터 남은 회선이 55,000원으로 5일(대체공휴일이라 6일)에 나간다
        val moved = bill("통신", 45_000, 21, JAN, SEP) + bill("통신", 55_000, 5, OCT, MAR27) + bill("통신", 33_000, 21, JAN, AUG)
        assertEquals("PAID 1/2 45000", short(view(moved, SEP, day("2026-10-06"))))
        for (today in listOf("2026-10-06", "2026-10-25")) {
            assertEquals(today, "PAID 1/1 55000", short(view(moved, OCT, day(today))))
            assertNotEquals(today, NoteTone.WARNING, note(moved, OCT, day(today))?.tone)
        }
        // 등록하기 금액을 새 금액으로 고쳐 10월 5일에 적어도 그 달 몫이다
        val registered = bill("통신", 45_000, 21, JAN, SEP) + bill("통신", 33_000, 21, JAN, AUG) + paid("통신", 55_000, "2026-10-05")
        assertEquals("PAID 1/1 55000", short(view(registered, OCT, day("2026-10-05"))))
        assertNotEquals(NoteTone.WARNING, note(registered, OCT, day("2026-10-22"))?.tone)
        assertEquals("PAID 1/2 45000", short(view(registered, SEP, day("2026-10-05"))))
    }

    @Test
    fun `같은 날 두 회선 금액이 비슷해도 놓친 회선을 다음 달에 늦게 메우면 놓친 회선 금액이라 앞 달 몫이다`() {
        // 25일 45,000원 · 48,000원 자동이체에서 2026년 4월 48,000원 회선이 5월 4일에 늦게 나갔다(5월 25일은 대체공휴일이라 26일)
        val near = bill("통신", 45_000, 25, YearMonth.of(2025, 1), DEC) +
            bill("통신", 48_000, 25, YearMonth.of(2025, 1), DEC).filter { YearMonth.from(it.localDate()) != APR } +
            paid("통신", 48_000, "2026-05-04")
        for (today in listOf("2026-05-04", "2026-05-10", "2026-05-25")) {
            assertEquals(today, "DUE 0/2 93000", short(view(near, MAY, day(today))))
            assertEquals(today, "PAID 2/2 93000", short(view(near, APR, day(today))))
        }
        // 21일 45,000원 · 40,000원에서 2026년 9월 40,000원 회선이 10월 2일에 늦게 나갔다
        val similar = bill("보험", 45_000, 21, YearMonth.of(2025, 1), DEC) +
            bill("보험", 40_000, 21, YearMonth.of(2025, 1), DEC).filter { YearMonth.from(it.localDate()) != SEP } +
            paid("보험", 40_000, "2026-10-02")
        for (today in listOf("2026-10-02", "2026-10-15", "2026-10-20")) {
            assertEquals(today, "DUE 0/2 85000", short(view(similar, OCT, day(today))))
            assertEquals(today, "PAID 2/2 85000", short(view(similar, SEP, day(today))))
        }
    }

    @Test
    fun `날이 따로인 두 차례 금액이 비슷해도 뒤 차례를 해지한 다음 달 앞 차례 결제는 그 달 몫이다`() {
        // 3일 50,000원 · 20일 45,000원(서로 20% 안) 가운데 20일 것을 2026년 9월부터 해지. 10월 3일(토 · 개천절)엔 카드는 그날 나간다.
        for (card in listOf(false, true)) {
            val rows = bill("보험", 50_000, 3, YearMonth.of(2025, 1), DEC, card) + bill("보험", 45_000, 20, YearMonth.of(2025, 1), AUG, card)
            for (today in listOf("2026-10-07", "2026-10-22")) {
                assertEquals("card=$card $today", "PAID 1/1 50000", short(view(rows, OCT, day(today))))
                assertNull("card=$card $today", note(rows, OCT, day(today)))
            }
            assertEquals("card=$card", "PAID 1/2 50000", short(view(rows, SEP, day("2026-10-07"))))
            assertEquals("card=$card", "DUE 0/1 50000", short(view(rows, NOV, day("2026-11-01"))))
            assertEquals("card=$card", "PAID 1/1 50000", short(view(rows, NOV, day("2026-11-22"))))
        }
        // 자동이체를 해지한 다음 달 10월 1일 · 2일에 등록하기로 적어도 그 달 몫이다
        val rows = bill("보험", 50_000, 3, YearMonth.of(2025, 1), SEP) + bill("보험", 45_000, 20, YearMonth.of(2025, 1), AUG)
        for (date in listOf("2026-10-01", "2026-10-02")) {
            val today = day(date)
            val saved = rows + paid("보험", fixedExpensePrefill(view(rows, OCT, today), today).amount, date)
            assertEquals(date, "PAID 1/1 50000", short(view(saved, OCT, today)))
            assertEquals(date, "PAID 1/2 50000", short(view(saved, SEP, today)))
        }
    }

    @Test
    fun `하루 차이 두 청구의 앞 것만 자동이체면 앞 날이 쉬는 날인 달에 뒤 것 카드가 먼저 나가도 그 달 몫이다`() {
        // 30일 자동이체 10,000원 · 말일 카드 20,000원. 2025년 11월 30일(일)엔 카드분이 그날, 자동이체분이 12월 1일에 나간다.
        val shop = bill("통신", 10_000, 30, YearMonth.of(2025, 1), YearMonth.of(2026, 1)) +
            bill("통신", 20_000, LAST_DAY, YearMonth.of(2025, 1), YearMonth.of(2026, 1), card = true)
        for (today in days("2025-12-01", "2025-12-29")) {
            assertEquals("$today", "PAID 2/2 30000", short(view(shop, YearMonth.of(2025, 11), today)))
            assertNull("$today", note(shop, YearMonth.of(2025, 11), today))
            assertEquals("$today", "DUE 0/2 30000", short(view(shop, YearMonth.of(2025, 12), today)))
            assertNull("$today", note(shop, YearMonth.of(2025, 12), today))
        }
        // 14일 자동이체 30,000원 · 15일 카드 9,000원. 2026년 2월 14일(토) 것은 설 연휴 뒤 19일, 15일(일) 카드분은 그날 나간다.
        val insurer = bill("보험", 30_000, 14, YearMonth.of(2025, 1), DEC) + bill("보험", 9_000, 15, YearMonth.of(2025, 1), DEC, card = true)
        for (today in days("2026-02-19", "2026-02-28")) {
            assertEquals("$today", "PAID 2/2 39000", short(view(insurer, FEB, today)))
            assertNull("$today", note(insurer, FEB, today))
        }
        for (today in days("2026-03-01", "2026-03-14")) assertEquals("$today", "DUE 0/2 39000", short(view(insurer, MAR, today)))
        // 5일 자동이체 4,400원 · 6일 카드 10,900원. 2025년 4월 5일(토) 것은 7일, 6일(일) 카드분은 그날 나갔다.
        val apple = bill("애플", 4_400, 5, YearMonth.of(2024, 1), YearMonth.of(2025, 8)) +
            bill("애플", 10_900, 6, YearMonth.of(2024, 1), YearMonth.of(2025, 8), card = true)
        assertEquals("DUE 1/2 10900", short(view(apple, YearMonth.of(2025, 6), day("2025-06-05"))))
        val august = view(apple, YearMonth.of(2025, 8), day("2025-08-05"), morning = true)
        assertEquals(5, august.dueDay)
        assertEquals("오늘 낼 차례예요", rowNote(august, YearMonth.of(2025, 8), day("2025-08-05"))?.text)
    }

    @Test
    fun `금액이 비슷한 1일 · 말일 두 청구가 쉬는 날로 한날 나가면 기준 금액이 가까운 결제가 앞 달 말일 몫이다`() {
        // 1일 30,000원 · 말일 33,000원 자동이체. 2026년 2월 28일(토) 것과 3월 1일(일) 것이 3월 3일(2일 대체공휴일)에 함께 나간다.
        val shop = bill("보험", 30_000, 1, YearMonth.of(2025, 1), YearMonth.of(2026, 4)) +
            bill("보험", 33_000, LAST_DAY, YearMonth.of(2025, 1), YearMonth.of(2026, 4))
        for (today in days("2026-03-03", "2026-03-30")) {
            assertEquals("$today", "DUE 1/2 33000", short(view(shop, MAR, today)))
            assertEquals("$today", "PAID 2/2 63000", short(view(shop, FEB, today)))
            assertNull("$today", note(shop, FEB, today))
        }
        assertEquals("2번 중 1번 냈고, 남은 건 오늘 낼 차례예요", note(shop, MAR, day("2026-03-31"), morning = true)?.text)
        assertEquals("PAID 2/2 63000", short(view(shop, MAR, day("2026-04-10"))))
    }

    @Test
    fun `금액이 비슷한 하루 차이 두 청구의 앞 것을 해지하면 남은 청구를 하루 가까운 앞 차례에 넣지 않아 뒤 차례를 기다린다`() {
        // 카드 5일 10,000원 · 6일 11,000원 가운데 5일 것을 2026년 6월부터 해지. 7월 5일(일) 것의 낼 날이 6일이라 7월 6일 결제는 두 차례와 거리가 같다.
        val sub = bill("구독", 10_000, 5, YearMonth.of(2025, 1), MAY, card = true) +
            bill("구독", 11_000, 6, YearMonth.of(2025, 1), SEP, card = true)
        val august = view(sub, AUG, day("2026-08-05"), morning = true)
        assertEquals(6, august.dueDay)
        assertNull(rowNote(august, AUG, day("2026-08-05")))
        assertEquals("오늘 낼 차례예요", note(sub, AUG, day("2026-08-06"), morning = true)?.text)
    }

    @Test
    fun `두 차례 가게의 한 청구 금액이 바뀌어 다른 청구와 같아지거나 맞바뀌어도 그 결제는 제 차례 몫이다`() {
        // 1일 50,000원 · 15일 30,000원 자동이체에서 2027년 6월부터 15일 것도 50,000원
        val same = bill("보험", 50_000, 1, YearMonth.of(2025, 1), YearMonth.of(2027, 12)) +
            bill("보험", 30_000, 15, YearMonth.of(2025, 1), YearMonth.of(2027, 5)) +
            bill("보험", 50_000, 15, YearMonth.of(2027, 6), YearMonth.of(2027, 12))
        for (today in days("2027-07-01", "2027-07-14")) {
            val item = view(same, YearMonth.of(2027, 7), today)
            assertEquals("$today", "DUE 1/2 50000 15", "${short(item)} ${item.dueDay}")
        }
        assertEquals("PAID 2/2 100000", short(view(same, YearMonth.of(2027, 6), day("2027-07-10"))))
        for (today in days(
            "2027-08-02",
            "2027-08-16",
        )) {
            assertEquals("$today", "DUE 1/2 50000", short(view(same, YearMonth.of(2027, 8), today)))
        }
        // 3일 50,000원 · 20일 30,000원 금액이 2026년 6월부터 맞바뀜(6월 3일 지방선거로 4일, 20일(토)은 22일에 나간다)
        val swapped = bill("보험", 50_000, 3, YearMonth.of(2025, 1), MAY) + bill("보험", 30_000, 20, YearMonth.of(2025, 1), MAY) +
            bill("보험", 30_000, 3, JUN, DEC) + bill("보험", 50_000, 20, JUN, DEC)
        for (today in days("2026-06-04", "2026-06-21")) {
            assertEquals("$today", "DUE 1/2", view(swapped, JUN, today).let { "${it.status} ${it.paidCount}/${it.requiredCount}" })
            assertNotEquals("$today", NoteTone.WARNING, note(swapped, JUN, today)?.tone)
        }
        for (today in days("2026-07-01", "2026-07-02")) assertEquals("$today", FixedStatus.DUE, view(swapped, JUL, today).status)
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
