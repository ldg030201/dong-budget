package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.format.formatDirected
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.calc.breakdownLabel
import com.dong.budget.ui.stats.calc.emptyMonthText
import com.dong.budget.ui.stats.calc.negativeHint
import com.dong.budget.ui.stats.calc.nothingText
import com.dong.budget.ui.stats.calc.singleSeriesSentence
import com.dong.budget.ui.stats.detailKey
import com.dong.budget.ui.stats.directedColor
import com.dong.budget.ui.stats.netExpenseColor
import com.dong.budget.ui.stats.tab.breakdown.BreakdownDonut
import com.dong.budget.ui.stats.tab.breakdown.BreakdownRow
import com.dong.budget.ui.stats.tab.breakdown.MerchantRow
import com.dong.budget.ui.stats.tab.breakdown.MerchantsSection
import com.dong.budget.ui.stats.tab.breakdown.entryCountText
import com.dong.budget.ui.stats.tab.breakdown.wholeName
import com.dong.budget.ui.theme.BudgetTheme

/** 분류 탭과 결제수단 탭은 같은 틀을 쓴다. 무엇으로 나눠 보는지 */
enum class BreakdownKind {
    /** 분류별. 지출/수입을 바꿔 볼 수 있다. */
    CATEGORY,

    /** 결제수단별. 지출만 본다. */
    PAYMENT,
}

/**
 * 분류 탭과 결제수단 탭. 무엇에(무엇으로) 썼는지 본다.
 *
 * 위에서부터
 * - (분류만) 지출/수입 전환. 달을 바꿔도 보던 쪽을 그대로 둔다. 통계를 나갔다 오면 지출부터 다시 본다.
 * - (분류만) 머리: "{달} 지출" 과 합계, "분류 {k}개". 환불이 더 많은 항목이 있으면 비율에서 뺐다고 알린다.
 * - 도넛과 옆 범례(계열이 둘 이상일 때). 계열이 하나뿐이면 "이번 달 지출은 모두 식비에 썼어요" 한 줄이 대신한다.
 *   결제수단은 머리가 없어서 안내(수입은 뺐다, 환불이 더 많은 항목은 비율에서 뺐다)를 도넛 아래에 둔다.
 * - 순위 목록: 금액이 0 이 아닌 항목 전부. 누르면 그 분류(결제수단)의 상세
 * - (분류의 지출만) 많이 쓴 곳 10곳
 *
 * 결제수단 탭은 지출만 모은다. 수입을 결제수단으로 나눠 봐야 얻는 게 적고 전환 버튼만 늘어난다.
 * 결제수단 탭의 머리(이번 달 지출 합계)는 통계 탭의 쓴 돈과 같아서 두지 않는다. 도넛이 있으면(결제수단이 둘 이상) 가운데에
 * 조각의 합(양수 항목의 합)이 있는데, 환불이 더 많은 결제수단이 있으면 그 합이 쓴 돈보다 커서 도넛 아래 안내가 까닭을 알린다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onOpenDetail 순위 목록의 줄을 누르면 그 분류나 결제수단의 상세
 */
@Composable
fun BreakdownTab(
    state: StatsUiState,
    kind: BreakdownKind,
    contentPadding: PaddingValues,
    onOpenDetail: (StatsDetailKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 탭마다 따로 보관된다(StatsScreen 의 탭별 상태 보관). 결제수단 탭에서는 쓰지 않는다.
    var showIncome by rememberSaveable { mutableStateOf(false) }
    val dimension =
        when {
            kind == BreakdownKind.PAYMENT -> StatsDimension.PAYMENT_METHOD
            showIncome -> StatsDimension.INCOME_CATEGORY
            else -> StatsDimension.EXPENSE_CATEGORY
        }
    val breakdown = state.breakdownOf(dimension)
    val label = monthLabel(state.month, state.today)
    val openEntry = { entry: BreakdownEntry ->
        onOpenDetail(entry.key.detailKey(dimension, state.month))
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        // 섹션 key 를 고정해 두어 달을 넘겨 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않는다
        if (kind == BreakdownKind.CATEGORY) {
            animatedItem(key = HEADER_KEY) {
                Column(
                    modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap),
                    verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionPadding),
                ) {
                    SegmentedToggle(
                        options = MEASURE_OPTIONS,
                        selectedIndex = if (showIncome) INCOME_INDEX else EXPENSE_INDEX,
                        onSelect = { index -> showIncome = index == INCOME_INDEX },
                        modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
                    )
                    if (breakdown.entries.isNotEmpty()) BreakdownHeader(breakdown = breakdown, dimension = dimension, monthLabel = label)
                }
            }
        }

        if (breakdown.entries.isEmpty()) {
            animatedItem(key = EMPTY_KEY) {
                // 이 달 전체가 빈 달이면 설명이 '홈에서 남기면…' / '미리 적어 두면…' 으로 갈린다
                StatsEmpty(title = nothingText(label, dimension), body = emptyMonthText(state.month, state.today).description)
            }
        } else {
            animatedItem(key = CHART_KEY) {
                BreakdownChart(
                    breakdown = breakdown,
                    dimension = dimension,
                    monthLabel = label,
                    // 머리가 없는 결제수단은 달 줄 바로 아래 조금만 띄우고, 머리에 있던 안내를 여기 둔다
                    showHints = kind == BreakdownKind.PAYMENT,
                    modifier =
                    Modifier.padding(
                        top = if (kind == BreakdownKind.PAYMENT) BudgetTheme.spacing.inlineGap else BudgetTheme.spacing.sectionGap,
                    ),
                )
            }
            animatedItem(key = ENTRIES_KEY) {
                Column(modifier = Modifier.padding(top = BudgetTheme.spacing.itemGap)) {
                    // 줄을 분류로 구분해 둔다. 순위가 바뀌어도 비율 막대가 제 분류를 따라 늘고 준다.
                    breakdown.entries.forEach { entry ->
                        key(entry.key) { BreakdownRow(entry = entry, dimension = dimension, onClick = { openEntry(entry) }) }
                    }
                }
            }
            if (dimension == StatsDimension.EXPENSE_CATEGORY && state.merchants.isNotEmpty()) {
                animatedItem(key = MERCHANTS_KEY) {
                    MerchantsSection(merchants = state.merchants)
                }
            }
        }
    }
}

