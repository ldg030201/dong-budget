package com.dong.budget.ui.stats

import androidx.compose.runtime.Immutable
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.SpendingComparison
import com.dong.budget.ui.home.Totals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 통계 화면이 그리는 값. 모두 거래 목록에서 순수 함수(calc/)로 계산한다.
// 숫자 규칙은 홈과 같다: 이체는 어디에도 넣지 않고, 환불은 지출에서 뺀다(Totals).
// ─────────────────────────────────────────────────────────────────────

/** 통계 안의 하위 메뉴. 아래 떠 있는 메뉴에 이 순서로 선다. */
enum class StatsTab(val label: String) {
    MONTHLY("월별"),
    DAILY("일별"),
    CATEGORY("분류"),
    PAYMENT("결제수단"),
}

/** 고른 달이 오늘에 견줘 지나간 달인지, 이번 달인지, 아직 오지 않은 달인지 */
enum class Period { PAST, CURRENT, FUTURE }

/** 분류·결제수단 묶음의 기준. 분류나 결제수단이 비어 있으면(고르지 않았거나 지움) [None] */
sealed interface GroupKey {
    data class Id(val id: Long) : GroupKey

    data object None : GroupKey
}

/**
 * 통계 화면 전체의 상태.
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 달 줄과 아래 메뉴만 그린다.
 * @property hasAnyRecord 이체 말고 거래가 하나라도 있는지(모든 달 통틀어)
 * @property monthIsEmpty 고른 달에 이체 말고 거래가 없는지
 */
@Immutable
data class StatsUiState(
    val loaded: Boolean,
    val month: YearMonth,
    val today: LocalDate,
    val period: Period,
    val hasAnyRecord: Boolean,
    val monthIsEmpty: Boolean,
    val monthly: MonthlyStats,
    val daily: DailyStats,
    val expenseByCategory: Breakdown,
    val incomeByCategory: Breakdown,
    val expenseByPayment: Breakdown,
    /** 이 달 많이 쓴 곳(가게별 순지출 상위 10곳) */
    val merchants: List<MerchantStat>,
) {
    companion object {
        /** 첫 계산 전 자리 값 */
        fun loading(month: YearMonth, today: LocalDate): StatsUiState = StatsUiState(
            loaded = false,
            month = month,
            today = today,
            period = Period.CURRENT,
            hasAnyRecord = false,
            monthIsEmpty = true,
            monthly = MonthlyStats.EMPTY,
            daily = DailyStats.EMPTY,
            expenseByCategory = Breakdown.EMPTY,
            incomeByCategory = Breakdown.EMPTY,
            expenseByPayment = Breakdown.EMPTY,
            merchants = emptyList(),
        )
    }
}

// ── 월별 ─────────────────────────────────────────────────────────────

/**
 * @property totals 고른 달의 수입·지출(오늘 뒤 날짜로 미리 적은 거래 포함, 홈 요약과 같다)
 * @property comparison 홈과 같은 지난달 비교. 비교할 수 없으면 null
 * @property futureCount 이번 달에서 오늘 뒤 날짜로 미리 적은 거래 수. 이번 달이 아니면 0
 * @property flow 누적 흐름. 아직 오지 않은 달이면 null
 * @property pace '지난달만큼 쓰려면 하루 얼마' (이번 달만). 지난달 기록이 없거나 지난달 지출이 0 이하이면 null
 * @property trend 고른 달까지 최근 6개월. 오래된 달이 앞이다.
 * @property trendAverage 고른 달 앞 5달 중 다 끝났고 기록이 온전한 달들의 평균. 그런 달이 없으면 null
 * @property largest 이 달 지출 중 큰 것 5건(금액 내림차순)
 * @property yearToDate 올해 모아 보기. 시작 달이 고른 달과 같으면 null
 */
@Immutable
data class MonthlyStats(
    val totals: Totals,
    val comparison: SpendingComparison?,
    val futureCount: Int,
    val insights: List<Insight>,
    val flow: CumulativeFlow?,
    val pace: Pace?,
    val trend: List<MonthPoint>,
    val trendAverage: TrendAverage?,
    val largest: List<TransactionListItem>,
    val yearToDate: YearToDate?,
) {
    companion object {
        val EMPTY = MonthlyStats(Totals(), null, 0, emptyList(), null, null, emptyList(), null, emptyList(), null)
    }
}

/**
 * 한 달의 합계.
 * @property hasRecord 이체 말고 거래가 있는지
 * @property beforeFirstRecord 기록을 시작하기 전 달인지(표와 막대에서 빈칸으로 둔다)
 */
data class MonthPoint(val month: YearMonth, val totals: Totals, val hasRecord: Boolean, val beforeFirstRecord: Boolean)

/** @property months 평균에 넣은 달 수 */
data class TrendAverage(val months: Int, val expense: Long, val income: Long)

