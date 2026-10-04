package com.dong.budget.ui.components

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
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 구분선 없는 리스트 한 줄.
 *
 * 구분선 대신 여백으로 항목을 나눈다.
 * @param note 부제 아래 덧붙임(고정지출의 '평소보다 2일 지났어요' 처럼 줄마다 다른 한두 줄). 부제와 같은 간격을 두고 그 아래에 둔다.
 * @param onClickLabel 누르면 무엇을 하는지 화면 읽기가 알려 줄 말. 없으면 기본 말로 읽는다.
 */
@Composable
fun BudgetListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    note: @Composable (() -> Unit)? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val base =
        modifier
            .fillMaxWidth()
            .let { m ->
                if (onClick != null) {
                    m.pressScaleClickable(
                        shape = RoundedCornerShape(BudgetTheme.radius.chip),
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    m
                }
            }.padding(
                horizontal = BudgetTheme.spacing.screenHorizontal,
                vertical = BudgetTheme.spacing.listItemVertical,
            )

    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = BudgetTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = BudgetTheme.spacing.tightGap),
                )
            }
            if (note != null) {
                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
                note()
            }
        }
        if (trailing != null) {
            Row(
                modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing()
            }
        }
    }
}
