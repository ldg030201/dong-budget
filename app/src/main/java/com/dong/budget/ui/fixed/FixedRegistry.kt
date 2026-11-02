package com.dong.budget.ui.fixed

import androidx.compose.runtime.Immutable
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.fixedHistoryEnd
import com.dong.budget.data.db.fixedHistoryStart
import com.dong.budget.data.db.fixedMerchantKey
import com.dong.budget.ui.home.localDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 목록으로 '고정지출' 분류 지출을 나눈다. 분류는 거래마다 그대로 두고, 탭에 보일 곳은 목록이 정한다.
// - 등록한 곳: 그 '고정지출' 분류 지출을 고정지출 계산([buildFixedExpenses])에 넘긴다. 요약 · 묶음은 등록한 곳(과 끝 달까지의 해지한 곳)만 센다.
// - 정한 적 없는 곳과, 등록 안 한(안 함 · 탭에서 뺌) 뒤에 새로 낸 곳: '고정지출로 등록할까요?' 로 묻는다(뺐다가 다시 구독한 것 등).
// - 그 밖의 등록 안 한 곳: 접어 둔 '등록 안 한 곳' 에 두어 다시 등록할 수 있게 한다.
// - 해지한 곳: 해지 전에 낸 결제만 끝 달(해지 전 마지막 결제의 몫 달)까지 계산에 넘기고, 접어 둔 '해지한 곳' 에 두어 되돌릴 수 있게 한다.
//   계산은 덜 낸 차례를 기다리지 않는다([FixedRegistrySplit.board]). 해지한 뒤에 새로 내면 다시 등록할지 묻는다.
// 묻는 곳 · 등록 안 한 곳 · 해지한 곳과 끝 달은 고른 달과 상관없이 탭이 이번 달을 볼 때 읽는 기간([fixedHistoryStart] ~ 이번 달)의
// 지출로만 본다. 그래서 지난 달을 넘겨 봐도 같고, DB 를 올릴 때 등록한 곳(같은 기간에 지출이 있는 곳)과도 맞는다.
// 고정저축 탭도 '저축' 분류 지출을 고정저축 목록으로 똑같이 나눈다(나누기는 분류 · 종류를 모른다).
// ─────────────────────────────────────────────────────────────────────

/**
 * 판에 없는(묻거나 등록 안 했거나 해지한) 한 곳. 가장 최근 그 분류('고정지출' · '저축') 지출([latest])로 보여 준다.
 * @property key 고정지출 · 고정저축 탭이 묶는 열쇠([fixedMerchantKey]). 목록에 이 열쇠로 정한다.
 * @property name 보이는 이름([fixedMerchantName]). 이름 없이 묶인 것은 [NO_MERCHANT_NAME]
 * @property latest 가장 최근 지출. 줄을 누르면 이 거래의 상세가 열리고, 뱃지 · 결제수단 · 금액도 이것으로 보여 준다.
 */
@Immutable
data class FixedCandidate(val key: String, val name: String, val latest: TransactionListItem) {
    /** 가장 최근에 낸 날 */
    val lastPaidOn: LocalDate get() = latest.localDate()
}

/**
 * 해지한 한 곳.
 * @property place 줄에 보여 줄 것. 이번 달 기준 기간의 해지 전 가장 최근 결제로 보여 준다. 그 기간에 해지 전 결제가 없으면
 *   (해지한 뒤에 낸 것에 '안 함' 을 눌러 둔 것뿐이면) 그 기간의 가장 최근 결제다.
 * @property rows 해지 전 결제(해지한 뒤에 낸 · 적은 것이 아닌 것, [isNewSince]). 받은 기간 전부라 지난 달 판도 등록한 곳처럼 옛 결제까지 센다.
 * @property endMonth 끝 달. 이 달까지는 판에 그대로 보이고 그다음 달부터는 안 보인다. 해지 전 결제로 오늘 본 마지막 몫의 달이고,
 *   다음 차례 몫을 미리 냈으면 그 몫의 달이다([endMonthOf]). 이번 달 기준 기간에 해지 전 결제가 없거나(날짜를 앞으로 적어 둔 다음 달 이후
 *   것뿐) 몫이 모두 이번 달 뒤면 null 이라 오늘은 어느 달 판에도 안 보인다. 오늘에 따라 보는 것이라, 그 날짜가 든 달이 오면 그 결제가
 *   기간에 들어와 그 몫의 달이 끝 달이 된다(해지 전에 적어 둔 결제라 해지한 뒤 날짜여도 판에 센다).
 * @property asking 해지한 뒤에 새로 내서 다시 등록할지 묻는 중인지. 묻는 동안에는 '해지한 곳' 묶음에 두지 않는다(판에는 끝 달까지 그대로다).
 */
