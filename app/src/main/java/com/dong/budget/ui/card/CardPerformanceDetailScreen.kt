package com.dong.budget.ui.card

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.BudgetTopAppBar

/** 카드 하나의 실적 상세(CardPerformanceDetailKey). 지금은 ← 만 있는 자리다. */
@Composable
fun CardPerformanceDetailScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BudgetTopAppBar(onNavigationClick = onBack, title = "카드실적")
    }
}
