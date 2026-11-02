package com.dong.budget.ui.savings

import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.isSavings
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.comparisonWindow
import com.dong.budget.ui.home.totals
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

    private fun build(month: YearMonth, vararg rows: TransactionListItem, year: Int = today.year) =
        buildSavings(month, year, today, hasCategory = true, rows = rows.toList())

    @Test
    fun `내역은 고른 해의 저축만 달마다 묶고 그해 · 이번 달 모은 돈을 센다`() {
        val oct = saved("2026-10-05", 300_000)
        val withdrawn = saved("2026-10-10", 50_000, type = REFUND)
        val sep = saved("2026-09-25", 300_000)
        val january = saved("2026-01-25", 100_000)
        // 지난해 것은 올해 모은 돈에도 목록에도 들지 않는다
        val lastYear = saved("2025-12-25", 700_000)
        // 다른 분류의 지출은 저축 조회에 오지 않지만, 섞여 와도 세지 않는다
        val lunch = tx("2026-10-06", 9_000, categoryId = 1)
        val state = build(october, oct, withdrawn, sep, january, lastYear, lunch)
        val history = state.history

        assertEquals(2026, history.year)
        assertTrue(state.isThisYear)
        // 꺼낸 돈(저축 분류의 환불)은 뺀다: 300,000 - 50,000 + 300,000 + 100,000
        assertEquals(650_000L, history.total)
        assertEquals(250_000L, history.thisMonth)
        assertEquals(listOf(october, september, YearMonth.of(2026, 1)), history.months.map { it.month })
        assertEquals(listOf(withdrawn, oct), history.months.first().days.flatMap { it.items })
        // 달 머리 합은 지출이 아니라 저축 칸에 든다(totalsText 가 '저축 250,000원' 으로 적는다)
        assertEquals(Totals(savings = 250_000), history.months.first().totals)
    }

    @Test
    fun `지난해를 보면 그해 것만 세고 이번 달 모은 돈은 두지 않는다`() {
        val dec = saved("2025-12-25", 300_000)
        val withdrawn = saved("2025-06-10", 50_000, type = REFUND)
        val state = build(october, dec, withdrawn, saved("2026-10-05", 300_000), saved("2024-12-25", 900_000), year = 2025)
        val history = state.history

        assertEquals(2025, history.year)
        assertFalse(state.isThisYear)
        assertEquals(250_000L, history.total)
        assertNull(history.thisMonth)
        assertEquals(listOf(YearMonth.of(2025, 12), YearMonth.of(2025, 6)), history.months.map { it.month })
        // 통계 탭은 고른 해와 상관없이 고른 달 그대로다
        assertEquals(300_000L, state.stats.total)
    }

    @Test
    fun `올해 이번 달에 저축이 없으면 이번 달 모은 돈은 0 이다`() {
        val history = build(october, saved("2026-09-25", 300_000)).history
        assertEquals(300_000L, history.total)
        assertEquals(0L, history.thisMonth)
    }

    @Test
    fun `저축이 없는 해는 비었지만 다른 해에 저축이 있으면 저축이 있는 것이다`() {
        val state = build(october, saved("2026-10-05", 300_000), year = 2024)
        assertTrue(state.hasAny)
        assertTrue(state.history.yearIsEmpty)
        assertEquals(0L, state.history.total)
        assertNull(state.history.thisMonth)
    }

    @Test
    fun `해를 넘겨 앞으로 적어 둔 저축은 적은 날짜의 해에 든다`() {
        // 올해 12월 것은 아직 오지 않았어도 올해에 든다(내역 목록처럼). 다음 해 1월 것은 다음 해에 든다.
        val december = saved("2026-12-25", 300_000)
        val nextJanuary = saved("2027-01-05", 500_000)
        val rows = arrayOf(saved("2026-10-05", 300_000), december, nextJanuary)

        val thisYear = build(october, *rows).history
        assertEquals(600_000L, thisYear.total)
        assertEquals(300_000L, thisYear.thisMonth)
        assertEquals(listOf(YearMonth.of(2026, 12), october), thisYear.months.map { it.month })

        // 다음 해로는 화면에서 넘기지 않지만, 계산은 그해 것만 센다
        val nextYear = build(october, *rows, year = 2027).history
        assertEquals(500_000L, nextYear.total)
        assertNull(nextYear.thisMonth)
        assertEquals(listOf(nextJanuary), nextYear.months.flatMap { month -> month.days.flatMap { it.items } })
    }

    @Test
    fun `저축이 없으면 내역도 통계도 비어 있다`() {
        val state = build(october)
        assertTrue(state.loaded)
        assertFalse(state.hasAny)
        assertTrue(state.stats.monthIsEmpty)
        assertTrue(state.history.yearIsEmpty)
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
    fun `지난달 대비는 홈의 지난달 비교와 같은 범위로 센다`() {
        // 홈은 지출과 저축이 섞인 거래로 범위를 정한다. 그 범위 안의 저축만 센 것이 저축 화면의 지난달 대비와 같아야 한다.
        val february = listOf(saved("2026-02-05", 100_000), saved("2026-02-28", 50_000), tx("2026-02-10", 9_000, categoryId = 1))
        // 3월 31일 것은 오늘 뒤 날짜로 미리 적은 저축이다(3월 20일에 볼 때)
        val march = listOf(saved("2026-03-03", 200_000), saved("2026-03-31", 300_000), tx("2026-03-02", 8_000, categoryId = 1))
        val cases =
            listOf(
                // 이번 달: 지난달 같은 날까지, 지난달이 짧으면 지난달 말일까지
                Triple(YearMonth.of(2026, 3), day("2026-03-20"), SavingsComparison(100_000, ComparisonScope.SAME_DAY)),
                Triple(YearMonth.of(2026, 3), day("2026-03-31"), SavingsComparison(350_000, ComparisonScope.SAME_DAY)),
                // 지나간 달: 한 달 전체끼리
                Triple(YearMonth.of(2026, 3), day("2026-04-02"), SavingsComparison(350_000, ComparisonScope.WHOLE_MONTH)),
            )
        for ((month, today, expected) in cases) {
            val window = comparisonWindow(month, today, march, february)!!
            val home = SavingsComparison(window.current.totals().savings - window.previous.totals().savings, window.scope)

            val savings = savingsComparison(month, today, march.filter { it.isSavings }, february.filter { it.isSavings })

            assertEquals("$today", expected, savings)
            assertEquals("$today", home, savings)
        }
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
        assertFalse(buildSavings(october, today.year, today, hasCategory = false, rows = emptyList()).hasCategory)
    }
}