@Immutable
data class FixedEndedPlace(val place: FixedCandidate, val rows: List<TransactionListItem>, val endMonth: YearMonth?, val asking: Boolean) {
    val key: String get() = place.key

    /** [month] 판에 보이는지. 끝 달까지다. */
    fun showsIn(month: YearMonth): Boolean = endMonth != null && month <= endMonth
}

/**
 * 고정지출 목록으로 나눈 '고정지출' 분류 지출.
 * @property registered 등록한 곳의 지출. 고정지출 계산에 넘긴다(받은 순서 그대로).
 * @property candidates 등록할지 물을 곳(해지한 뒤에 새로 낸 곳 포함). 가장 최근에 낸 곳부터
 * @property declined 등록 안 한 곳(다시 묻지 않는 것). 가장 최근에 낸 곳부터
 * @property ended 해지한 곳(다시 묻는 곳 포함). 가장 최근에 낸 곳부터
 */
data class FixedRegistrySplit(
    val registered: List<TransactionListItem>,
    val candidates: List<FixedCandidate>,
    val declined: List<FixedCandidate>,
    val ended: List<FixedEndedPlace> = emptyList(),
) {
    /** 접어 두는 '해지한 곳' 묶음의 줄. 다시 묻는 곳은 빼고, 고른 달과 상관없이 같다. */
    val endedPlaces: List<FixedCandidate> get() = ended.filterNot { it.asking }.map { it.place }

    /** [month] 판의 고정지출 계산에 넘길 지출. 등록한 곳의 지출과, 끝 달이 [month] 보다 앞이 아닌 해지한 곳의 해지 전 결제 */
    fun boardRows(month: YearMonth): List<TransactionListItem> = registered + ended.filter { it.showsIn(month) }.flatMap { it.rows }

    /** [month] 판에 보이는 해지한 곳의 열쇠 */
    fun endedKeysIn(month: YearMonth): Set<String> = ended.filter { it.showsIn(month) }.mapTo(HashSet()) { it.key }

    /**
     * [month] 판([boardRows] 를 [today] 에 계산한 것). 해지한 곳은 남은 차례를 더 내지 않으니 덜 낸 차례를 기다리지 않는다
     * (한 달에 두 번 내다 한 번만 내고 해지한 달은 '2번 중 1번만 냈어요' 인 냈어요이고, 남은 차례는 낼 돈에 들지 않는다).
     */
    fun board(month: YearMonth, today: LocalDate): FixedExpenseBoard =
        buildFixedExpenses(month, today, boardRows(month), endedKeysIn(month))
}

/**
 * [rows] ('고정지출' 분류 지출, 탭이 고른 달을 계산하려고 읽은 것)를 고정지출 목록 [registry] (열쇠마다 정한 것)로 나눈다.
 * 묻는 곳 · 등록 안 한 곳 · 해지한 곳은 [today] 에 탭이 이번 달을 볼 때 읽는 기간의 지출로만 본다(지출이 아닌 행은 거른다).
 * 등록 안 한 곳 · 해지한 곳은 정한 때([FixedMerchantEntity.decidedAt]) 뒤에 낸 지출이 있어야 다시 묻는다. 날짜를 앞으로 적어 둔 옛 거래가
 * 정하자마자 다시 묻게 하지 않도록, 그 지출도 정한 뒤에 적은 것이어야 한다([isNewSince]).
 * 해지한 곳은 해지한 때([FixedMerchantEntity.endedAt]) 뒤에 낸 · 적은 것이 아닌 결제가 해지 전 결제다([splitEnded]).
 * @param created [rows] 의 거래 번호 → 처음 적은 때(FixedExpenseRow.createdAt). 없는 번호는 낸 때로만 본다.
 */
