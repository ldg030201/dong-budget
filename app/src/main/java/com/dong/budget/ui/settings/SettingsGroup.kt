package com.dong.budget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.SectionLabel
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 설정의 한 묶음. 작은 제목 아래 회색 둥근 판(홈 요약과 같은 모양)에 줄들을 담는다.
 * 스위치가 길게 늘어선 화면에서도 어디까지가 한 묶음인지 판의 가장자리로 보인다.
 * 묶음 사이는 조금 띄운다. 제목이 없는 묶음(고급 설정 줄 하나)도 같은 간격을 둔다.
 *
 * @param title 판 위의 작은 제목. 없으면 판만 그린다.
 */
@Composable
internal fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionLabel(title) else Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(BudgetTheme.colors.sectionBackground, RoundedCornerShape(BudgetTheme.radius.block))
                .padding(horizontal = BudgetTheme.spacing.sectionPadding, vertical = BudgetTheme.spacing.inlineGap),
            content = content,
        )
    }
}
