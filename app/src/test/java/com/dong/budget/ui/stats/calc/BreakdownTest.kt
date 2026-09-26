package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionType.EXPENSE
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.testing.colorOf
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.comparisonWindow
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class BreakdownTest {
    private fun expenseByCategory(vararg rows: com.dong.budget.data.db.TransactionListItem) =
        breakdown(rows.toList(), Measure.EXPENSE, Grouping.CATEGORY, window = null)

    @Test
    fun `이체는 금액에도 건수에도 없고 환불은 그 분류에서 빠진다`() {
        val result = expenseByCategory(
            tx("2026-09-01", 10_000, categoryId = 1),
            tx("2026-09-02", 3_000, REFUND, categoryId = 1),
            tx("2026-09-03", 5_000, categoryId = 2),
            tx("2026-09-04", 70_000, TRANSFER, categoryId = 1),
            tx("2026-09-05", 50_000, INCOME, categoryId = 9),
        )
        assertEquals(12_000L, result.total)
        assertEquals(listOf(GroupKey.Id(1), GroupKey.Id(2)), result.entries.map { it.key })
        assertEquals(listOf(7_000L, 5_000L), result.entries.map { it.amount })
        assertEquals(listOf(2, 1), result.entries.map { it.count })
        // 한 번에 평균은 환불로 줄기 전 금액이다
        assertEquals(10_000L, result.entries.first().averageTicket)
    }

    @Test
    fun `수입 쪽은 수입만 넣고 한 번에 평균은 없다`() {
        val result = breakdown(
            listOf(
                tx("2026-09-05", 2_000_000, INCOME, categoryId = 9, categoryName = "월급"),
                tx("2026-09-06", 30_000, INCOME, categoryId = 10, categoryName = "용돈"),
                tx("2026-09-07", 10_000, categoryId = 1),
            ),
            Measure.INCOME,
            Grouping.CATEGORY,
            window = null,
        )
        assertEquals(2_030_000L, result.total)
        assertEquals(listOf("월급", "용돈"), result.entries.map { it.name })
        assertEquals(listOf(null, null), result.entries.map { it.averageTicket })
    }

    @Test
    fun `환불이 더 많은 항목은 비율이 없고 맨 아래에 온다`() {
        val result = expenseByCategory(
            tx("2026-09-01", 2_000, categoryId = 2),
            tx("2026-09-02", 5_000, REFUND, categoryId = 2),
            tx("2026-09-03", 10_000, categoryId = 1),
            tx("2026-09-04", 1_000, categoryId = 3),
        )
        assertEquals(listOf(10_000L, 1_000L, -3_000L), result.entries.map { it.amount })
        assertEquals(1, result.negativeCount)
        assertNull(result.entries.last().share)
        // 비율의 분모는 양수 항목의 합이다
        assertEquals(11_000L, result.positiveTotal)
        assertEquals(10_000.0 / 11_000, result.entries.first().share!!, 1e-12)
        assertEquals(8_000L, result.total)
        // 음수 항목은 계열에 없다
        assertEquals(listOf<GroupKey?>(GroupKey.Id(1), GroupKey.Id(3)), result.series.map { it.key })
    }

    @Test
    fun `금액이 0 인 항목은 목록에서 뺀다`() {
        val result = expenseByCategory(
            tx("2026-09-01", 5_000, categoryId = 3),
            tx("2026-09-02", 5_000, REFUND, categoryId = 3),
            tx("2026-09-03", 1_000, categoryId = 1),
        )
        assertEquals(listOf(GroupKey.Id(1)), result.entries.map { it.key })
    }

    @Test
    fun `같은 금액이면 이름 있는 것이 먼저고 그다음 이름순, 이름도 같으면 id 순이다`() {
        val result = expenseByCategory(
            tx("2026-09-01", 1_000),
            tx("2026-09-01", 1_000, categoryId = 5, categoryName = "쇼핑"),
            tx("2026-09-01", 1_000, categoryId = 4, categoryName = "교통"),
            tx("2026-09-01", 1_000, categoryId = 7, categoryName = "교통"),
            tx("2026-09-01", 1_000, categoryId = 6, categoryName = "교통"),
        )
        assertEquals(
            listOf(GroupKey.Id(4), GroupKey.Id(6), GroupKey.Id(7), GroupKey.Id(5), GroupKey.None),
            result.entries.map { it.key },
        )
    }

    @Test
    fun `분류나 결제수단이 없는 거래는 이름 없는 묶음에 모이고 아이콘과 색이 없다`() {
        val rows = listOf(tx("2026-09-01", 1_000), tx("2026-09-02", 2_000))
        val byCategory = breakdown(rows, Measure.EXPENSE, Grouping.CATEGORY, window = null).entries.single()
        assertEquals(GroupKey.None, byCategory.key)
        assertEquals("분류 없음", byCategory.name)
        assertNull(byCategory.icon)
        assertNull(byCategory.color)
        assertEquals("결제수단 없음", breakdown(rows, Measure.EXPENSE, Grouping.PAYMENT_METHOD, window = null).entries.single().name)
    }

    @Test
    fun `결제수단으로 묶으면 결제수단의 이름과 색을 쓴다`() {
        val result = breakdown(
            listOf(tx("2026-09-01", 1_000, categoryId = 1, paymentId = 3, paymentName = "현대카드")),
            Measure.EXPENSE,
            Grouping.PAYMENT_METHOD,
            window = null,
        )
        val entry = result.entries.single()
        assertEquals(GroupKey.Id(3), entry.key)
        assertEquals("현대카드", entry.name)
        assertEquals(colorOf(3), entry.color)
        assertEquals("credit_card", entry.icon)
    }

    @Test
    fun `상세 차원은 무엇을 잴지와 무엇으로 묶을지를 정한다`() {
        assertEquals(Measure.EXPENSE, StatsDimension.EXPENSE_CATEGORY.measure)
        assertEquals(Measure.INCOME, StatsDimension.INCOME_CATEGORY.measure)
        assertEquals(Measure.EXPENSE, StatsDimension.PAYMENT_METHOD.measure)
        assertEquals(Grouping.CATEGORY, StatsDimension.INCOME_CATEGORY.grouping)
        assertEquals(Grouping.PAYMENT_METHOD, StatsDimension.PAYMENT_METHOD.grouping)
    }

    // ── 계열(상위 + 그 외) ──────────────────────────────────────────────

    /** id 1 이 가장 크고 id 가 커질수록 작아지는 분류 [count] 개 */
    private fun namedRows(count: Int) = (1..count).map { id -> tx("2026-09-01", (100 - id) * 1_000L, categoryId = id.toLong()) }

    @Test
    fun `이름 있는 분류가 6개이고 분류 없음이 없으면 접지 않는다`() {
        val series = expenseByCategory(*namedRows(6).toTypedArray()).series
        assertEquals((1L..6L).map { GroupKey.Id(it) }, series.map { it.key })
        assertEquals((1L..6L).map { colorOf(it) }, series.map { it.color })
    }

    @Test
    fun `이름 있는 분류 6개에 분류 없음이 있으면 5개와 '그 외 2개' 다`() {
        val result = expenseByCategory(*(namedRows(6) + tx("2026-09-02", 500)).toTypedArray())
        val series = result.series
        assertEquals(6, series.size)
        assertEquals((1L..5L).map { GroupKey.Id(it) }, series.take(5).map { it.key })
        val other = series.last()
        assertEquals("그 외 2개", other.name)
        assertNull(other.key)
        assertNull(other.color)
        assertEquals(listOf(GroupKey.Id(6), GroupKey.None), other.members)
        assertEquals(94_000L + 500L, other.amount)
        assertEquals(other.amount.toDouble() / result.positiveTotal, other.share, 1e-12)
    }

    @Test
    fun `이름 있는 분류 5개에 분류 없음이 있으면 분류 없음 한 조각으로 접는다`() {
        val series = expenseByCategory(*(namedRows(5) + tx("2026-09-02", 500)).toTypedArray()).series
        assertEquals(6, series.size)
        val last = series.last()
        assertEquals(GroupKey.None, last.key)
        assertEquals("분류 없음", last.name)
        assertNull(last.color)
        assertEquals(listOf<GroupKey>(GroupKey.None), last.members)
    }

    @Test
    fun `이름 있는 분류가 7개면 5개와 '그 외 2개' 다`() {
        val series = expenseByCategory(*namedRows(7).toTypedArray()).series
        assertEquals(6, series.size)
        assertEquals("그 외 2개", series.last().name)
        assertEquals(listOf(GroupKey.Id(6), GroupKey.Id(7)), series.last().members)
    }

    @Test
    fun `기타 분류는 이름 있는 분류라 제 조각을 가진다`() {
        val series = expenseByCategory(
            tx("2026-09-01", 30_000, categoryId = 1, categoryName = "식비"),
            tx("2026-09-01", 20_000, categoryId = 99, categoryName = "기타"),
            tx("2026-09-01", 10_000),
        ).series
        assertEquals(listOf("식비", "기타", "분류 없음"), series.map { it.name })
        assertEquals(colorOf(99), series[1].color)
    }

    @Test
    fun `같은 금액의 계열은 이름순이다`() {
        val series = expenseByCategory(
            tx("2026-09-01", 10_000, categoryId = 1, categoryName = "쇼핑"),
            tx("2026-09-01", 10_000, categoryId = 2, categoryName = "교통"),
        ).series
        assertEquals(listOf("교통", "쇼핑"), series.map { it.name })
    }

    // ── 지난달 대비 ─────────────────────────────────────────────────────

    @Test
    fun `비교할 수 없으면 증감이 없다`() {
        val window =
            comparisonWindow(YearMonth.of(2026, 9), day("2026-09-25"), listOf(tx("2026-09-01", 1_000, categoryId = 1)), emptyList())
        assertNull(window)
        assertNull(entryChange(GroupKey.Id(1), window, Measure.EXPENSE, Grouping.CATEGORY))
    }

    @Test
    fun `지난달에 없던 분류는 지난 값이 0 이다`() {
        val window = comparisonWindow(
            YearMonth.of(2026, 9),
            day("2026-09-25"),
            listOf(tx("2026-09-10", 20_000, categoryId = 1)),
            listOf(tx("2026-08-10", 5_000, categoryId = 2)),
        )
        assertEquals(
            EntryChange(ComparisonScope.SAME_DAY, previous = 0, current = 20_000),
            entryChange(GroupKey.Id(1), window, Measure.EXPENSE, Grouping.CATEGORY),
        )
    }

    @Test
    fun `이번 달 증감은 지난달 같은 날까지와 견주고 오늘 뒤 거래는 뺀다`() {
        val current = listOf(
            tx("2026-09-10", 50_000, categoryId = 1),
            tx("2026-09-28", 999_000, categoryId = 1),
        )
        val previous = listOf(
            tx("2026-08-25T23:59", 20_000, categoryId = 1),
            tx("2026-08-26T00:00", 500_000, categoryId = 1),
        )
        val result =
            breakdown(
                current,
                Measure.EXPENSE,
                Grouping.CATEGORY,
                comparisonWindow(YearMonth.of(2026, 9), day("2026-09-25"), current, previous),
            )
        assertEquals(EntryChange(ComparisonScope.SAME_DAY, previous = 20_000, current = 50_000), result.entries.single().change)
    }

    @Test
    fun `3월 31일에는 2월 말일까지 전부와 견준다`() {
        val current = listOf(tx("2026-03-01", 10_000, categoryId = 1))
        val previous = listOf(tx("2026-02-28T22:00", 30_000, categoryId = 1))
        val window = comparisonWindow(YearMonth.of(2026, 3), day("2026-03-31"), current, previous)
        assertEquals(
            EntryChange(ComparisonScope.SAME_DAY, previous = 30_000, current = 10_000),
            entryChange(GroupKey.Id(1), window, Measure.EXPENSE, Grouping.CATEGORY),
        )
    }

    @Test
    fun `지나간 달은 한 달 전체끼리 견준다`() {
        val current = listOf(tx("2026-08-30", 80_000, categoryId = 1), tx("2026-08-31", 5_000, REFUND, categoryId = 1))
        val previous = listOf(tx("2026-07-31", 30_000, categoryId = 1), tx("2026-07-31", 1_000, EXPENSE, categoryId = 2))
        val window = comparisonWindow(YearMonth.of(2026, 8), day("2026-09-03"), current, previous)
        assertEquals(
            EntryChange(ComparisonScope.WHOLE_MONTH, previous = 30_000, current = 75_000),
            entryChange(GroupKey.Id(1), window, Measure.EXPENSE, Grouping.CATEGORY),
        )
    }

    @Test
    fun `두 쪽 모두 0 이면 말할 거리가 없어 증감이 없다`() {
        // 이번 달 거래가 모두 오늘 뒤라 비교 범위에서는 0 이다
        val current = listOf(tx("2026-09-28", 10_000, categoryId = 1))
        val previous = listOf(tx("2026-08-01", 10_000, categoryId = 2))
        val window = comparisonWindow(YearMonth.of(2026, 9), day("2026-09-25"), current, previous)
        assertNull(entryChange(GroupKey.Id(1), window, Measure.EXPENSE, Grouping.CATEGORY))
    }
}
