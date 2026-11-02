package com.dong.budget.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.MonthStepper
import com.dong.budget.ui.components.ThisMonthButton
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.StatRow
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.TREND_MONTHS
import com.dong.budget.ui.stats.chart.TrendBar
import com.dong.budget.ui.stats.chart.TrendBars
import com.dong.budget.ui.stats.chart.formatAxisWon
import com.dong.budget.ui.stats.tab.breakdown.BreakdownDonut
import com.dong.budget.ui.stats.tab.breakdown.MerchantsSection
import com.dong.budget.ui.stats.tab.breakdown.RankRow
import com.dong.budget.ui.stats.tab.breakdown.SmallLine
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 탭. 고른 달에 얼마나 모았고 어디에(어떤 상품 · 결제수단으로) 모았는지 본다. 맨 위 달 줄로 달을 고른다.
 *
 * 위에서부터 ① '{달} 모은 돈' 과 지난달 대비, ② 최근 6개월, ③ 올해 모은 돈 · 그 달까지 모은 돈, ④ 상품별(가게 이름), ⑤ 결제수단별.
 * 섹션마다 조건이 맞을 때만 나온다. 이 달에 저축이 없으면 빈 상태 안내 하나만 둔다.
 * 모은 돈은 쓴 돈이 아니라 부호 없이 본문색으로 적고, 꺼낸 돈(저축 분류의 환불)이 더 많을 때만 "-" 가 붙는다.
 * 줄은 누를 수 없다(지출 통계의 분류 · 결제수단 상세는 지출만 모은다).
 *
 * 섹션 item 은 key 를 고정한다. 달을 바꿔도 스크롤 위치는 그대로 두므로(탭마다 보관) 섹션이 숨었다 나타나도 기준이 흔들리지 않게 한다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 */
@Composable
internal fun SavingsStatsTab(
    state: SavingsUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = state.stats
    Column(modifier = modifier.fillMaxSize()) {
        // 저축은 앞날을 보지 않아서 이번 달에서는 '다음 달' 을 막는다(고정지출과 같다)
        MonthStepper(
            month = state.month,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            nextEnabled = !state.isThisMonth,
            trailing = { ThisMonthButton(visible = !state.isThisMonth, onClick = onThisMonth) },
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            if (stats.monthIsEmpty) {
                animatedItem(key = EMPTY_KEY) {
                    StatsEmpty(title = emptyMonthTitle(state.month, state.today), body = EMPTY_MONTH_BODY)
                }
                return@LazyColumn
            }
            animatedItem(key = SUMMARY_KEY) {
                // 달 줄 바로 아래 붙지 않게 조금 띄운다
                SummarySection(
                    stats = stats,
                    month = state.month,
                    today = state.today,
                    modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap),
                )
            }
            animatedItem(key = TREND_KEY) { TrendSection(trend = stats.trend) }
            if (stats.year != null || stats.cumulative != null) {
                animatedItem(key = TOTALS_KEY) { TotalsSection(stats = stats, month = state.month, today = state.today) }
            }
            if (stats.merchants.isNotEmpty()) {
                animatedItem(key = MERCHANTS_KEY) { MerchantsSection(merchants = stats.merchants, title = MERCHANTS_TITLE, savings = true) }
            }
            if (stats.byPayment.entries.isNotEmpty()) {
                animatedItem(key = PAYMENT_KEY) {
                    PaymentSection(breakdown = stats.byPayment, monthLabel = monthLabel(state.month, state.today))
                }
            }
        }
    }
}

/**
 * ① 머리. 카드 없이 바탕 위에 라벨과 큰 숫자, 지난달 대비 문장을 둔다(통계 탭 요약 머리와 같은 모양).
 * 꺼낸 돈이 더 많아 음수면 부제로 까닭을 붙인다.
 */
