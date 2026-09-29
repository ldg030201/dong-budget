package com.dong.budget.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/**
 * 화면 아래의 입력판 자리(키패드·아이콘 표·달력·시계). 등록창과 월급 설정이 같이 쓴다.
 * 닫혀 있다가 열리면 아래에서 올라오고, 닫으면 아래로 내려간다. 다른 입력판으로 바꾸면 제자리에서 겹쳐 바뀐다.
 * 입력판마다 따로 그려져서(targetState 가 다르면 content 도 다르다) 앞 입력판의 상태가 다음 입력판에 남지 않는다.
 *
 * @param panel 열린 입력판. null 이면 닫혀 있다.
 */
@Composable
fun <T : Any> AnimatedInputPanel(panel: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = panel,
        modifier = modifier,
        transitionSpec = {
            when {
                initialState == null ->
                    slideInVertically(Motion.standard()) { it / PANEL_RISE_DIVISOR } + fadeIn(Motion.standard()) togetherWith
                        fadeOut(Motion.quick())

                targetState == null ->
                    fadeIn(Motion.quick()) togetherWith
                        slideOutVertically(Motion.standard()) { it / PANEL_RISE_DIVISOR } + fadeOut(Motion.quick())

                else -> fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick())
            }.using(SizeTransform(clip = true) { _, _ -> Motion.standard() })
        },
        label = "inputPanel",
    ) { current ->
        if (current == null) Box(Modifier.fillMaxWidth()) else content(current)
    }
}

/**
 * 키패드와 아이콘 표 입력판의 높이를 맞춘다.
 * 둘 사이를 오갈 때 아래 버튼이 위아래로 출렁이지 않게 하기 위함이다.
 * 달력과 시계는 이 높이에 들어가지 않아서 제 크기대로 둔다.
 */
@Composable
fun InputPanelBox(content: @Composable () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.inputPanelHeight)
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(bottom = BudgetTheme.spacing.ctaTopGap),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}

/** 입력판이 열리고 닫힐 때 움직이는 거리. 입력판 높이의 1/4 */
private const val PANEL_RISE_DIVISOR = 4
