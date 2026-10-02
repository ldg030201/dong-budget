package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedExpensesTest {
    private val october = YearMonth.of(2026, 10)
    private val today = day("2026-10-12")

    private fun board(rows: List<TransactionListItem>, month: YearMonth = october, today: LocalDate = this.today) =
        buildFixedExpenses(month, today, rows)

    /** 결과가 하나뿐일 때 그 가게 */
    private fun only(rows: List<TransactionListItem>, month: YearMonth = october, today: LocalDate = this.today): FixedExpenseItem {
        val board = board(rows, month, today)
        val all = board.due + board.paid + board.notThisMonth + board.stopped
        assertEquals(1, all.size)
        return all.single()
    }

    /** [dates] 마다 같은 가게에 [amount] 를 낸 기록 */
    private fun paid(merchant: String, amount: Long, vararg dates: String, paymentId: Long? = 10) =
        dates.map { tx(it, amount, categoryId = 4, paymentId = paymentId, merchant = merchant) }

    @Test
    fun `매달 내는 것은 지난달에 냈고 이번 달에 아직이면 아직 안 냈어요다`() {
        val item = only(paid("넷플릭스", 17_000, "2026-07-25", "2026-08-25", "2026-09-25"))
        assertEquals(FixedStatus.DUE, item.status)
        assertEquals(1, item.cadence)
        assertEquals(25, item.usualDay)
        assertEquals(17_000L, item.amount)
        assertEquals(day("2026-09-25"), item.lastPaidOn)
        // 평소 날짜(25일)까지 아직 13일 남았다
        assertEquals(-13, item.daysPastUsual)
        assertNull(item.missedMonth)
    }

    @Test
    fun `이번 달에 냈으면 냈어요이고 낸 날과 금액, 지난번 금액을 안다`() {
        val item = only(paid("넷플릭스", 17_000, "2026-08-25", "2026-09-25") + paid("넷플릭스", 13_500, "2026-10-03"))
        assertEquals(FixedStatus.PAID, item.status)
        assertEquals(day("2026-10-03"), item.lastPaidOn)
        assertEquals(13_500L, item.amount)
        assertEquals(17_000L, item.previousAmount)
        assertNull(item.daysPastUsual)
    }

    @Test
    fun `2달마다 내는 것은 사이 달엔 이번 달엔 안 내요, 차례인 달엔 아직 안 냈어요다`() {
        val rows = paid("관리비", 120_000, "2026-03-10", "2026-05-10", "2026-07-10", "2026-09-10")
        val between = only(rows)
        assertEquals(2, between.cadence)
        assertEquals(FixedStatus.NOT_THIS_MONTH, between.status)
        assertEquals(YearMonth.of(2026, 11), between.nextMonth)
        val due = only(rows, month = YearMonth.of(2026, 11), today = day("2026-11-05"))
        assertEquals(FixedStatus.DUE, due.status)
        assertNull(due.nextMonth)
    }

    @Test
    fun `매년 내는 것은 그 앞 해 결제까지 읽어 매년인 줄 안다`() {
        val rows = paid("도메인", 22_000, "2024-10-15", "2025-10-14")
        // 2026년 10월에 낼 차례다. 읽는 범위가 2024년 9월부터라 2024년 결제도 들어온다.
        assertEquals(YearMonth.of(2024, 9), fixedHistoryStart(october))
        val due = only(rows)
        assertEquals(YEARLY, due.cadence)
        assertEquals(FixedStatus.DUE, due.status)
        assertEquals(14, due.usualDay)
        // 한 달 늦은 11월에도 아직 안 냈어요이고, 10월 차례를 놓쳤다고 알린다
        val late = only(rows, month = YearMonth.of(2026, 11), today = day("2026-11-20"))
        assertEquals(FixedStatus.DUE, late.status)
        assertEquals(YearMonth.of(2026, 10), late.missedMonth)
        // 사이 달엔 다음 낼 달을 안다
        val between = only(rows, month = YearMonth.of(2026, 3), today = day("2026-03-01"))
        assertEquals(FixedStatus.NOT_THIS_MONTH, between.status)
        assertEquals(YearMonth.of(2026, 10), between.nextMonth)
    }

    @Test
    fun `간격이 11달 넘게 벌어지면 매년, 10달까지는 그 달마다로 본다`() {
        assertEquals(YEARLY, only(paid("가", 1_000, "2025-01-05", "2025-12-05"), month = YearMonth.of(2026, 2)).cadence)
        assertEquals(10, only(paid("가", 1_000, "2025-01-05", "2025-11-05"), month = YearMonth.of(2026, 2)).cadence)
    }

    @Test
    fun `간격이 짝수 개면 짧은 쪽을 주기로 본다`() {
        // 간격 1, 2 → 1(매달)
        val item = only(paid("가", 1_000, "2026-06-05", "2026-07-05", "2026-09-05"))
        assertEquals(1, item.cadence)
    }

    @Test
    fun `한 달 건너뛴 적이 있어도 대부분 매달이면 매달이다`() {
        val item = only(paid("월세", 500_000, "2026-03-01", "2026-04-01", "2026-06-01", "2026-07-01", "2026-08-01", "2026-09-01"))
        assertEquals(1, item.cadence)
        assertEquals(FixedStatus.DUE, item.status)
    }

    @Test
    fun `평소 날짜는 최근 6번의 가운데 날이고 짝수 개면 이른 날이다`() {
        // 1월의 3일은 7번째 전이라 빠진다. 24, 25, 25, 26, 27, 28 → 25
        val item =
            only(
                paid(
                    "가",
                    1_000,
                    "2026-01-03",
                    "2026-02-28",
                    "2026-03-25",
                    "2026-04-26",
                    "2026-05-24",
                    "2026-06-27",
                    "2026-07-25",
                ),
                month = YearMonth.of(2026, 8),
                today = day("2026-08-01"),
            )
        assertEquals(25, item.usualDay)
    }

    @Test
    fun `평소 날짜가 그 달에 없으면 말일로 본다`() {
        val item =
            only(paid("가", 1_000, "2025-11-30", "2025-12-31", "2026-01-31"), month = YearMonth.of(2026, 2), today = day("2026-02-28"))
        assertEquals(31, item.usualDay)
        assertEquals(day("2026-02-28"), item.usualDateIn(YearMonth.of(2026, 2)))
        assertEquals(day("2028-02-29"), item.usualDateIn(YearMonth.of(2028, 2)))
        assertEquals(day("2026-04-30"), item.usualDateIn(YearMonth.of(2026, 4)))
        // 2월 말일이 오늘이라 딱 그날이다
        assertEquals(0, item.daysPastUsual)
    }

    @Test
    fun `평소 날짜를 넘겼으면 며칠 지났는지 센다`() {
        val item = only(paid("가", 1_000, "2026-08-05", "2026-09-05"))
        assertEquals(7, item.daysPastUsual)
    }

    @Test
    fun `매달 내는 것의 경계 - 1달 아직, 2달 지난 차례도 놓침, 3달 접기`() {
        val rows = paid("가", 1_000, "2026-05-10", "2026-06-10")
        val oneMonth = only(rows, month = YearMonth.of(2026, 7), today = day("2026-07-01"))
        assertEquals(FixedStatus.DUE, oneMonth.status)
        assertNull(oneMonth.missedMonth)
        val twoMonths = only(rows, month = YearMonth.of(2026, 8), today = day("2026-08-01"))
        assertEquals(FixedStatus.DUE, twoMonths.status)
        assertEquals(YearMonth.of(2026, 7), twoMonths.missedMonth)
        val threeMonths = only(rows, month = YearMonth.of(2026, 9), today = day("2026-09-01"))
        assertEquals(FixedStatus.STOPPED, threeMonths.status)
        assertNull(threeMonths.daysPastUsual)
    }

    @Test
    fun `2달마다 내는 것의 경계 - 1달 안 내는 달, 2·3달 아직, 4달 접기`() {
        val rows = paid("가", 1_000, "2026-01-10", "2026-03-10")
        val statuses = (4..7).map { only(rows, month = YearMonth.of(2026, it), today = day("2026-0$it-01")).status }
        assertEquals(listOf(FixedStatus.NOT_THIS_MONTH, FixedStatus.DUE, FixedStatus.DUE, FixedStatus.STOPPED), statuses)
    }

    @Test
    fun `한 달에 두 번 냈으면 합치고 그 달 날짜는 첫 결제일이다`() {
        val item = only(paid("통신비", 30_000, "2026-09-20", "2026-10-03", "2026-10-20"))
        assertEquals(FixedStatus.PAID, item.status)
        assertEquals(60_000L, item.amount)
        assertEquals(30_000L, item.previousAmount)
        assertEquals(day("2026-10-03"), item.lastPaidOn)
        assertEquals(2, item.lastPaidCount)
    }

    @Test
    fun `같은 날 두 번 낸 것도 그 달 하나로 합친다`() {
        val rows = paid("월세", 300_000, "2026-09-01T09:00") + paid("월세", 200_000, "2026-09-01T09:05", paymentId = 11)
        val item = only(rows)
        assertEquals(FixedStatus.DUE, item.status)
        assertEquals(500_000L, item.amount)
        assertEquals(2, item.lastPaidCount)
        assertEquals(1, item.cadence)
        // 결제수단은 가장 나중에 적은 거래를 따른다
        assertEquals(11L, item.paymentMethodId)
    }

    @Test
    fun `띄어쓰기·대소문자만 다른 가게 이름은 한 가게로 묶고 이름은 가장 최근 것이다`() {
        val rows =
            listOf(
                tx("2026-08-05", 10_000, categoryId = 4, merchant = "NETFLIX"),
                tx("2026-09-05", 10_000, categoryId = 4, merchant = "net flix"),
                tx("2026-10-05", 10_000, categoryId = 4, merchant = " Netflix "),
            )
        val item = only(rows)
        assertEquals("Netflix", item.name)
        assertEquals("Netflix", item.merchant)
        assertEquals(FixedStatus.PAID, item.status)
    }

    @Test
    fun `가게 이름 없이 적은 지출은 이름 없음 하나로 묶는다`() {
        val rows =
            listOf(
                tx("2026-09-05", 10_000, categoryId = 4, merchant = null),
                tx("2026-10-05", 10_000, categoryId = 4, merchant = "  "),
            )
        val item = only(rows)
        assertEquals(NO_MERCHANT_KEY, item.key)
        assertEquals(NO_MERCHANT_NAME, item.name)
        assertNull(item.merchant)
    }

    @Test
    fun `첫 결제 전 달에는 보이지 않는다`() {
        // 읽어 온 행에 고른 달 뒤의 거래가 섞여도 거른다
        val rows = paid("가", 1_000, "2026-11-05")
        assertTrue(board(rows).isEmpty)
    }

    @Test
    fun `처음 낸 달에는 냈어요, 다음 달엔 매달로 보고 아직 안 냈어요다`() {
        val rows = paid("헬스장", 50_000, "2026-09-15")
        val first = only(rows, month = YearMonth.of(2026, 9), today = day("2026-09-20"))
        assertEquals(FixedStatus.PAID, first.status)
        assertNull(first.previousAmount)
        val next = only(rows)
        assertEquals(FixedStatus.DUE, next.status)
        assertEquals(1, next.cadence)
    }

    @Test
    fun `아주 오래전에 한 번 낸 것은 한동안 안 냈어요로 접는다`() {
        val item = only(paid("가", 1_000, "2025-02-01"))
        assertEquals(FixedStatus.STOPPED, item.status)
        assertEquals(day("2025-02-01"), item.lastPaidOn)
    }

    @Test
    fun `12월에서 1월로 해가 넘어가도 달 수를 이어서 센다`() {
        val rows = paid("가", 9_900, "2025-11-28", "2025-12-28")
        val item = only(rows, month = YearMonth.of(2026, 1), today = day("2026-01-30"))
        assertEquals(FixedStatus.DUE, item.status)
        assertEquals(1, item.cadence)
        assertEquals(2, item.daysPastUsual)
        // 2월엔 1월 차례를 놓친 것이다
        val february = only(rows, month = YearMonth.of(2026, 2), today = day("2026-02-01"))
        assertEquals(YearMonth.of(2026, 1), february.missedMonth)
    }

    @Test
    fun `날짜를 앞으로 적어 둔 이번 달 거래도 낸 것으로 본다`() {
        val item = only(paid("가", 1_000, "2026-09-25", "2026-10-25"))
        assertEquals(FixedStatus.PAID, item.status)
        assertEquals(day("2026-10-25"), item.lastPaidOn)
    }

    @Test
    fun `지난 달을 보면 그 달까지의 기록으로만 보고 평소 날짜가 지났는지는 세지 않는다`() {
        val rows = paid("가", 1_000, "2026-07-05", "2026-08-05") + paid("가", 2_000, "2026-10-05")
        val september = only(rows, month = YearMonth.of(2026, 9))
        assertEquals(FixedStatus.DUE, september.status)
        // 10월에 낸 2,000원은 9월에서 보이지 않는다
        assertEquals(1_000L, september.amount)
        assertNull(september.daysPastUsual)
    }

    @Test
    fun `지출이 아닌 행은 거른다`() {
        val rows = paid("가", 1_000, "2026-09-05") + tx("2026-10-06", 1_000, REFUND, categoryId = 4, merchant = "가")
        assertEquals(FixedStatus.DUE, only(rows).status)
    }

    @Test
    fun `줄을 누르면 열 거래와 결제수단은 가장 최근 거래를 따른다`() {
        val rows =
            listOf(
                tx("2026-09-05T10:00", 1_000, categoryId = 4, paymentId = 10, merchant = "가", id = 101),
                tx("2026-09-05T10:00", 1_000, categoryId = 4, paymentId = 12, merchant = "가", id = 102),
            )
        val item = only(rows)
        assertEquals(102L, item.latestId)
        assertEquals(12L, item.paymentMethodId)
        assertEquals("결제12", item.paymentMethodName)
    }

    @Test
    fun `상태별로 나누고 정렬하며 낸 돈과 낼 돈을 더한다`() {
        val rows =
            paid("넷플릭스", 17_000, "2026-08-25", "2026-09-25") +
                paid("월세", 500_000, "2026-08-01", "2026-09-01") +
                // 지난 차례(9월)도 놓친 것이 맨 앞이다
                paid("보험", 80_000, "2026-07-20", "2026-08-20") +
                paid("통신비", 45_000, "2026-09-10", "2026-10-10") +
                paid("음악", 10_000, "2026-09-03", "2026-10-03") +
                paid("관리비", 100_000, "2026-05-15", "2026-07-15", "2026-09-15") +
                paid("도메인", 22_000, "2025-03-01", "2026-03-02") +
                paid("옛날 구독", 5_000, "2026-01-05", "2026-02-05") +
                paid("더 옛날 구독", 5_000, "2025-11-05", "2025-12-05")
        val board = board(rows)
        assertEquals(listOf("보험", "월세", "넷플릭스"), board.due.map { it.name })
        assertEquals(listOf("음악", "통신비"), board.paid.map { it.name })
        assertEquals(listOf("관리비", "도메인"), board.notThisMonth.map { it.name })
        assertEquals(listOf("옛날 구독", "더 옛날 구독"), board.stopped.map { it.name })
        assertEquals(55_000L, board.paidTotal)
        assertEquals(597_000L, board.dueTotal)
        assertEquals(5, board.dueCount)
    }
}
