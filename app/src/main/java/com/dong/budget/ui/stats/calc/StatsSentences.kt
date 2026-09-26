package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatCompactWon
import com.dong.budget.ui.format.formatDayShort
import com.dong.budget.ui.format.formatRatio
import com.dong.budget.ui.format.formatShare
import com.dong.budget.ui.format.formatSignedTotal
import com.dong.budget.ui.format.formatWeekdayFull
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.CumulativeFlow
import com.dong.budget.ui.stats.DayStack
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.MerchantStat
import com.dong.budget.ui.stats.Pace
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.TrendAverage
import com.dong.budget.ui.stats.WeekdayStats
import com.dong.budget.ui.stats.YearToDate
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 통계 문구. 화면과 떼어 두어 단위 테스트로 문장을 확인한다.
//
// 조사 규칙: 사용자가 지은 이름(분류·결제수단) 뒤에는 받침에 따라 모양이 바뀌지 않는 조사만 쓴다(에, 에서).
// '이/가', '은/는', '(으)로' 는 받침을 알아야 해서 쓰지 않는다. 고정된 말 뒤에서는 알맞게 고른다('지난달과', '지난달 이맘때와').
// 금액은 문장 안에서 줄여 쓰고(formatCompactWon), 화면 읽기에서는 전체 금액을 쓴다.
// {달} 자리는 모두 monthLabel(이번 달 / 9월 / 2025년 12월)이다.
// ─────────────────────────────────────────────────────────────────────

// ── 월별 ─────────────────────────────────────────────────────────────

/** 요약 머리의 라벨. "이번 달 쓴 돈" / "8월에 쓴 돈" / "10월에 미리 적은 지출" */
fun summaryLabel(month: YearMonth, today: LocalDate): String = when (periodOf(month, today)) {
    Period.CURRENT -> "이번 달 쓴 돈"
    Period.PAST -> "${monthLabel(month, today)}에 쓴 돈"
    Period.FUTURE -> "${monthLabel(month, today)}에 미리 적은 지출"
}

/** 이번 달 합계에 오늘 뒤 날짜 거래가 들어 있다는 안내 */
fun futureHint(count: Int): String = "오늘 뒤 날짜로 미리 적은 거래 ${count}건도 합쳤어요"

/**
 * 월별 '수입과 지출' 의 문장.
 * @property detail 아래에 붙는 보조 문장. 없으면 null
 * @property overspent 수입보다 더 썼는지. 참이면 경고 아이콘과 danger 색으로 적는다.
 */
data class SpendRatioSentence(val text: String, val detail: String?, val overspent: Boolean)

/**
 * 수입 대비 지출 문장. 순서대로 검사한다.
 * 수입이 없으면 비율을 낼 수 없고, 환불이 더 많아 지출이 음수면 '썼다' 고 말할 수 없다.
 */
fun spendRatioSentence(totals: Totals, period: Period): SpendRatioSentence {
    val (expense, income) = totals
    return when {
        income <= 0 -> SpendRatioSentence(if (period == Period.CURRENT) "아직 수입 기록이 없어요" else "이 달에는 수입 기록이 없어요", null, false)

        expense < 0 -> SpendRatioSentence("이 달에는 쓴 돈보다 돌려받은 돈이 많아요", null, false)

        expense <= income -> {
            val percent = divRound(expense * PERCENT, income)
            SpendRatioSentence("수입의 $percent%를 썼어요", "${PERCENT - percent}%가 남았어요", false)
        }

        else -> SpendRatioSentence("수입보다 ${formatCompactWon(expense - income)} 더 썼어요", null, true)
    }
}

/** 지난달 비교의 기준이 되는 말 */
private fun ComparisonScope.base(): String = when (this) {
    ComparisonScope.SAME_DAY -> "지난달 이맘때"
    ComparisonScope.WHOLE_MONTH -> "지난달"
}

