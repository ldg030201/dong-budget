package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.CumulativeFlow
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.MerchantStat
import com.dong.budget.ui.stats.Pace
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.TrendAverage
import com.dong.budget.ui.stats.WeekdayStats
import com.dong.budget.ui.stats.YearToDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

class StatsSentencesTest {
    private val today = day("2026-09-27")

    private fun entry(name: String, amount: Long = 523_000, share: Double? = 0.42, count: Int = 12, averageTicket: Long? = 20_000) =
        BreakdownEntry(GroupKey.Id(1), name, null, null, amount, count, share, averageTicket, change = null)

    // ── 월별 ────────────────────────────────────────────────────────────

    @Test
    fun `요약 라벨은 이번 달, 지나간 달, 오지 않은 달이 다르다`() {
        assertEquals("이번 달 쓴 돈", summaryLabel(YearMonth.of(2026, 9), today))
        assertEquals("8월에 쓴 돈", summaryLabel(YearMonth.of(2026, 8), today))
        assertEquals("2025년 12월에 쓴 돈", summaryLabel(YearMonth.of(2025, 12), today))
        assertEquals("10월에 미리 적은 지출", summaryLabel(YearMonth.of(2026, 10), today))
        assertEquals("오늘 뒤 날짜로 미리 적은 거래 2건도 합쳤어요", futureHint(2))
    }

    @Test
    fun `수입 대비 지출 문장`() {
        assertEquals(
            SpendRatioSentence("수입의 56%를 썼어요", "44%가 남았어요", overspent = false),
            spendRatioSentence(Totals(expense = 560_000, income = 1_000_000), Period.CURRENT),
        )
        assertEquals(
            SpendRatioSentence("수입보다 30만원 더 썼어요", null, overspent = true),
            spendRatioSentence(Totals(expense = 1_300_000, income = 1_000_000), Period.PAST),
        )
        // 지출이 0 이면 돌려받은 돈 이야기가 아니다
        assertEquals("수입의 0%를 썼어요", spendRatioSentence(Totals(expense = 0, income = 1_000), Period.PAST).text)
        // 조금이라도 쓰고 조금이라도 남았으면 0% 나 100% 로 반올림하지 않는다(아래 '남은 돈' 줄과 어긋나지 않게)
        assertEquals(
            SpendRatioSentence("수입의 99%를 썼어요", "1%가 남았어요", overspent = false),
            spendRatioSentence(Totals(expense = 1_995_000, income = 2_000_000), Period.PAST),
        )
        assertEquals(
            SpendRatioSentence("수입의 1%를 썼어요", "99%가 남았어요", overspent = false),
            spendRatioSentence(Totals(expense = 9_000, income = 3_000_000), Period.PAST),
        )
    }

    @Test
    fun `수입이 없거나 돌려받은 돈이 더 많으면 비율 대신 사정을 말한다`() {
        assertEquals("아직 수입 기록이 없어요", spendRatioSentence(Totals(expense = 5_000), Period.CURRENT).text)
        assertEquals("이 달에는 수입 기록이 없어요", spendRatioSentence(Totals(expense = 5_000), Period.PAST).text)
        assertEquals(
            SpendRatioSentence("이 달에는 쓴 돈보다 돌려받은 돈이 많아요", null, overspent = false),
            spendRatioSentence(Totals(expense = -12_000, income = 100_000), Period.PAST),
        )
    }

