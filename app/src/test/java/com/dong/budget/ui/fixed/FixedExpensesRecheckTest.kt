package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.editor.fixedExpensePrefill
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `같은 날 회선 하나를 해지한 뒤 다음 달 몫을 일찍 내면 해지한 달의 빈 회선이 아니라 다음 달 몫이다`() {
        // 1일 월세 500,000원 · 관리비 100,000원(같은 날 자동이체) 가운데 관리비를 9월부터 해지하고, 10월 월세를 9월 30일에 미리 냈다
        val september = YearMonth.of(2026, 9)
        val october = YearMonth.of(2026, 10)
        val house =
            bill("집", 500_000, 1, YearMonth.of(2026, 1), september) + bill("집", 100_000, 1, YearMonth.of(2026, 1), YearMonth.of(2026, 8)) +
                paid("집", 500_000, "2026-09-30") + bill("집", 500_000, 1, YearMonth.of(2026, 11), YearMonth.of(2027, 2))
        val before = view(house, september, day("2026-10-02"))
        assertEquals(FixedStatus.PAID, before.status)
        assertEquals(500_000L, before.amount)
        assertEquals("2번 중 1번만 냈어요", rowNote(before, september, day("2026-10-02"))?.text)
        for (today in listOf(day("2026-10-02"), day("2026-10-15"))) {
            val item = view(house, october, today)
            assertEquals("$today", FixedStatus.PAID, item.status)
            assertEquals("$today", 500_000L, item.amount)
            assertEquals("$today", 1, item.requiredCount)
        }
        // 1일 월세 · 20일 관리비(날이 따로)에서 20일 것을 해지해도 같다(미리 낸 월세는 금액이 다른 20일 차례를 메우지 않는다)
        val apart =
            bill("집", 500_000, 1, YearMonth.of(2026, 1), september) + bill("집", 100_000, 20, YearMonth.of(2026, 1), YearMonth.of(2026, 8)) +
                paid("집", 500_000, "2026-09-30")
        assertEquals("2번 중 1번만 냈어요", note(apart, september, day("2026-10-02")))
        assertEquals(FixedStatus.PAID, view(apart, october, day("2026-10-02")).status)
        assertEquals(FixedStatus.PAID, view(apart, october, day("2026-10-15")).status)
        // 해지한 뒤 남은 회선의 결제일이 5일로 바뀌어도 10월 5일 결제는 10월 몫이다
        val moved = bill("통신사", 45_000, 21, YearMonth.of(2026, 1), september) + bill("통신사", 45_000, 5, october, YearMonth.of(2027, 2)) +
            bill("통신사", 33_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        assertEquals(FixedStatus.PAID, view(moved, october, day("2026-10-25")).status)
        val november = view(moved, YearMonth.of(2026, 11), day("2026-11-04"))
        assertEquals(1, november.requiredCount)
        assertNull(november.missedMonth)
    }

    @Test
    fun `같은 날 회선 하나를 해지한 다음 달에 남은 회선을 일찍 등록해도 그 달 몫이고 둘 다 놓쳐 함께 늦게 내면 앞 달 몫이다`() {
        // 21일 45,000원 · 33,000원 두 회선 가운데 33,000원을 9월부터 해지했다. 10월 화면의 등록하기로 남은 회선을 그날 적는다.
        val september = YearMonth.of(2026, 9)
        val october = YearMonth.of(2026, 10)
        val lines =
            bill("통신사", 45_000, 21, YearMonth.of(2026, 1), september) +
                bill("통신사", 33_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        for (date in listOf("2026-10-02", "2026-10-05", "2026-10-12")) {
            val today = day(date)
            val due = view(lines, october, today)
            assertEquals(date, FixedStatus.DUE, due.status)
            assertEquals(date, 45_000L, due.amount)
            val prefill = fixedExpensePrefill(due, today)
            assertEquals(date, 45_000L, prefill.amount)
            val saved = lines + paid("통신사", prefill.amount, date)
            val after = view(saved, october, today)
            assertEquals(date, FixedStatus.PAID, after.status)
            assertEquals(date, 1, after.requiredCount)
            assertEquals(date, 45_000L, after.amount)
            assertEquals(date, "2번 중 1번만 냈어요", note(saved, september, today))
        }
        // 두 회선을 9월에 모두 놓쳐 10월 2일에 함께 냈으면 둘 다 9월 몫이고 10월은 아직 두 건을 기다린다
        val both = bill("통신사", 45_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 8)) +
            bill("통신사", 33_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 8)) + paid("통신사", 45_000, "2026-10-02") +
            paid("통신사", 33_000, "2026-10-02")
        val lateSeptember = view(both, september, day("2026-10-03"))
        assertEquals(FixedStatus.PAID, lateSeptember.status)
        assertEquals(2, lateSeptember.paidCount)
        val waiting = view(both, october, day("2026-10-03"))
        assertEquals(FixedStatus.DUE, waiting.status)
        assertEquals(2, waiting.requiredCount)
    }

    @Test
    fun `앞 차례를 해지하고 남은 차례를 카드로 쉬는 날에 내면 그 결제는 남은 차례 몫이라 다음 달도 남은 차례만 기다린다`() {
        // 4일 50,000원 · 11일 30,000원 카드 가운데 4일 것을 2027년 4월부터 해지했다. 4월 11일(일)에 30,000원이 나갔다(자동이체라면 12일).
        val rows = bill("보험", 50_000, 4, YearMonth.of(2025, 1), YearMonth.of(2027, 3), card = true) +
            bill("보험", 30_000, 11, YearMonth.of(2025, 1), YearMonth.of(2027, 12), card = true)
        val april = YearMonth.of(2027, 4)
        for (date in listOf("2027-04-11", "2027-04-13", "2027-04-15")) {
            val item = view(rows, april, day(date))
            assertEquals(date, FixedStatus.PAID, item.status)
            assertEquals(date, "2번 중 1번만 냈어요", rowNote(item, april, day(date))?.text)
        }
        val may = YearMonth.of(2027, 5)
        for (today in (4..10).map { may.atDay(it) }) {
            val item = view(rows, may, today)
            assertEquals("$today", 11, item.dueDay)
            assertEquals("$today", 1, item.requiredCount)
            assertTrue("$today", (item.daysPastUsual ?: 0) <= 0)
        }
    }

    @Test
    fun `30일 · 말일 두 청구에서 30일 것을 해지하면 30일 달의 남은 결제도 금액으로 말일 차례라 31일 달 말일 전에는 지났다고 하지 않는다`() {
        // 30일 50,000원 · 말일 30,000원 가운데 30일 것을 2027년 4월부터 해지했다. 30일 달에는 두 차례가 같은 날이라 금액으로만 가른다.
        for (card in listOf(false, true)) {
            val rows = bill("보험", 50_000, 30, YearMonth.of(2025, 1), YearMonth.of(2027, 3), card) +
                bill("보험", 30_000, LAST_DAY, YearMonth.of(2025, 1), YearMonth.of(2027, 12), card)
            val july = YearMonth.of(2027, 7)
            for (today in listOf(day("2027-07-30"), day("2027-07-31"))) {
                val item = view(rows, july, today, morning = true)
                assertEquals("$card $today", LAST_DAY, item.dueDay)
                assertTrue("$card $today", (item.daysPastUsual ?: 0) <= 0)
            }
        }
    }
}
