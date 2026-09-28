package com.dong.budget.ui.detail

import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatSpentAmount

// ─────────────────────────────────────────────────────────────────────
// 거래 상세 문구
// ─────────────────────────────────────────────────────────────────────

/** 같은 곳 섹션 제목. 지출·수입 모두 같다. */
const val SAME_PLACE_TITLE = "최근 내역"

/**
 * 같은 곳 섹션 제목 아래 한 줄. "최근 1년 · 12번 · 모두 69,600원 · 한 번에 평균 5,800원".
 * 한 번뿐이면 "최근 1년 · 1번 · 5,800원", 환불만 있으면 "최근 1년 · +3,000원" 이다.
 * 1년 안에 내역이 없으면 null 이고, 대신 [samePlaceEmptyText] 를 보여 준다.
 */
fun samePlaceSummary(samePlace: SamePlace): String? {
    if (samePlace.months.isEmpty()) return null
    val amount = if (samePlace.income) "${formatAmount(samePlace.amount)}원" else formatSpentAmount(samePlace.amount)
    return listOfNotNull(
        SAME_PLACE_PERIOD,
        samePlace.count.takeIf { it > 0 }?.let { "${it}번" },
        if (samePlace.count > 1) "모두 $amount" else amount,
        samePlace.averageTicket?.let { "한 번에 평균 ${formatAmount(it)}원" },
    ).joinToString(" · ")
}

/** 1년 안에 같은 곳 내역이 없을 때(보는 거래가 1년보다 오래됐고 그 뒤로 없을 때) */
fun samePlaceEmptyText(samePlace: SamePlace): String = if (samePlace.income) "최근 1년 동안 같은 곳에서 받은 내역이 없어요" else "최근 1년 동안 같은 곳에서 쓴 내역이 없어요"

/** 같은 곳 내역에서 지금 보고 있는 거래. 화면에는 바탕색으로만 보이고, 화면 읽기는 이 말로 알려 준다. */
const val CURRENT_STATE = "지금 보는 내역"

private const val SAME_PLACE_PERIOD = "최근 1년"
