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

    @Test
    fun `카드 · 자동이체 회선이 섞인 같은 날 가게는 쉬는 날로 벌어진 날을 평소 벌어짐으로 세지 않아 해지한 다음 날 냈어요다`() {
        // 21일 카드 45,000원(그날 나감) · 자동이체 33,000원(쉬는 날이면 다음 영업일). 11월 21일(토)엔 카드분 21일, 자동이체분 23일에 나갔다.
        val december = YearMonth.of(2026, 12)
        val lines = bill("통신사", 45_000, 21, YearMonth.of(2026, 1), december, card = true) +
            bill("통신사", 33_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 11))
        assertEquals("2번 중 1번 냈고, 남은 건 오늘 낼 차례예요", note(lines, december, day("2026-12-21")))
        for (today in (22..24).map { december.atDay(it) }) {
            val item = view(lines, december, today)
            assertEquals("$today", FixedStatus.PAID, item.status)
            assertEquals("$today", "2번 중 1번만 냈어요", rowNote(item, december, today)?.text)
        }
        // 카드 회선을 11월부터 해지하면 11월 21일(토) 것은 자동이체분이 23일에 나가 24일엔 냈어요다
        val november = YearMonth.of(2026, 11)
        val card = bill("통신사", 45_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 10), card = true) +
            bill("통신사", 33_000, 21, YearMonth.of(2026, 1), december)
        assertEquals("2번 중 1번만 냈어요", note(card, november, day("2026-11-24")))
    }

    @Test
    fun `한 번 늦게 메운 회선은 평소 벌어짐이 아니라 해지한 달도 남은 회선이 나간 다음 날 냈어요다`() {
        // 21일 45,000원 · 33,000원 가운데 5월 것 33,000원을 6월 3일에 늦게 냈고, 9월부터 33,000원을 해지했다
        val lines = bill("통신사", 45_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 12)) +
            bill("통신사", 33_000, 21, YearMonth.of(2026, 1), YearMonth.of(2026, 8)).filter { it.localDate() != day("2026-05-21") } +
            paid("통신사", 33_000, "2026-06-03")
        val september = YearMonth.of(2026, 9)
        for (date in listOf("2026-09-22", "2026-09-30", "2026-10-04")) {
            val item = view(lines, september, day(date))
            assertEquals(date, FixedStatus.PAID, item.status)
            assertEquals(date, "2번 중 1번만 냈어요", rowNote(item, september, day(date))?.text)
        }
    }

    @Test
    fun `한 차례로 본 두 청구의 남은 것은 평소 벌어짐이 끝나는 날이 낼 날이라 그날 아침엔 오늘 낼 차례다`() {
        // 자동이체 6일 4,400원 · 7일 10,900원을 2025년 4월부터 냈다. 쉬는 날로 한날 나간 달이 많아 갈린 달이 8월 하나뿐이라 한 차례(6일)로 본다.
        val school = bill("학원", 4_400, 6, YearMonth.of(2025, 4), YearMonth.of(2025, 12)) +
            bill("학원", 10_900, 7, YearMonth.of(2025, 4), YearMonth.of(2025, 12))
        val november = YearMonth.of(2025, 11)
        assertEquals("2번 중 1번 냈고, 남은 건 오늘 낼 차례예요", note(school, november, day("2025-11-07"), morning = true))
        // 카드 1일 4,400원 · 2일 10,900원을 2025년 1월부터 냈다. 2 · 3월은 1일 토요일 · 2일 일요일에 따로 나갔다.
        val apple = bill("애플", 4_400, 1, YearMonth.of(2025, 1), YearMonth.of(2025, 6), card = true) +
            bill("애플", 10_900, 2, YearMonth.of(2025, 1), YearMonth.of(2025, 6), card = true)
        assertEquals("2번 중 1번 냈고, 남은 건 오늘 낼 차례예요", note(apple, YearMonth.of(2025, 4), day("2025-04-02"), morning = true))
        // 2달마다(홀수 달) 10일 · 12일 두 회선은 10일 것만 낸 10 · 11일에 지났다고 하지 않는다
        val purifier = bill("정수기", 45_000, 10, YearMonth.of(2025, 1), YearMonth.of(2026, 11), card = true, step = 2) +
            bill("정수기", 33_000, 12, YearMonth.of(2025, 1), YearMonth.of(2026, 11), card = true, step = 2)
        val july = YearMonth.of(2026, 7)
        for (today in listOf(day("2026-07-10"), day("2026-07-11"))) {
            assertEquals("$today", "2번 중 1번 냈어요", note(purifier, july, today))
        }
        assertEquals("2번 중 1번 냈고, 남은 건 오늘 낼 차례예요", note(purifier, july, day("2026-07-12"), morning = true))
        assertEquals(FixedStatus.PAID, view(purifier, july, day("2026-07-12")).status)
    }

    @Test
    fun `하루 차이 두 청구는 쉬는 날로 낼 날이 겹친 달에도 카드가 따로 나갔으면 두 때로 세고 다음 달 초로 밀린 말일 것은 그 달 몫으로 센다`() {
        // 카드 1일 4,400원 · 2일 10,900원을 2025년 1월부터 냈다. 2 · 3월은 1일이 토요일이라 두 낼 날이 같지만 카드는 따로 나갔다.
        val apple = bill("애플", 4_400, 1, YearMonth.of(2025, 1), YearMonth.of(2025, 6), card = true) +
            bill("애플", 10_900, 2, YearMonth.of(2025, 1), YearMonth.of(2025, 6), card = true)
        val april = view(apple, YearMonth.of(2025, 4), day("2025-04-02"), morning = true)
        assertEquals(listOf(1, 2), april.usualDays)
        assertEquals(2, april.dueDay)
        // 자동이체 30일 10,000원 · 31일 20,000원을 2024년부터 냈다. 31일 것이 쉬는 날로 다음 달 초에 밀려도 그 달 몫이라 두 차례다.
        val savings = bill("적금", 10_000, 30, YearMonth.of(2024, 1), YearMonth.of(2025, 12)) +
            bill("적금", 20_000, LAST_DAY, YearMonth.of(2024, 1), YearMonth.of(2025, 12))
        val today = day("2025-07-30")
        val july = view(savings, YearMonth.of(2025, 7), today, morning = true)
        assertEquals(listOf(30, LAST_DAY), july.usualDays)
        assertEquals("오늘 낼 차례예요", rowNote(july, YearMonth.of(2025, 7), today)?.text)
    }

    @Test
    fun `쉬는 날 앞 영업일에 미리 빼 가는 것은 낼 날이 다음 달로 넘어가는 차례만이라 26일 자동이체를 뒤 날로 보지 않는다`() {
        // 26일 자동이체를 2027년 11월부터 냈다. 26일이 쉬는 날이면 다음 영업일(27 · 28일)에 나간다.
        val rows = bill("구독", 30_000, 26, YearMonth.of(2027, 11), YearMonth.of(2028, 12))
        for (date in listOf("2028-03-27", "2028-04-26")) {
            val today = day(date)
            val item = view(rows, YearMonth.from(today), today, morning = true)
            assertEquals(date, listOf(26), item.usualDays)
            assertEquals(date, "오늘 낼 차례예요", rowNote(item, YearMonth.from(today), today)?.text)
        }
        // 1일 300,000원 · 2일 200,000원 자동이체 월세는 연휴가 몰린 해에도 두 차례(1일 · 2일)라 1일 아침에 낼 차례를 알린다
        val rent = bill("월세", 300_000, 1, YearMonth.of(2024, 1), YearMonth.of(2025, 12)) +
            bill("월세", 200_000, 2, YearMonth.of(2024, 1), YearMonth.of(2025, 12))
        val july = view(rent, YearMonth.of(2025, 7), day("2025-07-01"), morning = true)
        assertEquals(listOf(1, 2), july.usualDays)
        assertEquals("오늘 낼 차례예요", rowNote(july, YearMonth.of(2025, 7), day("2025-07-01"))?.text)
    }

    @Test
    fun `월말 두 청구 새 가게는 첫 결제 달 앞을 빈 달로 세지 않아 다음 달 초로 밀린 두 건이 밀린 달 몫이다`() {
        // 자동이체 30일 4,400원 · 31일 10,900원을 2025년 12월부터 냈다. 2026년 2월 몫 두 건은 2월 28일(토) · 3월 2일(대체공휴일)을 지나 3월 3일에 나갔다.
        val rows = bill("구독", 4_400, 30, YearMonth.of(2025, 12), YearMonth.of(2026, 6)) +
            bill("구독", 10_900, LAST_DAY, YearMonth.of(2025, 12), YearMonth.of(2026, 6))
        val today = day("2026-03-15")
        assertEquals(FixedStatus.DUE, view(rows, YearMonth.of(2026, 3), today).status)
        val february = view(rows, YearMonth.of(2026, 2), today)
        assertEquals(FixedStatus.PAID, february.status)
        assertEquals(2, february.paidCount)
        assertEquals(15_300L, february.amount)
        assertNull(rowNote(february, YearMonth.of(2026, 2), today))
        // 28일 · 29일(2026년 1월부터)도 3월 몫이 3월 30일에 밀려 나가도 4월 28일 저녁에 4월은 아직 다 안 냈다
        val late = bill("구독2", 4_400, 28, YearMonth.of(2026, 1), YearMonth.of(2026, 8)) +
            bill("구독2", 10_900, 29, YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        assertEquals(FixedStatus.DUE, view(late, YearMonth.of(2026, 4), day("2026-04-28")).status)
    }

    @Test
    fun `같은 날 두 회선 가운데 하나를 가끔 미리 내도 평소 벌어짐이 아니라 다른 회선을 해지한 다음 날 냈어요다`() {
        // 1일 월세 500,000원(3 · 6 · 9 · 12월 몫은 전달 마지막 영업일에 미리 냄) · 관리비 100,000원 가운데 관리비를 2028년 11월부터 해지했다
        val rows = generateSequence(YearMonth.of(2027, 1)) {
            it.plusMonths(1)
        }.takeWhile { it <= YearMonth.of(2029, 3) }.toList().flatMap { month ->
            val due = KoreanCalendar.nextBusinessDay(month.atDay(1))
            val rent = if (month.monthValue % 3 == 0) KoreanCalendar.previousBusinessDay(month.atDay(1).minusDays(1)) else due
            paid("집", 500_000, rent.toString()) + if (month < YearMonth.of(2028, 11)) paid("집", 100_000, due.toString()) else emptyList()
        }
        val november = YearMonth.of(2028, 11)
        assertEquals("2번 중 1번만 냈어요", note(rows, november, day("2028-11-02")))
    }

    @Test
    fun `뒤 차례를 해지한 다음 달에 남은 앞 차례도 안 내면 그 달을 안 낸 것이고 지난 날 수는 건수가 있는 앞 차례부터 센다`() {
        // 3일 50,000원(1~10월) · 20일 30,000원(1~8월). 20일 것을 9월부터 해지했고 11월엔 3일 것도 안 냈다.
        val rows = bill("보험", 50_000, 3, YearMonth.of(2026, 1), YearMonth.of(2026, 10)) +
            bill("보험", 30_000, 20, YearMonth.of(2026, 1), YearMonth.of(2026, 8))
        val november = YearMonth.of(2026, 11)
        val item = view(rows, november, day("2026-11-07"))
        assertEquals(FixedStatus.DUE, item.status)
        assertEquals(3, item.dueDay)
        assertEquals("평소보다 4일 지났어요", rowNote(item, november, day("2026-11-07"))?.text)
    }
}