@Composable
private fun SummarySection(stats: SavingsStats, month: YearMonth, today: LocalDate, modifier: Modifier = Modifier) {
    StatsSection(modifier = modifier) {
        // 라벨과 금액(과 부제)을 한 번에 읽는다: "이번 달 모은 돈, 300,000원"
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(text = savingsTitle(month, today), style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
            Text(text = savedAmount(stats.total), style = BudgetTheme.amount.large, color = BudgetTheme.colors.textPrimary)
            if (stats.total < 0) {
                Text(text = WITHDRAWN_OVER_TEXT, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
            }
        }
        stats.comparison?.let { comparison ->
            Text(text = comparisonSentence(comparison), style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textPrimary)
        }
    }
}

/**
 * ② 최근 6개월. 마지막 칸(고른 달)만 저축 색인 한 계열 막대(통계 상세와 같은 TrendBars)다. 저축을 시작하기 전 달은 달 이름만 둔다.
 * 막대 위 글자는 꺼낸 돈이 더 많은 달이면 "-5만" 이다(지출 막대는 돌려받은 쪽 "+" 로 적는다).
 */
@Composable
private fun TrendSection(trend: List<SavingsMonth>) {
    val bars =
        trend.map {
            TrendBar(
                month = it.month,
                amount = it.amount,
                recorded = !it.beforeFirst,
                description = trendDescription(it),
                valueLabel = formatAxisWon(it.amount),
            )
        }
    StatsSection(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap), title = TREND_TITLE) {
        TrendBars(bars = bars, highlight = BudgetTheme.categoryPalette[SAVINGS_COLOR].content)
    }
}

/** ③ 올해 모은 돈 · 그 달까지 모은 돈. [SavingsStats.year] · [SavingsStats.cumulative] 중 있는 것만 둔다(겹치는 값은 계산이 뺐다). */
@Composable
private fun TotalsSection(stats: SavingsStats, month: YearMonth, today: LocalDate) {
    StatsSection(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap), block = true) {
        // 줄 사이는 띄우지 않는다(줄마다 최소 터치 높이가 있다)
        Column {
            stats.year?.let { year ->
                StatRow(label = yearLabel(year, today), value = savedAmount(year.amount), caption = yearRange(year))
            }
            stats.cumulative?.let { amount -> StatRow(label = cumulativeLabel(month, today), value = savedAmount(amount)) }
        }
    }
}

/**
 * ⑤ 결제수단별. 도넛과 옆 범례(결제수단이 둘 이상일 때), 하나뿐이면 문장 한 줄. 그 아래 결제수단 줄 전부.
 * 꺼낸 돈이 더 많은 결제수단은 비율에서 빠지고 그 까닭을 안내한다. 도넛 가운데는 조각의 합(양수 항목의 합)이다.
 */
@Composable
private fun PaymentSection(breakdown: Breakdown, monthLabel: String) {
    val series = breakdown.series
    Column(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap)) {
        StatsSection(title = PAYMENT_TITLE) {
            when (series.size) {
                0 -> Unit

                1 ->
                    Text(
                        text = singlePaymentSentence(monthLabel, series.single().name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BudgetTheme.colors.textPrimary,
                    )

                else -> BreakdownDonut(breakdown = breakdown, caption = DONUT_CAPTION, income = false, savings = true)
            }
            if (breakdown.negativeCount > 0) HintText(withdrawnHint(breakdown.negativeCount))
        }
        Column(modifier = Modifier.padding(top = BudgetTheme.spacing.itemGap)) {
            // 줄을 결제수단으로 구분해 둔다. 순위가 바뀌어도 비율 막대가 제 결제수단을 따라 늘고 준다.
            breakdown.entries.forEach { entry -> key(entry.key) { PaymentRow(entry = entry) } }
        }
    }
}

/**
 * 결제수단 줄 하나. 지출 통계의 순위 줄과 같은 모양(RankRow)이고, 누를 수 없고 지난달 대비가 없다.
 * ```
 * (뱃지)  토스뱅크                     300,000원
 *         42% · 3건 · 한 번에 평균 10만원
 *         ████████████░░░░                          ← 비율만큼, 결제수단 색, 트랙 없음
 * ```
 * 금액은 부호 없이 본문색이다. 꺼낸 돈이 더 많은 줄은 금액이 "-50,000원" 이고 비율 막대 대신 "꺼낸 돈이 더 많아요" 다.
 * 화면 읽기는 한 줄을 한 문장으로 읽는다([paymentDescription]).
 */
@Composable
private fun PaymentRow(entry: BreakdownEntry) {
    RankRow(
        entry = entry,
        amount = savedAmount(entry.amount),
        amountColor = BudgetTheme.colors.textPrimary,
        description = paymentDescription(entry),
        onClick = null,
    ) {
        SmallLine(paymentSummary(entry))
        paymentNote(entry)?.let { SmallLine(it) }
    }
}

private const val TREND_TITLE = "최근 ${TREND_MONTHS}개월"

private const val EMPTY_KEY = "empty"
private const val SUMMARY_KEY = "summary"
private const val TREND_KEY = "trend"
private const val TOTALS_KEY = "totals"
private const val MERCHANTS_KEY = "merchants"
private const val PAYMENT_KEY = "payment"
