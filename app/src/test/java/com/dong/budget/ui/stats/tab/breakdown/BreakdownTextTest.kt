package com.dong.budget.ui.stats.tab.breakdown

import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.MerchantStat
import com.dong.budget.ui.stats.StatSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreakdownTextTest {
    private fun entry(
        name: String = "식비",
        key: GroupKey = GroupKey.Id(1),
        amount: Long = 523_000,
        share: Double? = 0.42,
        count: Int = 12,
        averageTicket: Long? = 43_583,
        change: EntryChange? = null,
    ) = BreakdownEntry(key, name, null, null, amount, count, share, averageTicket, change)

    @Test
    fun `순위 줄은 설계서 예시처럼 한 문장으로 읽는다`() {
        val change = EntryChange(ComparisonScope.SAME_DAY, previous = 493_000, current = 523_000)
        assertEquals(
            "식비, 523,000원, 지출의 42퍼센트, 12건, 지난달 이맘때보다 30,000원 늘었어요",
            entryDescription(entry(change = change), StatsDimension.EXPENSE_CATEGORY),
        )
    }

    @Test
    fun `결제수단 줄은 한 번에 평균을 전체 금액으로 읽는다`() {
        assertEquals(
            "현금, 40,000원, 지출의 1퍼센트 미만, 3건, 한 번에 평균 13,333원",
            entryDescription(
                entry(name = "현금", amount = 40_000, share = 0.004, count = 3, averageTicket = 13_333),
                StatsDimension.PAYMENT_METHOD,
            ),
        )
    }

    @Test
    fun `수입 분류는 수입의 몫이고 한 번에 평균이 없다`() {
        assertEquals(
            "월급, 3,000,000원, 수입의 100퍼센트, 1건, 지난달엔 없었어요",
            entryDescription(
                entry(
                    name = "월급",
                    amount = 3_000_000,
                    share = 1.0,
                    count = 1,
                    averageTicket = null,
                    change = EntryChange(ComparisonScope.WHOLE_MONTH, 0, 3_000_000),
                ),
                StatsDimension.INCOME_CATEGORY,
            ),
        )
    }

    @Test
    fun `환불이 더 많은 줄은 + 금액과 비율 대신 사정을 읽는다`() {
        assertEquals("+12,000원", formatNetExpense(-12_000))
        assertEquals(
            "식비, +12,000원, 환불받은 돈이 더 많아요",
            entryDescription(entry(amount = -12_000, share = null, count = 2), StatsDimension.EXPENSE_CATEGORY),
        )
    }

    @Test
    fun `결제수단 없음 줄에만 지운 결제수단 안내가 붙는다`() {
        val note = "지운 결제수단으로 적은 거래도 여기에 모여요"
        assertEquals(note, groupNote(GroupKey.None, StatsDimension.PAYMENT_METHOD))
        assertNull(groupNote(GroupKey.Id(3), StatsDimension.PAYMENT_METHOD))
        assertNull(groupNote(GroupKey.None, StatsDimension.EXPENSE_CATEGORY))
        assertEquals(
            "결제수단 없음, 8,000원, 지출의 10퍼센트, 1건, 한 번에 평균 8,000원, $note",
            entryDescription(
                entry(name = "결제수단 없음", key = GroupKey.None, amount = 8_000, share = 0.1, count = 1, averageTicket = 8_000),
                StatsDimension.PAYMENT_METHOD,
            ),
        )
    }

    @Test
    fun `머리 부제, 범례, 많이 쓴 곳 문장`() {
        assertEquals("523,000원", formatNetExpense(523_000))
        assertEquals("분류 5개", entryCountText(5, StatsDimension.EXPENSE_CATEGORY))
        assertEquals("분류 2개", entryCountText(2, StatsDimension.INCOME_CATEGORY))
        assertEquals("결제수단 3개", entryCountText(3, StatsDimension.PAYMENT_METHOD))
        assertEquals(
            "식비 42퍼센트",
            legendDescription(StatSeries(GroupKey.Id(1), "식비", "orange", 523_000, 0.42, listOf(GroupKey.Id(1)))),
        )
        assertEquals(
            "1위, 스타벅스, 52,000원, 4번, 한 번에 평균 13,000원",
            merchantDescription(1, MerchantStat("스타벅스", 52_000, 4, 13_000)),
        )
    }
}
