package com.dong.budget.ui.history

import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.Totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class HistoryTest {
    private val today = day("2026-10-06")
    private val lunch =
        tx("2026-10-02T12:10", 9_000, merchant = "김밥천국 강남점", categoryId = 1, categoryName = "식비", paymentId = 1, paymentName = "하나카드")
    private val coffee =
        tx("2026-10-02T15:00", 4_500, merchant = "스타벅스", categoryId = 1, categoryName = "식비", paymentId = 2, paymentName = "카카오페이")
    private val rent =
        tx("2026-09-25", 500_000, merchant = "월세", categoryId = 2, categoryName = "고정지출", paymentId = 3, paymentName = "계좌이체")
    private val salary = tx("2026-09-25T09:00", 3_000_000, type = INCOME, merchant = "월급", categoryId = 3, categoryName = "급여")
    private val refund = tx("2025-12-30", 4_500, type = REFUND, merchant = "스타벅스", categoryId = 1, categoryName = "식비")
    private val all = listOf(lunch, coffee, rent, salary, refund)
    private val index = searchIndex(all)

    @Test
    fun `검색어가 없으면 모든 거래를 최근 달부터, 달 안은 날짜별로 묶는다`() {
        val state = buildHistory(index, "", today)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2025, 12)), state.months.map { it.month })
        val october = state.months.first()
        assertEquals(listOf(day("2026-10-02")), october.days.map { it.date })
        // 같은 날 안에서는 늦은 시각이 먼저
        assertEquals(listOf(coffee, lunch), october.days.single().items)
        assertEquals(Totals(expense = 13_500), october.totals)
        assertEquals(Totals(expense = 500_000, income = 3_000_000), state.months[1].totals)
        assertEquals(5, state.count)
        assertTrue(state.hasAny)
        assertFalse(state.searching)
    }

    @Test
    fun `가게·분류·결제수단·거래 종류로 찾고, 큰 글자 작은 글자를 가리지 않는다`() {
        assertEquals(listOf(coffee, refund), buildHistory(index, "스타벅스", today).matched())
        assertEquals(listOf(coffee, lunch, refund), buildHistory(index, "식비", today).matched())
        assertEquals(listOf(coffee), buildHistory(index, "카카오", today).matched())
        assertEquals(listOf(refund), buildHistory(index, "환불", today).matched())
        assertEquals(listOf(salary), buildHistory(index, "수입", today).matched())
        val gs = tx("2026-10-03", 1_200, merchant = "GS25 역삼점")
        assertEquals(listOf(gs), buildHistory(searchIndex(listOf(gs)), "gs25", today).matched())
    }

    @Test
    fun `빈칸으로 나눈 낱말은 모두 맞아야 한다`() {
        assertEquals(listOf(coffee), buildHistory(index, "스타벅스 카카오페이", today).matched())
        assertEquals(emptyList<Any>(), buildHistory(index, "스타벅스 하나카드", today).matched())
        // 앞뒤 빈칸만 있으면 찾지 않는 것과 같다
        assertFalse(buildHistory(index, "   ", today).searching)
        assertEquals(5, buildHistory(index, "   ", today).count)
    }

    @Test
    fun `숫자만 있는 낱말은 금액으로도 찾는다(쉼표와 원은 빼고 본다)`() {
        assertEquals(listOf(rent), buildHistory(index, "500,000원", today).matched())
        assertEquals(listOf(coffee, refund), buildHistory(index, "4500", today).matched())
        // 금액 일부도 맞는다
        assertEquals(listOf(salary), buildHistory(index, "3,000,000", today).matched())
        // 숫자 밖의 글자가 섞이면 금액으로 보지 않는다('9월' 은 9,000원에 맞지 않는다)
        assertEquals(emptyList<Any>(), buildHistory(index, "9월", today).matched())
    }

    @Test
    fun `찾은 거래 수와 합을 낸다`() {
        val state = buildHistory(index, "스타벅스", today)
        assertTrue(state.searching)
        assertEquals(2, state.count)
        // 지출 4,500 − 환불 4,500
        assertEquals(Totals(expense = 0), state.totals)
    }

    @Test
    fun `저축은 지출로 찾히지 않고 저축으로 찾히며 합계에서는 저축 칸에 모인다`() {
        // 분류 이름을 '적금통장' 으로 바꾼 저축 분류. 코드가 그대로라 저축이다.
        val deposit =
            tx(
                "2026-10-03",
                300_000,
                merchant = "카카오뱅크",
                categoryId = 7,
                categoryName = "적금통장",
                categoryCode = SAVINGS_CATEGORY_CODE,
            )
        val withdraw =
            tx(
                "2026-10-04",
                100_000,
                REFUND,
                merchant = "카카오뱅크",
                categoryId = 7,
                categoryName = "적금통장",
                categoryCode = SAVINGS_CATEGORY_CODE,
            )
        val index = searchIndex(all + deposit + withdraw)

        assertEquals(listOf(coffee, lunch, rent), buildHistory(index, "지출", today).matched())
        assertEquals(listOf(withdraw, deposit), buildHistory(index, "저축", today).matched())
        // 환불 낱말도 저축 환불은 저축이다
        assertEquals(listOf(refund), buildHistory(index, "환불", today).matched())

        val october = buildHistory(index, "", today).months.first()
        assertEquals(Totals(expense = 13_500, savings = 200_000), october.totals)
        assertEquals(Totals(savings = 200_000), buildHistory(index, "카카오뱅크", today).totals)
    }

    @Test
    fun `합계 글은 0 인 쪽을 빼고 저축은 부호 없이 따로 적는다`() {
        assertEquals("지출 -45,000원 · 수입 +3,000원", totalsText(Totals(expense = 45_000, income = 3_000)))
        assertEquals("지출 -45,000원 · 저축 300,000원", totalsText(Totals(expense = 45_000, savings = 300_000)))
        // 저축만 찾으면 저축만 남는다(전에는 건수만 남았다)
        assertEquals("저축 300,000원", totalsText(Totals(savings = 300_000)))
        // 꺼낸 돈이 더 많으면 - 가 붙는다
        assertEquals("저축 -50,000원", totalsText(Totals(savings = -50_000)))
        assertNull(totalsText(Totals()))
    }

    @Test
    fun `거래가 없을 때와 찾은 것이 없을 때를 가른다`() {
        assertFalse(buildHistory(emptyList(), "", today).hasAny)
        val none = buildHistory(index, "없는가게", today)
        assertTrue(none.hasAny)
        assertTrue(none.months.isEmpty())
    }

    private fun HistoryUiState.matched() = months.flatMap { month -> month.days.flatMap { it.items } }
}
