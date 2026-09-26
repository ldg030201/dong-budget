package com.dong.budget.ui.stats.detail

import com.dong.budget.data.db.StyledItem
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.home.groupByDay
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.calc.Measure
import com.dong.budget.ui.stats.calc.TREND_MONTHS
import com.dong.budget.ui.stats.calc.breakdown
import com.dong.budget.ui.stats.calc.breakdownWindow
import com.dong.budget.ui.stats.calc.effectiveFirstRecord
import com.dong.budget.ui.stats.calc.entryChange
import com.dong.budget.ui.stats.calc.groupKey
import com.dong.budget.ui.stats.calc.grouping
import com.dong.budget.ui.stats.calc.isRecord
import com.dong.budget.ui.stats.calc.measure
import com.dong.budget.ui.stats.calc.topMerchants
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 통계 상세 계산. 분류·결제수단 탭과 같은 함수(breakdown, entryChange, topMerchants)를 쓴다.
// 거르는 규칙도 탭과 같다: 지출 쪽은 지출·환불, 수입 쪽은 수입만 넣고 이체는 어디에도 없다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 상세 화면 전체를 계산한다.
 *
 * - 머리의 비율·건수·한 번에 평균은 그 달 전체를 탭과 똑같이 나눈 것([breakdown])에서 이 항목을 꺼낸다.
 *   그래서 탭에서 누른 줄과 상세 머리의 숫자가 같다.
 * - 증감은 이 달 금액이 0 이어도 낸다(지난달에만 있었으면 "줄었어요").
 * - 교차 비중은 이 항목의 거래만 다른 기준으로 나눈 것이다. 증감은 붙이지 않는다(비중만 본다).
 *
 * @param id 분류나 결제수단의 id. null 이면 '분류 없음' / '결제수단 없음'
 * @param rows [month] 까지 최근 6개월의 거래 전부. 이체도 섞여 온다.
 * @param firstRecord 기록 시작일. 거래가 없으면 null
 * @param entity 이름·아이콘·색([detailEntity])
 */
fun buildDetail(
    dimension: StatsDimension,
    id: Long?,
    month: YearMonth,
    today: LocalDate,
    rows: List<TransactionListItem>,
    firstRecord: LocalDate?,
    entity: DetailEntity,
): StatsDetailUiState {
    val measure = dimension.measure
    val grouping = dimension.grouping
    val key = groupKeyOf(id)
    val inGroup = { item: TransactionListItem -> measure.includes(item) && item.groupKey(grouping) == key }
    val byMonth = rows.groupBy { YearMonth.from(it.localDate()) }
    val current = byMonth[month].orEmpty()
    val first = effectiveFirstRecord(rows, firstRecord)
    // 탭(buildStatistics)과 같은 비교가 되도록 그 달 거래 전부를 넘긴다. 지난달을 1일부터 기록하지 않았으면 증감을 내지 않는다.
    val window = breakdownWindow(month, today, current, byMonth[month.minusMonths(1)].orEmpty(), first)
    val mine = current.filter(inGroup)

    return StatsDetailUiState(
        loaded = true,
        dimension = dimension,
        key = key,
        month = month,
        today = today,
        entity = entity,
        amount = measure.amountOf(mine),
        entry = breakdown(current, measure, grouping, window).entries.firstOrNull { it.key == key },
        change = entryChange(key, window, measure, grouping),
        trend = detailTrend(month, byMonth, inGroup, measure, first),
        cross = dimension.crossDimension?.let { breakdown(mine, Measure.EXPENSE, it.grouping, window = null) },
        merchants = if (measure == Measure.EXPENSE) topMerchants(mine, DETAIL_MERCHANTS) else emptyList(),
        days = groupByDay(mine),
        count = mine.size,
    )
}

/** 상세 주소의 id 를 묶음 키로. null 이면 '분류 없음' / '결제수단 없음' */
fun groupKeyOf(id: Long?): GroupKey = id?.let { GroupKey.Id(it) } ?: GroupKey.None

/**
 * 상세가 보는 분류나 결제수단을 목록에서 찾는다.
 * @param items 분류 상세면 그 쪽(지출·수입) 분류 전부, 결제수단 상세면 결제수단 전부
 * @return id 가 null 이면 '분류 없음' / '결제수단 없음'. 못 찾으면(지웠으면) '지운 분류' / '지운 결제수단' 이고 missing 이다.
 */
fun detailEntity(dimension: StatsDimension, id: Long?, items: List<StyledItem>): DetailEntity {
    val grouping = dimension.grouping
    id ?: return DetailEntity(name = grouping.noneName, icon = null, color = null, missing = false)
    val found =
        items.firstOrNull { it.id == id } ?: return DetailEntity(name = grouping.missingName, icon = null, color = null, missing = true)
    return DetailEntity(name = found.name, icon = found.icon, color = found.color, missing = false)
}

/**
 * 교차 비중의 줄이 무엇인지. 분류 상세는 결제수단별로, 결제수단 상세는 지출 분류별로 나눈다.
 * 수입은 결제수단으로 나눠 봐야 얻는 게 적어 수입 분류 상세에는 없다(null).
 */
val StatsDimension.crossDimension: StatsDimension?
    get() = when (this) {
        StatsDimension.EXPENSE_CATEGORY -> StatsDimension.PAYMENT_METHOD
        StatsDimension.PAYMENT_METHOD -> StatsDimension.EXPENSE_CATEGORY
        StatsDimension.INCOME_CATEGORY -> null
    }

/** 고른 달까지 최근 [TREND_MONTHS] 달의 이 항목 금액. 오래된 달이 앞이다. 해를 넘어도 그대로 이어진다. */
private fun detailTrend(
    month: YearMonth,
    byMonth: Map<YearMonth, List<TransactionListItem>>,
    inGroup: (TransactionListItem) -> Boolean,
    measure: Measure,
    firstRecord: LocalDate?,
): List<DetailMonth> = (TREND_MONTHS - 1 downTo 0).map { back ->
    val m = month.minusMonths(back.toLong())
    val mine = byMonth[m].orEmpty().filter(inGroup)
    DetailMonth(
        month = m,
        amount = measure.amountOf(mine),
        count = mine.size,
        beforeFirstRecord = firstRecord == null || m.isBefore(YearMonth.from(firstRecord)),
    )
}

/** 상세의 많이 쓴 곳은 5곳까지. 한 항목 안이라 탭(10곳)보다 적게 보인다. */
const val DETAIL_MERCHANTS = 5
