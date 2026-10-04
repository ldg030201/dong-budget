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

/** 최대 강도 리뷰(2026-10-04)가 찾은 고정지출 계산 결함의 회귀 테스트. 읽는 범위와 오늘은 화면 모델처럼 준다. */
class FixedExpensesReviewTest {
    /** 화면 모델처럼 [month] 를 [today] 에 볼 때 읽는 행으로 계산한 그 가게 하나 */
    private fun view(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate): FixedExpenseItem {
        val read = rows.filter {
            !it.localDate().isAfter(today) && YearMonth.from(it.localDate()) in fixedHistoryStart(month)..fixedHistoryEnd(month, today)
        }
        val board = buildFixedExpenses(month, today, read)
        return (board.due + board.paid + board.notThisMonth + board.stopped).single()
    }

    private fun paid(merchant: String, amount: Long, vararg dates: String) =
        dates.map { tx(it, amount, categoryId = 4, paymentId = 10, merchant = merchant) }

    /** [from] ~ [to] 달마다 [day] 일(없으면 말일, 쉬는 날이면 다음 영업일)에 [amount] 를 낸 기록 */
    private fun monthly(merchant: String, amount: Long, day: Int, from: YearMonth, to: YearMonth) =
        generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.toList().flatMap {
            paid(merchant, amount, KoreanCalendar.nextBusinessDay(usualDateOf(it, day)).toString())
        }

    @Test
    fun `c2 - 금액이 철마다 크게 바뀌는 말일 자동이체도 주말로 밀리면 앞 달 몫이다`() {
        val amounts = listOf(150_000L, 150_000, 150_000, 170_000, 200_000, 260_000, 250_000, 180_000, 160_000, 250_000)
        val rows = (3..12).flatMap { m -> monthly("관리비", amounts[m - 3], LAST_DAY, YearMonth.of(2026, m), YearMonth.of(2026, m)) } +
            paid("관리비", 280_000, "2027-02-01") + paid("관리비", 270_000, "2027-03-02")
        val today = day("2027-03-03")
        assertEquals(FixedStatus.PAID, view(rows, YearMonth.of(2027, 1), today).status)
        assertEquals(day("2027-03-02"), view(rows, YearMonth.of(2027, 2), today).lastPaidOn)
        val march = view(rows, YearMonth.of(2027, 3), today)
        assertEquals(FixedStatus.DUE, march.status)
        assertEquals(270_000L, march.amount)
    }

    @Test
    fun `c3 - 지난 달 몫을 다음 달 16일 뒤에 냈어도 지난 달 화면이 그 결제를 센다`() {
        // 2028년 9월 30일(토) 몫은 추석으로 10월 6일이 낼 날인데 10월 16일에 냈다
        val rows = monthly("관리비", 120_000, LAST_DAY, YearMonth.of(2028, 3), YearMonth.of(2028, 8)) + paid("관리비", 120_000, "2028-10-16")
        val today = day("2028-10-20")
        val september = view(rows, YearMonth.of(2028, 9), today)
        assertEquals(FixedStatus.PAID, september.status)
        assertEquals(day("2028-10-16"), september.lastPaidOn)
        val october = view(rows, YearMonth.of(2028, 10), today)
        assertEquals(FixedStatus.DUE, october.status)
        assertNull(october.missedMonth)
        // 9월 몫을 이미 9월 30일에 냈으면 10월 16일 것은 10월 몫이고 11월 낼 돈은 두 배가 아니다
        val twice = monthly("관리비", 120_000, LAST_DAY, YearMonth.of(2028, 3), YearMonth.of(2028, 9)) + paid("관리비", 120_000, "2028-10-16")
        assertEquals(FixedStatus.PAID, view(twice, YearMonth.of(2028, 10), today).status)
        assertEquals(120_000L, view(twice, YearMonth.of(2028, 11), day("2028-11-03")).amount)
    }

