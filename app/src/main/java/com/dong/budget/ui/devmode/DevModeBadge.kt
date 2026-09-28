package com.dong.budget.ui.devmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dong.budget.R
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable
import kotlin.math.roundToInt

/**
 * 개발자 모드가 켜져 있는 동안 모든 화면 위에 떠 있는 벌레 표시. 로그가 쌓이고 있다는 걸 잊지 않게 한다.
 *
 * - 처음에는 위쪽 막대의 가운데보다 조금 오른쪽에 뜬다. 왼쪽의 제목·달 넘김과 오른쪽 끝 버튼 사이라 대부분의 화면에서 비어 있다.
 * - 무언가를 가리면 끌어서 옮길 수 있다. 옮긴 자리는 화면을 돌려도 비슷한 곳에 남는다.
 * - 누르면 개발자 모드 화면이 열린다.
 *
 * 화면 전체를 덮는 틀이지만 누름을 받는 건 표시 하나뿐이라, 나머지 자리의 누름은 아래 화면으로 그대로 간다.
 */
@Composable
fun DevModeBadge(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 자리는 움직일 수 있는 범위 안의 비율로 둔다. 화면을 돌려 크기가 바뀌어도 비슷한 자리에 있다.
    var xFraction by rememberSaveable { mutableFloatStateOf(DEFAULT_X_FRACTION) }
    // 아직 옮기지 않았으면 음수. 그동안은 위쪽 막대 가운데 높이에 둔다.
    var yFraction by rememberSaveable { mutableFloatStateOf(-1f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - BadgeSize).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - BadgeSize).toPx() }.coerceAtLeast(0f)
        val defaultY = with(density) { ((BudgetTheme.size.topBarHeight - BadgeSize) / 2).toPx() }
        fun currentX() = xFraction * maxX
        fun currentY() = if (yFraction < 0f) defaultY else yFraction * maxY

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(Motion.standard()) + fadeIn(Motion.quick()),
            exit = scaleOut(Motion.standard()) + fadeOut(Motion.quick()),
            // 위치는 배치 단계에서 읽는다. 끄는 동안 표시만 옮겨 그리고 다시 구성하지 않는다.
            modifier = Modifier.offset { IntOffset(currentX().roundToInt(), currentY().roundToInt()) },
        ) {
            Box(
                modifier =
                Modifier
                    .size(BadgeSize)
                    .pointerInput(maxX, maxY) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val x = (currentX() + drag.x).coerceIn(0f, maxX)
                            val y = (currentY() + drag.y).coerceIn(0f, maxY)
                            xFraction = if (maxX > 0f) x / maxX else 0f
                            yFraction = if (maxY > 0f) y / maxY else 0f
                        }
                    }.pressScaleClickable(shape = CircleShape, onClick = onClick)
                    // 밝은 화면에서는 짙게, 어두운 화면에서는 밝게. 어느 화면 위에서도 눈에 띈다.
                    .background(BudgetTheme.colors.textPrimary.copy(alpha = BADGE_ALPHA), CircleShape)
                    .semantics { contentDescription = "개발자 모드 켜짐" },
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

/** 처음 자리. 가운데에 두면 홈의 달 넘김 화살표에 붙어서 조금 오른쪽으로 비킨다. */
private const val DEFAULT_X_FRACTION = 0.65f

/** 뒤의 글자가 살짝 비쳐 무엇을 가렸는지 알 수 있을 만큼만 투명하게 */
private const val BADGE_ALPHA = 0.85f
