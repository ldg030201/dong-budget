package com.dong.budget.ui.stats.chart

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/**
 * 수입 대비 지출 미터. 높이 chart.meterHeight 의 알약 모양 트랙(chartTrack) 위에 채움(chartExpense)을 [ratio] 만큼 그린다.
 *
 * 1 을 넘으면(수입보다 더 씀) 가득 채우고 danger 로 칠한다. 색에만 기대지 않도록 부르는 쪽이
 * 경고 아이콘과 문장("수입보다 12만원 더 썼어요")을 함께 둔다. 뜻은 그 문장이 전하므로 화면 읽기에서는 뺀다.
 *
 * @param ratio 지출 ÷ 수입. 0 아래는 0 으로 본다. 지출 ÷ 수입을 그대로 넘긴다. 반올림한 % 로 넘기면 수입을 조금 넘게 쓴 달(100.4%)이 100% 로 보여 문장과 어긋난다.
 */
@Composable
fun Meter(ratio: Float, modifier: Modifier = Modifier) {
    val colors = BudgetTheme.colors
    val corner = BudgetTheme.radius.full
    val over = ratio > 1f
    // 처음에는 0 에서 차오르고, 값이 바뀌면 지금 자리에서 늘거나 준다. 수입을 넘기면 색이 번지듯 경고색으로 바뀐다.
    val fill by animateColorAsState(if (over) colors.danger else colors.chartExpense, Motion.standard(), label = "meterFill")
    val fraction by rememberGrowingFraction(ratio.coerceIn(0f, 1f))
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