private fun StatsUiState.breakdownOf(dimension: StatsDimension): Breakdown = when (dimension) {
    StatsDimension.EXPENSE_CATEGORY -> expenseByCategory
    StatsDimension.INCOME_CATEGORY -> incomeByCategory
    StatsDimension.PAYMENT_METHOD -> expenseByPayment
}

/**
 * 머리. 라벨, 합계, 항목 수를 한 번에 읽는다.
 * 지출 합계가 음수(환불이 더 많음)면 홈 요약처럼 "+12,000원" 을 income 색으로 적는다.
 */
@Composable
private fun BreakdownHeader(breakdown: Breakdown, dimension: StatsDimension, monthLabel: String) {
    StatsSection {
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(
                text = breakdownLabel(monthLabel, dimension),
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
            Text(
                text = formatDirected(breakdown.total, income = dimension == StatsDimension.INCOME_CATEGORY),
                style = BudgetTheme.amount.summary,
                color = directedColor(breakdown.total, income = dimension == StatsDimension.INCOME_CATEGORY),
            )
            Text(
                text = entryCountText(breakdown.entries.size, dimension),
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
            )
        }
        if (breakdown.negativeCount > 0) HintText(negativeHint(breakdown.negativeCount, dimension))
    }
}

/**
 * 도넛과 범례. 양수 계열이 하나뿐이면 문장 한 줄, 없으면(모두 환불이 더 많음) 그림은 두지 않는다.
 * @param showHints 머리가 없는 결제수단 탭에서 머리 대신 안내(수입은 뺐다, 환불이 더 많은 항목은 비율에서 뺐다)를 아래에 둔다
 */
@Composable
private fun BreakdownChart(
    breakdown: Breakdown,
    dimension: StatsDimension,
    monthLabel: String,
    showHints: Boolean,
    modifier: Modifier = Modifier,
) {
    val series = breakdown.series
    if (series.isEmpty() && !showHints) return
    StatsSection(modifier = modifier) {
        when (series.size) {
            0 -> Unit

            1 ->
                Text(
                    text = singleSeriesSentence(monthLabel, dimension, series.single().name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textPrimary,
                )

            else ->
                BreakdownDonut(
                    breakdown = breakdown,
                    caption = dimension.wholeName(),
                    income = dimension == StatsDimension.INCOME_CATEGORY,
                )
        }
        if (showHints) {
            if (breakdown.negativeCount > 0) HintText(negativeHint(breakdown.negativeCount, dimension))
            HintText(PAYMENT_ONLY_EXPENSE_HINT)
        }
    }
}

private const val EXPENSE_LABEL = "지출"
private const val INCOME_LABEL = "수입"
private val MEASURE_OPTIONS = listOf(EXPENSE_LABEL, INCOME_LABEL)
private const val EXPENSE_INDEX = 0
private const val INCOME_INDEX = 1

private const val PAYMENT_ONLY_EXPENSE_HINT = "수입은 빼고 쓴 돈만 모았어요"

private const val HEADER_KEY = "header"
private const val EMPTY_KEY = "empty"
private const val CHART_KEY = "chart"
private const val ENTRIES_KEY = "entries"
private const val MERCHANTS_KEY = "merchants"
