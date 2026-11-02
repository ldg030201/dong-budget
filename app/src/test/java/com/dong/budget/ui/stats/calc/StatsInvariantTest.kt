package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.isSavings
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.compareSpending
import com.dong.budget.ui.home.dailyTotals
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.GroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import kotlin.random.Random

/**
 * 어떻게 나눠도 합은 홈 숫자와 같아야 한다.
 * 임의의 거래 목록(이체·환불·저축·미래 날짜·분류 없음 포함)을 여러 번 만들어 확인한다.
 * 통계는 저축을 거르고 홈 합계는 저축을 따로 모으므로(savings), 합계는 저축 칸을 빼고 견준다.
 */
class StatsInvariantTest {
    private val month = YearMonth.of(2026, 9)
    private val today = day("2026-09-20")

    /** 분류 [SAVINGS_ID] 는 '저축' 분류(코드가 SAVINGS)다. [count] 가 적으면 저축뿐인 달 · 빈 달도 자주 나온다. */
    private fun randomRows(random: Random, count: Int = 80): List<TransactionListItem> = List(count) {
        val date = YearMonth.of(2026, random.nextInt(4, 10)).let { m -> m.atDay(random.nextInt(1, m.lengthOfMonth() + 1)) }
        val categoryId = random.nextInt(0, 9).takeIf { it > 0 }?.toLong()
        tx(
            at = "${date}T${"%02d".format(random.nextInt(0, 24))}:${"%02d".format(random.nextInt(0, 60))}",
            amount = random.nextLong(1, 200_000),
            type = TransactionType.entries[random.nextInt(TransactionType.entries.size)],
            categoryId = categoryId,
            paymentId = random.nextInt(0, 4).takeIf { it > 0 }?.toLong(),
            merchant = listOf(null, "가게A", "가게 a", "가게B").random(random),
            categoryCode = SAVINGS_CATEGORY_CODE.takeIf { categoryId == SAVINGS_ID },
        )
    }

    /** 통계가 보는 합. 저축은 통계 입구에서 걸러져 늘 0 이다. */
    private fun Totals.withoutSavings(): Totals = copy(savings = 0)

    @Test
    fun `분류별 합, 결제수단별 합, 날마다의 합이 모두 홈 합계와 같다`() {
        repeat(30) { seed ->
            val rows = randomRows(Random(seed))
            val current = rows.filter { YearMonth.from(it.localDate()) == month }
            val state = buildStatistics(month, today, rows, firstRecord = null)
            val expected = current.totals().withoutSavings()

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
                assertEquals(rows.filter { YearMonth.from(it.localDate()) == point.month }.totals().withoutSavings(), point.totals)
            }
        }
    }

    @Test
    fun `지난달 비교는 홈과 같다`() {
        repeat(30) { seed ->
            // 거래가 적은 목록도 섞어 지난달이 비었거나 저축뿐인 때도 본다
            listOf(randomRows(Random(seed)), randomRows(Random(seed), count = 6)).forEach { rows ->
                val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
                val home = compareSpending(month, today, byMonth[month].orEmpty(), byMonth[month.minusMonths(1)].orEmpty())
                assertEquals(home, buildStatistics(month, today, rows, firstRecord = null).monthly.comparison)
            }
        }
    }

    @Test
    fun `통계 금액에는 저축이 없다`() {
        repeat(30) { seed ->
            val rows = randomRows(Random(seed))
            val state = buildStatistics(month, today, rows, firstRecord = null)
            assertTrue(state.expenseByCategory.entries.none { it.key == GroupKey.Id(SAVINGS_ID) })
            // 저축 분류 코드라도 수입 · 이체는 저축이 아니다(지출 · 환불만 저축이다)
            assertTrue(state.daily.days.all { day -> day.items.none { it.isSavings } })
            assertTrue(state.monthly.largest.none { it.isSavings })
        }
    }

    private companion object {
        const val SAVINGS_ID = 7L
    }
}
