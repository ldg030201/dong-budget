package com.dong.budget.ui.stats.detail

import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatCompactWon
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.format.formatShare
import com.dong.budget.ui.format.formatSpentAmount
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.calc.breakdownLabel
import com.dong.budget.ui.stats.calc.changeText
import com.dong.budget.ui.stats.calc.entrySummary
import com.dong.budget.ui.stats.tab.breakdown.spokenShare
import com.dong.budget.ui.stats.tab.breakdown.wholeName

// ─────────────────────────────────────────────────────────────────────
// 통계 상세 문구. 탭과 같은 말은 calc 의 문장 함수(breakdownLabel, entrySummary, changeText)를 그대로 쓴다.
// 사용자가 지은 이름 뒤에는 조사를 붙이지 않는다('이 분류로' 처럼 고정된 말 뒤에서만 고른다).
// ─────────────────────────────────────────────────────────────────────

/** 머리 금액 위 라벨. "이번 달 지출" / "9월 수입". 결제수단도 지출만 모은다. */
fun detailLabel(monthLabel: String, dimension: StatsDimension): String = breakdownLabel(
    monthLabel,
    if (dimension == StatsDimension.INCOME_CATEGORY) StatsDimension.INCOME_CATEGORY else StatsDimension.EXPENSE_CATEGORY,
)

/**
 * 머리 부제. "지출의 42% · 12건 · 한 번에 평균 2만원", 수입 분류는 "수입의 30% · 2건".
 * 환불이 더 많아 비율이 없으면 "환불받은 돈이 더 많아요 · 3건 · …" 이다.
 */
fun detailSummary(entry: BreakdownEntry, dimension: StatsDimension): String = listOfNotNull(
    entry.share?.let { "${dimension.wholeName()}의 ${formatShare(it)}" } ?: entrySummary(entry, dimension),
    "${entry.count}건",
    entry.averageTicket?.let { "한 번에 평균 ${formatCompactWon(it)}" },
).joinToString(" · ")

/**
 * 머리를 화면 읽기가 한 번에 읽을 문장. 금액은 줄이지 않는다.
 * "이번 달 지출, 523,000원, 지출의 42퍼센트, 12건, 한 번에 평균 20,000원, 지난달 이맘때보다 30,000원 늘었어요"
 */
fun detailDescription(state: StatsDetailUiState, monthLabel: String): String = buildList {
    add(detailLabel(monthLabel, state.dimension))
    add(formatSpentAmount(state.amount))
    state.entry?.let { entry ->
        add(entry.share?.let { "${state.dimension.wholeName()}의 ${spokenShare(it)}" } ?: entrySummary(entry, state.dimension))
        add("${entry.count}건")
        entry.averageTicket?.let { add("한 번에 평균 ${formatAmount(it)}원") }
    }
    state.change?.let { add(changeText(it, spoken = true)) }
}.joinToString(", ")

/** 지운 분류·결제수단의 상세. 분류를 지우면 거래가 '기타' 로, 결제수단을 지우면 '결제수단 없음' 으로 간다. */
fun missingHint(dimension: StatsDimension): String = if (dimension == StatsDimension.PAYMENT_METHOD) {
    "지운 결제수단의 거래는 '결제수단 없음'으로 옮겨졌어요"
} else {
    "지운 분류의 거래는 '기타'로 옮겨졌어요"
}

/** 교차 비중 섹션 제목. 분류 상세는 "결제수단별", 결제수단 상세는 "분류별". 수입 분류는 섹션이 없다(null). */
fun crossTitle(dimension: StatsDimension): String? = when (dimension) {
    StatsDimension.EXPENSE_CATEGORY -> "결제수단별"
    StatsDimension.PAYMENT_METHOD -> "분류별"
    StatsDimension.INCOME_CATEGORY -> null
}

/** 거래 목록 제목. "거래 12건" */
fun transactionsTitle(count: Int): String = "거래 ${count}건"

/** 이 달 이 항목의 거래가 없을 때. "9월에는 이 분류로 적은 거래가 없어요" / "…이 결제수단으로…" */
fun noTransactionsText(monthLabel: String, dimension: StatsDimension): String {
    val noun = if (dimension == StatsDimension.PAYMENT_METHOD) "이 결제수단으로" else "이 분류로"
    return "${monthLabel}에는 $noun 적은 거래가 없어요"
}

/** 6개월 막대 한 칸을 읽는 문장. "2026년 7월, 83,000원, 5건". 기록을 시작하기 전 달은 "2026년 3월, 기록을 시작하기 전" */
fun trendSlotDescription(point: DetailMonth): String = if (point.beforeFirstRecord) {
    "${formatMonth(point.month)}, 기록을 시작하기 전"
} else {
    "${formatMonth(point.month)}, ${formatSpentAmount(point.amount)}, ${point.count}건"
}
