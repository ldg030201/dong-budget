package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedExpenseTextTest {
    private val october = YearMonth.of(2026, 10)
    private val today = day("2026-10-12")

    private fun item(rows: List<TransactionListItem>, month: YearMonth = october, today: LocalDate = this.today): FixedExpenseItem {
        val board = buildFixedExpenses(month, today, rows)
        return (board.due + board.paid + board.notThisMonth + board.stopped).single()
    }

    private fun paid(vararg dates: String, amount: Long = 17_000, paymentId: Long? = 10) =
        dates.map { tx(it, amount, categoryId = 4, paymentId = paymentId, merchant = "넷플릭스", paymentName = paymentId?.let { "하나카드" }) }

    @Test
    fun `주기는 기호 없이 말로 적는다`() {
        assertEquals("매달", cadenceText(1))
        assertEquals("2달마다", cadenceText(2))
        assertEquals("10달마다", cadenceText(10))
        assertEquals("매년", cadenceText(YEARLY))
    }

    @Test
    fun `평소 날은 쯤을 붙이고 31일은 말일이다`() {
        assertEquals("1일쯤", usualDayText(1))
        assertEquals("30일쯤", usualDayText(30))
        assertEquals("말일쯤", usualDayText(31))
    }

    @Test
    fun `아직 안 냈어요 줄은 평소 언제 어느 카드로 내는지 적는다`() {
        val due = item(paid("2026-08-25", "2026-09-25"))
        assertEquals("매달 25일쯤 · 하나카드", rowSubtitle(due, october))
        // 결제수단을 지웠으면 언제만 적는다
        assertEquals("매달 25일쯤", rowSubtitle(item(paid("2026-09-25", paymentId = null)), october))
    }

    @Test
    fun `매년 내는 것은 무슨 달인지도 적는다`() {
        val yearly = item(paid("2025-03-02", "2026-03-02"))
        assertEquals("매년 3월 2일쯤 · 하나카드", rowSubtitle(yearly, october))
        assertEquals("다음은 2027년 3월에 내요", rowNote(yearly, october, today)?.text)
    }

    @Test
    fun `몇 달마다 내는 것은 다음 낼 달을 덧붙인다`() {
        // 7월 31일 · 9월 30일은 둘 다 말일이다
        val every2 = item(paid("2026-07-31", "2026-09-30"))
        assertEquals("2달마다 말일쯤 · 하나카드", rowSubtitle(every2, october))
        assertEquals(RowNote("다음은 11월에 내요", NoteTone.PLAIN), rowNote(every2, october, today))
    }

    @Test
    fun `말일에 내면 30일에 낸 달이 섞여도 말일쯤이고 그달 말일이 오늘이면 지났다고 하지 않는다`() {
        val lastDay = item(paid("2026-08-31", "2026-09-30"), today = day("2026-10-31"))
        assertEquals("매달 말일쯤 · 하나카드", rowSubtitle(lastDay, october))
        assertEquals(RowNote("오늘 낼 차례예요", NoteTone.TODAY), rowNote(lastDay, october, today))
    }

    @Test
    fun `냈어요 줄은 낸 날을 적고 한 달에 여러 번이면 몇 번 더 냈는지 붙인다`() {
        assertEquals("10월 3일 · 하나카드", rowSubtitle(item(paid("2026-10-03")), october))
        assertEquals("10월 3일 외 2번 · 하나카드", rowSubtitle(item(paid("2026-10-03", "2026-10-03", "2026-10-20")), october))
    }

    @Test
    fun `냈어요 줄은 지난번과 금액이 다를 때만 오르내림을 덧붙인다`() {
        val up = item(paid("2026-09-03") + paid("2026-10-03", amount = 18_000))
        assertEquals(RowNote("지난번보다 1,000원 올랐어요", NoteTone.PLAIN), rowNote(up, october, today))
        val down = item(paid("2026-09-03") + paid("2026-10-03", amount = 13_500))
        assertEquals("지난번보다 3,500원 내렸어요", rowNote(down, october, today)?.text)
        assertNull(rowNote(item(paid("2026-09-03", "2026-10-03")), october, today))
        // 처음 낸 달은 견줄 것이 없다
        assertNull(rowNote(item(paid("2026-10-03")), october, today))
    }

    @Test
    fun `냈어요 줄은 지난번과 낸 횟수가 다르면 오르내림을 덧붙이지 않는다`() {
        // 9월 몫을 10월 2일에 늦게 내고 10월 몫도 냈다. 값이 오른 것이 아니다.
        val twice = item(paid("2026-08-25", "2026-10-02", "2026-10-25"))
        assertEquals("34,000원", rowAmount(twice))
        assertNull(rowNote(twice, october, today))
    }

    @Test
    fun `아직 안 냈어요 줄은 평소 날짜가 지났거나 오늘이면 알린다`() {
        val rows = paid("2026-08-10", "2026-09-10")
        assertEquals(RowNote("평소보다 2일 지났어요", NoteTone.WARNING), rowNote(item(rows), october, today))
        assertEquals(RowNote("오늘 낼 차례예요", NoteTone.TODAY), rowNote(item(rows, today = day("2026-10-10")), october, today))
        assertNull(rowNote(item(rows, today = day("2026-10-09")), october, today))
    }

    @Test
    fun `지난 차례도 놓쳤으면 그 달을 알린다`() {
        val missed = item(paid("2026-07-10", "2026-08-10"))
        assertEquals(RowNote("9월 차례도 안 냈어요", NoteTone.WARNING), rowNote(missed, october, today))
        // 해가 바뀌면 연도를 붙인다
        val acrossYear = item(paid("2025-10-10", "2025-11-10"), month = YearMonth.of(2026, 1), today = day("2026-01-05"))
        assertEquals("2025년 12월 차례도 안 냈어요", rowNote(acrossYear, YearMonth.of(2026, 1), day("2026-01-05"))?.text)
    }

    @Test
    fun `몇 달마다·매년 내는 것은 놓친 차례를 도 없이 알린다`() {
        // 2달마다: 6월·8월에 내고 11월을 보면 10월 차례를 놓쳤다. 11월은 낼 차례가 아니라 '10월 차례도' 가 아니다.
        val november = YearMonth.of(2026, 11)
        val every2 = item(paid("2026-06-25", "2026-08-25"), month = november, today = day("2026-11-05"))
        assertEquals(RowNote("10월 차례를 아직 안 냈어요", NoteTone.WARNING), rowNote(every2, november, day("2026-11-05")))
        // 지난 달로 보면 그 달까지의 일이라 '아직' 을 빼고 적는다
        assertEquals("10월 차례를 안 냈어요", rowNote(every2, november, day("2027-01-10"))?.text)
        // 매년: 2024년·2025년 10월에 내고 2026년 11월을 본다
        val yearly = item(paid("2024-10-15", "2025-10-14"), month = november, today = day("2026-11-20"))
        assertEquals("10월 차례를 아직 안 냈어요", rowNote(yearly, november, day("2026-11-20"))?.text)
        // 매달이면 이번 달도 낼 차례라 '도' 가 맞다
        assertEquals("9월 차례도 안 냈어요", rowNote(item(paid("2026-07-10", "2026-08-10")), october, day("2027-01-10"))?.text)
    }

    @Test
    fun `옆 달 몫으로 옮긴 결제는 실제로 낸 날을 적고 놓쳤다거나 지났다고 하지 않는다`() {
        // 1일에 내는 것을 9월 30일에 미리 냈으면 10월 줄에 9월 30일이 보인다
        val early = item(paid("2026-07-01", "2026-08-01", "2026-09-01", "2026-09-30"), today = day("2026-10-05"))
        assertEquals("9월 30일 · 하나카드", rowSubtitle(early, october))
        assertNull(rowNote(early, october, today))
        // 말일 것이 10월 1일로 밀렸으면 9월 줄에 10월 1일이 보이고, 10월 중순엔 아직 낼 날이 안 됐다
        val lateRows = paid("2026-07-31", "2026-08-31", "2026-10-01")
        val september = YearMonth.of(2026, 9)
        assertEquals("10월 1일 · 하나카드", rowSubtitle(item(lateRows, month = september, today = day("2026-10-15")), september))
        val october15 = item(lateRows, today = day("2026-10-15"))
        assertEquals("매달 말일쯤 · 하나카드", rowSubtitle(october15, october))
        assertNull(rowNote(october15, october, today))
    }

    @Test
    fun `한동안 안 냈어요 줄은 마지막으로 낸 날을 적고 해가 다르면 연도를 붙인다`() {
        assertEquals("마지막 6월 3일 · 하나카드", rowSubtitle(item(paid("2026-05-03", "2026-06-03")), october))
        assertEquals("마지막 2025년 12월 3일 · 하나카드", rowSubtitle(item(paid("2025-12-03")), october))
        assertNull(rowNote(item(paid("2025-12-03")), october, today))
    }

    @Test
    fun `금액은 부호 없이 원까지 적는다`() {
        assertEquals("1,317,000원", rowAmount(item(paid("2026-09-25", amount = 1_317_000))))
    }

    @Test
    fun `등록하기는 화면 읽기에서 가게 이름을 붙여 읽는다`() {
        assertEquals("넷플릭스 등록하기", registerLabel("넷플릭스"))
    }

    @Test
    fun `묶음 제목은 지난 달을 보면 끝난 달에 맞게 적는다`() {
        assertEquals("아직 안 냈어요", statusTitle(FixedStatus.DUE, october, today))
        assertEquals("안 냈어요", statusTitle(FixedStatus.DUE, YearMonth.of(2026, 9), today))
        assertEquals("냈어요", statusTitle(FixedStatus.PAID, october, today))
        assertEquals("이번 달엔 안 내요", statusTitle(FixedStatus.NOT_THIS_MONTH, october, today))
        assertEquals("9월엔 낼 차례가 아니었어요", statusTitle(FixedStatus.NOT_THIS_MONTH, YearMonth.of(2026, 9), today))
        assertEquals("2025년 12월엔 낼 차례가 아니었어요", statusTitle(FixedStatus.NOT_THIS_MONTH, YearMonth.of(2025, 12), today))
        assertEquals("한동안 안 냈어요", statusTitle(FixedStatus.STOPPED, october, today))
        assertEquals("3개", countText(3))
    }

    @Test
    fun `요약 문장은 낼 차례였던 것 중 몇 개를 냈는지 센다`() {
        val partial = FixedExpenseBoard(due = listOf(item(paid("2026-09-25"))), paid = listOf(item(paid("2026-10-03"))))
        assertEquals("2개 중 1개 냈어요", countSentence(partial, october, today))
        val all = FixedExpenseBoard(paid = listOf(item(paid("2026-10-03")), item(paid("2026-10-03")).copy(key = "다른")))
        assertEquals("2개 모두 냈어요", countSentence(all, october, today))
        assertEquals("모두 냈어요", countSentence(FixedExpenseBoard(paid = listOf(item(paid("2026-10-03")))), october, today))
        val none = FixedExpenseBoard(notThisMonth = listOf(item(paid("2026-07-31", "2026-09-30"))))
        assertEquals("이번 달에 낼 고정지출이 없어요", countSentence(none, october, today))
        assertEquals("9월엔 낼 고정지출이 없었어요", countSentence(none, YearMonth.of(2026, 9), today))
    }

    @Test
    fun `요약 판은 한 문장으로 읽고 금액은 줄이지 않는다`() {
        val board =
            FixedExpenseBoard(
                due = listOf(item(paid("2026-09-25", amount = 1_317_000))),
                paid = listOf(item(paid("2026-10-03", amount = 85_000))),
            )
        assertEquals("이번 달 고정지출. 2개 중 1개 냈어요. 낸 돈 85,000원, 낼 돈 1,317,000원", summarySpoken(board, october, today))
        assertEquals(
            "9월 고정지출. 2개 중 1개 냈어요. 낸 돈 85,000원, 안 낸 돈 1,317,000원",
            summarySpoken(board, YearMonth.of(2026, 9), today),
        )
        assertEquals("이번 달 고정지출. 이번 달에 낼 고정지출이 없어요", summarySpoken(FixedExpenseBoard(), october, today))
    }

    @Test
    fun `빈 화면 제목은 지난 달이면 그 달까지는 없었다고 적는다`() {
        assertEquals("고정지출로 등록한 지출이 없어요", emptyTitle(october, today))
        assertEquals("8월까지는 고정지출로 등록한 지출이 없어요", emptyTitle(YearMonth.of(2026, 8), today))
    }
}
