package com.dong.budget.ui.stats.tab.monthly

import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.format.formatSignedTotal
import com.dong.budget.ui.format.formatSpentAmount
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.MonthPoint
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.calc.SpendRatioSentence
import com.dong.budget.ui.stats.calc.spendRatioSentence
import com.dong.budget.ui.stats.categoryEntry
import com.dong.budget.ui.stats.detailKey
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 월별·통계 탭의 줄·표·제목 글자와 고르기. 화면과 떼어 두어 단위 테스트로 확인한다.
// 문장은 calc 의 StatsSentences 가 만들고, 여기에는 두 탭의 배치에만 묶인 것을 둔다(요약 머리·큰 지출·눈에 띄는 점은 통계 탭이 쓴다).
// ─────────────────────────────────────────────────────────────────────

/** 요약 머리에서 지출이 음수일 때 금액 아래 붙는 부제 */
internal const val REFUND_OVER_TEXT = "돌려받은 돈이 쓴 돈보다 많아요"

/** 흐름 섹션 제목. "이번 달 흐름" / "8월 흐름" / "2025년 12월 흐름" */
internal fun flowTitle(month: YearMonth, today: LocalDate): String = "${monthLabel(month, today)} 흐름"

/** 큰 지출 섹션 제목. "이번 달 큰 지출" / "8월 큰 지출" */
internal fun largestTitle(month: YearMonth, today: LocalDate): String = "${monthLabel(month, today)} 큰 지출"

/**
 * '눈에 띄는 점' 한 줄을 누르면 갈 분류 상세. 분류 이야기(a·b)만 상세가 있다.
 * '분류 없음' 이면 id 가 null 인 상세다.
 * @return 요일·돈 안 쓴 날(c·d)이면 null. 요일은 일별 탭의 요일별 하루 평균, 돈 안 쓴 날은 통계 탭의 하루 기록에서 본다.
 */
internal fun Insight.detailKey(month: YearMonth): StatsDetailKey? = categoryEntry?.key?.detailKey(StatsDimension.EXPENSE_CATEGORY, month)

/** '눈에 띄는 점' 한 줄을 누르면 무엇을 하는지. 화면 읽기가 '두 번 탭하여 …' 뒤에 읽는다. */
internal fun insightActionLabel(insight: Insight): String = when (insight) {
    is Insight.TopShare, is Insight.CategoryChange -> "분류 상세 보기"
    is Insight.WeekPattern -> "요일별 하루 평균 보기"
    is Insight.NoSpendDays -> "하루 기록 보기"
}

// ── 최근 6개월 ─────────────────────────────────────────────────────────

/** 최근 6개월 섹션 제목 */
internal const val TREND_TITLE = "최근 6개월"

/** 기록한 달이 모자라 차트와 표 대신 두는 안내 */
internal const val TREND_FALLBACK = "기록한 달이 두 달은 되어야 달마다 비교해 보여 드려요"

/** 6개월 막대 차트의 화면 읽기 요약. 값은 표가 줄마다 읽는다. */
internal const val TREND_CHART_DESCRIPTION = "최근 6개월 지출과 수입 막대 그래프. 아래 표에 달마다 금액이 있어요"

/** 창 안에 이번 달이 있으면 표 아래 붙는 안내 */
internal const val TREND_CURRENT_HINT = "이번 달은 지금까지 적은 거래로 셌어요"

/** 6개월 표 머리줄 */
internal val TREND_COLUMNS = listOf("달", "지출", "수입", "남은 돈")

/** 기록한 달이 이보다 적으면 비교하지 않는다. 막대 한 달짜리 차트는 견줄 것이 없다. */
private const val MIN_COMPARED_MONTHS = 2

/** 6개월 차트와 표를 보여 줄 만큼 기록한 달이 창 안에 있는지 */
internal fun canCompareMonths(trend: List<MonthPoint>): Boolean = trend.count { it.hasRecord } >= MIN_COMPARED_MONTHS

/**
 * 6개월 표의 줄. 기록을 시작한 달부터 고른 달까지다. 그 전 달은 차트에 x축 글자만 남고 표에는 없다.
 * 비어 있으면(기록을 시작하기 전 달을 보고 있으면) 섹션 전체를 숨긴다.
 */
internal fun trendTableRows(trend: List<MonthPoint>): List<MonthPoint> = trend.filterNot { it.beforeFirstRecord }

/**
 * 6개월 표 한 줄의 칸 글자. 좁은 표라 단위(원)는 뺀다.
 * 지출이 음수(환불이 더 많음)면 돌아온 돈이라 "+12,000", 남은 돈은 부호를 붙인다.
 */
internal fun trendCells(point: MonthPoint): List<String> {
    val (expense, income) = point.totals
    return listOf(
        "${point.month.monthValue}월",
        formatNetExpense(expense, unit = ""),
        formatSignedTotal(income, unit = ""),
        formatSignedTotal(income - expense, unit = ""),
    )
}

/**
 * 6개월 표 한 줄의 화면 읽기 문장. 열 이름과 전체 금액, 연도까지 붙여 읽는다(표의 '12월' 이 몇 년인지 헷갈리지 않게).
 * "2026년 8월, 지출 1,234,000원, 수입 2,000,000원, 남은 돈 +766,000원" + 지금 보는 달이면 ", 지금 보는 달"
 */
internal fun trendRowDescription(point: MonthPoint, isShownMonth: Boolean): String {
    val (expense, income) = point.totals
    return listOfNotNull(
        formatMonth(point.month),
        "지출 ${formatSpentAmount(expense)}",
        "수입 ${formatAmount(income)}원",
        "남은 돈 ${formatSignedTotal(income - expense)}",
        "지금 보는 달".takeIf { isShownMonth },
    ).joinToString(", ")
}

// ── 올해 모아 보기 ──────────────────────────────────────────────────────

/**
 * 올해 모아 보기의 수입 대비 지출 문장. "수입의 62%를 썼어요", 수입보다 더 썼으면 "수입보다 30만원 더 썼어요".
 * 보조 문장(남은 %)은 두지 않는다.
 *
 * @return 수입이 없거나 환불이 더 많아 지출이 음수면 null. 이때 월별 문장은 '이 달에는…' 이라 한 해에 맞지 않고,
 *   위 줄(수입·지출·남은 돈)이 이미 사정을 보여준다.
 */
internal fun yearRatioSentence(totals: Totals): SpendRatioSentence? {
    if (totals.income <= 0 || totals.expense < 0) return null
    // 수입이 있고 지출이 0 이상이면 문장은 달의 시점과 상관없다. 시점은 수입이 없을 때만 쓰인다.
    return spendRatioSentence(totals, Period.PAST).copy(detail = null)
}
