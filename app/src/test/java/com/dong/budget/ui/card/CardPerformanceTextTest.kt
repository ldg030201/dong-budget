package com.dong.budget.ui.card

import com.dong.budget.testing.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class CardPerformanceTextTest {
    private val today = day("2026-10-03")
    private val october = YearMonth.of(2026, 10)
    private val tiers = listOf(300_000L, 700_000L)

    @Test
    fun `기간 이름과 날짜`() {
        assertEquals("10월 실적", periodName(october, today))
        assertEquals("2025년 12월 실적", periodName(YearMonth.of(2025, 12), today))
        assertEquals("10월 1일 ~ 10월 31일", periodRange(performancePeriod(october, 1)))
        assertEquals("12월 15일 ~ 1월 14일", periodRange(performancePeriod(YearMonth.of(2026, 12), 15)))
        assertEquals("10월 1일부터 10월 31일까지", spokenPeriodRange(performancePeriod(october, 1)))
    }

    @Test
    fun `이번 기간으로 돌아가는 버튼은 시작일이 1일일 때만 이번 달이라고 부른다`() {
        assertEquals("이번 달", thisPeriodLabel(performancePeriod(october, 1)))
        // 시작일이 15일이면 10월 3일의 이번 기간은 9월 실적이다. 전에는 '이번 달' 을 누르면 9월 실적이 나왔다
        assertEquals("이번 기간", thisPeriodLabel(currentPeriod(today, 15)))
        // 시작일이 말일(31)이면 2월 기간은 28일에 시작한다
        assertEquals("이번 기간", thisPeriodLabel(performancePeriod(YearMonth.of(2026, 2), 31)))
    }

    @Test
    fun `남은 날은 기호 없이 말로 적는다`() {
        assertEquals("29일 남았어요", daysLeftText(29))
        assertEquals("오늘이 마지막 날이에요", daysLeftText(1))
        assertEquals("29일 남았어요 · 하루에 6,088원씩 쓰면 돼요", daysLeftLine(29, 6_088))
        assertEquals("29일 남았어요", daysLeftLine(29, null))
    }

    @Test
    fun `마지막 날은 하루에 얼마씩이 아니라 오늘 얼마 더 쓰면 되는지 알린다`() {
        // 전에는 "오늘이 마지막 날이에요 · 하루에 176,550원씩 쓰면 돼요" 였다
        val need = dailyNeed(TierProgress(123_450, tiers), daysLeft = 1)
        assertEquals("오늘이 마지막 날이에요 · 오늘 176,550원 더 쓰면 돼요", daysLeftLine(1, need))
        assertEquals("오늘이 마지막 날이에요", daysLeftLine(1, null))
        assertEquals("2일 남았어요 · 하루에 88,275원씩 쓰면 돼요", daysLeftLine(2, dailyNeed(TierProgress(123_450, tiers), daysLeft = 2)))
    }

    @Test
    fun `쓴 돈은 1원까지, 환불이 더 많으면 그렇게 읽는다`() {
        assertEquals("123,450원", spentText(123_450))
        // 앱의 다른 곳처럼 돌려받은 쪽은 + 다. 전에는 "-1,000원" 이라 같은 화면의 결제 줄(-)과 부호가 겹쳤다
        assertEquals("+1,000원", spentText(-1_000))
        assertEquals("0원", spentText(0))
        assertEquals("123,450원", spokenSpent(123_450))
        assertEquals("환불받은 돈이 1,000원 더 많아요", spokenSpent(-1_000))
    }

    @Test
    fun `구간은 만 단위로 끊되 1원까지 적는다`() {
        assertEquals("30만원", tierName(300_000))
        assertEquals("30만 5,000원", tierName(305_000))
        assertEquals("1억원", tierName(100_000_000))
    }

    @Test
    fun `구간이 여럿일 때 이번 기간 문장`() {
        assertEquals("첫 구간 30만원까지 176,550원 남았어요", tierSentence(TierProgress(123_450, tiers), past = false))
        assertEquals("30만원 구간을 채웠어요 · 70만원까지 176,550원 남았어요", tierSentence(TierProgress(523_450, tiers), past = false))
        // 정확히 구간 금액이면 채운 것이다
        assertEquals("30만원 구간을 채웠어요 · 70만원까지 400,000원 남았어요", tierSentence(TierProgress(300_000, tiers), past = false))
        assertEquals("가장 높은 구간 70만원을 채웠어요", tierSentence(TierProgress(800_000, tiers), past = false))
    }

    @Test
    fun `지난 기간은 모자랐어요로 끝난다`() {
        assertEquals("첫 구간 30만원까지 176,550원 모자랐어요", tierSentence(TierProgress(123_450, tiers), past = true))
        assertEquals("실적 30만원까지 1,000원 모자랐어요", tierSentence(TierProgress(299_000, listOf(300_000)), past = true))
    }

    @Test
    fun `구간이 하나면 실적이라고 부르고, 없으면 빈 글이다`() {
        val one = listOf(300_000L)
        assertEquals("실적 30만원까지 176,550원 남았어요", tierSentence(TierProgress(123_450, one), past = false))
        assertEquals("실적 30만원을 채웠어요", tierSentence(TierProgress(300_000, one), past = false))
        assertEquals("", tierSentence(TierProgress(300_000, emptyList()), past = false))
    }

    @Test
    fun `지난 기간 한 줄`() {
        val september = YearMonth.of(2026, 9)
        assertEquals("9월 실적 523,000원 · 30만원 구간을 채웠어요", previousLine(september, today, TierProgress(523_000, tiers)))
        assertEquals("9월 실적 120,000원 · 30만원까지 180,000원 모자랐어요", previousLine(september, today, TierProgress(120_000, tiers)))
        assertEquals("9월 실적 0원 · 30만원까지 300,000원 모자랐어요", previousLine(september, today, TierProgress(0, tiers)))
        assertEquals("9월 실적 +1,000원 · 30만원까지 301,000원 모자랐어요", previousLine(september, today, TierProgress(-1_000, tiers)))
        // 카드를 쓰기 전 기간
        assertEquals("9월 실적은 기록이 없어요", previousLine(september, today, null))
        assertEquals("2025년 12월 실적은 기록이 없어요", previousLine(YearMonth.of(2025, 12), today, null))
    }

    @Test
    fun `카드 판은 한 번에 읽는다`() {
        val item = TrackedCard(
            card = CardInfo(id = 2, name = "하나카드", icon = "credit_card", color = "blue"),
            period = performancePeriod(october, 1),
            daysLeft = 29,
            progress = TierProgress(123_450, tiers),
            previousMonth = YearMonth.of(2026, 9),
            previous = TierProgress(523_000, tiers),
        )
        assertEquals(
            "하나카드, 10월 1일부터 10월 31일까지, 10월 실적 123,450원, 29일 남았어요. " +
                "첫 구간 30만원까지 176,550원 남았어요. 9월 실적 523,000원, 30만원 구간을 채웠어요",
            trackedCardDescription(item, today),
        )
        // 지난 기간도 이번 기간처럼 환불이 더 많으면 부호 대신 말로 읽는다. 전에는 '9월 실적 -1,000원' 을 그대로 읽었다
        assertEquals(
            "하나카드, 10월 1일부터 10월 31일까지, 10월 실적 123,450원, 29일 남았어요. " +
                "첫 구간 30만원까지 176,550원 남았어요. 9월 실적 환불받은 돈이 1,000원 더 많아요, 30만원까지 301,000원 모자랐어요",
            trackedCardDescription(item.copy(previous = TierProgress(-1_000, tiers)), today),
        )
    }

    @Test
    fun `탭 판의 남은 날은 기간 날짜 뒤가 아니라 기간 이름 옆에 둔다`() {
        val item = TrackedCard(
            card = CardInfo(id = 2, name = "하나카드", icon = "credit_card", color = "blue"),
            period = performancePeriod(october, 1),
            daysLeft = 30,
            progress = TierProgress(0, tiers),
            previousMonth = YearMonth.of(2026, 9),
            previous = TierProgress(0, tiers),
        )
        // 전에는 카드 이름 아래 '10월 1일 ~ 10월 31일 · 30일 남았어요' 가 320dp 에서 '30일 / 남았어요' 로 꺾였다
        assertEquals("10월 실적 · 30일 남았어요", trackedHeadline(item, today))
        assertEquals("10월 실적 · 오늘이 마지막 날이에요", trackedHeadline(item.copy(daysLeft = 1), today))
    }

    @Test
    fun `실적을 안 적은 카드 줄`() {
        val card = CardInfo(id = 1, name = "체크카드", icon = "credit_card", color = "blue")
        assertEquals("이번 달 50,000원 썼어요", untrackedSubtitle(UntrackedCard(card, performancePeriod(october, 1), 50_000)))
        assertEquals("이번 달 쓴 돈이 없어요", untrackedSubtitle(UntrackedCard(card, performancePeriod(october, 1), 0)))
        assertEquals("9월 15일부터 환불받은 돈이 더 많아요", untrackedSubtitle(UntrackedCard(card, performancePeriod(YearMonth.of(2026, 9), 15), -10)))
        assertEquals("체크카드 실적 추가", addPerformanceLabel("체크카드"))
    }

    @Test
    fun `최근 6개월 요약과 막대 한 칸`() {
        fun history(vararg spent: Long, tiers: List<Long> = this.tiers) = spent.mapIndexed { index, amount ->
            PeriodSpent(performancePeriod(YearMonth.of(2026, 5 + index), 1), TierProgress(amount, tiers))
        }
        assertEquals("최근 6개월 중 2번 가장 높은 구간을 채웠어요", historySummary(history(0, 800_000, 0, 700_000, 10, 20), tiers, october))
        assertEquals("최근 6개월에는 가장 높은 구간을 채운 적이 없어요", historySummary(history(0, 0, 0, 0, 0, 0), tiers, october))
        assertEquals("최근 6개월 모두 실적을 채웠어요", historySummary(history(1, 1, 1, 1, 1, 1, tiers = listOf(1L)), listOf(1L), october))
        assertNull(historySummary(history(0, 0, 0, 0, 0, 0), emptyList(), october))

        // 기록이 없는 기간(카드를 쓰기 전)은 세지 않는다
        val partly = history(0, 0, 0, 800_000, 0, 20).mapIndexed { index, entry -> entry.copy(recorded = index >= 3) }
        assertEquals("기록한 3개월 중 1번 가장 높은 구간을 채웠어요", historySummary(partly, tiers, october))
        val reachedAll = history(0, 0, 0, 800_000, 900_000, 700_000).mapIndexed { index, entry -> entry.copy(recorded = index >= 3) }
        assertEquals("기록한 3개월 모두 가장 높은 구간을 채웠어요", historySummary(reachedAll, tiers, october))
        val onlyNow = history(0, 0, 0, 0, 0, 20).mapIndexed { index, entry -> entry.copy(recorded = index == 5) }
        assertEquals("아직 지난 기록이 없어요", historySummary(onlyNow, tiers, october))
        assertNull(historySummary(onlyNow.map { it.copy(recorded = false) }, tiers, YearMonth.of(2026, 11)))

        val entry = PeriodSpent(performancePeriod(october, 1), TierProgress(523_000, tiers))
        assertEquals("2026년 10월 실적, 523,000원, 30만원 구간을 채웠어요, 70만원까지 177,000원 남았어요", historySlotDescription(entry, past = false))
        val bare = PeriodSpent(performancePeriod(october, 1), TierProgress(523_000, emptyList()))
        assertEquals("2026년 10월 실적, 523,000원", historySlotDescription(bare, past = true))
        val before = PeriodSpent(performancePeriod(YearMonth.of(2026, 5), 1), TierProgress(0, tiers), recorded = false)
        assertEquals("2026년 5월 실적, 기록이 없어요", historySlotDescription(before, past = true))
    }

    @Test
    fun `구간 목록 한 줄의 상태`() {
        assertEquals("채웠어요", tierStatus(300_000, 300_000, past = false))
        assertEquals("176,550원 남았어요", tierStatus(300_000, 123_450, past = false))
        assertEquals("176,550원 모자랐어요", tierStatus(300_000, 123_450, past = true))
        assertEquals("2구간", tierOrder(1))
    }

    @Test
    fun `시작일은 매달 며칠부터로 적고, 말일과 예시 기간을 알린다`() {
        assertEquals("매달 1일부터", startDayValue(1))
        assertEquals("매달 말일부터", startDayValue(31))
        assertEquals("매달 31일부터, 없는 달은 말일부터", startDayCellDescription(31))
        assertEquals("매달 15일부터", startDayCellDescription(15))
        assertEquals("10월 1일 ~ 10월 31일을 10월 실적으로 세요", startDayHint(today, 1))
        assertEquals("9월 15일 ~ 10월 14일을 9월 실적으로 세요", startDayHint(today, 15))
        assertEquals("9월 30일 ~ 10월 29일을 9월 실적으로 세요. 그 달에 없는 날이면 말일부터 세요", startDayHint(today, 30))
    }

    @Test
    fun `편집의 구간 줄 값과 안내`() {
        assertEquals("300,000원", tierRowValue(300_000))
        assertEquals("정해 주세요", tierRowValue(0))
        val rows = listOf(300_000L, 0L, 5_000L, 300_000L, 1_205_000L)
        assertEquals("30만원", tierRowHint(rows, 0))
        assertNull(tierRowHint(rows, 1))
        assertNull(tierRowHint(rows, 2))
        assertEquals("1구간과 같은 금액이라 하나로 쳐요", tierRowHint(rows, 3))
        assertEquals("120만 5,000원", tierRowHint(rows, 4))
        assertEquals("3구간 지우기", deleteTierLabel(2))
    }
}
