package com.dong.budget.ui.card

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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dong.budget.ui.stats.chart.rememberGrowingFraction
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 실적 구간 막대. 바탕(chartTrack) 위에 쓴 돈만큼 카드 색으로 채우고, 구간 자리마다 눈금을 비워 둔다.
 * 맨 끝이 가장 높은 구간이라 다 채우면 가득 찬다. 구간을 넘기는 건 좋은 일이라 통계의 Meter 처럼 경고색으로 바뀌지 않는다.
 * 처음에는 0 에서 차오르고, 값이 바뀌면 지금 길이에서 늘거나 준다. 뜻은 옆 문장이 전하므로 화면 읽기에서 뺀다.
 *
 * @param color 카드 색 이름(결제수단의 color)
 */
@Composable
internal fun TierBar(progress: TierProgress, color: String, modifier: Modifier = Modifier) {
    val track = BudgetTheme.colors.chartTrack
    val fill = BudgetTheme.categoryPalette[color].content
    val corner = BudgetTheme.radius.full
    val notch = BudgetTheme.chart.gap
    val ticks = progress.ticks
    val shown by rememberGrowingFraction(progress.fraction)
    Spacer(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.chart.meterHeight)
            .clearAndSetSemantics {}
            // 눈금은 막대를 지워서 낸다. 회색 판 위든 화면 바탕 위든 그 바탕이 비쳐 보인다.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val radius = CornerRadius(minOf(corner.toPx(), size.height / 2f))
                drawRoundRect(track, cornerRadius = radius)
                if (shown > 0f) drawRoundRect(fill, size = Size(size.width * shown, size.height), cornerRadius = radius)
                val width = notch.toPx()
                ticks.forEach { at ->
                    drawRect(
                        color = track,
                        topLeft = Offset(size.width * at - width / 2f, 0f),
                        size = Size(width, size.height),
                        blendMode = BlendMode.Clear,
                    )
                }
            },
    )
}
