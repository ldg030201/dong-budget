package com.dong.budget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.NavButtonStyle
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 아직 만들지 않은 서브플로우 자리.
 *
 * 네비게이션 동작(탭바가 통째로 밀려나고 왼쪽 위에 뒤로가기만 남는 것)을
 * 먼저 확인하기 위한 임시 화면이다. 각 기능을 만들면서 교체한다.
 */
@Composable
fun PlaceholderSubflow(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, isModal: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onClose,
                title = title,
                style = if (isModal) NavButtonStyle.CLOSE else NavButtonStyle.BACK,
            )
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(BudgetTheme.spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "아직 만들지 않은 화면이에요",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
        }
    }
}