/**
 * 올해 모아 보기.
 * @property startMonth 1월, 또는 올해 기록을 시작한 달
 * @property monthlyAverageExpense 범위 안에서 다 끝났고 1일부터 기록한 달들의 한 달 평균 지출. 그런 달이 없으면 null
 * @property startsLate 1월보다 늦게 시작했는지
 */
data class YearToDate(
    val year: Int,
    val startMonth: YearMonth,
    val endMonth: YearMonth,
    val totals: Totals,
    val monthlyAverageExpense: Long?,
    val startsLate: Boolean,
)

/**
 * 누적 지출(그날까지 순지출의 합).
 * @property thisMonth 고른 달 1일부터의 누적. 이번 달이면 오늘까지(오늘 뒤 날짜 거래 빼고), 지나간 달이면 말일까지. 크기 = 마지막 날
 * @property previous 지난달 1일부터 말일까지의 누적. 지난달 기록이 없으면 null
 */
data class CumulativeFlow(val month: YearMonth, val thisMonth: List<Long>, val previous: List<Long>?)

/**
 * 이번 달 속도. S = 오늘까지 쓴 돈, T₀ = 지난달 전체 지출.
 * @property remaining T₀ − S − scheduled. 음수면 (미리 적은 지출까지 쳐서) 지난달보다 더 쓰게 된다.
 * @property scheduled 오늘 뒤 날짜로 미리 적은 이번 달 순지출
 * @property daysLeft 오늘을 포함한 남은 날 수
 * @property dailyAllowance remaining > 0 일 때 하루에 쓸 수 있는 돈(반올림). 아니면 null
 */
data class Pace(val remaining: Long, val scheduled: Long, val daysLeft: Int, val dailyAllowance: Long?)

/** 월별 '눈에 띄는 점' 한 줄. 문장은 calc 의 insightSentence 가 만든다. */
sealed interface Insight {
    /** 1위 지출 분류가 지출의 [percent]% 를 차지한다(20% 이상) */
    data class TopShare(val entry: BreakdownEntry, val percent: Int) : Insight

    /** 지난달 대비 가장 크게 달라진 분류 */
    data class CategoryChange(val entry: BreakdownEntry, val change: EntryChange) : Insight

    /** 주말과 평일의 하루 평균 지출 비. [ratio] 는 큰 쪽 ÷ 작은 쪽(1.3 이상) */
    data class WeekPattern(val weekendHigher: Boolean, val ratio: Double) : Insight

    /** 돈을 안 쓴 날 수 */
    data class NoSpendDays(val days: Int) : Insight
}

/** 분류 이야기(1위 비율, 증감)의 분류. 요일·돈 안 쓴 날 이야기면 null */
val Insight.categoryEntry: BreakdownEntry?
    get() = when (this) {
        is Insight.TopShare -> entry
        is Insight.CategoryChange -> entry
        is Insight.WeekPattern, is Insight.NoSpendDays -> null
    }

/** 이 분류·결제수단의 상세 주소. [GroupKey.None] 이면 id 가 null 인 상세('분류 없음' / '결제수단 없음')다. */
fun GroupKey.detailKey(dimension: StatsDimension, month: YearMonth): StatsDetailKey =
    StatsDetailKey(dimension, (this as? GroupKey.Id)?.id, month.year, month.monthValue)

// ── 일별 ─────────────────────────────────────────────────────────────

/**
 * @property series 쌓는 계열(상위 지출 분류 + '그 외'). 분류 탭 도넛과 같은 계열·순서다.
 * @property days 1일부터 말일까지 하루씩
 * @property stackMax 하루 쌓은 높이(양수 조각의 합)의 최댓값
 * @property defaultDay 처음 고를 날(일). 이번 달은 오늘, 지나간 달은 가장 많이 쓴 날, 오지 않은 달은 기록이 있는 첫날
 * @property counted 평균 등에 넣는 날(일 범위). 기록 시작일부터 오늘까지로 자른다. 없으면 비어 있다.
 * @property average counted 안의 하루 평균 지출. counted 가 비었거나 음수면 null
 * @property spentDays counted 안에서 지출이 있었던 날 수
 * @property spentDayAverage 쓴 날 평균. 쓴 날이 없거나 환불이 더 많아 음수면 null
 * @property peak counted 안에서 가장 많이 쓴 날. 없으면 null
 * @property noSpendDays counted 안에서 지출이 없던 날 수. counted 가 비었으면 null
 * @property longestNoSpend 돈 안 쓴 날의 최장 연속 일수
 * @property weekday 요일별 하루 평균. 창이 14일 미만이면 null
 */
@Immutable
data class DailyStats(
    val series: List<StatSeries>,
    val days: List<DayStack>,
    val stackMax: Long,
    val defaultDay: Int,
    val counted: IntRange,
    val average: Long?,
    val spentDays: Int,
    val spentDayAverage: Long?,
    val peak: DayPeak?,
    val noSpendDays: Int?,
    val longestNoSpend: Int,
    val weekday: WeekdayStats?,
) {
    companion object {
        val EMPTY = DailyStats(emptyList(), emptyList(), 0, 1, IntRange.EMPTY, null, 0, null, null, null, 0, null)
    }
}

