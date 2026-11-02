package com.dong.budget.ui.savings

import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatCompactWon
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.format.formatShare
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.home.baseText
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.tab.breakdown.spokenShare
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 저축 화면의 글자. 모은 돈은 쓴 돈이 아니라 거래 줄처럼 부호 없이 적고, 꺼낸 돈이 더 많을 때만 "-" 가 붙는다.
// 지출 통계의 말('썼어요', '환불받은 돈이 더 많아요')이 새지 않게 문장을 따로 둔다.
// ─────────────────────────────────────────────────────────────────────

/** 모은 돈. "300,000원", 꺼낸 돈이 더 많으면 "-50,000원" */
fun savedAmount(amount: Long): String = "${formatAmount(amount)}원"

// ── 내역 ─────────────────────────────────────────────────────────────

internal const val TOTAL_LABEL = "지금까지 모은 돈"
internal const val THIS_MONTH_LABEL = "이번 달"

// ── 통계 ─────────────────────────────────────────────────────────────

/** 통계 머리 라벨. "이번 달 모은 돈" / "9월 모은 돈" / "2025년 12월 모은 돈" */
fun savingsTitle(month: YearMonth, today: LocalDate): String = "${monthLabel(month, today)} 모은 돈"

/** 머리 금액이 음수일 때 붙이는 까닭 */
internal const val WITHDRAWN_OVER_TEXT = "넣은 돈보다 꺼낸 돈이 더 많아요"

/** 지난달 대비. "지난달 이맘때보다 5만원 더 모았어요" / "지난달보다 3만원 덜 모았어요" / "지난달만큼 모았어요" */
fun comparisonSentence(comparison: SavingsComparison): String {
    val base = comparison.scope.baseText
    return when {
        comparison.difference == 0L -> "${base}만큼 모았어요"
        comparison.difference > 0 -> "${base}보다 ${formatCompactWon(comparison.difference)} 더 모았어요"
        else -> "${base}보다 ${formatCompactWon(comparison.difference)} 덜 모았어요"
    }
}

/** 최근 6개월 막대 한 칸을 읽는 문장. "2026년 7월, 300,000원, 2건" / "2026년 5월, 저축을 시작하기 전" */
fun trendDescription(point: SavingsMonth): String = if (point.beforeFirst) {
    "${formatMonth(point.month)}, 저축을 시작하기 전"
} else {
    "${formatMonth(point.month)}, ${savedAmount(point.amount)}, ${point.count}건"
}

/** 한 해 모은 돈 줄의 라벨. "올해 모은 돈" / "2025년 모은 돈" */
fun yearLabel(year: YearSavings, today: LocalDate): String = if (year.year == today.year) "올해 모은 돈" else "${year.year}년 모은 돈"

/** 한 해 모은 돈 줄 아래 범위. "1월~9월" */
fun yearRange(year: YearSavings): String = "1월~${year.endMonth.monthValue}월"

/** 처음부터 고른 달까지 모은 돈 줄의 라벨. "이번 달까지 모은 돈" / "9월까지 모은 돈" */
fun cumulativeLabel(month: YearMonth, today: LocalDate): String = "${monthLabel(month, today)}까지 모은 돈"

internal const val MERCHANTS_TITLE = "상품별"
internal const val PAYMENT_TITLE = "결제수단별"

/** 결제수단 도넛 가운데 위 글자 */
internal const val DONUT_CAPTION = "모은 돈"

/**
 * 결제수단 줄 둘째 줄. "42% · 3건 · 한 번에 평균 10만원"(지출 통계의 결제수단 줄과 같은 꼴). 평균은 넣은 돈만으로 잰다([BreakdownEntry.averageTicket]).
 * 꺼낸 돈이 더 많은 결제수단은 비율 대신 "꺼낸 돈이 더 많아요"
 */
fun paymentSummary(entry: BreakdownEntry): String {
    val share = entry.share ?: return WITHDRAWN_MORE_TEXT
    return listOfNotNull(formatShare(share), "${entry.count}건", entry.averageTicket?.let { "한 번에 평균 ${formatCompactWon(it)}" })
        .joinToString(" · ")
}

/** 지운 결제수단으로 적은 저축이 '결제수단 없음' 에 모인다는 안내. 아니면 null */
fun paymentNote(entry: BreakdownEntry): String? = if (entry.key == GroupKey.None) "지운 결제수단으로 적은 저축도 여기에 모여요" else null

/**
 * 결제수단 줄을 화면 읽기가 한 번에 읽을 문장. 보이는 줄과 같은 순서이고 금액은 줄이지 않는다.
 * "토스뱅크, 300,000원, 모은 돈의 42퍼센트, 3건, 한 번에 평균 100,000원" / "현금, -50,000원, 꺼낸 돈이 더 많아요"
 */
fun paymentDescription(entry: BreakdownEntry): String = buildList {
    add(entry.name)
    add(savedAmount(entry.amount))
    val share = entry.share
    if (share == null) {
        add(WITHDRAWN_MORE_TEXT)
    } else {
        add("모은 돈의 ${spokenShare(share)}")
        add("${entry.count}건")
        entry.averageTicket?.let { add("한 번에 평균 ${formatAmount(it)}원") }
    }
    paymentNote(entry)?.let(::add)
}.joinToString(", ")

/** 결제수단이 하나뿐일 때 도넛 대신 쓰는 한 줄. "이번 달 저축은 모두 토스뱅크에서 나갔어요" */
fun singlePaymentSentence(monthLabel: String, name: String): String = "$monthLabel 저축은 모두 ${name}에서 나갔어요"

/** 음수 항목이 비율에서 빠졌다는 안내. "꺼낸 돈이 더 많은 결제수단 2개는 비율에서 뺐어요" */
fun withdrawnHint(count: Int): String = "꺼낸 돈이 더 많은 결제수단 ${count}개는 비율에서 뺐어요"

/** 저축이 없는 달. "9월에는 모은 돈이 없어요" */
fun emptyMonthTitle(month: YearMonth, today: LocalDate): String = "${monthLabel(month, today)}에는 모은 돈이 없어요"

internal const val EMPTY_MONTH_BODY = "'저축' 분류로 적으면 여기서 모아 볼 수 있어요"

private const val WITHDRAWN_MORE_TEXT = "꺼낸 돈이 더 많아요"

// ── 빈 화면 ───────────────────────────────────────────────────────────

/** 저축 거래가 하나도 없을 때(내역 · 통계 탭) */
internal const val EMPTY_TITLE = "아직 저축이 없어요"
internal const val EMPTY_BODY = "'저축' 분류로 적으면 여기 모여요"

/**
 * '저축' 분류를 지웠을 때. 같은 이름으로 다시 만들면 저장소가 기본 분류로 알아본다(지울 때 '기타' 로 옮겨 간 거래는 돌아오지 않는다).
 */
internal const val NO_CATEGORY_TITLE = "저축 분류가 없어요"
internal const val NO_CATEGORY_BODY = "분류 관리에서 '저축' 분류를 다시 만들면, 그 분류로 적은 돈을 여기서 볼 수 있어요."
internal const val OPEN_CATEGORIES_TEXT = "분류 관리 열기"
