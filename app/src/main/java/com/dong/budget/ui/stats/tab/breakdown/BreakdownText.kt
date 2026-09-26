package com.dong.budget.ui.stats.tab.breakdown

import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatShare
import com.dong.budget.ui.format.formatSignedTotal
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.MerchantStat
import com.dong.budget.ui.stats.StatSeries
import com.dong.budget.ui.stats.calc.changeText
import com.dong.budget.ui.stats.calc.entrySummary

// ─────────────────────────────────────────────────────────────────────
// 분류·결제수단 줄의 글자. 보이는 문장은 calc 의 문장 함수(entrySummary, changeText)를 그대로 쓰고,
// 여기에는 금액 표기, 줄 아래 덧붙임, 화면 읽기가 한 번에 읽을 문장만 둔다.
// 화면 읽기 문장은 금액을 줄이지 않고 다 읽는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 줄 오른쪽 금액. "523,000원"
 * 환불이 더 많은 항목(음수)은 돌려받은 쪽이라 "+12,000원" 으로 적는다(income 색). 부호가 방향을 전한다.
 */
fun entryAmountText(amount: Long): String = if (amount < 0) formatSignedTotal(-amount) else "${formatAmount(amount)}원"

/** 머리의 부제. "분류 5개" / "결제수단 3개". 목록 줄 수와 같다. */
fun entryCountText(count: Int, dimension: StatsDimension): String = "${dimension.noun()} ${count}개"

/**
 * 묶음 아래 덧붙이는 안내. 결제수단을 지우면 그 거래가 결제수단 없이 남아 '결제수단 없음' 에 모인다.
 * @return 덧붙일 것이 없으면 null
 */
fun groupNote(key: GroupKey, dimension: StatsDimension): String? =
    if (dimension == StatsDimension.PAYMENT_METHOD && key == GroupKey.None) "지운 결제수단으로 적은 거래도 여기에 모여요" else null

/**
 * 순위 줄을 화면 읽기가 한 번에 읽을 문장. 보이는 줄과 같은 순서다.
 * "식비, 523,000원, 지출의 42퍼센트, 12건, 지난달 이맘때보다 30,000원 늘었어요"
 * 결제수단은 "…, 12건, 한 번에 평균 20,000원, …" 이 붙고, 환불이 더 많은 줄은 비율 대신 "환불받은 돈이 더 많아요" 다.
 */
fun entryDescription(entry: BreakdownEntry, dimension: StatsDimension): String = buildList {
    add(entry.name)
    add(entryAmountText(entry.amount))
    val share = entry.share
    if (share == null) {
        add(entrySummary(entry, dimension))
    } else {
        add("${dimension.wholeName()}의 ${spokenShare(share)}")
        add("${entry.count}건")
        entry.averageTicket?.takeIf { dimension == StatsDimension.PAYMENT_METHOD }?.let { add("한 번에 평균 ${formatAmount(it)}원") }
    }
    entry.change?.let { add(changeText(it, spoken = true)) }
    groupNote(entry.key, dimension)?.let(::add)
}.joinToString(", ")

/** 도넛 옆 범례 한 줄을 읽는 문장. "식비 42퍼센트" */
fun legendDescription(series: StatSeries): String = "${series.name} ${spokenShare(series.share)}"

/** 많이 쓴 곳 한 줄을 읽는 문장. "1위, 스타벅스, 52,000원, 4번, 한 번에 평균 13,000원" */
fun merchantDescription(rank: Int, merchant: MerchantStat): String = listOf(
    "${rank}위",
    merchant.name,
    "${formatAmount(merchant.amount)}원",
    "${merchant.count}번",
    "한 번에 평균 ${formatAmount(merchant.averageTicket)}원",
).joinToString(", ")

/** 비율을 소리 내어 읽는 꼴. "42%" → "42퍼센트", "1% 미만" → "1퍼센트 미만". 상세 머리도 쓴다. */
internal fun spokenShare(share: Double): String = formatShare(share).replace("%", "퍼센트")

/** 비율이 무엇의 몫인지. 결제수단도 지출만 모으므로 '지출' 이다. 상세 머리도 쓴다. */
internal fun StatsDimension.wholeName(): String = if (this == StatsDimension.INCOME_CATEGORY) "수입" else "지출"

private fun StatsDimension.noun(): String = if (this == StatsDimension.PAYMENT_METHOD) "결제수단" else "분류"