fun splitByRegistry(
    rows: List<TransactionListItem>,
    registry: Map<String, FixedMerchantEntity>,
    created: Map<Long, Instant>,
    today: LocalDate,
): FixedRegistrySplit {
    val thisMonth = YearMonth.from(today)
    val window = fixedHistoryStart(thisMonth).atDay(1)..fixedHistoryEnd(thisMonth, today).atEndOfMonth()
    // 가게 열쇠는 줄마다 한 번만 만든다(띄어쓰기를 빼고 소문자로 바꾸는 일이라). 등록한 곳의 줄은 받은 순서 그대로 둔다.
    val (registered, rest) =
        rows.map { fixedMerchantKey(it.merchant) to it }.partition { (key, _) -> registry[key]?.state == FixedMerchantState.REGISTERED }
    val (endedRows, unlistedRows) = rest.partition { (key, _) -> registry[key]?.state == FixedMerchantState.ENDED }
    val unlisted =
        unlistedRows
            .filter { (_, row) -> row.isSeenIn(window) }
            .groupBy({ it.first }, { it.second })
    val (asked, quiet) =
        unlisted.entries.partition { (key, pays) ->
            val decided = registry[key] ?: return@partition true
            pays.any { isNewSince(it, created[it.id], decided.decidedAt) }
        }
    val endings = splitEnded(endedRows.groupBy({ it.first }, { it.second }), registry, created, today, window)
    return FixedRegistrySplit(
        registered = registered.map { it.second },
        candidates = (asked.map { (key, pays) -> candidateOf(key, pays) } + endings.mapNotNull { it.ask }).sortedWith(latestFirst),
        declined = quiet.map { (key, pays) -> candidateOf(key, pays) }.sortedWith(latestFirst),
        ended = endedPlacesOf(endings, today),
    )
}

/**
 * 해지한 곳들([byKey], 가게 열쇠 → 받은 지출 전부)을 나눈다. 이번 달 기준 기간([window])에 지출이 없는 곳은 묻지도 두지도 않는다.
 * - 해지 전 결제: 해지한 때 뒤에 낸 · 적은 것이 아닌 결제([isNewSince]). 끝 달까지 판에 넘긴다.
 * - 다시 묻기: 정한 때 뒤에 낸 · 적은 결제가 기간 안에 있으면 묻는다. 묻는 줄은 그 새 결제로 보여 준다.
 *   해지한 곳에 다시 물은 것에 '안 함' 을 누르면 정한 때만 바뀌어, 해지한 때와 정한 때 사이의 결제는 묻지도 판에 넘기지도 않는다.
 */
private fun splitEnded(
    byKey: Map<String, List<TransactionListItem>>,
    registry: Map<String, FixedMerchantEntity>,
    created: Map<Long, Instant>,
    today: LocalDate,
    window: ClosedRange<LocalDate>,
): List<EndedRows> = byKey.mapNotNull { (key, pays) ->
    val seen = pays.filter { it.isSeenIn(window) }
    if (seen.isEmpty()) return@mapNotNull null
    val decided = registry.getValue(key)
    // 해지한 때는 해지한 곳에만 있다. 손으로 고친 백업처럼 빠졌으면 정한 때에 해지한 것으로 본다(백업 복원과 같다).
    val endedAt = decided.endedAt ?: decided.decidedAt
    val fresh = seen.filter { isNewSince(it, created[it.id], decided.decidedAt) }
    val beforeSeen = seen.filterNot { isNewSince(it, created[it.id], endedAt) }
    EndedRows(
        before = pays.filterNot { isNewSince(it, created[it.id], endedAt) },
        beforeSeen = beforeSeen,
        place = candidateOf(key, beforeSeen.ifEmpty { seen }),
        ask = fresh.takeIf { it.isNotEmpty() }?.let { candidateOf(key, it) },
    )
}

