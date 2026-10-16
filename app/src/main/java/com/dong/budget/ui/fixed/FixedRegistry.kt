package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.db.NO_MERCHANT_KEY
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.fixedMerchantKey
import com.dong.budget.ui.home.localDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 목록으로 '고정지출' 분류 지출을 나눈다. 분류는 거래마다 그대로 두고, 탭에 보일 곳은 목록이 정한다.
// - 등록한 곳: 그 '고정지출' 분류 지출을 고정지출 계산([buildFixedExpenses])에 넘긴다. 요약 · 묶음은 등록한 곳만 센다.
// - 정한 적 없는 곳과, 등록 안 한(안 함 · 탭에서 뺌) 뒤에 새로 낸 곳: '고정지출로 등록할까요?' 로 묻는다(해지했다 다시 구독한 것 등).
// - 그 밖의 등록 안 한 곳: 접어 둔 '등록 안 한 곳' 에 두어 다시 등록할 수 있게 한다.
// 묻는 곳과 등록 안 한 곳은 고른 달과 상관없이 탭이 이번 달을 볼 때 읽는 기간([fixedHistoryStart] ~ 이번 달)의 지출로만 본다.
// 그래서 지난 달을 넘겨 봐도 같고, DB 를 올릴 때 등록한 곳(같은 기간에 지출이 있는 곳)과도 맞는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 고정지출 목록에 없는(묻거나 등록 안 한) 한 곳. 가장 최근 '고정지출' 분류 지출로 보여 준다.
 * @property key 고정지출 탭이 묶는 열쇠([fixedMerchantKey]). 목록에 이 열쇠로 정한다.
 * @property name 가장 최근 지출의 가게 이름(앞뒤 공백을 뺀다). 이름 없이 묶인 것은 [NO_MERCHANT_NAME]
 * @property lastPaidOn 가장 최근에 낸 날
 * @property lastAmount 가장 최근에 낸 돈
 * @property latestId 가장 최근 거래. 줄을 누르면 이 거래의 상세가 열린다.
 * @property paymentMethodId 가장 최근 거래의 결제수단. 지웠거나 비웠으면 null이고, 이름·아이콘·색도 같다.
 */
@Immutable
data class FixedCandidate(
    val key: String,
    val name: String,
    val lastPaidOn: LocalDate,
    val lastAmount: Long,
    val latestId: Long,
    val paymentMethodId: Long?,
    val paymentMethodName: String?,
    val paymentMethodIcon: String?,
    val paymentMethodColor: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
)

/**
 * 고정지출 목록으로 나눈 '고정지출' 분류 지출.
 * @property registered 등록한 곳의 지출. 고정지출 계산에 넘긴다(받은 순서 그대로).
 * @property candidates 등록할지 물을 곳. 가장 최근에 낸 곳부터
 * @property declined 등록 안 한 곳(다시 묻지 않는 것). 가장 최근에 낸 곳부터
 */
@Immutable
data class FixedRegistrySplit(
    val registered: List<TransactionListItem> = emptyList(),
    val candidates: List<FixedCandidate> = emptyList(),
    val declined: List<FixedCandidate> = emptyList(),
)

/**
 * [rows] ('고정지출' 분류 지출, 탭이 고른 달을 계산하려고 읽은 것)를 고정지출 목록 [registry] (열쇠마다 정한 것)로 나눈다.
 * 묻는 곳 · 등록 안 한 곳은 [today] 에 탭이 이번 달을 볼 때 읽는 기간의 지출로만 본다(지출이 아닌 행은 거른다).
 * 등록 안 한 곳은 정한 때([FixedMerchantEntity.decidedAt]) 뒤에 낸 지출이 있어야 다시 묻는다. 날짜를 앞으로 적어 둔 옛 거래가 정하자마자
 * 다시 묻게 하지 않도록, 그 지출도 정한 뒤에 적은 것이어야 한다([isNewSince]).
 */
fun splitByRegistry(rows: List<TransactionListItem>, registry: Map<String, FixedMerchantEntity>, today: LocalDate): FixedRegistrySplit {
    val thisMonth = YearMonth.from(today)
    val from = fixedHistoryStart(thisMonth).atDay(1)
    val until = fixedHistoryEnd(thisMonth, today).atEndOfMonth()
    val byKey = rows.groupBy { fixedMerchantKey(it.merchant) }
    val registered = rows.filter { registry[fixedMerchantKey(it.merchant)]?.state == FixedMerchantState.REGISTERED }
    val unlisted =
        byKey
            .filterKeys { registry[it]?.state != FixedMerchantState.REGISTERED }
            .mapValues { (_, pays) -> pays.filter { it.type == TransactionType.EXPENSE && it.localDate() in from..until } }
            .filterValues { it.isNotEmpty() }
    val (asked, quiet) =
        unlisted.entries.partition { (key, pays) ->
            val decided = registry[key] ?: return@partition true
            pays.any { isNewSince(it, decided.decidedAt) }
        }
    return FixedRegistrySplit(
        registered = registered,
        candidates = asked.map { (key, pays) -> candidateOf(key, pays) }.sortedWith(latestFirst),
        declined = quiet.map { (key, pays) -> candidateOf(key, pays) }.sortedWith(latestFirst),
    )
}

/**
 * [row] 가 [at] 뒤에 새로 낸 지출인지. 낸 때가 뒤이고, 적은 때도 뒤여야 한다(적은 때를 모르면 낸 때로만 본다).
 * '안 함' · 빼기 전에 날짜를 앞으로 적어 둔 거래는 낸 때만 뒤라 새 지출이 아니다. 정한 뒤에 지난 날짜로 적은 거래도 아니다.
 */
internal fun isNewSince(row: TransactionListItem, at: Instant): Boolean = row.occurredAt > at && (row.createdAt ?: row.occurredAt) > at

/** 한 곳의 지출([pays], 비어 있지 않다)을 가장 최근 것으로 보여 줄 줄 */
private fun candidateOf(key: String, pays: List<TransactionListItem>): FixedCandidate {
    val latest = pays.maxWith(compareBy<TransactionListItem> { it.occurredAt }.thenBy { it.id })
    return FixedCandidate(
        key = key,
        name = latest.merchant?.trim()?.takeIf { key != NO_MERCHANT_KEY } ?: NO_MERCHANT_NAME,
        lastPaidOn = latest.localDate(),
        lastAmount = latest.amount,
        latestId = latest.id,
        paymentMethodId = latest.paymentMethodId,
        paymentMethodName = latest.paymentMethodName,
        paymentMethodIcon = latest.paymentMethodIcon,
        paymentMethodColor = latest.paymentMethodColor,
        categoryIcon = latest.categoryIcon,
        categoryColor = latest.categoryColor,
    )
}

/** 가장 최근에 낸 곳부터, 같으면 이름순 */
private val latestFirst = compareByDescending<FixedCandidate> { it.lastPaidOn }.thenBy { it.name }
