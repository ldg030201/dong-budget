package com.dong.budget.ui.card

import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class CardPerformanceStatesTest {
    private val today = day("2026-10-03")

    private fun method(
        id: Long,
        name: String,
        type: PaymentMethodType = PaymentMethodType.OTHER,
        tiers: String? = null,
        startDay: Int = 1,
    ) = PaymentMethodEntity(id = id, uuid = "u$id", name = name, type = type, performanceTiers = tiers, performanceStartDay = startDay)

    // 결제수단 순서: 현금, 체크카드(1), 하나카드(2, 실적 30만·70만), 계좌이체, 원더카드(3, 15일 시작·30만), 토스카드(4), 신용카드(5)
    private val methods = listOf(
        method(10, "현금", PaymentMethodType.CASH),
        method(1, "체크카드", PaymentMethodType.CARD),
        method(2, "하나카드", tiers = "300000,700000"),
        method(11, "계좌이체", PaymentMethodType.ACCOUNT),
        method(3, "원더카드", tiers = "300000", startDay = 15),
        method(4, "토스카드"),
        method(5, "신용카드", PaymentMethodType.CARD),
    )

    private val rows = listOf(
        tx("2026-10-02", 123_450, paymentId = 2),
        tx("2026-09-10", 523_000, paymentId = 2),
        tx("2026-09-20", 40_000, paymentId = 3),
        tx("2026-10-01", 30_000, paymentId = 3),
        tx("2026-09-05", 90_000, paymentId = 3),
        tx("2026-10-01", 50_000, paymentId = 4),
        tx("2026-10-02", 50_000, paymentId = 1),
        tx("2026-10-02", 70_000, paymentId = 10),
        tx("2026-10-02", 20_000, paymentId = 5),
    )

    @Test
    fun `현금과 계좌이체는 빼고, 실적을 적은 카드는 결제수단 순서로 둔다`() {
        val state = buildCardPerformance(methods, rows, today, emptyMap())
        assertTrue(state.loaded)
        assertEquals(listOf("하나카드", "원더카드"), state.tracked.map { it.card.name })
        assertEquals(listOf("체크카드", "토스카드", "신용카드").sorted(), state.untracked.map { it.card.name }.sorted())
        assertTrue(state.hasCards)
    }

    @Test
    fun `실적을 적은 카드는 이번 기간과 바로 앞 기간을 함께 센다`() {
        val hana = buildCardPerformance(methods, rows, today, emptyMap()).tracked.first()
        assertEquals(YearMonth.of(2026, 10), hana.period.month)
        assertEquals(29, hana.daysLeft)
        assertEquals(123_450L, hana.progress.spent)
        assertEquals(listOf(300_000L, 700_000L), hana.progress.tiers)
        assertEquals(YearMonth.of(2026, 9), hana.previousMonth)
        assertEquals(523_000L, hana.previous?.spent)
        assertEquals(300_000L, hana.previous?.reached)
    }

    @Test
    fun `시작일이 15일인 카드는 10월 3일에 9월 실적 기간에 있다`() {
        val wonder = buildCardPerformance(methods, rows, today, emptyMap()).tracked.last()
        assertEquals(YearMonth.of(2026, 9), wonder.period.month)
        assertEquals(day("2026-09-15"), wonder.period.start)
        // 9월 20일 40,000 + 10월 1일 30,000. 9월 5일 것은 8월 실적이다
        assertEquals(70_000L, wonder.progress.spent)
        assertEquals(12, wonder.daysLeft)
        assertEquals(YearMonth.of(2026, 8), wonder.previousMonth)
        assertEquals(90_000L, wonder.previous?.spent)
    }

    @Test
    fun `실적을 안 적은 카드는 쓴 돈이 많은 순, 같으면 결제수단 순서다`() {
        val state = buildCardPerformance(methods, rows, today, emptyMap())
        // 체크카드 50,000, 토스카드 50,000, 신용카드 20,000
        assertEquals(listOf("체크카드", "토스카드", "신용카드"), state.untracked.map { it.card.name })
        assertEquals(listOf(50_000L, 50_000L, 20_000L), state.untracked.map { it.spent })
    }

    @Test
    fun `볼 카드가 하나도 없으면 비어 있다`() {
        val state = buildCardPerformance(methods.filter { !it.isPerformanceTarget() }, rows, today, emptyMap())
        assertTrue(state.loaded)
        assertFalse(state.hasCards)
    }

    private val hana = method(2, "하나카드", tiers = "300000,700000")

    // 하나카드의 지출·환불(상세는 이 카드 것만 읽는다)
    private val hanaRows = listOf(
        tx("2026-10-02T09:00", 100_000, paymentId = 2),
        tx("2026-10-02T18:00", 30_000, paymentId = 2),
        tx("2026-10-01T12:00", 6_550, REFUND, paymentId = 2),
        tx("2026-09-10", 523_000, paymentId = 2),
        tx("2026-08-10", 800_000, paymentId = 2),
        tx("2026-05-01", 10_000, paymentId = 2),
        // 6개월 밖
        tx("2026-04-30", 999_000, paymentId = 2),
    )

    @Test
    fun `상세는 고른 기간까지 최근 6개월과 그 기간의 거래를 날짜별로 보여 준다`() {
        val state = buildCardDetail(hana, YearMonth.of(2026, 10), today, hanaRows, firstUse = null)
        assertTrue(state.loaded)
        assertTrue(state.isCurrent)
        assertEquals(123_450L, state.progress.spent)
        assertEquals(29, state.daysLeft)
        assertEquals((5..10).map { YearMonth.of(2026, it) }, state.history.map { it.period.month })
        assertEquals(listOf(10_000L, 0L, 0L, 800_000L, 523_000L, 123_450L), state.history.map { it.progress.spent })
        assertTrue(state.history[3].progress.allReached)
        assertEquals(3, state.count)
        assertEquals(listOf(day("2026-10-02"), day("2026-10-01")), state.days.map { it.date })
        // 같은 날은 늦은 시각이 먼저다
        assertEquals(listOf(30_000L, 100_000L), state.days.first().items.map { it.amount })
    }

    @Test
    fun `지난 기간을 고르면 남은 날이 없고, 앞으로의 기간은 이번 기간으로 맞춘다`() {
        val september = buildCardDetail(hana, YearMonth.of(2026, 9), today, hanaRows, firstUse = null)
        assertFalse(september.isCurrent)
        assertEquals(0, september.daysLeft)
        assertEquals(523_000L, september.progress.spent)
        assertEquals(YearMonth.of(2026, 10), september.currentMonth)

        val future = buildCardDetail(hana, YearMonth.of(2027, 1), today, hanaRows, firstUse = null)
        assertEquals(YearMonth.of(2026, 10), future.period?.month)
        assertTrue(future.isCurrent)
    }

    @Test
    fun `실적을 지운 카드도 쓴 돈과 거래는 보여 준다`() {
        val state = buildCardDetail(hana.copy(performanceTiers = null), YearMonth.of(2026, 10), today, hanaRows, firstUse = null)
        assertEquals(emptyList<Long>(), state.tiers)
        assertEquals(123_450L, state.progress.spent)
        assertFalse(state.progress.allReached)
        assertEquals(3, state.count)
    }

    @Test
    fun `카드를 처음 쓴 날 전에 끝난 지난 기간은 모자랐어요 대신 기록이 없다고 한다`() {
        // 10월 2일에 처음 쓴 새 카드. 전에는 탭에 '9월 실적 0원 · 30만원까지 300,000원 모자랐어요',
        // 상세에 '최근 6개월에는 실적을 채운 적이 없어요' 가 나왔다
        val fresh = method(6, "새카드", tiers = "300000")
        val freshRows = listOf(tx("2026-10-02", 20_000, paymentId = 6))
        val firstUse = day("2026-10-02")
        val card = buildCardPerformance(listOf(fresh), freshRows, today, mapOf(6L to firstUse)).tracked.single()
        assertNull(card.previous)
        assertEquals("9월 실적은 기록이 없어요", previousLine(card.previousMonth, today, card.previous))
        assertEquals(
            "새카드, 10월 1일부터 10월 31일까지, 10월 실적 20,000원, 29일 남았어요. 실적 30만원까지 280,000원 남았어요. 9월 실적은 기록이 없어요",
            trackedCardDescription(card, today),
        )
        // 이번 기간은 쓴 돈이 적어도 보통대로 남은 돈을 알린다
        assertEquals("실적 30만원까지 280,000원 남았어요", tierSentence(card.progress, past = false))
        // 한 번도 안 쓴 카드도 지난 기간은 기록이 없다
        assertNull(buildCardPerformance(listOf(fresh), emptyList(), today, emptyMap()).tracked.single().previous)

        val detail = buildCardDetail(fresh, YearMonth.of(2026, 10), today, freshRows, firstUse)
        assertTrue(detail.recorded)
        assertEquals(listOf(false, false, false, false, false, true), detail.history.map { it.recorded })
        assertEquals("아직 지난 기록이 없어요", historySummary(detail.history, detail.tiers, detail.currentMonth))
        assertEquals("2026년 9월 실적, 기록이 없어요", historySlotDescription(detail.history[4], past = true))
        assertEquals("280,000원 남았어요", tierRowStatus(detail, 300_000))

        // 기록이 없는 지난 기간을 고르면 머리와 구간 목록도 채웠는지 따지지 않는다
        val september = buildCardDetail(fresh, YearMonth.of(2026, 9), today, freshRows, firstUse)
        assertFalse(september.recorded)
        assertEquals("9월 실적, 0원, 이 기간은 기록이 없어요", detailHeaderDescription(september))
        assertNull(tierRowStatus(september, 300_000))
        assertNull(historySummary(september.history, september.tiers, september.currentMonth))
    }

    @Test
    fun `기간 중간에 처음 쓴 카드는 그 기간부터 보통대로 센다`() {
        // 9월 15일에 처음 썼으면 9월 실적은 기록이 있는 기간이라 모자랐는지 따진다
        val card = method(6, "새카드", tiers = "300000")
        val cardRows = listOf(tx("2026-09-15", 120_000, paymentId = 6), tx("2026-10-02", 20_000, paymentId = 6))
        val firstUse = day("2026-09-15")
        val tracked = buildCardPerformance(listOf(card), cardRows, today, mapOf(6L to firstUse)).tracked.single()
        assertEquals("9월 실적 120,000원 · 30만원까지 180,000원 모자랐어요", previousLine(tracked.previousMonth, today, tracked.previous))

        val detail = buildCardDetail(card, YearMonth.of(2026, 10), today, cardRows, firstUse)
        assertEquals(listOf(false, false, false, false, true, true), detail.history.map { it.recorded })
        // 기록이 없는 넉 달은 세지 않는다
        assertEquals("기록한 2개월에는 실적을 채운 적이 없어요", historySummary(detail.history, detail.tiers, detail.currentMonth))
        val september = buildCardDetail(card, YearMonth.of(2026, 9), today, cardRows, firstUse)
        assertTrue(september.recorded)
        assertEquals("180,000원 모자랐어요", tierRowStatus(september, 300_000))
        assertEquals("9월 실적, 120,000원, 실적 30만원까지 180,000원 모자랐어요", detailHeaderDescription(september))
    }
}