    @Test
    fun `c4 - 같은 날 본 지난 달과 이번 달 화면이 한 결제를 함께 세지 않는다`() {
        // 말일 관리비가 3~6월 100,000원, 7~9월 130,000원이고 10월 31일(토) 몫이 11월 2일에 100,000원으로 나갔다
        val rows = monthly("관리비", 100_000, LAST_DAY, YearMonth.of(2026, 3), YearMonth.of(2026, 6)) +
            monthly("관리비", 130_000, LAST_DAY, YearMonth.of(2026, 7), YearMonth.of(2026, 9)) +
            paid("관리비", 100_000, "2026-11-02") + paid("관리비", 130_000, "2026-11-30")
        val today = day("2026-12-01")
        val october = view(rows, YearMonth.of(2026, 10), today)
        assertEquals(day("2026-11-02"), october.lastPaidOn)
        val november = view(rows, YearMonth.of(2026, 11), today)
        assertEquals(1, november.lastPaidCount)
        assertEquals(130_000L, november.amount)
        assertEquals(130_000L, view(rows, YearMonth.of(2026, 12), today).amount)
    }

    @Test
    fun `c5 - 연간 결제는 그 달 낸 돈에는 들지만 다음 낼 돈에는 들지 않는다`() {
        val rows = paid("애플", 3_300, "2026-04-06", "2026-05-06", "2026-06-05", "2026-07-06", "2026-08-05", "2026-09-07") +
            paid("애플", 99_000, "2026-09-20")
        val today = day("2026-10-04")
        val october = view(rows, YearMonth.of(2026, 10), today)
        assertEquals(FixedStatus.DUE, october.status)
        assertEquals(3_300L, october.amount)
        val september = view(rows, YearMonth.of(2026, 9), today)
        assertEquals(FixedStatus.PAID, september.status)
        assertEquals(102_300L, september.amount)
        assertEquals(2, september.lastPaidCount)
        assertNull(september.previousAmount)
    }

    @Test
    fun `s1 - 한 달에 두 차례 내는 가게가 한 차례만 내면 냈어요가 아니고 남은 차례를 알린다`() {
        val insurance = monthly("보험", 50_000, 3, YearMonth.of(2026, 4), YearMonth.of(2026, 9)) +
            monthly("보험", 30_000, 28, YearMonth.of(2026, 4), YearMonth.of(2026, 9))
        val rows = insurance + paid("보험", 50_000, "2026-10-05")
        val october30 = view(rows, YearMonth.of(2026, 10), day("2026-10-30"))
        assertEquals(FixedStatus.DUE, october30.status)
        assertEquals(1, october30.paidCount)
        assertEquals(2, october30.requiredCount)
        assertEquals(30_000L, october30.amount)
        assertEquals(50_000L, october30.paidAmount)
        assertEquals(28, october30.dueDay)
        assertEquals(2, october30.daysPastUsual)
        // 28일 것의 낼 날 뒤 사흘이 지나면 28일 것을 해지한 것과 가를 수 없어 안 나간 것으로 보아 지난 달 화면은 낸 것만으로 냈어요다.
        // 다음 달 낼 돈은 10월에 낸 한 차례다(해지한 다음 달처럼 10월 건수만큼 낸 가장 최근 달인 10월이 기준이다).
        val november10 = day("2026-11-10")
        val ended = view(rows, YearMonth.of(2026, 10), november10)
        assertEquals(FixedStatus.PAID, ended.status)
        assertEquals(listOf(28), ended.droppedDays)
        assertEquals(50_000L, view(rows, YearMonth.of(2026, 11), november10).amount)
        // 3일 것을 빠뜨리고 28일 것만 냈으면 3일 것의 낼 날(6일) 뒤 사흘이 지난 뒤라 낸 것만으로 냈어요다. 평소 날은 3일 그대로다.
        val late = view(insurance + paid("보험", 30_000, "2026-10-28"), YearMonth.of(2026, 10), day("2026-10-30"))
        assertEquals(FixedStatus.PAID, late.status)
        assertEquals(30_000L, late.amount)
        assertEquals(3, late.usualDay)
        assertEquals(listOf(3), late.droppedDays)
    }

