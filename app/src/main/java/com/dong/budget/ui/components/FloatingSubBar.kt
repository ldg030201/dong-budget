package com.dong.budget.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 화면 아래에 떠 있는 둥근 메뉴(알약). 맨 왼쪽에 뒤로, 그 오른쪽에 하위 메뉴 칸들이 선다.
 * 통계처럼 한 서브플로우 안을 몇 갈래로 나눠 볼 때 쓴다. 셸의 아래 메뉴와 달리 화면과 함께 들어오고 나간다.
 *
 * 고른 칸은 옅은 브랜드색 채움, 굵은 글자, 화면 읽기의 '선택됨' 셋으로 알린다. 색만으로 구분하지 않는다.
 * 채움은 칸 사이를 미끄러져 옮겨 가서 어디서 어디로 바뀌었는지 보인다.
 * 칸 글자는 폭이 모자라면(삼성 화면 확대로 폭이 320dp 쯤 되는 경우) 한 줄에 들어갈 때까지 줄인다. 잘려 보이지 않게 한다.
 *
 * 자리는 이 부품이 직접 잡는다. 시스템 내비게이션 줄(제스처 줄이나 버튼 줄) 위로 itemGap 만큼 띄우고,
 * 좌우에 화면 여백을 두고, 넓은 화면에서는 가운데에 최대 폭까지만 선다. 부모는 화면 전체 Box 의 아래 가운데에 두기만 하면 된다.
 * 밑에 깔리는 목록은 [floatingBarClearance] 만큼 아래를 비워야 마지막 줄이 가리지 않는다.
 *
 * 알약 바탕(Surface)은 터치를 받아서, 알약 칸 사이의 빈 곳을 눌러도 밑에 가려진 목록 줄이 눌리지 않는다.
 * 알약 옆 여백은 그대로 밑으로 통과한다.
 *
 * @param tabs 칸 글자. 360dp 폭에서 4개가 상한이다(칸 하나 약 64dp).
 * @param onBack 맨 왼쪽 ← 버튼
 */
@Composable
fun FloatingSubBar(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            // 가로 화면의 옆 버튼 줄은 앱 전체(DongBudgetApp)가 이미 뺐다. 여기서는 아래 인셋만 남는다.
            .navigationBarsPadding()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(bottom = BudgetTheme.spacing.itemGap),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
            Modifier
                // 최대 폭을 먼저 걸어야 한다. fillMaxWidth 를 먼저 걸면 폭이 부모 폭으로 고정돼 최대 폭이 먹지 않는다.
                .widthIn(max = BudgetTheme.size.floatingBarMaxWidth)
                .fillMaxWidth()
                .height(BudgetTheme.size.floatingBarHeight)
                // 떠 있어서 목록 줄과 겹치지만 화면 읽기는 본문을 다 읽은 뒤에 이 메뉴를 읽는다(뒤로 → 칸 순서).
                .semantics { traversalIndex = 1f },
            shape = RoundedCornerShape(BudgetTheme.radius.full),
            color = BudgetTheme.colors.floatingBar,
            // 다크에서는 그림자가 거의 안 보인다. 한 단계 밝은 바탕과 가는 테두리가 대신 떠 보이게 한다.
            shadowElevation = BudgetTheme.elevation.floatingBar,
            border = BorderStroke(BudgetTheme.size.underline, BudgetTheme.colors.divider),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = BudgetTheme.spacing.tightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BudgetIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    onClick = onBack,
                    shape = CircleShape,
                )
                Box(
                    modifier =
                    Modifier
                        .padding(horizontal = BudgetTheme.spacing.tightGap)
                        .width(BudgetTheme.size.underline)
                        .height(BudgetTheme.size.iconSmall)
                        .background(BudgetTheme.colors.divider),
                )
                SubBarTabs(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** 알약 안의 칸들. 칸 폭이 모두 같아서 고른 칸 표시는 '칸 번호 × 칸 폭' 만큼 옮기면 된다. */
@Composable
private fun SubBarTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BudgetTheme.radius.full)
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val cellWidth = maxWidth / tabs.size.coerceAtLeast(1)
        val indicatorOffset =
            animateDpAsState(
                targetValue = cellWidth * selectedIndex,
                animationSpec = spring(dampingRatio = INDICATOR_DAMPING, stiffness = Spring.StiffnessMediumLow),
                label = "subBarIndicator",
            )
        // 위치는 그리기 직전(배치 단계)에 읽는다. 움직이는 동안 칸 글자까지 다시 그리지 않게 한다.
        Box(
            modifier =
            Modifier
                .offset { IntOffset(indicatorOffset.value.roundToPx(), 0) }
                .width(cellWidth)
                .height(BudgetTheme.size.minTouchTarget)
                .background(MaterialTheme.colorScheme.primaryContainer, shape),
        )
        Row(modifier = Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .height(BudgetTheme.size.minTouchTarget)
                        .pressScaleClickable(shape = shape, role = Role.Tab, onClick = { onSelect(index) })
                        // 화면 읽기가 지금 고른 칸을 알려준다(예: '월별, 선택됨, 탭')
                        .semantics { this.selected = selected }
                        .padding(horizontal = BudgetTheme.spacing.tightGap),
                    contentAlignment = Alignment.Center,
                ) {
                    TabLabel(text = label, selected = selected)
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean) {
    val style = MaterialTheme.typography.labelMedium
    BasicText(
        text = text,
        style =
        style.copy(
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else BudgetTheme.colors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_TAB_LABEL_SIZE, maxFontSize = style.fontSize),
    )
}

/**
 * [FloatingSubBar] 밑에 깔리는 목록이 비워 둘 아래 여백.
 * 알약 높이 + 알약을 띄운 간격 + 시스템 내비게이션 줄 + 숨 쉴 여백이다. 목록 끝까지 내리면 마지막 줄이 알약 위에 온다.
 * 내비게이션 줄 높이는 기기와 설정(제스처·버튼)마다 달라서 그때그때 읽는다.
 */
@Composable
fun floatingBarClearance(): Dp {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return BudgetTheme.size.floatingBarHeight + BudgetTheme.spacing.itemGap + navigationBar + BudgetTheme.spacing.sectionPadding
}

/**
 * [FloatingSubBar] 뒤에 까는 그라데이션. 알약 둘레로 스크롤돼 올라오는 글자가 알약과 겹쳐 어지럽지 않게 아래로 갈수록 바탕색으로 덮는다.
 * 그리기만 한다. 터치와 화면 읽기는 그대로 밑의 목록으로 통과한다.
 */
@Composable
fun FloatingSubBarScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(floatingBarClearance())
            // Color.Transparent 는 알파가 0 인 검정이라 섞으면 중간이 회색으로 탁해진다. 바탕색에서 알파만 0 으로 둔다.
            .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
    )
}

/** 고른 칸 표시가 미끄러지다 살짝만 튕기고 멈추게 한다 */
private const val INDICATOR_DAMPING = 0.8f

/** 칸 글자를 줄이는 하한. 이보다 작으면 읽기 어렵다. */
private val MIN_TAB_LABEL_SIZE = 11.sp
