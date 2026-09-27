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
import com.dong.budget.ui.stats.MerchantStat
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.merchantSubtitle
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 많이 쓴 곳 한 줄. 앞에 순위, 가운데 가게 이름과 "4번 · 한 번에 평균 1만원", 뒤에 금액.
 * 누를 수 없다(가게 상세는 없다). 좌우 여백은 두지 않으니 StatsSection 안에 둔다.
 *
 * 순위 칸은 뱃지(size.badge)와 같은 폭이라, 위 순위 목록의 뱃지와 세로로 줄이 맞는다.
 */
@Composable
fun MerchantRow(rank: Int, merchant: MerchantStat, modifier: Modifier = Modifier) {
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
            Text(
                text = merchantSubtitle(merchant),
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${formatAmount(merchant.amount)}원",
            style = BudgetTheme.amount.medium,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap),
        )
    }
}

/** 많이 쓴 곳 섹션. 분류 탭과 상세가 같이 쓴다. 앞 섹션과는 sectionGap 만큼 띄운다. */
@Composable
internal fun MerchantsSection(merchants: List<MerchantStat>, modifier: Modifier = Modifier) {
    StatsSection(modifier = modifier.padding(top = BudgetTheme.spacing.sectionGap), title = "많이 쓴 곳") {
        Column {
            merchants.forEachIndexed { index, merchant -> MerchantRow(rank = index + 1, merchant = merchant) }
        }
    }
}
