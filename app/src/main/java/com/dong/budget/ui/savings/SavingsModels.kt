package com.dong.budget.ui.savings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.dong.budget.R
import com.dong.budget.ui.history.HistoryMonth
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.MerchantStat
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 저축 화면이 그리는 값. 모두 '저축' 분류 거래(isSavings)에서 순수 함수(SavingsCalc)로 계산한다.
// 금액은 모은 돈(저축 − 꺼낸 돈, Totals.savings)이다. 꺼낸 돈(저축 분류의 환불)이 더 많으면 음수다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 저축 안의 하위 메뉴. 아래 떠 있는 메뉴에 이 순서로 선다.
 * @property paneTitle 탭을 바꿀 때 화면 읽기가 알리는 이름. 내역 메뉴 · 통계와 가리게 '저축' 을 붙인다.
 */
enum class SavingsTab(val label: String, @DrawableRes val icon: Int, val paneTitle: String) {
    /** 저축 거래를 달마다 늘어놓는다. 들어오면 이 칸부터 본다. 내역 메뉴와 같은 아이콘이다. */
    HISTORY("내역", R.drawable.ic_sym_receipt_long, "저축 내역"),

    /** 고른 달 모은 돈을 상품 · 결제수단으로 나눠 본다. 통계 메뉴와 같은 아이콘이다. */
    STATS("통계", R.drawable.ic_sym_bar_chart, "저축 통계"),

    /** 매달 넣는 저축(적금 · 청약 등)을 그 달 냈는지 본다. 고정지출 메뉴와 같은 아이콘이다. */
    FIXED("고정저축", R.drawable.ic_sym_event_repeat, "고정저축"),
}

/**
 * 저축 화면(내역 · 통계 탭)의 상태. 고정저축 탭은 따로 읽는다.
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 아래 떠 있는 메뉴만 그린다(빈 상태 안내가 잠깐 비치지 않게).
 * @property month 통계 탭에서 고른 달. 이번 달보다 뒤로는 가지 않는다.
 * @property hasCategory '저축'(코드 SAVINGS) 분류가 있는지. 분류 관리에서 지웠고 같은 이름(띄어쓰기 무시)의 분류도 없으면 false
 * @property history 내역 탭. 고른 달과 상관없이 같다.
 * @property stats 통계 탭. 고른 달의 것이다.
 */
@Immutable
data class SavingsUiState(
    val loaded: Boolean,
    val month: YearMonth,
    val today: LocalDate,
    val hasCategory: Boolean,
    val history: SavingsHistory,
    val stats: SavingsStats,
) {
    val isThisMonth: Boolean get() = month == YearMonth.from(today)

    /** 저축 거래가 하나라도 있는지(모든 달 통틀어) */
    val hasAny: Boolean get() = history.months.isNotEmpty()

    companion object {
        /** 첫 계산 전 자리 값 */
        fun loading(today: LocalDate): SavingsUiState = SavingsUiState(
            loaded = false,
            month = YearMonth.from(today),
            today = today,
            hasCategory = true,
            history = SavingsHistory.EMPTY,
            stats = SavingsStats.EMPTY,
        )
    }
}

/**
 * 내역 탭. 머리에 모은 돈 둘을 적고, 그 아래 저축 거래를 달마다 늘어놓는다(내역 화면과 같은 목록).
 * @property total 지금까지 모은 돈. 아래 목록을 모두 더한 것이라 날짜를 앞으로 적어 둔 저축도 들어 있다.
 * @property thisMonth 이번 달(오늘이 든 달) 모은 돈
 * @property months 달마다 묶은 저축 거래(최근 달부터). 달 머리에 그 달 모은 돈을 적는다(totalsText).
 */
@Immutable
data class SavingsHistory(val total: Long, val thisMonth: Long, val months: List<HistoryMonth>) {
    companion object {
        val EMPTY = SavingsHistory(0, 0, emptyList())
    }
}

/**
 * 통계 탭(고른 달).
 * @property total 고른 달 모은 돈. 날짜를 앞으로 적어 둔 저축도 넣는다(홈의 달 합계와 같은 규칙).
 * @property count 고른 달 저축 거래 수(꺼낸 것 포함). 0 이면 빈 달이다.
 * @property comparison 지난달 대비. 지난달에 저축이 없거나 오지 않은 달이면 null
 * @property trend 고른 달까지 최근 6개월(trendMonths). 오래된 달이 앞이다.
 * @property year 고른 달의 해 1월부터 고른 달까지 모은 돈. 저축을 그해보다 먼저 시작했고 고른 달이 1월이 아닐 때만 둔다
 *   (아니면 [total] 이나 [cumulative] 와 같은 값이라 겹친다).
 * @property cumulative 처음부터 고른 달까지 모은 돈. 저축을 고른 달보다 먼저 시작했을 때만 둔다(아니면 [total] 과 같다).
 * @property merchants 상품(가게 이름)별 모은 돈 상위 10곳(topMerchants). 모은 돈이 0 이하인 상품은 뺀다.
 * @property byPayment 결제수단별 모은 돈
 */
@Immutable
data class SavingsStats(
    val total: Long,
    val count: Int,
    val comparison: SavingsComparison?,
    val trend: List<SavingsMonth>,
    val year: YearSavings?,
    val cumulative: Long?,
    val merchants: List<MerchantStat>,
    val byPayment: Breakdown,
) {
    val monthIsEmpty: Boolean get() = count == 0

    companion object {
        val EMPTY = SavingsStats(0, 0, null, emptyList(), null, null, emptyList(), Breakdown.EMPTY)
    }
}

/**
 * 최근 6개월 막대의 한 칸.
 * @property count 그 달 저축 거래 수(꺼낸 것 포함)
 * @property beforeFirst 저축을 시작하기 전 달인지. 막대와 금액 없이 달 이름만 둔다.
 */
data class SavingsMonth(val month: YearMonth, val amount: Long, val count: Int, val beforeFirst: Boolean)

/**
 * 지난달 대비. 범위([scope])는 홈의 지난달 비교와 같은 규칙이다(이번 달은 오늘까지와 지난달 같은 날까지, 지나간 달은 한 달 전체끼리).
 * @property difference 이번 − 지난. 음수면 덜 모은 것이다.
 */
data class SavingsComparison(val difference: Long, val scope: ComparisonScope)

/** 한 해 모은 돈. 1월부터 [endMonth](고른 달)까지다. */
data class YearSavings(val year: Int, val endMonth: YearMonth, val amount: Long)
