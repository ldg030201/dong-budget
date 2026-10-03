package com.dong.budget.ui.editor

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.PrefillSource
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.fixed.FixedExpenseItem
import com.dong.budget.ui.fixed.FixedStatus
import com.dong.budget.ui.fixed.buildFixedExpenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class FixedExpensePrefillTest {
    /** [today] 가 든 달에서 본 가게 하나 */
    private fun item(rows: List<TransactionListItem>, today: LocalDate): FixedExpenseItem {
        val board = buildFixedExpenses(YearMonth.from(today), today, rows)
        return (board.due + board.paid + board.notThisMonth + board.stopped).single()
    }

    private fun at(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(BudgetTime.ZONE).toLocalDateTime()

    private val netflix =
        listOf(
            tx("2026-08-25T09:10", 17_000, categoryId = 4, paymentId = 10, merchant = "넷플 릭스"),
            tx("2026-09-25T07:30", 17_000, categoryId = 4, paymentId = 12, merchant = " 넷플릭스 "),
        )

    @Test
    fun `지난번 가게·금액·결제수단과 고정지출 분류로 채운 지출 등록창이다`() {
        val today = day("2026-10-27")
        val prefill = fixedExpensePrefill(item(netflix, today), today)
        assertEquals(PrefillSource.FIXED_EXPENSE, prefill.source)
        assertEquals(FIXED_CATEGORY_CODE, prefill.categoryCode)
        assertEquals(17_000L, prefill.amount)
        assertEquals("넷플릭스", prefill.merchant)
        assertEquals(12L, prefill.paymentMethodId)
        // 결제수단은 번호로만 고른다. 이름으로 고르기·새로 만들기·같은 가게 분류 짐작은 끈다.
        assertNull(prefill.paymentName)
        assertFalse(prefill.addMissingCard)
        assertFalse(prefill.guessCategory)
        assertNull(prefill.memo)
        // 한 달에 두 번 내는 것도 등록할 수 있게 막는 열쇠는 없다
        assertNull(prefill.dedupKey)
    }

    @Test
    fun `평소 날짜가 지났으면 그날, 시각은 지난번 거래의 시각이다`() {
        val today = day("2026-10-27")
        val prefill = fixedExpensePrefill(item(netflix, today), today)
        assertEquals(LocalDateTime.of(2026, 10, 25, 7, 30), at(prefill.occurredAtMillis))
    }

    @Test
    fun `평소 날짜가 아직이면 앞날로 적히지 않게 오늘이다`() {
        val today = day("2026-10-02")
        val prefill = fixedExpensePrefill(item(netflix, today), today)
        assertEquals(LocalDateTime.of(2026, 10, 2, 7, 30), at(prefill.occurredAtMillis))
    }

    @Test
    fun `평소 날짜가 이번 달에 없으면 말일이다`() {
        val rows =
            listOf(
                tx("2026-08-31T23:50", 50_000, categoryId = 4, merchant = "월세"),
                tx("2026-10-31", 50_000, categoryId = 4, merchant = "월세"),
            )
        // 11월은 30일까지다
        val today = day("2026-11-30")
        val prefill = fixedExpensePrefill(item(rows, today), today)
        assertEquals(LocalDateTime.of(2026, 11, 30, 12, 0), at(prefill.occurredAtMillis))
    }

    @Test
    fun `말일에 내면 30일에 낸 달이 섞여도 이번 달 말일로 채운다`() {
        val rows =
            listOf(
                tx("2026-08-31T09:00", 120_000, categoryId = 4, merchant = "관리비"),
                tx("2026-09-30T09:00", 120_000, categoryId = 4, merchant = "관리비"),
            )
        val today = day("2026-10-31")
        val prefill = fixedExpensePrefill(item(rows, today), today)
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), at(prefill.occurredAtMillis))
    }

    @Test
    fun `같은 날 몇 번을 눌러도 같은 값이다`() {
        val today = day("2026-10-27")
        assertEquals(fixedExpensePrefill(item(netflix, today), today), fixedExpensePrefill(item(netflix, today), today))
    }

    @Test
    fun `가게 이름 없이 묶인 것은 가게 칸을 비우고, 결제수단이 없으면 비워 둔다`() {
        val today = day("2026-10-27")
        val nameless = item(listOf(tx("2026-09-05", 3_000, categoryId = 4, merchant = null)), today)
        assertEquals(FixedStatus.DUE, nameless.status)
        val prefill = fixedExpensePrefill(nameless, today)
        assertEquals("", prefill.merchant)
        assertNull(prefill.paymentMethodId)
    }

    @Test
    fun `한 달에 여러 번 낸 합이 12자리를 넘으면 등록창이 받는 가장 큰 금액으로 자른다`() {
        val rows = List(2) { tx("2026-09-05", 900_000_000_000, categoryId = 4, merchant = "큰돈") }
        val today = day("2026-10-27")
        assertEquals(999_999_999_999L, fixedExpensePrefill(item(rows, today), today).amount)
    }
}
