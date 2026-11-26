package com.dong.budget.ui.stats.tab.breakdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.stats.MerchantStat
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.Measure
import com.dong.budget.ui.stats.calc.merchantSubtitle
import com.dong.budget.ui.stats.netExpenseColor
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 많이 쓴 곳 한 줄. 앞에 순위, 가운데 가게 이름과 "4번 · 한 번에 평균 1만원", 뒤에 금액.
 * 누를 수 없다(가게 상세는 없다). 좌우 여백은 두지 않으니 StatsSection 안에 둔다.
 *
 * 순위 칸은 뱃지(size.badge)와 같은 폭이라, 위 순위 목록의 뱃지와 세로로 줄이 맞는다.
 *
 * @param measure 무엇을 잰 줄인지. 많이 쓴 곳은 지출, 저축 통계의 상품별 줄은 저축([Measure.SAVINGS])이다.
 *   모은 돈은 쓴 돈이 아니라 거래 줄처럼 부호 없이 본문색으로 적는다.
 */
@Composable
fun MerchantRow(rank: Int, merchant: MerchantStat, modifier: Modifier = Modifier, measure: Measure = Measure.EXPENSE) {
    val description = merchantDescription(rank, merchant)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = BudgetTheme.spacing.inlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge,
            color = BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(BudgetTheme.size.badge),
        )
        Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
            Text(
                text = merchant.name,
                style = MaterialTheme.typography.bodyLarge,
                color = BudgetTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SmallLine(merchantSubtitle(merchant), maxLines = 1)
        }
        Text(
            text = if (measure == Measure.SAVINGS) "${formatAmount(merchant.amount)}원" else formatNetExpense(merchant.amount),
            style = BudgetTheme.amount.medium,
            color = if (measure == Measure.SAVINGS) BudgetTheme.colors.textPrimary else netExpenseColor(merchant.amount),
            modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap),
        )
    }
}

/**
 * 많이 쓴 곳 섹션. 분류 탭과 상세가 같이 쓴다. 앞 섹션과는 sectionGap 만큼 띄운다.
 * 저축 통계는 제목을 '상품별' 로 바꾸고 [measure] 를 [Measure.SAVINGS] 로 넘겨 상품마다 모은 돈으로 쓴다.
 */
@Composable
internal fun MerchantsSection(
    merchants: List<MerchantStat>,
    modifier: Modifier = Modifier,
    title: String = "많이 쓴 곳",
    measure: Measure = Measure.EXPENSE,
) {
    StatsSection(modifier = modifier.padding(top = BudgetTheme.spacing.sectionGap), title = title) {
        Column {
            merchants.forEachIndexed { index, merchant -> MerchantRow(rank = index + 1, merchant = merchant, measure = measure) }
        }
    }
}