    @Test
    fun `눈에 띄는 점 문장`() {
        assertEquals("지출의 42%를 식비에 썼어요", insightSentence(Insight.TopShare(entry("식비"), 42)))
        assertEquals(
            "지난달 이맘때보다 식비에 12만원 더 썼어요",
            insightSentence(Insight.CategoryChange(entry("식비"), EntryChange(ComparisonScope.SAME_DAY, 100_000, 220_000))),
        )
        assertEquals(
            "지난달보다 교통/차량에 3만원 덜 썼어요",
            insightSentence(Insight.CategoryChange(entry("교통/차량"), EntryChange(ComparisonScope.WHOLE_MONTH, 80_000, 50_000))),
        )
        assertEquals("주말에는 평일보다 하루 1.8배 더 써요", insightSentence(Insight.WeekPattern(weekendHigher = true, ratio = 1.8)))
        assertEquals("평일에는 주말보다 하루 1.3배 더 써요", insightSentence(Insight.WeekPattern(weekendHigher = false, ratio = 1.3)))
        assertEquals("돈을 안 쓴 날이 4일 있었어요", insightSentence(Insight.NoSpendDays(4)))
    }

    @Test
    fun `속도 문장`() {
        assertEquals(
            "지난달만큼 쓰려면 남은 21일 동안 하루 4,762원까지 쓸 수 있어요",
            paceSentence(Pace(remaining = 100_000, scheduled = 0, daysLeft = 21, dailyAllowance = 4_762)),
        )
        assertEquals("벌써 지난달 전체만큼 썼어요", paceSentence(Pace(0, 0, 21, null)))
        assertEquals("벌써 지난달 전체보다 5만원 더 썼어요", paceSentence(Pace(-50_000, 0, 21, null)))
        // 오늘까지는 덜 썼는데 미리 적은 지출까지 치면 지난달만큼(또는 넘게) 쓰게 된다
        assertEquals("미리 적은 지출까지 치면 지난달 전체만큼 써요", paceSentence(Pace(0, 100_000, 21, null)))
        assertEquals("미리 적은 지출까지 치면 지난달 전체보다 5만원 더 써요", paceSentence(Pace(-50_000, 100_000, 21, null)))
        // 미리 적은 지출을 빼도 이미 더 썼으면 실제로 쓴 만큼만 말한다
        assertEquals("벌써 지난달 전체보다 3만원 더 썼어요", paceSentence(Pace(-80_000, 50_000, 21, null)))
    }

    @Test
    fun `누적 선 읽기 줄과 화면 읽기 요약`() {
        val flow =
            CumulativeFlow(YearMonth.of(2026, 9), thisMonth = listOf(100_000, 812_000), previous = listOf(500_000, 930_000, 1_000_000))
        assertEquals("2일까지 · 9월 81만원 · 8월 93만원", flowReading(flow, 2))
        // 이번 달 선이 닿지 않은 날은 지난달만
        assertEquals("3일까지 · 8월 100만원", flowReading(flow, 3))
        // 지난달이 더 짧으면 지난달 말일 값
        assertEquals("5일까지 · 8월 100만원", flowReading(flow, 5))
        assertEquals("9월 누적 지출 선 그래프. 2일까지 812,000원, 지난달 같은 날까지 930,000원.", flowDescription(flow))
        assertEquals("9월 누적 지출 선 그래프. 2일까지 812,000원.", flowDescription(flow.copy(previous = null)))
        assertEquals("1일까지 · 9월 10만원", flowReading(flow.copy(previous = null), 1))
    }

    @Test
    fun `6개월 평균 줄과 큰 지출 부제`() {
        assertEquals("앞선 4달 평균 · 지출 93만원 · 수입 250만원", trendAverageText(TrendAverage(4, 930_000, 2_500_000)))
        // 2026년 9월 3일은 목요일
        assertEquals("9월 3일 (목) · 식비", largestSubtitle(tx("2026-09-03", 1_000, categoryId = 1, categoryName = "식비")))
        assertEquals("9월 3일 (목)", largestSubtitle(tx("2026-09-03", 1_000)))
    }

