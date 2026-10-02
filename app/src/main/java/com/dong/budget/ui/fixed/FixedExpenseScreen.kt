package com.dong.budget.ui.fixed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.TabHeader

/**
 * 고정지출 탭. 지금은 탭 머리만 있는 자리다.
 * '고정지출' 분류로 적은 지출을 가게별로 묶어 얼마 내는지 · 이번 달 냈는지 · 평소 언제 내는지를 보여 줄 곳이다.
 * 상단 인셋은 이 화면이, 하단은 셸의 아래 메뉴가 처리한다(HomeShell).
 */
@Composable
fun FixedExpenseScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = "고정지출")
    }
}
