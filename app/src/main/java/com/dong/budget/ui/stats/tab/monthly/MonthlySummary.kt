package com.dong.budget.ui.stats.tab.monthly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.format.formatSignedTotal
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.comparisonText
import com.dong.budget.ui.home.totalColor
import com.dong.budget.ui.stats.MonthlyStats
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.StatRow
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.YearToDate
import com.dong.budget.ui.stats.calc.SpendRatioSentence
import com.dong.budget.ui.stats.calc.futureHint
import com.dong.budget.ui.stats.calc.spendRatioSentence
import com.dong.budget.ui.stats.calc.summaryLabel
import com.dong.budget.ui.stats.calc.yearToDateRange
import com.dong.budget.ui.stats.calc.yearToDateStartHint
import com.dong.budget.ui.stats.calc.yearToDateTitle
import com.dong.budget.ui.stats.chart.Meter
import com.dong.budget.ui.stats.netExpenseColor
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 통계·월별 탭의 숫자 섹션: 요약 머리와 수입과 지출(통계 탭), 올해 모아 보기(월별 탭).
// 값 글자는 모두 본문색이고 방향은 부호로 전한다. 요약 머리의 음수 지출만 홈 요약처럼 초록 + 로 적는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * ① 요약 머리. 카드 없이 바탕 위에 라벨과 큰 숫자, 홈과 같은 지난달 비교 문장을 둔다.
 *
 * 쓴 돈은 부호 없이 본문색으로 적는다. 라벨이 방향을 말하므로 큰 빨간 숫자로 겁주지 않는다.
 * 환불이 더 많아 음수면 돌아온 돈이라 홈처럼 초록 + 로 적고, 부제로 까닭을 붙인다.
 * 이번 달에 오늘 뒤 날짜로 미리 적은 거래가 합계에 들어 있으면 흐린 안내로 알린다.
 */
@Composable
internal fun SummaryHead(monthly: MonthlyStats, month: YearMonth, today: LocalDate, modifier: Modifier = Modifier) {
    val expense = monthly.totals.expense
    StatsSection(modifier = modifier) {
        // 라벨과 금액(과 부제)을 한 번에 읽는다: "이번 달 쓴 돈, 1,234,560원"
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(
                text = summaryLabel(month, today),
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
            Text(
                text = formatNetExpense(expense),
                style = BudgetTheme.amount.large,
                color = netExpenseColor(expense),
            )
            if (expense < 0) {
                Text(
                    text = REFUND_OVER_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
        }
        // 홈 요약과 같은 문장이다. 금액만 색을 입히고, 더/덜은 글자가 말한다.
        monthly.comparison?.let { comparison ->
            Text(
                text = comparisonText(comparison),
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textPrimary,
            )
        }
        // 이번 달이 아니면 0 이다
        if (monthly.futureCount > 0) HintText(text = futureHint(monthly.futureCount))
    }
}

/**
 * ② 수입과 지출. 수입 대비 지출 문장, 미터, 수입·지출·남은 돈 세 줄.
 * 수입이 없으면 견줄 기준이 없어 미터를 두지 않는다.
 */
@Composable
internal fun IncomeExpenseSection(totals: Totals, period: Period, modifier: Modifier = Modifier) {
    StatsSection(modifier = modifier, title = "수입과 지출", block = true) {
        RatioSentence(sentence = spendRatioSentence(totals, period))
        if (totals.income > 0) {
            // 반올림한 % 가 아니라 실제 비로 채운다. 100.4% 가 100% 로 반올림되면
            // 문장은 '더 썼어요' 인데 미터는 넘치지 않은 색으로 남는다.
            Meter(ratio = totals.expense.toFloat() / totals.income.toFloat())
        }
        TotalsRows(totals = totals)
    }
}

/**
 * ⑦ 올해 모아 보기. 1월(올해 기록을 시작했으면 그 달)부터 고른 달까지의 합.
 * 한 달 평균 지출은 다 끝났고 1일부터 기록한 달이 있을 때만 둔다.
 */
@Composable
internal fun YearToDateSection(ytd: YearToDate, modifier: Modifier = Modifier) {
    StatsSection(modifier = modifier, title = yearToDateTitle(ytd), subtitle = yearToDateRange(ytd), block = true) {
        yearToDateStartHint(ytd)?.let { HintText(text = it) }
        TotalsRows(totals = ytd.totals) {
            ytd.monthlyAverageExpense?.let {
                StatRow(label = "한 달 평균 지출", value = formatNetExpense(it), valueColor = netExpenseColor(it))
            }
        }
        yearRatioSentence(ytd.totals)?.let { RatioSentence(sentence = it) }
    }
}

/**
 * 수입·지출·남은 돈 세 줄. 줄 사이는 띄우지 않는다(줄마다 최소 터치 높이가 있다).
 * @param extra 세 줄 아래 같은 묶음에 더 붙일 줄
 */
@Composable
private fun TotalsRows(totals: Totals, extra: @Composable () -> Unit = {}) {
    Column {
        StatRow(label = "수입", value = formatSignedTotal(totals.income), valueColor = totalColor(totals.income))
        StatRow(label = "지출", value = formatNetExpense(totals.expense), valueColor = netExpenseColor(totals.expense))
        val left = totals.income - totals.expense
        StatRow(label = "남은 돈", value = formatSignedTotal(left), valueColor = totalColor(left))
        extra()
    }
}

/**
 * 수입 대비 지출 문장과 보조 문장. 한 번에 읽는다.
 * 수입보다 더 썼으면 경고 아이콘과 danger 글자색으로 적는다. 미터 색만으로 알리지 않고 아이콘과 문장이 함께 전한다.
 */
@Composable
private fun RatioSentence(sentence: SpendRatioSentence) {
    val color = if (sentence.overspent) BudgetTheme.colors.danger else BudgetTheme.colors.textPrimary
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sentence.overspent) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    // 꾸밈이다. 뜻은 옆 문장('수입보다 … 더 썼어요')이 전한다.
                    contentDescription = null,
                    tint = BudgetTheme.colors.danger,
                    modifier = Modifier.size(BudgetTheme.size.iconSmall),
                )
                Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
            }
            Text(text = sentence.text, style = MaterialTheme.typography.titleSmall, color = color)
        }
        sentence.detail?.let { HintText(text = it) }
    }
}
