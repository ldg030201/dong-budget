package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.ComparisonWindow
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.StatSeries

// ─────────────────────────────────────────────────────────────────────
// 분류·결제수단별로 나누기. 분류 탭, 결제수단 탭, 일별 쌓은 막대, 상세가 같이 쓴다.
// 금액은 홈 합계(Totals)로 잰다. 그래서 모든 항목을 더하면 그 달 지출(수입)과 같다.
// ─────────────────────────────────────────────────────────────────────

/** 무엇을 잴지 */
enum class Measure {
    /** 지출과 환불을 넣고 순지출(지출 − 환불)로 잰다. 환불이 더 많으면 음수다. */
    EXPENSE,

    /** 수입만 넣는다. */
    INCOME,
    ;

    fun includes(item: TransactionListItem): Boolean = when (this) {
        EXPENSE -> item.type == TransactionType.EXPENSE || item.type == TransactionType.REFUND
        INCOME -> item.type == TransactionType.INCOME
    }

    /** [rows] 에서 이 쪽 금액. 들어오지 않는 행은 Totals 가 알아서 뺀다. */
    fun amountOf(rows: Iterable<TransactionListItem>): Long = rows.totals().let { if (this == EXPENSE) it.expense else it.income }
}

/**
 * 무엇으로 묶을지.
 * @property noneName 분류(결제수단)가 비었거나 지운 것으로 적은 거래를 모은 줄의 이름
 * @property missingName id 는 있는데 이름을 못 찾은 줄의 이름. 조회 중에 지워진 경우다.
 */
enum class Grouping(val noneName: String, val missingName: String) {
    CATEGORY("분류 없음", "지운 분류"),
    PAYMENT_METHOD("결제수단 없음", "지운 결제수단"),
}

/** 상세 화면의 차원이 무엇을 잴지 */
val StatsDimension.measure: Measure
    get() = if (this == StatsDimension.INCOME_CATEGORY) Measure.INCOME else Measure.EXPENSE

/** 상세 화면의 차원이 무엇으로 묶을지 */
val StatsDimension.grouping: Grouping
    get() = if (this == StatsDimension.PAYMENT_METHOD) Grouping.PAYMENT_METHOD else Grouping.CATEGORY

/** 이 거래가 [grouping] 에서 들어가는 묶음 */
fun TransactionListItem.groupKey(grouping: Grouping): GroupKey {
    val id = if (grouping == Grouping.CATEGORY) categoryId else paymentMethodId
    return if (id == null) GroupKey.None else GroupKey.Id(id)
}

private fun TransactionListItem.groupName(grouping: Grouping): String? =
    if (grouping == Grouping.CATEGORY) categoryName else paymentMethodName

private fun TransactionListItem.groupIcon(grouping: Grouping): String? =
    if (grouping == Grouping.CATEGORY) categoryIcon else paymentMethodIcon

private fun TransactionListItem.groupColor(grouping: Grouping): String? =
    if (grouping == Grouping.CATEGORY) categoryColor else paymentMethodColor

/**
 * [rows] 를 [grouping] 으로 나눠 [measure] 로 잰다.
 *
 * @param rows 한 달(또는 상세처럼 이미 거른) 거래. 이체가 섞여 와도 된다.
 * @param window 지난달 대비 증감을 잴 범위. null 이면 증감을 붙이지 않는다.
 */
fun breakdown(rows: List<TransactionListItem>, measure: Measure, grouping: Grouping, window: ComparisonWindow?): Breakdown {
    val included = rows.filter(measure::includes)
    val groups = included.groupBy { it.groupKey(grouping) }
    val positiveTotal = groups.values.sumOf { measure.amountOf(it).coerceAtLeast(0) }
    val changes = changesByKey(window, measure, grouping)
    val entries = groups
        .map { (key, items) -> entryOf(key, items, measure, grouping, positiveTotal, changes(key)) }
        .filter { it.amount != 0L }
        .sortedWith(ENTRY_ORDER)
    return Breakdown(
        total = measure.amountOf(included),
        positiveTotal = positiveTotal,
        entries = entries,
        series = seriesOf(entries, positiveTotal),
        negativeCount = entries.count { it.amount < 0 },
    )
}

