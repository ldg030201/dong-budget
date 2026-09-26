package com.dong.budget.ui.stats.tab.breakdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.ui.format.formatCompactWon
import com.dong.budget.ui.format.formatShare
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.chart.Donut
import com.dong.budget.ui.stats.chart.DonutCenterLabel
import com.dong.budget.ui.stats.chart.LegendValueRow
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 비율 도넛과 옆 범례. 계열(상위 5~6개 + '그 외')이 둘 이상일 때만 둔다. 하나뿐이면 문장 한 줄이 대신한다.
 *
 * - 도넛은 정적이고 화면 읽기에서 빠진다. 범례 줄("식비 42퍼센트")과 아래 순위 목록이 값을 전한다.
 * - 가운데 합계는 조각의 합(양수 항목의 합)이다. 비율의 분모와 같아서 조각과 % 가 서로 맞는다.
 *   환불이 더 많은 항목이 있으면 머리의 합계보다 크고, 머리 아래 안내가 그 까닭을 알린다.
 *
 * @param caption 가운데 위 작은 글자. "지출" / "수입"
 */
@Composable
fun BreakdownDonut(breakdown: Breakdown, caption: String, modifier: Modifier = Modifier) {
    val series = breakdown.series
    val colors = series.map { entityColor(it.color) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Donut(values = series.map { it.amount }, colors = colors) {
            DonutCenterLabel(caption = caption, value = formatCompactWon(breakdown.positiveTotal))
        }
        Spacer(Modifier.width(BudgetTheme.spacing.sectionPadding))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap)) {
            series.forEachIndexed { index, item ->
                LegendValueRow(
                    color = colors[index],
                    label = item.name,
                    value = formatShare(item.share),
                    contentDescription = legendDescription(item),
                )
            }
        }
    }
}
