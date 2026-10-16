package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.merchantKey
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.MerchantStat

// ─────────────────────────────────────────────────────────────────────
// 많이 쓴 곳. 가게 이름으로 묶는다(열쇠는 merchantKey).
// 이름이 비슷한 가게(예: '스타벅스 강남점' 과 '스타벅스')는 합치지 않는다. 틀리게 합치면 통계가 망가진다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 가게별 순지출(지출 − 환불) 상위 [limit] 곳. 순지출이 0 이하인 가게는 뺀다.
 * 이름은 가장 최근에 적은 원래 이름이다. 같은 금액이면 이름순이다.
 */
fun topMerchants(rows: List<TransactionListItem>, limit: Int = TOP_MERCHANTS): List<MerchantStat> = rows
    .filter { Measure.EXPENSE.includes(it) && merchantKey(it.merchant) != null }
    .groupBy { merchantKey(it.merchant) }
    .values
    .map(::merchantOf)
    .filter { it.amount > 0 }
    .sortedWith(compareByDescending<MerchantStat> { it.amount }.thenBy { it.name })
    .take(limit)

private fun merchantOf(items: List<TransactionListItem>): MerchantStat {
    val latest = items.maxWith(compareBy<TransactionListItem> { it.occurredAt }.thenBy { it.id })
    return MerchantStat(
        name = latest.merchant.orEmpty().trim(),
        amount = items.totals().expense,
        count = items.count { it.type == TransactionType.EXPENSE },
        // 순지출이 양수면 지출이 한 건은 있다
        averageTicket = averageTicket(items) ?: 0L,
    )
}

/** 많이 쓴 곳을 몇 곳까지 보이는지 */
const val TOP_MERCHANTS = 10
