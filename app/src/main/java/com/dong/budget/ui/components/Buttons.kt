package com.dong.budget.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable

private const val DISABLED_ALPHA = 0.4f

/** 누를 수 있게 되거나 막힐 때 흐려짐이 튀지 않고 바뀐다 */
@Composable
private fun enabledAlpha(enabled: Boolean): Float {
    val alpha by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, Motion.quick(), label = "enabledAlpha")
    return alpha
}

/** 화면 하단에 고정되는 주 실행 버튼 */
@Composable
fun BudgetPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.ctaHeight)
            .alpha(enabledAlpha(enabled))
            .pressScaleClickable(shape = shape, enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.primary, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 줄 옆에 붙이는 작은 버튼. 화면 폭을 채우지 않고 글자만큼만 차지한다.
 * 보이는 높이는 테마 칩과 같게 두고, 누를 수 있는 범위는 최소 터치 크기를 지킨다.
 * @param container 버튼 바탕. 회색 판 위에 둘 때는 판과 같은 색이라 안 보이므로 떠 있는 색(BudgetColorTokens.raised)을 준다.
 */
@Composable
fun BudgetSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = BudgetTheme.colors.sectionBackground,
    contentColor: Color = BudgetTheme.colors.textPrimary,
) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    Box(
        modifier =
        modifier
            .minimumInteractiveComponentSize()
            .alpha(enabledAlpha(enabled))
            .pressScaleClickable(shape = shape, enabled = enabled, onClick = onClick)
            .background(container, shape)
            .padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.inlineGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

/**
 * 글자만 있는 버튼. 자주 쓰지 않는 보조 동작을 한 줄에 여러 개 늘어놓을 때 쓴다.
 * @param color 기본은 배경 위 브랜드색 글자다. 다크의 primary 는 채움용이라 어두운 바탕 위 글자로는 대비가 모자라다(3.91:1).
 */
@Composable
fun BudgetTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BudgetTheme.colors.brandText,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    Box(
        modifier =
        modifier
            .minimumInteractiveComponentSize()
            .alpha(enabledAlpha(enabled))
            // 글자만 있어 줄어드는 것만으로는 눌린 게 안 보인다
            .pressScaleClickable(shape = shape, enabled = enabled, pressedTint = true, onClick = onClick)
            .padding(horizontal = BudgetTheme.spacing.tightGap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

/**
 * 아이콘 하나짜리 버튼. 최소 터치 크기(48dp)를 지킨다.
 * @param contentDescription 화면 읽기가 읽을 이름. 목록에 같은 버튼이 여럿이면 무엇의 버튼인지까지 적는다.
 * @param enabled false 면 흐리게 그리고 누를 수 없다(예: 첫날에서 '이전 날'). 화면 읽기는 '사용 중지됨' 으로 읽는다.
 */
@Composable
fun BudgetIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = BudgetTheme.colors.textPrimary,
    iconSize: Dp = BudgetTheme.size.icon,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
) {
    Box(
        modifier =
        modifier
            .size(BudgetTheme.size.minTouchTarget)
            .alpha(enabledAlpha(enabled))
            // 아이콘만 있어 줄어드는 것만으로는 눌린 게 안 보인다
            .pressScaleClickable(shape = shape, enabled = enabled, pressedTint = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
