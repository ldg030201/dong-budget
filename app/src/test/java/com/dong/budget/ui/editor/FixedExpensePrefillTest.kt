package com.dong.budget.ui.editor

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.PrefillSource
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.fixed.FixedExpenseItem
import com.dong.budget.ui.fixed.FixedStatus
import com.dong.budget.ui.fixed.KoreanCalendar
import com.dong.budget.ui.fixed.LAST_DAY
import com.dong.budget.ui.fixed.buildFixedExpenses
import com.dong.budget.ui.fixed.fixedHistoryEnd
import com.dong.budget.ui.fixed.fixedHistoryStart
import com.dong.budget.ui.fixed.usualDateOf
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** 화면 모델처럼 [month] 를 [today] 에 볼 때 읽는 행으로 계산한 그 가게 하나 */
    private fun view(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate): FixedExpenseItem {
        val read = rows.filter {
            !it.localDate().isAfter(today) && YearMonth.from(it.localDate()) in fixedHistoryStart(month)..fixedHistoryEnd(month, today)
        }
        val board = buildFixedExpenses(month, today, read)
        return (board.due + board.paid + board.notThisMonth + board.stopped).single()
    }

    /** 등록창을 고치지 않고 그대로 저장한 거래 */
    private fun saved(prefill: EditorPrefill): TransactionListItem = tx(
        at(prefill.occurredAtMillis).toString(),
        prefill.amount,
        categoryId = 4,
        paymentId = prefill.paymentMethodId,
        merchant = prefill.merchant,
    )

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
    fun `밀린 몫을 함께 낸 달 뒤에는 그 달 합이 아니라 한 번 낸 금액으로 채운다`() {
        val rows =
            listOf("2026-06-25", "2026-07-25", "2026-09-05", "2026-09-25").map {
                tx(it, 17_000, categoryId = 4, merchant = "넷플릭스")
            }
        val today = day("2026-10-26")
        assertEquals(17_000L, fixedExpensePrefill(item(rows, today), today).amount)
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

    @Test
    fun `c1 - 평소 날짜 전에 등록하기로 오늘 날짜에 그대로 저장하면 이번 달이 냈어요가 되고 낼 돈이 두 배가 되지 않는다`() {
        val october = YearMonth.of(2026, 10)
        val today = day("2026-10-02")
        val before = view(netflix, october, today)
        assertEquals(FixedStatus.DUE, before.status)
        val rows = netflix + saved(fixedExpensePrefill(before, today))
        val after = view(rows, october, today)
        assertEquals(FixedStatus.PAID, after.status)
        assertEquals(17_000L, after.amount)
        assertEquals(1, after.lastPaidCount)
        // 9월 몫은 그대로 한 번이고, 다음 달 낼 돈과 등록하기 금액은 한 번 낸 금액이다
        val september = view(rows, YearMonth.of(2026, 9), today)
        assertEquals(FixedStatus.PAID, september.status)
        assertEquals(1, september.lastPaidCount)
        val november = view(rows, YearMonth.of(2026, 11), day("2026-11-03"))
        assertEquals(FixedStatus.DUE, november.status)
        assertEquals(17_000L, november.amount)
        assertEquals(null, november.missedMonth)
    }

    @Test
    fun `c1 - 앞 달 몫을 낸 뒤 평소 날짜 전 어느 날에 등록해도 저장하면 그 달이 냈어요다`() {
        val failures = mutableListOf<String>()
        var checked = 0
        var waited = 0
        listOf(1, 10, 25, 28, 30, LAST_DAY).forEach { usual ->
            val dues = generateSequence(YearMonth.of(2025, 10)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.of(2027, 12) }
                .associateWith { KoreanCalendar.nextBusinessDay(usualDateOf(it, usual)) }
            val history = dues.values.map { tx("${it}T09:00", 50_000, categoryId = 4, paymentId = 10, merchant = "자동이체") }
            generateSequence(YearMonth.of(2026, 10)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.of(2027, 12) }.forEach { month ->
                generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { it < usualDateOf(month, usual) }.forEach { today ->
                    val known = history.filter { it.localDate().isBefore(today) }
                    val before = view(known, month, today)
                    val rows = known + saved(fixedExpensePrefill(before, today))
                    val after = view(rows, month, today)
                    val previous = month.minusMonths(1)
                    if (!dues.getValue(previous).isBefore(today)) {
                        // 앞 달 몫이 쉬는 날로 이번 달 초에 밀려 아직 안 나갔으면 줄이 그 차례를 함께 알리고, 적은 결제는 그 달 몫이 된다
                        waited++
                        if (before.waitingMonth != previous || view(rows, previous, today).status != FixedStatus.PAID ||
                            after.waitingMonth != null
                        ) {
                            failures += "${usual}일 $today(앞 달 기다림): ${before.waitingMonth} -> ${after.status} ${after.waitingMonth}"
                        }
                        return@forEach
                    }
                    checked++
                    if (before.status != FixedStatus.DUE || after.status != FixedStatus.PAID || after.amount != 50_000L) {
                        failures += "${usual}일 $today: ${before.status} -> ${after.status} ${after.amount}"
                    }
                }
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
        assertTrue("$checked", checked > 1_000)
        assertTrue("$waited", waited > 5)
    }

    @Test
    fun `c1 - 앞 달 몫도 놓쳤으면 등록하기를 누른 줄이 그 차례를 함께 알리고 적은 결제는 앞 달 몫이 된다`() {
        val rows = listOf(tx("2026-08-25T07:30", 17_000, categoryId = 4, paymentId = 12, merchant = "넷플릭스"))
        val october = YearMonth.of(2026, 10)
        val today = day("2026-10-02")
        val before = view(rows, october, today)
        assertEquals(FixedStatus.DUE, before.status)
        assertEquals(YearMonth.of(2026, 9), before.missedMonth)
        val saved = rows + saved(fixedExpensePrefill(before, today))
        assertEquals(FixedStatus.PAID, view(saved, YearMonth.of(2026, 9), today).status)
        val after = view(saved, october, today)
        assertEquals(FixedStatus.DUE, after.status)
        assertNull(after.missedMonth)
    }

    @Test
    fun `s1 - 일부만 냈으면 남은 차례의 금액과 날짜로 채우고 그대로 저장하면 냈어요다`() {
        // 보험에 매달 3일 50,000원 · 28일 30,000원을 낸다. 10월에는 5일 50,000원만 냈다.
        val rows = generateSequence(YearMonth.of(2026, 4)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.of(2026, 9) }.flatMap { month ->
            listOf(3 to 50_000L, 28 to 30_000L).map { (usual, amount) ->
                tx(
                    "${KoreanCalendar.nextBusinessDay(usualDateOf(month, usual))}T08:00",
                    amount,
                    categoryId = 4,
                    paymentId = 10,
                    merchant = "보험",
                )
            }
        }.toList() + tx("2026-10-05T08:00", 50_000, categoryId = 4, paymentId = 10, merchant = "보험")
        val october = YearMonth.of(2026, 10)
        listOf(day("2026-10-20"), day("2026-10-30")).forEach { today ->
            val before = view(rows, october, today)
            val prefill = fixedExpensePrefill(before, today)
            assertEquals("$today", 30_000L, prefill.amount)
            assertEquals("$today", minOf(today, day("2026-10-28")), at(prefill.occurredAtMillis).toLocalDate())
            val after = view(rows + saved(prefill), october, today)
            assertEquals("$today", FixedStatus.PAID, after.status)
            assertEquals("$today", 80_000L, after.amount)
        }
    }
}