private fun entryOf(
    key: GroupKey,
    items: List<TransactionListItem>,
    measure: Measure,
    grouping: Grouping,
    positiveTotal: Long,
    change: EntryChange?,
): BreakdownEntry {
    val amount = measure.amountOf(items)
    val sample = items.first()
    return BreakdownEntry(
        key = key,
        name = if (key == GroupKey.None) grouping.noneName else sample.groupName(grouping) ?: grouping.missingName,
        icon = if (key == GroupKey.None) null else sample.groupIcon(grouping),
        color = if (key == GroupKey.None) null else sample.groupColor(grouping),
        amount = amount,
        count = items.size,
        share = if (amount > 0) amount.toDouble() / positiveTotal else null,
        averageTicket = if (measure == Measure.EXPENSE) averageTicket(items) else null,
        change = change,
        sortOrder = if (grouping == Grouping.CATEGORY) sample.categorySortOrder else null,
    )
}

/** 한 번에 평균 = 지출 합 ÷ 지출 건수. 환불로 줄기 전 금액으로 잰다(한 번 결제할 때의 크기). 지출이 없으면 null */
internal fun averageTicket(items: List<TransactionListItem>): Long? {
    val expenses = items.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return null
    return divRound(expenses.sumOf { it.amount }, expenses.size)
}

/**
 * [key] 묶음의 지난달 대비 증감. 범위는 홈의 지난달 비교와 같은 [window] 다.
 * @return 비교할 수 없거나([window] 가 null), 두 쪽 모두 0 이라 말할 거리가 없으면 null
 */
fun entryChange(key: GroupKey, window: ComparisonWindow?, measure: Measure, grouping: Grouping): EntryChange? {
    window ?: return null
    val inGroup = inGroup(key, measure, grouping)
    return changeOf(window.scope, measure.amountOf(window.previous.filter(inGroup)), measure.amountOf(window.current.filter(inGroup)))
}

/** [key] 묶음에 들고 [measure] 로 재는 거래인지. 상세가 자기 거래를 거를 때도 쓴다. */
fun inGroup(key: GroupKey, measure: Measure, grouping: Grouping): (TransactionListItem) -> Boolean =
    { item -> measure.includes(item) && item.groupKey(grouping) == key }

/** 두 쪽 모두 0 이면 말할 거리가 없어 null */
private fun changeOf(scope: ComparisonScope, previous: Long, current: Long): EntryChange? =
    if (previous == 0L && current == 0L) null else EntryChange(scope, previous, current)

/** 묶음별 증감을 찾는 함수. 비교 창을 묶음마다 다시 훑지 않게 두 쪽을 한 번씩만 묶어 둔다. [window] 가 null 이면 늘 null */
private fun changesByKey(window: ComparisonWindow?, measure: Measure, grouping: Grouping): (GroupKey) -> EntryChange? {
    window ?: return { null }
    val previous = amountsByKey(window.previous, measure, grouping)
    val current = amountsByKey(window.current, measure, grouping)
    return { key -> changeOf(window.scope, previous[key] ?: 0L, current[key] ?: 0L) }
}

/** 묶음마다 [measure] 로 잰 금액 */
private fun amountsByKey(rows: List<TransactionListItem>, measure: Measure, grouping: Grouping): Map<GroupKey, Long> =
    rows.filter(measure::includes).groupBy { it.groupKey(grouping) }.mapValues { (_, items) -> measure.amountOf(items) }

/**
 * 목록 순서: 금액 내림차순 → 이름 없는 묶음(None)은 뒤 → 이름 → id.
 * 음수 항목은 금액 순서대로 자연히 맨 아래로 간다. 순서가 늘 같아야 달을 넘겨도 색과 자리가 흔들리지 않는다.
 */
