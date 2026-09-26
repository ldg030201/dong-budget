package com.dong.budget.ui.stats.tab.monthly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.R
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.stats.Insight
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.insightSentence
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.YearMonth

/**
 * ③ 눈에 띄는 점. 조건에 맞은 것만 최대 3줄. 줄마다 누르면 더 자세히 본다.
 * 분류 이야기(1위 분류 비율, 크게 달라진 분류)는 그 분류 상세로, 요일·돈 안 쓴 날은 일별 탭으로 간다.
 *
 * @param onShowDaily 요일·돈 안 쓴 날 줄을 눌렀을 때
 */
@Composable
internal fun InsightsSection(
    insights: List<Insight>,
    month: YearMonth,
    onOpenDetail: (StatsDetailKey) -> Unit,
    onShowDaily: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsSection(modifier = modifier, title = "눈에 띄는 점") {
        // 줄마다 최소 터치 높이가 있어 줄 사이는 따로 띄우지 않는다
        Column {
            insights.forEach { insight ->
                InsightRow(
                    insight = insight,
                    onClick = {
                        val key = insight.detailKey(month)
                        if (key != null) onOpenDetail(key) else onShowDaily()
                    },
                )
            }
        }
    }
}

/** 뱃지 + 문장 + 꺾쇠. 누를 수 있는 줄이라 한 번에 '버튼' 으로 읽힌다. */
@Composable
private fun InsightRow(insight: Insight, onClick: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onClick)
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsightBadge(insight = insight)
        Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
        Text(
            text = insightSentence(insight),
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // 누를 수 있다는 표시일 뿐이다. 화면 읽기는 '버튼' 으로 알려준다.
            contentDescription = null,
            tint = BudgetTheme.colors.textSecondary,
            modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap).size(BudgetTheme.size.iconSmall),
        )
    }
}

/** 분류 이야기는 그 분류의 뱃지('분류 없음' 은 회색 …), 요일·돈 안 쓴 날은 회색 막대 그림 뱃지 */
@Composable
private fun InsightBadge(insight: Insight) {
    val size = BudgetTheme.size.badgeSmall
    when (insight) {
        is Insight.TopShare -> CategoryBadge(icon = insight.entry.icon, color = insight.entry.color, size = size)

        is Insight.CategoryChange -> CategoryBadge(icon = insight.entry.icon, color = insight.entry.color, size = size)

        is Insight.WeekPattern, is Insight.NoSpendDays ->
            IconBadge(
                iconRes = R.drawable.ic_sym_bar_chart,
                swatch = BudgetTheme.categoryPalette[CategoryStyle.FALLBACK_COLOR],
                size = size,
            )
    }
}