    @Test
    fun `s2 - 결제가 드문 가게도 같은 날 본 지난 달과 이번 달 화면의 평소 날이 같다`() {
        val rows = paid("도메인", 22_000, "2024-06-17", "2025-06-16", "2026-06-15")
        val today = day("2026-10-04")
        val inOctober = view(rows, YearMonth.of(2026, 10), today)
        val inSeptember = view(rows, YearMonth.of(2026, 9), today)
        assertEquals(inOctober.usualDay, inSeptember.usualDay)
        assertEquals(scheduleText(inOctober), scheduleText(inSeptember))
    }

    @Test
    fun `c35 - 같은 날 청구되는 새 회선은 밀린 몫이 아니라 그 달 합을 낼 돈으로 본다`() {
        val rows = monthly("통신비", 50_000, 10, YearMonth.of(2026, 4), YearMonth.of(2026, 9)) +
            monthly("통신비", 48_000, 10, YearMonth.of(2026, 7), YearMonth.of(2026, 9))
        listOf(YearMonth.of(2026, 8) to day("2026-08-05"), YearMonth.of(2026, 10) to day("2026-10-05")).forEach { (month, today) ->
            val item = view(rows.filter { !it.localDate().isAfter(today) }, month, today)
            assertEquals(month.toString(), FixedStatus.DUE, item.status)
            assertEquals(month.toString(), 98_000L, item.amount)
        }
    }

    @Test
    fun `c36 - 두 차례 가게가 이른 차례 하나를 빠뜨려도 평소 날은 이른 차례이고 그 달 낼 날이 지나면 알린다`() {
        val rows = monthly("보험", 50_000, 3, YearMonth.of(2026, 1), YearMonth.of(2026, 9)).filter { it.localDate() != day("2026-09-03") } +
            monthly("보험", 30_000, 28, YearMonth.of(2026, 1), YearMonth.of(2026, 9))
        assertEquals(1, view(rows, YearMonth.of(2026, 9), day("2026-09-04")).daysPastUsual)
        val october10 = view(rows, YearMonth.of(2026, 10), day("2026-10-10"))
        assertEquals(3, october10.usualDay)
        assertEquals(listOf(3, 28), october10.usualDays)
        // 3일 것을 해지한 것과 9월만으로는 가를 수 없어 10월은 해지한 다음 달처럼 9월에 낸 28일 것 한 건을 기다린다
        assertEquals(1, october10.requiredCount)
        assertEquals(28, october10.dueDay)
        assertEquals(30_000L, october10.amount)
        // 9월은 28일 것만 냈는데 3일 것의 낼 날 뒤 사흘이 지났으니 3일 것은 안 나간 것으로 보아 9월 화면은 낸 것만으로 냈어요다
        val september = view(rows, YearMonth.of(2026, 9), day("2026-10-10"))
        assertEquals(FixedStatus.PAID, september.status)
        assertEquals(1, september.paidCount)
        assertEquals(2, september.requiredCount)
    }

    @Test
    fun `c37 - 금액이 비슷한 두 차례 가게의 28일 것이 3일 것과 한날 나가도 앞 달 몫과 이번 달 몫으로 나눈다`() {
        // 2026년 2월 28일(토) 것이 3월 1일 · 2일(대체공휴일)을 지나 3월 3일에 3일 것과 함께 나갔다
        val rows = paid("보험", 33_000, "2025-12-03", "2026-01-05", "2026-02-03") + paid("보험", 30_000, "2025-12-29", "2026-01-28") +
            paid("보험", 30_000, "2026-03-03") + paid("보험", 33_000, "2026-03-03") + paid("보험", 30_000, "2026-03-30")
        val march = YearMonth.of(2026, 3)
        val march20 = view(rows, march, day("2026-03-20"))
        assertEquals(FixedStatus.DUE, march20.status)
        assertEquals(1, march20.paidCount)
        val february = YearMonth.of(2026, 2)
        listOf("2026-03-20", "2026-04-01").forEach { date ->
            val item = view(rows, february, day(date))
            assertEquals(date, FixedStatus.PAID, item.status)
            assertEquals(date, 63_000L, item.amount)
        }
        val april1 = view(rows, march, day("2026-04-01"))
        assertEquals(FixedStatus.PAID, april1.status)
        assertEquals(2, april1.lastPaidCount)
        assertEquals(63_000L, april1.amount)
    }
}
