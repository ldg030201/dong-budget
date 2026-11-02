package com.dong.budget.ui.savings

import com.dong.budget.testing.day
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.GroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class SavingsTextTest {
    private val today = day("2026-10-16")

    @Test
    fun `모은 돈은 부호 없이 적고 꺼낸 돈이 더 많을 때만 - 가 붙는다`() {
        assertEquals("300,000원", savedAmount(300_000))
        assertEquals("0원", savedAmount(0))
        assertEquals("-50,000원", savedAmount(-50_000))
    }

    @Test
    fun `머리 라벨은 달 이름 뒤에 모은 돈이다`() {
        assertEquals("이번 달 모은 돈", savingsTitle(YearMonth.of(2026, 10), today))
        assertEquals("9월 모은 돈", savingsTitle(YearMonth.of(2026, 9), today))
        assertEquals("2025년 12월 모은 돈", savingsTitle(YearMonth.of(2025, 12), today))
        assertEquals("9월까지 모은 돈", cumulativeLabel(YearMonth.of(2026, 9), today))
        assertEquals("9월에는 모은 돈이 없어요", emptyMonthTitle(YearMonth.of(2026, 9), today))
    }

    @Test
    fun `지난달 대비는 더 · 덜 모았다고 말한다`() {
        assertEquals("지난달 이맘때보다 5만원 더 모았어요", comparisonSentence(SavingsComparison(50_000, ComparisonScope.SAME_DAY)))
        assertEquals("지난달보다 3만원 덜 모았어요", comparisonSentence(SavingsComparison(-30_000, ComparisonScope.WHOLE_MONTH)))
        assertEquals("지난달만큼 모았어요", comparisonSentence(SavingsComparison(0, ComparisonScope.WHOLE_MONTH)))
    }

    @Test
    fun `최근 6개월 칸은 달 · 모은 돈 · 건수를 읽고, 시작 전 달은 그렇다고 읽는다`() {
        assertEquals("2026년 7월, 300,000원, 2건", trendDescription(SavingsMonth(YearMonth.of(2026, 7), 300_000, 2, beforeFirst = false)))
        assertEquals("2026년 8월, -50,000원, 1건", trendDescription(SavingsMonth(YearMonth.of(2026, 8), -50_000, 1, beforeFirst = false)))
        assertEquals("2026년 5월, 저축을 시작하기 전", trendDescription(SavingsMonth(YearMonth.of(2026, 5), 0, 0, beforeFirst = true)))
    }

    @Test
    fun `한 해 모은 돈은 올해면 올해, 아니면 그 해 이름이다`() {
        assertEquals("올해 모은 돈", yearLabel(YearSavings(2026, YearMonth.of(2026, 9), 1), today))
        assertEquals("2025년 모은 돈", yearLabel(YearSavings(2025, YearMonth.of(2025, 11), 1), today))
        assertEquals("1월~9월", yearRange(YearSavings(2026, YearMonth.of(2026, 9), 1)))
    }

    @Test
    fun `결제수단 줄은 비율과 건수, 꺼낸 돈이 더 많으면 그렇다고 적는다`() {
        val toss = entry(GroupKey.Id(1), "토스뱅크", 300_000, share = 0.42, count = 3)
        assertEquals("42% · 3건", paymentSummary(toss))
        assertEquals("토스뱅크, 300,000원, 모은 돈의 42퍼센트, 3건", paymentDescription(toss))
        assertNull(paymentNote(toss))

        val cash = entry(GroupKey.Id(2), "현금", -50_000, share = null, count = 1)
        assertEquals("꺼낸 돈이 더 많아요", paymentSummary(cash))
        assertEquals("현금, -50,000원, 꺼낸 돈이 더 많아요", paymentDescription(cash))

        val none = entry(GroupKey.None, "결제수단 없음", 10_000, share = 1.0, count = 1)
        assertEquals("결제수단 없음, 10,000원, 모은 돈의 100퍼센트, 1건, 지운 결제수단으로 적은 저축도 여기에 모여요", paymentDescription(none))

        assertEquals("이번 달 저축은 모두 토스뱅크에서 나갔어요", singlePaymentSentence("이번 달", "토스뱅크"))
        assertEquals("꺼낸 돈이 더 많은 결제수단 2개는 비율에서 뺐어요", withdrawnHint(2))
    }

    @Test
    fun `저축 문장에 쓴 돈 · 환불 말투가 새지 않는다`() {
        val sentences =
            listOf(
                savingsTitle(YearMonth.of(2026, 9), today),
                comparisonSentence(SavingsComparison(50_000, ComparisonScope.SAME_DAY)),
                comparisonSentence(SavingsComparison(-50_000, ComparisonScope.SAME_DAY)),
                paymentSummary(entry(GroupKey.Id(2), "현금", -50_000, share = null, count = 1)),
                withdrawnHint(1),
                emptyMonthTitle(YearMonth.of(2026, 9), today),
                EMPTY_MONTH_BODY,
                WITHDRAWN_OVER_TEXT,
            )
        sentences.forEach { sentence ->
            listOf("쓴", "썼", "지출", "환불").forEach { word -> assertEquals(sentence, false, word in sentence) }
        }
    }

    private fun entry(key: GroupKey, name: String, amount: Long, share: Double?, count: Int) = BreakdownEntry(
        key = key,
        name = name,
        icon = null,
        color = null,
        amount = amount,
        count = count,
        share = share,
        averageTicket = null,
        change = null,
    )
}
