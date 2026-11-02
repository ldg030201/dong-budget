package com.dong.budget.ui.savings

import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.GroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SavingsCalcTest {
    private val today = day("2026-10-16")
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    /** '저축' 분류(id 7)로 적은 거래 */
    private fun saved(
        at: String,
        amount: Long,
        merchant: String? = "적금",
        paymentId: Long? = 1,
        type: TransactionType = TransactionType.EXPENSE,
    ) = tx(
        at,
        amount,
        type = type,
        categoryId = 7,
        paymentId = paymentId,
        merchant = merchant,
        categoryName = "저축",
        categoryCode = SAVINGS_CATEGORY_CODE,
    )

    private fun build(month: YearMonth, vararg rows: TransactionListItem) =
        buildSavings(month, today, hasCategory = true, rows = rows.toList())

    @Test
    fun `내역은 저축만 달마다 묶고 지금까지 · 이번 달 모은 돈을 센다`() {
        val oct = saved("2026-10-05", 300_000)
        val withdrawn = saved("2026-10-10", 50_000, type = REFUND)
        val sep = saved("2026-09-25", 300_000)
        // 다른 분류의 지출은 저축 조회에 오지 않지만, 섞여 와도 세지 않는다
        val lunch = tx("2026-10-06", 9_000, categoryId = 1)
        val history = build(october, oct, withdrawn, sep, lunch).history

        assertEquals(550_000L, history.total)
        assertEquals(250_000L, history.thisMonth)
        assertEquals(listOf(october, september), history.months.map { it.month })
        assertEquals(listOf(withdrawn, oct), history.months.first().days.flatMap { it.items })
        // 달 머리 합은 지출이 아니라 저축 칸에 든다(totalsText 가 '저축 250,000원' 으로 적는다)
        assertEquals(Totals(savings = 250_000), history.months.first().totals)
    }

    @Test
    fun `저축이 없으면 내역도 통계도 비어 있다`() {
        val state = build(october)
        assertTrue(state.loaded)
        assertFalse(state.hasAny)
        assertTrue(state.stats.monthIsEmpty)
        assertEquals(0L, state.history.total)
    }

    @Test
    fun `통계는 고른 달만 세고 꺼낸 돈은 뺀다`() {
        val stats =
            build(
                september,
                saved("2026-09-03", 300_000, merchant = "적금", paymentId = 1),
                saved("2026-09-10", 200_000, merchant = "청약", paymentId = 2),
                saved("2026-09-20", 50_000, merchant = "청약", paymentId = 2, type = REFUND),
                saved("2026-10-05", 999_000),
            ).stats

        assertEquals(450_000L, stats.total)
        assertEquals(3, stats.count)
        assertFalse(stats.monthIsEmpty)
        // 상품(가게 이름)별 모은 돈. 꺼낸 돈은 그 상품에서 빠진다.
        assertEquals(listOf("적금" to 300_000L, "청약" to 150_000L), stats.merchants.map { it.name to it.amount })
        // 결제수단별 모은 돈
        assertEquals(listOf(GroupKey.Id(1) to 300_000L, GroupKey.Id(2) to 150_000L), stats.byPayment.entries.map { it.key to it.amount })
        assertEquals(450_000L, stats.byPayment.total)
    }

    @Test
    fun `지나간 달은 지난달 전체와 견주고, 지난달에 저축이 없으면 견주지 않는다`() {
        val aug = saved("2026-08-25", 200_000)
        val sep = saved("2026-09-25", 300_000)
        assertEquals(SavingsComparison(100_000, ComparisonScope.WHOLE_MONTH), build(september, aug, sep).stats.comparison)
        assertNull(build(september, sep).stats.comparison)
    }

    @Test
    fun `이번 달은 오늘까지 모은 돈을 지난달 같은 날까지와 견준다`() {
        val stats =
            build(
                october,
                saved("2026-09-05", 100_000),
                // 지난달 같은 날(16일) 뒤라 넣지 않는다
                saved("2026-09-25", 300_000),
                saved("2026-10-05", 250_000),
                // 오늘 뒤 날짜로 미리 적은 저축은 아직 모은 돈이 아니라 뺀다(달 합계에는 든다)
                saved("2026-10-25", 300_000),
            ).stats
        assertEquals(SavingsComparison(150_000, ComparisonScope.SAME_DAY), stats.comparison)
        assertEquals(550_000L, stats.total)
    }

    @Test
    fun `최근 6개월은 저축을 시작하기 전 달을 빈칸으로 둔다`() {
        val trend = build(october, saved("2026-08-25", 200_000), saved("2026-10-05", 300_000), saved("2026-10-06", 10_000)).stats.trend
        assertEquals((5 downTo 0).map { october.minusMonths(it.toLong()) }, trend.map { it.month })
        assertEquals(listOf(true, true, true, false, false, false), trend.map { it.beforeFirst })
        assertEquals(SavingsMonth(YearMonth.of(2026, 9), 0, 0, beforeFirst = false), trend[4])
        assertEquals(SavingsMonth(october, 310_000, 2, beforeFirst = false), trend[5])
    }

    @Test
    fun `올해 모은 돈은 그해보다 먼저 시작했을 때만, 고른 달까지 모은 돈은 고른 달보다 먼저 시작했을 때만 둔다`() {
        val lastYear = saved("2025-11-25", 100_000)
        val march = saved("2026-03-25", 200_000)
        val oct = saved("2026-10-05", 300_000)
        val later = saved("2026-11-05", 999_000)

        val stats = build(october, lastYear, march, oct, later).stats
        assertEquals(YearSavings(2026, october, 500_000), stats.year)
        assertEquals(600_000L, stats.cumulative)

        // 올해 시작했으면 올해 모은 돈이 고른 달까지 모은 돈과 같아 두지 않는다
        val thisYear = build(october, march, oct).stats
        assertNull(thisYear.year)
        assertEquals(500_000L, thisYear.cumulative)

        // 고른 달에 시작했으면 둘 다 이 달 모은 돈과 같다
        val firstMonth = build(october, oct).stats
        assertNull(firstMonth.year)
        assertNull(firstMonth.cumulative)

        // 1월이면 올해 모은 돈이 그 달 모은 돈과 같다
        val january = build(YearMonth.of(2026, 1), lastYear, saved("2026-01-25", 50_000)).stats
        assertNull(january.year)
        assertEquals(150_000L, january.cumulative)
    }

    @Test
    fun `저축이 없는 달은 빈 달이다`() {
        val stats = build(september, saved("2026-08-25", 200_000), saved("2026-10-05", 300_000)).stats
        assertTrue(stats.monthIsEmpty)
        assertEquals(0L, stats.total)
        assertTrue(stats.merchants.isEmpty())
        assertTrue(stats.byPayment.entries.isEmpty())
    }

    @Test
    fun `분류가 없다는 것은 그대로 넘긴다`() {
        assertFalse(buildSavings(october, today, hasCategory = false, rows = emptyList()).hasCategory)
    }
}