/** '눈에 띄는 점' 한 줄 */
fun insightSentence(insight: Insight): String = when (insight) {
    is Insight.TopShare -> "지출의 ${insight.percent}%를 ${insight.entry.name}에 썼어요"

    is Insight.CategoryChange -> {
        val difference = insight.change.current - insight.change.previous
        val direction = if (difference > 0) "더" else "덜"
        "${insight.change.scope.base()}보다 ${insight.entry.name}에 ${formatCompactWon(difference)} $direction 썼어요"
    }

    is Insight.WeekPattern ->
        if (insight.weekendHigher) {
            "주말에는 평일보다 하루 ${formatRatio(insight.ratio)} 더 써요"
        } else {
            "평일에는 주말보다 하루 ${formatRatio(insight.ratio)} 더 써요"
        }

    is Insight.NoSpendDays -> "돈을 안 쓴 날이 ${insight.days}일 있었어요"
}

/** 이번 달 속도 문장. 하루 허용액은 대략이 아니라 그대로 쓸 돈이라 전체 금액으로 적는다. */
fun paceSentence(pace: Pace): String = when {
    pace.remaining > 0 -> {
        val allowance = pace.dailyAllowance ?: divRound(pace.remaining, pace.daysLeft)
        "지난달만큼 쓰려면 남은 ${pace.daysLeft}일 동안 하루 ${formatAmount(allowance)}원까지 쓸 수 있어요"
    }

    pace.remaining == 0L -> "벌써 지난달 전체만큼 썼어요"

    else -> "벌써 지난달 전체보다 ${formatCompactWon(-pace.remaining)} 더 썼어요"
}

/**
 * 누적 선 아래 읽기 줄. "27일까지 · 9월 81만원 · 8월 93만원"
 * 이번 달 선이 [day] 까지 오지 않았으면(오늘 뒤) 이번 달 값을 빼고, 지난달이 더 짧으면 지난달 말일 값을 쓴다.
 */
fun flowReading(flow: CumulativeFlow, day: Int): String = buildList {
    add("${day}일까지")
    flow.thisMonth.getOrNull(day - 1)?.let { add("${flow.month.monthValue}월 ${formatCompactWon(it)}") }
    flow.previous?.let { add("${flow.month.minusMonths(1).monthValue}월 ${formatCompactWon(it.upTo(day))}") }
}.joinToString(" · ")

/** 화면 읽기용 누적 선 요약. "9월 누적 지출 선 그래프. 27일까지 812,000원, 지난달 같은 날까지 930,000원." */
fun flowDescription(flow: CumulativeFlow): String {
    val day = flow.thisMonth.size
    val current = "${day}일까지 ${formatAmount(flow.thisMonth.last())}원"
    val previous = flow.previous?.let { ", 지난달 같은 날까지 ${formatAmount(it.upTo(day))}원" }.orEmpty()
    return "${flow.month.monthValue}월 누적 지출 선 그래프. $current$previous."
}

/** 누적 값에서 [day] 일까지. 그 달이 더 짧으면 말일까지 */
private fun List<Long>.upTo(day: Int): Long = this[minOf(day, size) - 1]

/** 6개월 표 아래 평균 줄. "앞선 4달 평균 · 지출 93만원 · 수입 250만원" */
fun trendAverageText(average: TrendAverage): String =
    "앞선 ${average.months}달 평균 · 지출 ${formatCompactWon(average.expense)} · 수입 ${formatCompactWon(average.income)}"

/** 큰 지출 줄의 부제. "9월 3일 (목) · 식비". 분류가 없으면 날짜만 */
fun largestSubtitle(item: TransactionListItem): String =
    listOfNotNull(formatDayShort(item.localDate()), item.categoryName).joinToString(" · ")

/** 올해 모아 보기 제목. "2026년 모아 보기" */
fun yearToDateTitle(ytd: YearToDate): String = "${ytd.year}년 모아 보기"

/** 올해 모아 보기 부제. "1월~9월" */
fun yearToDateRange(ytd: YearToDate): String = "${ytd.startMonth.monthValue}월~${ytd.endMonth.monthValue}월"

