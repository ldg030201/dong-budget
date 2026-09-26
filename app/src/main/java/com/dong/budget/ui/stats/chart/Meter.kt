package com.dong.budget.ui.stats.chart

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 수입 대비 지출 미터. 높이 chart.meterHeight 의 알약 모양 트랙(chartTrack) 위에 채움(chartExpense)을 [ratio] 만큼 그린다.
 *
 * 1 을 넘으면(수입보다 더 씀) 가득 채우고 danger 로 칠한다. 색에만 기대지 않도록 부르는 쪽이
 * 경고 아이콘과 문장("수입보다 12만원 더 썼어요")을 함께 둔다. 뜻은 그 문장이 전하므로 화면 읽기에서는 뺀다.
 *
 * @param ratio 지출 ÷ 수입. 0 아래는 0 으로 본다. `spendRatioPercent / 100f` 를 넘기면 된다.
 */
@Composable
fun Meter(ratio: Float, modifier: Modifier = Modifier) {
    val colors = BudgetTheme.colors
    val corner = BudgetTheme.radius.full
    val over = ratio > 1f
    val fill = if (over) colors.danger else colors.chartExpense
    val fraction = ratio.coerceIn(0f, 1f)
    Spacer(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.chart.meterHeight)
            .clearAndSetSemantics {}
            .drawBehind {
                val radius = CornerRadius(minOf(corner.toPx(), size.height / 2f))
                drawRoundRect(colors.chartTrack, cornerRadius = radius)
                if (fraction > 0f) drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            },
    )
}