    @Test
    fun `올해 모아 보기 문구`() {
        val ytd = YearToDate(2026, YearMonth.of(2026, 9), YearMonth.of(2026, 11), Totals(), null, null, startsLate = true)
        assertEquals("2026년 모아 보기", yearToDateTitle(ytd))
        assertEquals("9월~11월", yearToDateRange(ytd))
        assertEquals("기록을 시작한 9월부터 모았어요", yearToDateStartHint(ytd))
        assertNull(yearToDateStartHint(ytd.copy(startMonth = YearMonth.of(2026, 1), startsLate = false)))
    }

    @Test
    fun `빈 달 문구`() {
        assertEquals(EmptyText("8월에는 거래가 없어요", "홈에서 거래를 남기면 여기서 모아 볼 수 있어요"), emptyMonthText(YearMonth.of(2026, 8), today))
        assertEquals("이번 달에는 거래가 없어요", emptyMonthText(YearMonth.of(2026, 9), today).title)
        assertEquals(EmptyText("아직 오지 않은 달이에요", "미리 적어 둔 거래가 생기면 여기에 보여요"), emptyMonthText(YearMonth.of(2026, 10), today))
    }

    // ── 일별 ────────────────────────────────────────────────────────────

    @Test
    fun `기록 시작 안내와 돈 안 쓴 날 캡션`() {
        assertEquals("기록을 시작한 9월 12일부터 셌어요", countedStartHint(YearMonth.of(2026, 9), 12..27))
        assertNull(countedStartHint(YearMonth.of(2026, 9), 1..27))
        assertNull(countedStartHint(YearMonth.of(2026, 10), IntRange.EMPTY))
        assertEquals("27일 중", noSpendCaption(1..27, 1))
        assertEquals("27일 중 · 최장 4일 연속", noSpendCaption(1..27, 4))
    }

    @Test
    fun `읽기 판의 첫 줄과 덧붙임 줄`() {
        val days = dayStacks(
            YearMonth.of(2026, 9),
            day("2026-09-10"),
            listOf(
                tx("2026-09-03", 35_000),
                tx("2026-09-03", 3_000, REFUND),
                tx("2026-09-03", 300_000, INCOME),
                tx("2026-09-20", 12_000),
            ),
            emptyList(),
        )
        assertEquals("32,000원 썼어요", daySpentText(days[2]))
        assertEquals(listOf("환불 3,000원이 빠진 금액이에요", "수입 +300,000원"), dayNotes(days[2]))
        assertEquals("쓴 돈이 없어요", daySpentText(days[3]))
        assertEquals(emptyList<String>(), dayNotes(days[3]))
        assertEquals("아직 오지 않은 날이에요", daySpentText(days[19]))
        assertEquals(listOf("미리 적은 지출 12,000원"), dayNotes(days[19]))
    }

    @Test
    fun `요일 섹션 문장`() {
        val stats = WeekdayStats(day("2026-07-01"), day("2026-09-27"), emptyList(), DayOfWeek.SATURDAY, weekendRatio = 1.5)
        assertEquals("최근 3달 (7월~9월)", weekdayRange(stats))
        assertEquals("최근 3달 (9월)", weekdayRange(stats.copy(from = day("2026-09-01"))))
        assertEquals("토요일에 가장 많이 써요", weekdayHeadline(stats))
        assertNull(weekdayHeadline(stats.copy(top = null)))
        assertEquals("주말에는 평일보다 하루 1.5배 더 써요", weekdayPatternSentence(stats))
        assertEquals("평일에는 주말보다 하루 2.0배 더 써요", weekdayPatternSentence(stats.copy(weekendRatio = 0.5)))
        assertEquals("평일과 주말에 비슷하게 써요", weekdayPatternSentence(stats.copy(weekendRatio = 1.1)))
        assertNull(weekdayPatternSentence(stats.copy(weekendRatio = null)))
    }

    // ── 분류·결제수단 ───────────────────────────────────────────────────

    @Test
    fun `증감이 없으면 기준에 맞는 조사로 같다고 말한다`() {
        assertEquals("지난달 이맘때와 같아요", changeText(EntryChange(ComparisonScope.SAME_DAY, 30_000, 30_000)))
        assertEquals("지난달과 같아요", changeText(EntryChange(ComparisonScope.WHOLE_MONTH, 30_000, 30_000)))
    }