/** 1월보다 늦게 기록을 시작했을 때의 안내. 아니면 null */
fun yearToDateStartHint(ytd: YearToDate): String? = if (ytd.startsLate) "기록을 시작한 ${ytd.startMonth.monthValue}월부터 모았어요" else null

/** 빈 달을 알리는 문구 */
data class EmptyText(val title: String, val description: String)

/** 이체 말고 거래가 없는 달. 오지 않은 달은 미리 적을 수 있다는 것을 알린다. */
fun emptyMonthText(month: YearMonth, today: LocalDate): EmptyText = if (periodOf(month, today) == Period.FUTURE) {
    EmptyText("아직 오지 않은 달이에요", "미리 적어 둔 거래가 생기면 여기에 보여요")
} else {
    EmptyText("${monthLabel(month, today)}에는 거래가 없어요", "홈에서 거래를 남기면 여기서 모아 볼 수 있어요")
}

// ── 일별 ─────────────────────────────────────────────────────────────

/** 기록을 1일보다 늦게 시작한 달의 안내. "기록을 시작한 9월 12일부터 셌어요". 1일부터 셌거나 센 날이 없으면 null */
fun countedStartHint(month: YearMonth, counted: IntRange): String? {
    if (counted.isEmpty() || counted.first == 1) return null
    return "기록을 시작한 ${month.monthValue}월 ${counted.first}일부터 셌어요"
}

/** 돈 안 쓴 날 줄의 캡션. "27일 중", 최장 연속이 이틀 이상이면 "27일 중 · 최장 4일 연속" */
fun noSpendCaption(counted: IntRange, longestNoSpend: Int): String {
    val base = "${counted.count()}일 중"
    return if (longestNoSpend >= 2) "$base · 최장 ${longestNoSpend}일 연속" else base
}

/** 읽기 판의 첫 줄. "32,000원 썼어요" / "쓴 돈이 없어요" / "아직 오지 않은 날이에요" */
fun daySpentText(day: DayStack): String = when {
    day.isFuture -> "아직 오지 않은 날이에요"
    day.expense > 0 -> "${formatAmount(day.expense)}원 썼어요"
    else -> "쓴 돈이 없어요"
}

/** 읽기 판의 덧붙임 줄. 미리 적은 지출, 빠진 환불, 수입이 있을 때만 */
fun dayNotes(day: DayStack): List<String> = listOfNotNull(
    if (day.isFuture && day.expense > 0) "미리 적은 지출 ${formatAmount(day.expense)}원" else null,
    if (day.refund > 0) "환불 ${formatAmount(day.refund)}원이 빠진 금액이에요" else null,
    if (day.income > 0) "수입 ${formatSignedTotal(day.income)}" else null,
)

/** 요일 섹션 부제. "최근 3달 (7월~9월)". 창이 한 달 안이면 "최근 3달 (9월)" */
fun weekdayRange(stats: WeekdayStats): String {
    val from = stats.from.monthValue
    val to = stats.to.monthValue
    return if (YearMonth.from(stats.from) == YearMonth.from(stats.to)) "최근 3달 (${from}월)" else "최근 3달 (${from}월~${to}월)"
}

/** 요일 섹션 머리 문장. "토요일에 가장 많이 써요". 모든 요일이 0 이면 null */
fun weekdayHeadline(stats: WeekdayStats): String? = stats.top?.let { "${formatWeekdayFull(it)}에 가장 많이 써요" }

/**
 * 요일 섹션의 주말·평일 문장. 문턱(1.3배)은 '눈에 띄는 점' 과 같다.
 * @return 주말이나 평일 한쪽에 쓴 돈이 없어 배수를 낼 수 없으면 null
 */
fun weekdayPatternSentence(stats: WeekdayStats): String? {
    stats.weekendRatio ?: return null
    return weekPattern(stats)?.let(::insightSentence) ?: "평일과 주말에 비슷하게 써요"
}

// ── 분류·결제수단 ───────────────────────────────────────────────────────

