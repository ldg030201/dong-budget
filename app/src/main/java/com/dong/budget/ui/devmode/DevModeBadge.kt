package com.dong.budget.ui.devmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.dong.budget.R
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/**
 * 개발자 모드가 켜져 있는 동안 모든 화면 오른쪽 아래에 떠 있는 벌레 표시. 로그가 쌓이고 있다는 걸 잊지 않게 한다.
 *
 * 보이기만 하는 층이다. 누름을 받지 않아서 표시 밑의 버튼도 그대로 눌린다. 반투명이라 가린 것도 비쳐 보인다.
 * 화면 읽기는 읽지 않는다. 켜져 있는지는 개발자 모드 화면이 알려준다.
 */
@Composable
fun DevModeBadge(visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(Motion.standard()) + fadeIn(Motion.quick()),
            exit = scaleOut(Motion.standard()) + fadeOut(Motion.quick()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(BudgetTheme.spacing.inlineGap),
        ) {
            Box(
                modifier =
                Modifier
                    .size(BadgeSize)
                    .alpha(BADGE_ALPHA)
                    .clearAndSetSemantics {}
                    // 밝은 화면에서는 짙게, 어두운 화면에서는 밝게. 어느 화면 위에서도 눈에 띈다.
                    .background(BudgetTheme.colors.textPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_bug_report),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(BudgetTheme.size.iconSmall),
                )
            }
        }
    }
}

private val BadgeSize = 32.dp

/** 뒤의 화면이 비쳐 무엇을 가렸는지 보이도록 반만 보이게 */
private const val BADGE_ALPHA = 0.5f