/**
 * 하루치.
 * @property expense 그날 순지출(홈 달력 칸과 같다. 환불이 더 많으면 음수)
 * @property segments [DailyStats.series] 순서의 조각. 계열마다 max(0, 순지출)
 * @property details 그날 금액이 0 이 아닌 지출 분류 전부(접힌 분류도 제 이름으로). 금액 내림차순
 * @property items 그날 거래(이체 빼고). 늦은 시각이 먼저
 * @property isFuture 오늘 뒤의 날인지
 */
data class DayStack(
    val date: LocalDate,
    val expense: Long,
    val income: Long,
    val refund: Long,
    val segments: List<Long>,
    val details: List<DayDetail>,
    val items: List<TransactionListItem>,
    val isFuture: Boolean,
)

/**
 * 하루 안 분류 하나. 견본은 [seriesIndex] 의 계열 색을 쓴다(접힌 분류면 '그 외' 색).
 * @property seriesIndex 이 분류가 속한 계열 번호. 계열이 없으면 null
 */
data class DayDetail(val name: String, val seriesIndex: Int?, val amount: Long)

data class DayPeak(val date: LocalDate, val amount: Long)

/**
 * 요일별 하루 평균(최근 3달, 기록 시작일부터 오늘까지).
 * @property averages 일요일부터 토요일까지(WEEK_ORDER)
 * @property top 가장 많이 쓴 요일. 모두 0 이면 null
 * @property weekendRatio 주말 하루 평균 ÷ 평일 하루 평균. 주말이나 평일 한쪽 평균이 0 이하면 null(몇 배인지 말할 수 없다)
 */
data class WeekdayStats(
    val from: LocalDate,
    val to: LocalDate,
    val averages: List<WeekdayAverage>,
    val top: DayOfWeek?,
    val weekendRatio: Double?,
)

/**
 * @property average 그 요일의 하루 평균 순지출(반올림). 환불이 더 많았으면 음수일 수 있다(막대는 0 으로 그린다).
 * @property days 창 안의 그 요일 날 수(0원인 날 포함)
 */
data class WeekdayAverage(val day: DayOfWeek, val average: Long, val days: Int)

// ── 분류·결제수단 ───────────────────────────────────────────────────────

/**
 * 한 기준(지출 분류, 수입 분류, 결제수단)으로 나눈 것.
 * @property total 이 기준의 합계. 지출이면 totals().expense 와 같다(음수 가능)
 * @property positiveTotal 비율의 분모. 양수 항목의 합
 * @property entries 금액이 0 이 아닌 항목 전부. 금액 내림차순 → None 뒤 → 이름 → id. 음수 항목은 맨 아래
 * @property series 도넛과 일별 쌓은 막대의 계열(상위 5~6개 + '그 외'). 양수 항목만
 * @property negativeCount 환불이 더 많아 음수인 항목 수
 */
@Immutable
data class Breakdown(
    val total: Long,
    val positiveTotal: Long,
    val entries: List<BreakdownEntry>,
    val series: List<StatSeries>,
    val negativeCount: Int,
) {
    companion object {
        val EMPTY = Breakdown(0, 0, emptyList(), emptyList(), 0)
    }
}

/**
 * 분류나 결제수단 하나.
 * @property name None 이면 '분류 없음' / '결제수단 없음'
 * @property icon 아이콘 이름(CategoryStyle). None 이면 null
 * @property color 색 이름(CategoryPalette). None 이면 null
 * @property count 넣은 거래 수(지출이면 지출·환불, 수입이면 수입)
 * @property share 비율(0~1). 음수 항목은 null
 * @property averageTicket 한 번에 평균 = 지출 합(환불 전) ÷ 지출 건수. 수입 쪽이나 지출이 없으면 null
 * @property change 지난달 대비. 비교할 수 없으면 null
 */
data class BreakdownEntry(
    val key: GroupKey,
    val name: String,
    val icon: String?,
    val color: String?,
    val amount: Long,
    val count: Int,
    val share: Double?,
    val averageTicket: Long?,
    val change: EntryChange?,
)

/** 지난달 대비 증감. [previous] 는 비교 범위(ComparisonWindow) 안의 지난 값, [current] 는 이번 값 */
data class EntryChange(val scope: ComparisonScope, val previous: Long, val current: Long)

/**
 * 차트 계열 하나.
 * @property key 한 항목이면 그 키. '그 외' 묶음이면 null
 * @property color 색 이름. '그 외'·'분류 없음' 은 null(차트는 chartOther 로 칠한다)
 * @property members 이 계열에 든 항목들
 */
data class StatSeries(
    val key: GroupKey?,
    val name: String,
    val color: String?,
    val amount: Long,
    val share: Double,
    val members: List<GroupKey>,
)

/** 많이 쓴 곳. [name] 은 가장 최근에 적은 원래 이름, [averageTicket] 은 한 번에 평균 */
data class MerchantStat(val name: String, val amount: Long, val count: Int, val averageTicket: Long)