/**
 * 지난달 대비 증감. "지난달 이맘때보다 3만원 늘었어요"
 * @param spoken 화면 읽기용이면 전체 금액("30,000원")으로 쓴다.
 */
fun changeText(change: EntryChange, spoken: Boolean = false): String {
    val base = change.scope.base()
    val difference = change.current - change.previous
    val amount = if (spoken) "${formatAmount(abs(difference))}원" else formatCompactWon(difference)
    return when {
        change.previous == 0L && change.current != 0L -> "${base}엔 없었어요"
        difference == 0L -> if (change.scope == ComparisonScope.SAME_DAY) "지난달 이맘때와 같아요" else "지난달과 같아요"
        difference > 0 -> "${base}보다 $amount 늘었어요"
        else -> "${base}보다 $amount 줄었어요"
    }
}

/** 분류·결제수단 탭 머리 라벨. "이번 달 지출" / "9월 수입" / "9월 결제수단별 지출" */
fun breakdownLabel(monthLabel: String, dimension: StatsDimension): String = when (dimension) {
    StatsDimension.EXPENSE_CATEGORY -> "$monthLabel 지출"
    StatsDimension.INCOME_CATEGORY -> "$monthLabel 수입"
    StatsDimension.PAYMENT_METHOD -> "$monthLabel 결제수단별 지출"
}

/** 순위 줄 둘째 줄. "42% · 12건", 결제수단은 "42% · 12건 · 한 번에 평균 2만원". 음수 항목은 "환불받은 돈이 더 많아요" */
fun entrySummary(entry: BreakdownEntry, dimension: StatsDimension): String {
    val share = entry.share ?: return "환불받은 돈이 더 많아요"
    val ticket = entry.averageTicket?.takeIf { dimension == StatsDimension.PAYMENT_METHOD }
    return listOfNotNull(formatShare(share), "${entry.count}건", ticket?.let { "한 번에 평균 ${formatCompactWon(it)}" }).joinToString(" · ")
}

/**
 * 양수 계열이 하나뿐일 때 도넛 대신 쓰는 한 줄.
 * "이번 달 지출은 모두 식비에 썼어요" / "9월 수입은 모두 월급에서 들어왔어요" / "9월 지출은 모두 현금에서 나갔어요"
 */
fun singleSeriesSentence(monthLabel: String, dimension: StatsDimension, name: String): String = when (dimension) {
    StatsDimension.EXPENSE_CATEGORY -> "$monthLabel 지출은 모두 ${name}에 썼어요"
    StatsDimension.INCOME_CATEGORY -> "$monthLabel 수입은 모두 ${name}에서 들어왔어요"
    StatsDimension.PAYMENT_METHOD -> "$monthLabel 지출은 모두 ${name}에서 나갔어요"
}

/** 음수 항목이 비율에서 빠졌다는 안내. "환불이 더 많은 분류 2개는 비율에서 뺐어요" */
fun negativeHint(count: Int, dimension: StatsDimension): String {
    val noun = if (dimension == StatsDimension.PAYMENT_METHOD) "결제수단" else "분류"
    return "환불이 더 많은 $noun ${count}개는 비율에서 뺐어요"
}

/** 이 달에 쓴 돈(수입이면 들어온 돈)이 없을 때. "9월에는 쓴 돈이 없어요". 일별 탭도 지출 쪽을 쓴다. */
fun nothingText(monthLabel: String, dimension: StatsDimension = StatsDimension.EXPENSE_CATEGORY): String =
    if (dimension == StatsDimension.INCOME_CATEGORY) "${monthLabel}에는 들어온 돈이 없어요" else "${monthLabel}에는 쓴 돈이 없어요"

/** 많이 쓴 곳 줄의 부제. "4번 · 한 번에 평균 1만원" */
fun merchantSubtitle(merchant: MerchantStat): String = // 한 번뿐이면 평균이 금액과 같아서 적지 않는다
    if (merchant.count == 1) "1번" else "${merchant.count}번 · 한 번에 평균 ${formatCompactWon(merchant.averageTicket)}"
