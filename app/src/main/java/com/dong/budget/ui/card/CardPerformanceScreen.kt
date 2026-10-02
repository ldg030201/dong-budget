package com.dong.budget.ui.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.TabHeader

/**
 * 카드실적 탭. 지금은 탭 머리만 있는 자리다.
 * 카드마다 실적 구간을 이번 기간에 얼마나 채웠는지, 얼마 더 쓰면 되는지를 보여 줄 곳이다.
 * 상단 인셋은 이 화면이, 하단은 셸의 아래 메뉴가 처리한다(HomeShell).
 */
@Composable
fun CardPerformanceScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = "카드실적")
    }
}