/**
 * 해지한 한 곳을 나눈 것.
 * @property before 해지 전 결제 전부. 끝 달까지 판에 넘긴다.
 * @property beforeSeen 그 가운데 이번 달 기준 기간의 것. 끝 달을 센다.
 * @property place '해지한 곳' 묶음의 줄
 * @property ask 해지한 뒤에 새로 내서 다시 물을 줄(그 새 결제로 보여 준다). 묻지 않으면 null
 */
private class EndedRows(
    val before: List<TransactionListItem>,
    val beforeSeen: List<TransactionListItem>,
    val place: FixedCandidate,
    val ask: FixedCandidate?,
)

/** 나눈 해지한 곳들([ended])에 끝 달을 붙인다. 가장 최근에 낸 곳부터 */
private fun endedPlacesOf(ended: List<EndedRows>, today: LocalDate): List<FixedEndedPlace> {
    val ends = endMonthOf(ended.flatMap { it.beforeSeen }, today)
    return ended
        .map { FixedEndedPlace(place = it.place, rows = it.before, endMonth = ends[it.place.key], asking = it.ask != null) }
        .sortedWith(compareBy(latestFirst) { it.place })
}

/**
 * 해지한 곳들의 끝 달. [rows] 는 해지한 곳들의 해지 전 결제 가운데 이번 달 기준 기간의 것이다. 오늘이 든 달로 고정지출을 계산해
 * 가게마다 마지막 몫의 달을 끝 달로 본다. 다음 차례 몫을 미리 냈으면(2월에 3월 몫을 냄, [FixedExpenseItem.prepaidMonth]) 그 달이다.
 * 고른 달로 계산하면 마지막 몫이 그 달까지로 잘리고, 고른 달마다 읽는 기간이 달라 끝 달이 달라져서 오늘이 든 달 · 이번 달 기준 기간으로만 센다.
 * 가게끼리 계산이 따로라 한 번에 넣는다. 몫이 모두 이번 달 뒤인 곳(다음 달 몫을 미리 낸 것뿐)은 들어 있지 않다.
 */
private fun endMonthOf(rows: List<TransactionListItem>, today: LocalDate): Map<String, YearMonth> {
    if (rows.isEmpty()) return emptyMap()
    val board = buildFixedExpenses(YearMonth.from(today), today, rows)
    return sequenceOf(board.due, board.paid, board.notThisMonth, board.stopped)
        .flatten()
        .associate { it.key to maxOf(it.lastShareMonth, it.prepaidMonth ?: it.lastShareMonth) }
}

/** 묻는 곳 · 등록 안 한 곳 · 해지한 곳을 가릴 때 보는 지출인지. 이번 달 기준 기간([window])의 지출만 본다. */
private fun TransactionListItem.isSeenIn(window: ClosedRange<LocalDate>): Boolean = type == TransactionType.EXPENSE && localDate() in window

/**
 * [row] 가 [at] 뒤에 새로 낸 지출인지. 낸 때가 뒤이고, 적은 때([createdAt])도 뒤여야 한다(적은 때를 모르면 낸 때로만 본다).
 * '안 함' · 빼기 전에 날짜를 앞으로 적어 둔 거래는 낸 때만 뒤라 새 지출이 아니다. 정한 뒤에 지난 날짜로 적은 거래도 아니다.
 */
internal fun isNewSince(row: TransactionListItem, createdAt: Instant?, at: Instant): Boolean =
    row.occurredAt > at && (createdAt ?: row.occurredAt) > at

/** 한 곳의 지출([pays], 비어 있지 않다)을 가장 최근 것으로 보여 줄 줄 */
private fun candidateOf(key: String, pays: List<TransactionListItem>): FixedCandidate {
    val latest = pays.maxWith(compareBy<TransactionListItem> { it.occurredAt }.thenBy { it.id })
    return FixedCandidate(key = key, name = fixedMerchantName(key, latest.merchant) ?: NO_MERCHANT_NAME, latest = latest)
}

/** 가장 최근에 낸 곳부터, 같으면 이름순 */
private val latestFirst = compareByDescending<FixedCandidate> { it.lastPaidOn }.thenBy { it.name }
