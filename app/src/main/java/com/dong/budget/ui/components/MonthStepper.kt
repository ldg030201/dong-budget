package com.dong.budget.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.format.formatMonth
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.slideByDirection
import java.time.YearMonth

/**
 * 화면 맨 위의 달 고르기. ‹ 2026년 9월 › 와 오른쪽 끝 자리([trailing]).
 * 홈은 오른쪽에 종을, 통계·고정지출은 '이번 달' 버튼([ThisMonthButton])을 둔다.
 * @param nextEnabled false 면 › 를 흐리게 막는다(앞날을 보지 않는 고정지출의 이번 달). 화면 읽기는 '사용 중지됨' 으로 읽는다.
 */
@Composable
fun MonthStepper(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BudgetIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "이전 달",
            onClick = onPreviousMonth,
        )
        // 달을 넘기면 제목이 넘긴 방향으로 밀려 바뀐다. '9월' → '10월' 처럼 폭이 달라져도
        // 오른쪽 화살표가 한 번에 튀지 않고 따라 움직인다(SizeTransform).
        AnimatedContent(
            targetState = month,
            transitionSpec = { slideByDirection().using(SizeTransform(clip = false)) },
            label = "monthTitle",
        ) { shown ->
            Text(
                text = formatMonth(shown),
                style = MaterialTheme.typography.titleLarge,
                color = BudgetTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = BudgetTheme.spacing.tightGap).semantics { heading() },
            )
        }
        BudgetIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "다음 달",
            onClick = onNextMonth,
            enabled = nextEnabled,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * 지난 달(기간)을 보고 있을 때 달 줄 오른쪽 끝에 나타나는 '이번 달' 버튼. 살짝 커지며 나타나고 줄며 사라진다.
 * 통계·고정지출·카드 실적 상세가 함께 쓴다.
 * @param text 버튼 글. 카드 실적처럼 시작일이 1일이 아닌 기간이면 '이번 기간'.
 */
@Composable
fun ThisMonthButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, text: String = "이번 달") {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(Motion.quick()) + scaleIn(Motion.standard(), initialScale = HIDDEN_BUTTON_SCALE),
        exit = fadeOut(Motion.quick()) + scaleOut(Motion.standard(), targetScale = HIDDEN_BUTTON_SCALE),
    ) {
        BudgetTextButton(text = text, onClick = onClick)
    }
}

/** '이번 달' 버튼이 나타나고 사라질 때 이 크기에서 커지고 여기까지 줄어든다 */
private const val HIDDEN_BUTTON_SCALE = 0.8f
