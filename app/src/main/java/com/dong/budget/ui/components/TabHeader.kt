package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 탭 화면을 아래 메뉴 밖에서 따로 열었을 때(아래 메뉴에 넣지 않은 메뉴를 전체에서 열 때) 머리의 뒤로 가기.
 * 탭으로 열린 동안은 null 이다. 탭 화면마다 뒤로 가기를 넘기지 않고 감싸는 틀(MenuPage)이 채운다.
 */
val LocalTabBack: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/**
 * 아래 메뉴 탭 화면의 머리(홈을 뺀 탭: 월급·고정지출·카드실적·내역·전체). 왼쪽에 큰 제목, 오른쪽에 버튼 하나(설정 톱니 등)를 둔다.
 * 따로 열린 화면이면([LocalTabBack]) 제목 왼쪽에 뒤로 가기를 둔다.
 * 상단 인셋은 부르는 쪽이 처리한다(탭 화면의 statusBarsPadding).
 * @param action 오른쪽 버튼. 없어도 머리 높이는 같게 둔다.
 */
@Composable
fun TabHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val onBack = LocalTabBack.current
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(
                // 뒤로 가기 버튼은 제 안에 여백이 있어 상단 바의 뒤로 가기와 같은 자리에 오게 한다
                start = if (onBack != null) BudgetTheme.spacing.inlineGap else BudgetTheme.spacing.screenHorizontal,
                end = BudgetTheme.spacing.inlineGap,
                top = BudgetTheme.spacing.inlineGap,
                bottom = BudgetTheme.spacing.inlineGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BudgetIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", onClick = onBack)
            Spacer(Modifier.width(BudgetTheme.spacing.tightGap))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) action() else Spacer(Modifier.size(BudgetTheme.size.minTouchTarget))
    }
}
