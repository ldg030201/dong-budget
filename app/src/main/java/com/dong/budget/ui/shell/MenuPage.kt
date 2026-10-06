package com.dong.budget.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.LocalTabBack

/**
 * 아래 메뉴에 넣지 않은 메뉴를 셸 위에 따로 열 때의 틀. 탭 화면을 그대로 그리고, 머리(TabHeader) 왼쪽에 뒤로 가기를 둔다.
 * 탭일 때는 아래 메뉴가 아래 시스템 막대를 피해 주지만, 여기에는 아래 메뉴가 없어 틀이 피한다.
 */
@Composable
fun MenuPage(onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalTabBack provides onBack) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) { content() }
        }
    }
}
