package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.compareSpending
import com.dong.budget.ui.home.dailyTotals
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import kotlin.random.Random

/**
 * 어떻게 나눠도 합은 홈 숫자와 같아야 한다.
 * 임의의 거래 목록(이체·환불·미래 날짜·분류 없음 포함)을 여러 번 만들어 확인한다.
 */
class StatsInvariantTest {
    private val month = YearMonth.of(2026, 9)
    private val today = day("2026-09-20")

    private fun randomRows(random: Random): List<TransactionListItem> = List(80) {
        val date = YearMonth.of(2026, random.nextInt(4, 10)).let { m -> m.atDay(random.nextInt(1, m.lengthOfMonth() + 1)) }
        tx(
            at = "${date}T${"%02d".format(random.nextInt(0, 24))}:${"%02d".format(random.nextInt(0, 60))}",
            amount = random.nextLong(1, 200_000),
            type = TransactionType.entries[random.nextInt(TransactionType.entries.size)],
            categoryId = random.nextInt(0, 9).takeIf { it > 0 }?.toLong(),
            paymentId = random.nextInt(0, 4).takeIf { it > 0 }?.toLong(),
            merchant = listOf(null, "가게A", "가게 a", "가게B").random(random),
        )
    }

    @Test
    fun `분류별 합, 결제수단별 합, 날마다의 합이 모두 홈 합계와 같다`() {
        repeat(30) { seed ->
            val rows = randomRows(Random(seed))
            val current = rows.filter { YearMonth.from(it.localDate()) == month }
            val state = buildStatistics(month, today, rows, firstRecord = null)
            val expected = current.totals()

            assertEquals(expected, state.monthly.totals)
            assertEquals(expected.expense, state.expenseByCategory.total)
            assertEquals(expected.expense, state.expenseByCategory.entries.sumOf { it.amount })
            assertEquals(expected.expense, state.expenseByPayment.entries.sumOf { it.amount })
            assertEquals(expected.income, state.incomeByCategory.entries.sumOf { it.amount })
            assertEquals(expected.expense, state.daily.days.sumOf { it.expense })
        }
    }

    @Test
    fun `날마다의 금액은 홈 달력 칸과 같다`() {
        repeat(30) { seed ->
            val rows = randomRows(Random(seed))
            val homeDays = dailyTotals(rows.filter { YearMonth.from(it.localDate()) == month })
            buildStatistics(month, today, rows, firstRecord = null).daily.days.forEach { day ->
                assertEquals(homeDays[day.date]?.expense ?: 0L, day.expense)
                assertEquals(homeDays[day.date]?.income ?: 0L, day.income)
            }
        }
    }

    @Test
    fun `최근 6개월의 각 달은 그 달 홈 합계와 같다`() {
        repeat(30) { seed ->
            val rows = randomRows(Random(seed))
            buildStatistics(month, today, rows, firstRecord = null).monthly.trend.forEach { point ->
                assertEquals(rows.filter { YearMonth.from(it.localDate()) == point.month }.totals(), point.totals)
            }
        }
    }

    @Test
    fun `지난달 비교는 홈과 같다`() {
        repeat(30) { seed ->
            val rows = randomRows(Random(seed))
            val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
            val home = compareSpending(month, today, byMonth[month].orEmpty(), byMonth[month.minusMonths(1)].orEmpty())
            assertEquals(home, buildStatistics(month, today, rows, firstRecord = null).monthly.comparison)
        }
    }
}
