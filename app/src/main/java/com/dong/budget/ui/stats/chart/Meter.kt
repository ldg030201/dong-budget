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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/**
 * 수입 대비 지출 미터. 높이 chart.meterHeight 의 알약 모양 트랙(chartTrack) 위에 채움(chartExpense)을 [ratio] 만큼 그린다.
 *
 * 1 을 넘으면(수입보다 더 씀) 가득 채우고 danger 로 칠한다. 색에만 기대지 않도록 부르는 쪽이
 * 경고 아이콘과 문장("수입보다 12만원 더 썼어요")을 함께 둔다. 뜻은 그 문장이 전하므로 화면 읽기에서는 뺀다.
 *
 * 고정지출 요약(낸 것 ÷ 낼 것)과 카드 실적 구간 막대도 같은 막대를 쓴다.
 *
 * @param ratio 지출 ÷ 수입. 0 아래는 0 으로 본다. 지출 ÷ 수입을 그대로 넘긴다. 반올림한 % 로 넘기면 수입을 조금 넘게 쓴 달(100.4%)이 100% 로 보여 문장과 어긋난다.
 * @param fill 채움 색. 넘기면 1 을 넘어도 경고색으로 바꾸지 않는다(구간을 넘기는 게 좋은 일인 카드 실적은 카드 색).
 * @param ticks 막대를 비워 낼 눈금 자리(0~1, 카드 실적의 구간). 막대를 지워서 내므로 회색 판 위든 화면 바탕 위든 그 바탕이 비쳐 보인다.
 */
@Composable
fun Meter(ratio: Float, modifier: Modifier = Modifier, fill: Color? = null, ticks: List<Float> = emptyList()) {
    val colors = BudgetTheme.colors
    val corner = BudgetTheme.radius.full
    val notch = BudgetTheme.chart.gap
    val over = ratio > 1f
    // 처음에는 0 에서 차오르고, 값이 바뀌면 지금 자리에서 늘거나 준다. 수입을 넘기면 색이 번지듯 경고색으로 바뀐다.
    val target = fill ?: if (over) colors.danger else colors.chartExpense
    val color by animateColorAsState(target, Motion.standard(), label = "meterFill")
    val fraction by rememberGrowingFraction(ratio.coerceIn(0f, 1f))
    Spacer(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.chart.meterHeight)
            .clearAndSetSemantics {}
            // 눈금을 지운 자리가 막대 바탕까지 뚫리도록 따로 그린 뒤 합친다. 눈금이 없으면 그럴 필요가 없다.
            .then(if (ticks.isEmpty()) Modifier else Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen })
            .drawBehind {
                val radius = CornerRadius(minOf(corner.toPx(), size.height / 2f))
                drawRoundRect(colors.chartTrack, cornerRadius = radius)
                if (fraction > 0f) drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                val width = notch.toPx()
                ticks.forEach { at ->
                    drawRect(
                        color = colors.chartTrack,
                        topLeft = Offset(size.width * at - width / 2f, 0f),
                        size = Size(width, size.height),
                        blendMode = BlendMode.Clear,
                    )
                }
            },
    )
}