    @Test
    fun `증감 문장`() {
        assertEquals("지난달 이맘때보다 3만원 늘었어요", changeText(EntryChange(ComparisonScope.SAME_DAY, 100_000, 130_000)))
        assertEquals("지난달보다 8,500원 줄었어요", changeText(EntryChange(ComparisonScope.WHOLE_MONTH, 10_000, 1_500)))
        assertEquals("지난달 이맘때엔 없었어요", changeText(EntryChange(ComparisonScope.SAME_DAY, 0, 20_000)))
        assertEquals("지난달엔 없었어요", changeText(EntryChange(ComparisonScope.WHOLE_MONTH, 0, 20_000)))
    }

    @Test
    fun `화면 읽기용 증감은 전체 금액이다`() {
        assertEquals("지난달 이맘때보다 30,000원 늘었어요", changeText(EntryChange(ComparisonScope.SAME_DAY, 100_000, 130_000), spoken = true))
    }

    @Test
    fun `한 곳에 모두 쓴 달은 이름 뒤에 모양이 안 바뀌는 조사만 쓴다`() {
        assertEquals("이번 달 지출은 모두 식비에 썼어요", singleSeriesSentence("이번 달", StatsDimension.EXPENSE_CATEGORY, "식비"))
        assertEquals("9월 수입은 모두 월급에서 들어왔어요", singleSeriesSentence("9월", StatsDimension.INCOME_CATEGORY, "월급"))
        assertEquals("9월 지출은 모두 현대카드에서 나갔어요", singleSeriesSentence("9월", StatsDimension.PAYMENT_METHOD, "현대카드"))
    }

    @Test
    fun `분류·결제수단 머리와 안내`() {
        assertEquals("이번 달 지출", breakdownLabel("이번 달", StatsDimension.EXPENSE_CATEGORY))
        assertEquals("9월 수입", breakdownLabel("9월", StatsDimension.INCOME_CATEGORY))
        assertEquals("9월 결제수단별 지출", breakdownLabel("9월", StatsDimension.PAYMENT_METHOD))
        assertEquals("환불이 더 많은 분류 2개는 비율에서 뺐어요", negativeHint(2, StatsDimension.EXPENSE_CATEGORY))
        assertEquals("환불이 더 많은 결제수단 1개는 비율에서 뺐어요", negativeHint(1, StatsDimension.PAYMENT_METHOD))
        assertEquals("9월에는 쓴 돈이 없어요", nothingText("9월"))
        assertEquals("이번 달에는 들어온 돈이 없어요", nothingText("이번 달", StatsDimension.INCOME_CATEGORY))
    }

    @Test
    fun `순위 줄 둘째 줄`() {
        assertEquals("42% · 12건", entrySummary(entry("식비"), StatsDimension.EXPENSE_CATEGORY))
        assertEquals("42% · 12건 · 한 번에 평균 2만원", entrySummary(entry("현대카드"), StatsDimension.PAYMENT_METHOD))
        assertEquals("1% 미만 · 1건", entrySummary(entry("간식", share = 0.004, count = 1), StatsDimension.EXPENSE_CATEGORY))
        assertEquals("환불받은 돈이 더 많아요", entrySummary(entry("쇼핑", amount = -12_000, share = null), StatsDimension.EXPENSE_CATEGORY))
    }

    @Test
    fun `많이 쓴 곳 부제`() {
        assertEquals("4번 · 한 번에 평균 1만원", merchantSubtitle(MerchantStat("스타벅스", 40_000, 4, 10_000)))
        // 한 번뿐이면 평균이 금액과 같아 적지 않는다
        assertEquals("1번", merchantSubtitle(MerchantStat("가전", 213_400, 1, 213_400)))
    }
}
