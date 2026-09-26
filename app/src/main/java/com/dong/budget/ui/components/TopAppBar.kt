package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/** 상단바에 놓을 버튼의 모양 */
enum class NavButtonStyle {
    /** 서브플로우에서 한 단계 뒤로 */
    BACK,

    /** 모달을 닫음 */
    CLOSE,
}

/**
 * 서브플로우용 상단바.
 *
 * 왼쪽에 뒤로가기 하나만 두고 제목은 비워두는 것이 기본이다.
 * 화면 본문이 제목을 크게 들고 있는 편이 이 앱의 레이아웃과 맞는다.
 */
@Composable
fun BudgetTopAppBar(
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    style: NavButtonStyle = NavButtonStyle.BACK,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(BudgetTheme.size.topBarHeight)
            .padding(horizontal = BudgetTheme.spacing.inlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BudgetIconButton(
            icon =
            when (style) {
                NavButtonStyle.BACK -> Icons.AutoMirrored.Filled.ArrowBack
                NavButtonStyle.CLOSE -> Icons.Filled.Close
            },
            contentDescription = if (style == NavButtonStyle.BACK) "뒤로" else "닫기",
            onClick = onNavigationClick,
        )
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
                modifier = Modifier.padding(start = BudgetTheme.spacing.tightGap),
            )
        }
        Box(modifier = Modifier.weight(1f))
        actions()
    }
}
