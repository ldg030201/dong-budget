package com.dong.budget.ui.stats.tab.breakdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.calc.changeText
import com.dong.budget.ui.stats.calc.entrySummary
import com.dong.budget.ui.stats.chart.entityColor
import com.dong.budget.ui.stats.netExpenseColor
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 분류·결제수단 순위 줄 하나. 분류 탭, 결제수단 탭, 상세의 교차 비중이 같이 쓴다.
 *
 * ```
 * (뱃지)  식비                         523,000원
 *         42% · 12건
 *         지난달 이맘때보다 3만원 늘었어요            ← 최대 2줄
 *         ████████████░░░░                          ← 비율만큼, 분류 색, 트랙 없음
 * ```
 * 공용 BudgetListItem 은 부제가 한 줄이라 증감 문구가 잘려서 따로 만든다.
 *
 * - 뱃지는 그 분류(결제수단)의 아이콘과 색이다. '분류 없음' 은 more_horiz 회색이다(CategoryBadge 가 이름 없는 것을 그렇게 그린다).
 * - 환불이 더 많은 줄은 금액이 "+12,000원"(income 색)이고 비율 막대가 없다. 색만이 아니라 부호와 둘째 줄 문장이 함께 알린다.
 * - 화면 읽기는 한 줄을 한 문장으로 읽는다([entryDescription]). 누를 수 있으면 '버튼' 이 붙는다.
 *
 * @param dimension 이 줄이 무엇인지(지출 분류 / 수입 분류 / 결제수단). 둘째 줄과 덧붙임, 읽는 문장이 달라진다.
 * @param onClick 누르면 상세. null 이면 누를 수 없다(상세의 교차 비중).
 */
@Composable
fun BreakdownRow(entry: BreakdownEntry, dimension: StatsDimension, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val description = entryDescription(entry, dimension)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onClick)
                } else {
                    Modifier
                },
            )
            // 뱃지·이름·금액·비율·증감을 따로 훑지 않고 한 번에 읽는다
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal, vertical = BudgetTheme.spacing.listItemVertical),
        // 줄 수가 달라도 뱃지가 이름 줄에 맞춰 선다
        verticalAlignment = Alignment.Top,
    ) {
        CategoryBadge(icon = entry.icon, color = entry.color)
        Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BudgetTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatNetExpense(entry.amount),
                    style = BudgetTheme.amount.medium,
                    color = netExpenseColor(entry.amount),
                    modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap),
                )
            }
            SmallLine(entrySummary(entry, dimension))
            entry.change?.let { SmallLine(changeText(it), maxLines = CHANGE_MAX_LINES) }
            groupNote(entry.key, dimension)?.let { SmallLine(it) }
            entry.share?.let { share -> ShareBar(share = share, color = entityColor(entry.color)) }
        }
    }
}

@Composable
private fun SmallLine(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BudgetTheme.colors.textSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 비율 막대. 폭이 곧 비율이고 트랙은 깔지 않는다. 값은 둘째 줄 글자가 전한다. */
@Composable
private fun ShareBar(share: Double, color: Color) {
    Spacer(
        Modifier
            .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
            .height(BudgetTheme.chart.shareBarHeight)
            .background(color, RoundedCornerShape(BudgetTheme.radius.full)),
    )
}

/** 증감 문구는 길어도 두 줄까지. 더 길면 말줄임한다(화면 읽기는 다 읽는다). */
private const val CHANGE_MAX_LINES = 2
