package com.dong.budget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 아래 메뉴 탭(월급·고정지출·카드 실적)의 빈 화면. 큰 뱃지 아래에 제목과 설명을 가운데에 두고, [action] 이 있으면 그 아래에 둔다.
 * 가로 화면처럼 높이가 모자라면 버튼이 찌그러지지 않게 스크롤한다. 높이가 넉넉하면 가운데에 둔다.
 * @param color 뱃지 색. 분류 색 팔레트의 이름("teal" 등).
 */
@Composable
fun TabEmptyState(
    @DrawableRes iconRes: Int,
    color: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BudgetTheme.spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        IconBadge(iconRes = iconRes, swatch = BudgetTheme.categoryPalette[color], size = BudgetTheme.size.badgeLarge)
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        // 큰 글꼴이나 좁은 화면에서 두 줄로 꺾여도 설명과 같이 가운데에 선다
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            action()
        }
        Spacer(Modifier.weight(1f))
    }
}