internal val ENTRY_ORDER: Comparator<BreakdownEntry> =
    compareByDescending<BreakdownEntry> { it.amount }
        .thenBy { it.key == GroupKey.None }
        .thenBy { it.name }
        .thenBy { (it.key as? GroupKey.Id)?.id ?: Long.MAX_VALUE }

/**
 * 분류 순서: 설정의 분류 목록과 같은 정렬값 → 이름 → id. 정렬값이 없는 지운 분류와 분류 없음(None)은 뒤로 간다.
 * 날마다 쓴 돈의 칩·쌓는 순서·읽기 판이 이 순서다. 금액이 바뀌어도 자리가 그대로라 날끼리 견주기 쉽다.
 */
internal val CATEGORY_ORDER: Comparator<BreakdownEntry> =
    compareBy<BreakdownEntry> { it.key == GroupKey.None }
        .thenBy(nullsLast()) { it.sortOrder }
        .thenBy { it.name }
        .thenBy { (it.key as? GroupKey.Id)?.id ?: Long.MAX_VALUE }

/**
 * 차트 계열: 상위 몇 개 + '그 외'. 도넛과 일별 쌓은 막대가 같은 결과를 쓴다. 최대 [MAX_SERIES] 개다.
 *
 * 양수이고 이름 있는 항목(named)만 제 조각을 가진다. '분류 없음' 은 늘 접는다(색이 없어 다른 조각과 갈리지 않는다).
 * 접을 것이 있으면(분류 없음이 양수이거나 named 가 넘치면) named 는 5개까지, 없으면 6개까지 보인다.
 * 접힌 것이 하나뿐이면 그 이름을 그대로 쓰고, 여럿이면 '그 외 k개' 다.
 *
 * @param entries [ENTRY_ORDER] 로 정렬된 항목. 제 조각을 가질 항목은 늘 금액 순으로 고른다.
 * @param order 고른 계열을 늘어놓을 순서. null 이면 금액 순이다. 어느 쪽이든 '그 외' 는 맨 뒤다.
 */
fun seriesOf(entries: List<BreakdownEntry>, positiveTotal: Long, order: Comparator<BreakdownEntry>? = null): List<StatSeries> {
    val positive = entries.filter { it.amount > 0 }
    val named = positive.filter { it.key is GroupKey.Id }
    val none = positive.filter { it.key == GroupKey.None }
    val limit = if (none.isNotEmpty() || named.size > MAX_SERIES) MAX_SERIES - 1 else MAX_SERIES
    val picked = named.take(limit)
    val shown = (if (order == null) picked else picked.sortedWith(order)).map { it.toSeries(positiveTotal) }
    return shown + listOfNotNull(otherSeries(named.drop(limit) + none, positiveTotal))
}

private fun BreakdownEntry.toSeries(positiveTotal: Long): StatSeries =
    StatSeries(key = key, name = name, color = color, amount = amount, share = amount.toDouble() / positiveTotal, members = listOf(key))

/** 접힌 항목들을 한 계열로. 색은 null 이라 차트가 chartOther 로 칠한다. */
private fun otherSeries(rest: List<BreakdownEntry>, positiveTotal: Long): StatSeries? {
    if (rest.isEmpty()) return null
    if (rest.size == 1) return rest.single().toSeries(positiveTotal).copy(color = null)
    val amount = rest.sumOf { it.amount }
    return StatSeries(
        key = null,
        name = "그 외 ${rest.size}개",
        color = null,
        amount = amount,
        share = amount.toDouble() / positiveTotal,
        members = rest.map { it.key },
    )
}

/** 도넛 조각과 쌓은 막대 계열의 상한. 이보다 많으면 분류 색끼리 가려 보기 어렵다. */
const val MAX_SERIES = 6
