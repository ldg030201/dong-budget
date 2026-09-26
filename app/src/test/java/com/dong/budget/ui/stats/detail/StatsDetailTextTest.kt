package com.dong.budget.ui.stats.detail

import com.dong.budget.navigation.StatsDimension
import com.dong.budget.testing.day
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class StatsDetailTextTest {
    private fun entry(amount: Long = 523_000, share: Double? = 0.42, count: Int = 12, averageTicket: Long? = 20_000) =
        BreakdownEntry(GroupKey.Id(1), "식비", null, null, amount, count, share, averageTicket, change = null)

    @Test
    fun `머리 라벨은 결제수단도 지출이라고 쓴다`() {
        assertEquals("이번 달 지출", detailLabel("이번 달", StatsDimension.EXPENSE_CATEGORY))
        assertEquals("9월 수입", detailLabel("9월", StatsDimension.INCOME_CATEGORY))
        assertEquals("9월 지출", detailLabel("9월", StatsDimension.PAYMENT_METHOD))
    }

    @Test
    fun `머리 부제는 무엇의 몇 퍼센트인지와 건수, 지출 쪽은 한 번에 평균까지`() {
        assertEquals("지출의 42% · 12건 · 한 번에 평균 2만원", detailSummary(entry(), StatsDimension.EXPENSE_CATEGORY))
        assertEquals("지출의 42% · 12건 · 한 번에 평균 2만원", detailSummary(entry(), StatsDimension.PAYMENT_METHOD))
        assertEquals("수입의 30% · 2건", detailSummary(entry(share = 0.3, count = 2, averageTicket = null), StatsDimension.INCOME_CATEGORY))
        assertEquals(
            "지출의 1% 미만 · 1건 · 한 번에 평균 3,000원",
            detailSummary(entry(share = 0.004, count = 1, averageTicket = 3_000), StatsDimension.EXPENSE_CATEGORY),
        )
        assertEquals(
            "환불받은 돈이 더 많아요 · 3건 · 한 번에 평균 1만원",
            detailSummary(entry(amount = -12_000, share = null, count = 3, averageTicket = 10_000), StatsDimension.EXPENSE_CATEGORY),
        )
    }

    @Test
    fun `머리는 금액을 줄이지 않은 한 문장으로 읽는다`() {
        val state = StatsDetailUiState.loading(
            StatsDimension.EXPENSE_CATEGORY,
            GroupKey.Id(1),
            YearMonth.of(2026, 9),
            day("2026-09-27"),
        ).copy(
            loaded = true,
            amount = 523_000,
            entry = entry(),
            change = EntryChange(ComparisonScope.SAME_DAY, previous = 493_000, current = 523_000),
        )
        assertEquals(
            "이번 달 지출, 523,000원, 지출의 42퍼센트, 12건, 한 번에 평균 20,000원, 지난달 이맘때보다 30,000원 늘었어요",
            detailDescription(state, "이번 달"),
        )
        // 이 달에 없으면 금액과 증감만
        assertEquals(
            "9월 지출, 0원, 지난달보다 25,000원 줄었어요",
            detailDescription(
                state.copy(amount = 0, entry = null, change = EntryChange(ComparisonScope.WHOLE_MONTH, previous = 25_000, current = 0)),
                "9월",
            ),
        )
    }

    @Test
    fun `지운 분류와 지운 결제수단은 거래가 어디로 갔는지 알린다`() {
        assertEquals("지운 분류의 거래는 '기타'로 옮겨졌어요", missingHint(StatsDimension.EXPENSE_CATEGORY))
        assertEquals("지운 분류의 거래는 '기타'로 옮겨졌어요", missingHint(StatsDimension.INCOME_CATEGORY))
        assertEquals("지운 결제수단의 거래는 '결제수단 없음'으로 옮겨졌어요", missingHint(StatsDimension.PAYMENT_METHOD))
    }

    @Test
    fun `교차 비중 제목과 거래 목록 문구`() {
        assertEquals("결제수단별", crossTitle(StatsDimension.EXPENSE_CATEGORY))
        assertEquals("분류별", crossTitle(StatsDimension.PAYMENT_METHOD))
        assertEquals(null, crossTitle(StatsDimension.INCOME_CATEGORY))
        assertEquals("거래 12건", transactionsTitle(12))
        assertEquals("9월에는 이 분류로 적은 거래가 없어요", noTransactionsText("9월", StatsDimension.EXPENSE_CATEGORY))
        assertEquals("이번 달에는 이 결제수단으로 적은 거래가 없어요", noTransactionsText("이번 달", StatsDimension.PAYMENT_METHOD))
    }

    @Test
    fun `6개월 막대 칸은 달, 전체 금액, 건수를 읽는다`() {
        assertEquals(
            "2026년 7월, 83,000원, 5건",
            trendSlotDescription(DetailMonth(YearMonth.of(2026, 7), 83_000, 5, beforeFirstRecord = false)),
        )
        assertEquals(
            "2026년 7월, +12,000원, 2건",
            trendSlotDescription(DetailMonth(YearMonth.of(2026, 7), -12_000, 2, beforeFirstRecord = false)),
        )
        assertEquals("2026년 3월, 기록을 시작하기 전", trendSlotDescription(DetailMonth(YearMonth.of(2026, 3), 0, 0, beforeFirstRecord = true)))
    }

    @Test
    fun `교차 비중의 줄은 분류 상세면 결제수단, 결제수단 상세면 지출 분류다`() {
        assertEquals(StatsDimension.PAYMENT_METHOD, StatsDimension.EXPENSE_CATEGORY.crossDimension)
        assertEquals(StatsDimension.EXPENSE_CATEGORY, StatsDimension.PAYMENT_METHOD.crossDimension)
        assertEquals(null, StatsDimension.INCOME_CATEGORY.crossDimension)
    }
}
