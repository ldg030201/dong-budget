package com.dong.budget.ui.card

import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionType.EXPENSE
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class PerformancePeriodsTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `시작일이 1일이면 달력 달과 같다`() {
        val period = performancePeriod(october, 1)
        assertEquals(day("2026-10-01"), period.start)
        assertEquals(day("2026-11-01"), period.end)
        assertEquals(day("2026-10-31"), period.lastDay)
        assertEquals(october, period.month)
    }

    @Test
    fun `시작일이 15일이면 그달 15일부터 다음 달 14일까지를 그달 실적으로 센다`() {
        val period = performancePeriod(october, 15)
        assertEquals(day("2026-10-15"), period.start)
        assertEquals(day("2026-11-14"), period.lastDay)
    }

    @Test
    fun `그 달에 없는 시작일은 말일부터 센다`() {
        // 31일: 1월은 31일, 2월은 28일, 4월은 30일부터
        assertEquals(day("2026-01-31"), performancePeriod(YearMonth.of(2026, 1), 31).start)
        assertEquals(day("2026-02-27"), performancePeriod(YearMonth.of(2026, 1), 31).lastDay)
        assertEquals(day("2026-02-28"), performancePeriod(YearMonth.of(2026, 2), 31).start)
        assertEquals(day("2026-03-30"), performancePeriod(YearMonth.of(2026, 2), 31).lastDay)
        assertEquals(day("2026-04-30"), performancePeriod(YearMonth.of(2026, 4), 31).start)
        // 윤년 2월은 29일
        assertEquals(day("2028-02-29"), performancePeriod(YearMonth.of(2028, 2), 31).start)
        // 30일: 2월만 말일(28일)이고 3월은 다시 30일
        val february = performancePeriod(YearMonth.of(2026, 2), 30)
        assertEquals(day("2026-02-28"), february.start)
        assertEquals(day("2026-03-30"), february.end)
    }

    @Test
    fun `기간 사이에 틈도 겹침도 없다`() {
        (1..31).forEach { startDay ->
            var month = YearMonth.of(2027, 1)
            repeat(26) {
                assertEquals(
                    "$startDay 일, $month",
                    performancePeriod(month, startDay).end,
                    performancePeriod(month.plusMonths(1), startDay).start,
                )
                month = month.plusMonths(1)
            }
        }
    }

    @Test
    fun `오늘이 그 달 시작일 전이면 지난달 기간이다`() {
        assertEquals(YearMonth.of(2026, 9), periodMonthOf(day("2026-10-14"), 15))
        assertEquals(october, periodMonthOf(day("2026-10-15"), 15))
        assertEquals(october, periodMonthOf(day("2026-10-01"), 1))
        assertEquals(october, periodMonthOf(day("2026-10-31"), 1))
        // 31일 시작: 2월 27일은 1월 실적, 말일인 28일부터 2월 실적
        assertEquals(YearMonth.of(2026, 1), periodMonthOf(day("2026-02-27"), 31))
        assertEquals(YearMonth.of(2026, 2), periodMonthOf(day("2026-02-28"), 31))
        assertEquals(day("2026-09-15"), currentPeriod(day("2026-10-02"), 15).start)
    }

    @Test
    fun `기간은 시작일 0시를 넣고 끝날 0시는 뺀다`() {
        val period = performancePeriod(october, 1)
        val rows = listOf(
            tx("2026-10-01T00:00", 1_000, paymentId = 1),
            tx("2026-10-31T23:59", 2_000, paymentId = 1),
            tx("2026-11-01T00:00", 40_000, paymentId = 1),
            tx("2026-09-30T23:59", 80_000, paymentId = 1),
        )
        assertEquals(3_000L, spentIn(rows, 1, period))
    }

    @Test
    fun `쓴 돈은 그 카드의 지출에서 환불을 빼고 이체와 수입은 넣지 않는다`() {
        val period = performancePeriod(october, 1)
        val rows = listOf(
            tx("2026-10-03", 50_000, EXPENSE, paymentId = 1),
            tx("2026-10-04", 8_000, REFUND, paymentId = 1),
            tx("2026-10-05", 100_000, TRANSFER, paymentId = 1),
            tx("2026-10-06", 3_000_000, INCOME, paymentId = 1),
            // 다른 카드와 결제수단 없는 거래
            tx("2026-10-07", 20_000, EXPENSE, paymentId = 2),
            tx("2026-10-08", 9_000, EXPENSE),
            // 앞으로의 날짜로 적어 둔 것도 기간 안이면 든다
            tx("2026-10-29", 7_000, EXPENSE, paymentId = 1),
        )
        assertEquals(49_000L, spentIn(rows, 1, period))
        assertEquals(20_000L, spentIn(rows, 2, period))
        // 환불이 더 많으면 음수다
        assertEquals(-8_000L, spentIn(listOf(tx("2026-10-04", 8_000, REFUND, paymentId = 3)), 3, period))
    }

    @Test
    fun `남은 날은 오늘을 넣어 센다`() {
        val period = performancePeriod(october, 1)
        assertEquals(29, daysLeft(period, day("2026-10-03")))
        assertEquals(31, daysLeft(period, day("2026-10-01")))
        assertEquals(1, daysLeft(period, day("2026-10-31")))
        assertEquals(0, daysLeft(period, day("2026-11-02")))
    }

    @Test
    fun `구간이 없으면 채운 것도 다음도 없고 막대는 비어 있다`() {
        val progress = TierProgress(spent = 120_000, tiers = emptyList())
        assertEquals(0, progress.reachedCount)
        assertNull(progress.reached)
        assertNull(progress.next)
        assertFalse(progress.allReached)
        assertEquals(0L, progress.remaining)
        assertEquals(0f, progress.fraction)
        assertEquals(emptyList<Float>(), progress.ticks)
    }

    @Test
    fun `구간이 하나면 그 금액이 막대 끝이다`() {
        val below = TierProgress(spent = 123_450, tiers = listOf(300_000))
        assertNull(below.reached)
        assertEquals(300_000L, below.next)
        assertEquals(176_550L, below.remaining)
        assertEquals(0.4115f, below.fraction, 0.0001f)
        assertEquals(emptyList<Float>(), below.ticks)
        // 정확히 구간 금액이면 채운 것이다
        val exact = TierProgress(spent = 300_000, tiers = listOf(300_000))
        assertEquals(300_000L, exact.reached)
        assertTrue(exact.allReached)
        assertEquals(1f, exact.fraction)
    }

    @Test
    fun `구간이 여럿이면 넘은 가장 높은 구간과 다음 구간까지 남은 돈을 안다`() {
        val tiers = listOf(300_000L, 700_000L, 1_000_000L)
        val none = TierProgress(spent = 0, tiers = tiers)
        assertNull(none.reached)
        assertEquals(300_000L, none.next)
        assertEquals(300_000L, none.remaining)

        val middle = TierProgress(spent = 523_450, tiers = tiers)
        assertEquals(1, middle.reachedCount)
        assertEquals(300_000L, middle.reached)
        assertEquals(700_000L, middle.next)
        assertEquals(176_550L, middle.remaining)
        assertFalse(middle.allReached)
        assertEquals(listOf(0.3f, 0.7f), middle.ticks)

        val atSecond = TierProgress(spent = 700_000, tiers = tiers)
        assertEquals(700_000L, atSecond.reached)
        assertEquals(1_000_000L, atSecond.next)

        val over = TierProgress(spent = 1_234_000, tiers = tiers)
        assertTrue(over.allReached)
        assertEquals(1_000_000L, over.reached)
        assertNull(over.next)
        assertEquals(0L, over.remaining)
        assertEquals(1f, over.fraction)
        // 환불이 더 많아도 막대는 비어 있을 뿐이다
        assertEquals(0f, TierProgress(spent = -5_000, tiers = tiers).fraction)
    }

    @Test
    fun `하루에 쓸 돈은 남은 날로 나눠 원 아래를 올린다`() {
        val progress = TierProgress(spent = 123_450, tiers = listOf(300_000))
        // 176,550 ÷ 29 = 6,087.9…
        assertEquals(6_088L, dailyNeed(progress, 29))
        assertEquals(176_550L, dailyNeed(progress, 1))
        assertNull(dailyNeed(progress, 0))
        assertNull(dailyNeed(TierProgress(spent = 300_000, tiers = listOf(300_000)), 10))
        assertNull(dailyNeed(TierProgress(spent = 0, tiers = emptyList()), 10))
    }

    @Test
    fun `탭이 읽는 범위는 어느 시작일이든 지난 기간 시작부터 이번 기간 끝까지 덮는다`() {
        var today = day("2026-01-01")
        repeat(400) {
            val (from, until) = tabReadRange(today)
            (1..31).forEach { startDay ->
                val current = currentPeriod(today, startDay)
                val previous = performancePeriod(current.month.minusMonths(1), startDay)
                assertTrue("$today $startDay", today in current)
                assertFalse("$today $startDay", previous.start.isBefore(from))
                assertFalse("$today $startDay", current.end.isAfter(until))
            }
            today = today.plusDays(1)
        }
    }

    @Test
    fun `상세 기간 넘기기는 그려진 기간이 아니라 고른 기간에서 움직여 빨리 두 번 누르면 두 칸 간다`() {
        // 이번 기간 10월. 화면이 아직 10월을 그리는 동안 ‹ 를 두 번 눌러도 9월에서 멈추지 않고 8월로 간다
        val once = previousPick(null, october)
        assertEquals(YearMonth.of(2026, 9), once)
        assertEquals(YearMonth.of(2026, 8), previousPick(once, october))
        // › 도 고른 기간에서 움직이고, 이번 기간에 닿으면 다시 이번 기간 따라가기(null)다
        assertEquals(YearMonth.of(2026, 9), nextPick(YearMonth.of(2026, 8), october))
        assertNull(nextPick(YearMonth.of(2026, 9), october))
        assertNull(nextPick(null, october))
        // 시작일을 바꿔 고른 기간이 이번 기간보다 뒤가 됐으면 이번 기간으로 맞춘 뒤 움직인다
        assertEquals(YearMonth.of(2026, 9), previousPick(YearMonth.of(2026, 11), october))
        assertNull(nextPick(YearMonth.of(2026, 11), october))
    }

    @Test
    fun `카드를 처음 쓴 날 전에 끝난 지난 기간만 기록이 없다`() {
        val september = performancePeriod(YearMonth.of(2026, 9), 1)
        assertFalse(hasRecord(september, day("2026-10-02"), october))
        // 기간 끝은 들지 않는다. 10월 1일에 처음 썼으면 9월에는 쓴 적이 없다
        assertFalse(hasRecord(september, day("2026-10-01"), october))
        assertTrue(hasRecord(september, day("2026-09-30"), october))
        assertTrue(hasRecord(september, day("2026-05-01"), october))
        // 한 번도 안 쓴 카드는 지난 기간이 모두 기록이 없다
        assertFalse(hasRecord(september, null, october))
        // 이번 기간은 아직 안 썼어도 보통대로 센다
        assertTrue(hasRecord(performancePeriod(october, 1), null, october))
        // 시작일이 15일이면 9월 실적은 10월 14일까지다
        assertTrue(hasRecord(performancePeriod(YearMonth.of(2026, 8), 15), day("2026-09-14"), YearMonth.of(2026, 9)))
        assertFalse(hasRecord(performancePeriod(YearMonth.of(2026, 8), 15), day("2026-09-15"), YearMonth.of(2026, 9)))
    }

    @Test
    fun `첫 사용일은 조회 값과 보이는 거래 중 이른 날이고 이 카드의 지출과 환불만 본다`() {
        val rows = listOf(
            tx("2026-09-20", 10_000, paymentId = 6),
            tx("2026-09-25", 1_000, REFUND, paymentId = 6),
            tx("2026-09-01", 10_000, INCOME, paymentId = 6),
            tx("2026-09-02", 10_000, TRANSFER, paymentId = 6),
            tx("2026-08-01", 10_000, paymentId = 7),
        )
        // 첫 거래를 막 등록해 조회 값이 아직 없거나 늦어도 보이는 거래로 센다
        assertEquals(day("2026-09-20"), effectiveFirstUse(rows, 6, null))
        assertEquals(day("2026-09-20"), effectiveFirstUse(rows, 6, day("2026-10-02")))
        assertEquals(day("2026-05-01"), effectiveFirstUse(rows, 6, day("2026-05-01")))
        assertNull(effectiveFirstUse(rows, 8, null))
    }

    @Test
    fun `현금과 계좌이체만 빼고 모두 카드실적을 본다`() {
        fun method(type: PaymentMethodType) = PaymentMethodEntity(id = 1, uuid = "u", name = "이름", type = type)
        assertFalse(method(PaymentMethodType.CASH).isPerformanceTarget())
        assertFalse(method(PaymentMethodType.ACCOUNT).isPerformanceTarget())
        assertTrue(method(PaymentMethodType.CARD).isPerformanceTarget())
        // 알림으로 생긴 카드·직접 만든 결제수단
        assertTrue(method(PaymentMethodType.OTHER).isPerformanceTarget())
    }
}
