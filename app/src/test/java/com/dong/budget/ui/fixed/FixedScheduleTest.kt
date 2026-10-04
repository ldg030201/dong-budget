package com.dong.budget.ui.fixed

import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** 일정 짐작([estimateSchedule])과 짝짓기([matchPayments])를 따로 본다. 화면에 보이는 결과는 [FixedExpensesTest] 가 본다. */
class FixedScheduleTest {
    private fun pays(amount: Long, vararg dates: String) =
        dates.map { tx(it, amount, categoryId = 4, merchant = "가") }.map { Paid(it, it.localDate()) }

    /** 매달 [day] 일(그 달에 없으면 말일)이 쉬는 날이면 다음 영업일에 나간 결제들 */
    private fun monthly(amount: Long, day: Int, from: YearMonth, to: YearMonth) = generateSequence(from) { it.plusMonths(1) }
        .takeWhile { it <= to }
        .map { KoreanCalendar.nextBusinessDay(usualDateOf(it, day)).toString() }
        .toList()
        .let { pays(amount, *it.toTypedArray()) }

    private fun schedule(pays: List<Paid>, base: YearMonth) = estimateSchedule(pays, base, DueDates())

    @Test
    fun `쉬는 날로 밀린 결제는 낼 날에 딱 맞아 평소 날이 늦은 쪽으로 치우치지 않는다`() {
        // 2025년 4월 · 5월 · 7월 · 9월 · 10월 25일 자동이체는 주말 · 공휴일로 밀렸다(리뷰 c6)
        val item = schedule(monthly(17_000, 25, YearMonth.of(2025, 4), YearMonth.of(2026, 9)), YearMonth.of(2026, 10))
        assertEquals(listOf(25), item.days)
        assertEquals(1, item.cadence)
        // 매달 30일 적금은 31일로 밀린 달이 있어도 30일이다(말일이 아니다)
        assertEquals(listOf(30), schedule(monthly(50_000, 30, YearMonth.of(2025, 1), YearMonth.of(2026, 9)), YearMonth.of(2026, 10)).days)
        assertEquals(
            listOf(LAST_DAY),
            schedule(monthly(120_000, 31, YearMonth.of(2025, 1), YearMonth.of(2026, 9)), YearMonth.of(2026, 10)).days,
        )
    }

    @Test
    fun `한 달에 두 때에 내면 두 차례이고 차례마다 평소 날과 금액이 있다`() {
        val three = monthly(50_000, 3, YearMonth.of(2026, 1), YearMonth.of(2026, 9))
        val twentyEight = monthly(30_000, 28, YearMonth.of(2026, 1), YearMonth.of(2026, 9))
        val item = schedule(three + twentyEight, YearMonth.of(2026, 10))
        assertEquals(listOf(3, 28), item.days)
        assertEquals(listOf(50_000L, 30_000L), item.slotAmounts)
        assertEquals(1, item.slotByAmount(30_000))
        // 9월 3일 것 하나를 빠뜨려도 두 차례 그대로다(리뷰 c36: 섞은 가운데 값으로 28일이 되었다)
        val missing = (three + twentyEight).filter { it.date != day("2026-09-03") }
        assertEquals(listOf(3, 28), schedule(missing, YearMonth.of(2026, 10)).days)
    }

    @Test
    fun `말일 것이 밀려 한 달력 달에 두 번 낸 달이 생기거나 가끔 늦게 내도 한 차례다`() {
        // 2030년엔 3월 · 6월 · 8월 말일이 주말이라 4월 · 7월 · 9월에 두 번씩 나갔다
        assertEquals(
            listOf(LAST_DAY),
            schedule(monthly(150_000, 31, YearMonth.of(2029, 1), YearMonth.of(2030, 9)), YearMonth.of(2030, 10)).days,
        )
        // 석 달에 한 번 이틀 늦게(그 뒤 영업일) 낸 말일 관리비
        val late = generateSequence(YearMonth.of(2028, 1)) {
            it.plusMonths(1)
        }.takeWhile { it <= YearMonth.of(2029, 3) }.mapIndexed { i, month ->
            val due = KoreanCalendar.nextBusinessDay(month.atEndOfMonth())
            (if (i % 3 == 1) KoreanCalendar.nextBusinessDay(due.plusDays(2)) else due).toString()
        }.toList()
        assertEquals(listOf(LAST_DAY), schedule(pays(130_000, *late.toTypedArray()), YearMonth.of(2029, 4)).days)
    }

    @Test
    fun `몇 달마다 내면 차례 달이 정해지고 달 경계를 넘어 밀린 결제도 그 달 차례다`() {
        // 홀수 달 말일에 내고 9월 몫이 10월 1일에 나갔다
        val item = schedule(pays(30_000, "2026-03-31", "2026-05-31", "2026-07-31", "2026-10-01"), YearMonth.of(2026, 10))
        assertEquals(2, item.cadence)
        assertTrue(item.isSlotMonth(YearMonth.of(2026, 9)))
        assertFalse(item.isSlotMonth(YearMonth.of(2026, 10)))
    }

    @Test
    fun `금액이 동떨어진 결제는 따로 낸 것이지만 두 차례 금액이 크게 달라도 따로 낸 것이 아니다`() {
        // 애플 3,300원 사이의 연간 99,000원(리뷰 c5)
        val annual = pays(99_000, "2026-08-20")
        val apple = (pays(3_300, "2026-05-05", "2026-06-05", "2026-07-05", "2026-08-05") + annual).sortedWith(paidByTime)
        assertEquals(setOf(annual.single().id), extrasOf(apple))
        // 같은 가게 이름의 월세 500,000원 · 관리비 100,000원은 둘 다 평소 결제다
        val home = pays(500_000, "2026-06-01", "2026-07-01", "2026-08-01") + pays(100_000, "2026-06-25", "2026-07-25", "2026-08-25")
        assertEquals(emptySet<Long>(), extrasOf(home.sortedWith(paidByTime)))
    }

    @Test
    fun `결제를 차례에 짝지을 때 빈 차례를 메우고 이미 찬 차례에는 넘치게 넣지 않는다`() {
        // 말일 관리비가 9월을 놓쳐 10월 2일에 두 건을 냈다(rr-fixed-5). 하나는 9월, 하나는 10월 몫이다.
        val bill = pays(120_000, "2026-06-30", "2026-07-31", "2026-08-31", "2026-10-02", "2026-10-02")
        val dues = DueDates()
        val item = estimateSchedule(bill, YearMonth.of(2026, 10), dues)
        val matched = matchPayments(bill, item, MonthCounts(bill, item.days, dues), YearMonth.of(2026, 10), dues, emptySet())
        assertEquals(listOf(6, 7, 8, 9, 10), bill.map { matched.slotOf.getValue(it.id).month.monthValue }.sorted())
        // 25일 넷플릭스를 9월에 냈는데 10월 2일에 또 냈다(등록하기로 오늘 날짜에 적음, 리뷰 c1). 이미 찬 9월이 아니라 10월 몫이다.
        val netflix = pays(17_000, "2026-08-25", "2026-09-25", "2026-10-02")
        val schedule = estimateSchedule(netflix, YearMonth.of(2026, 10), dues)
        val second = matchPayments(netflix, schedule, MonthCounts(netflix, schedule.days, dues), YearMonth.of(2026, 10), dues, emptySet())
        assertEquals(YearMonth.of(2026, 10), second.slotOf.getValue(netflix.last().id).month)
    }
}
