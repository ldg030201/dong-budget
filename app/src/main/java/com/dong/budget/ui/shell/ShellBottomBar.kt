package com.dong.budget.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.components.STATS_ENTRY_SHARED_KEY
import com.dong.budget.ui.components.sharedNavElement
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressFeedback
import com.dong.budget.ui.theme.pressScaleClickable
import kotlinx.coroutines.launch

/**
 * 아래 메뉴. 홈이 맨 왼쪽, 전체가 맨 오른쪽에 붙어 있고, 그 사이 칸은 [BottomMenu.shown] 순서로 놓인다.
 *
 * 가운데가 [BottomMenu.VISIBLE_MIDDLE] 칸 이하면 모든 칸이 너비를 똑같이 나눈다.
 * 넘치면 가운데만 옆으로 밀어 바꾼다. 가운데 네 칸이 다 보이고, 그다음 칸이 가장자리에 [PEEK] 칸만큼 걸쳐 보인다.
 * 걸친 칸은 가장자리로 갈수록 흐려져(fadingPeek) 옆에 더 있다는 것을 알린다. 밀다 놓으면 칸 경계에 맞춰 멈춘다.
 * 홈·전체도 가운데 칸과 같은 너비라 모든 칸의 간격이 고르다.
 * Material 의 NavigationBar 는 칸 사이에 틈을 두어 밀리는 칸과 고정 칸의 간격을 고르게 맞출 수 없어서, 같은 높이·색·여백의 줄을 직접 둔다.
 *
 * 통계 칸은 통계 하위 메뉴의 첫 칸과 이어진다([STATS_ENTRY_SHARED_KEY]). 통계를 열면 이 칸의 아이콘과 글자가 위로 옮겨 가고,
 * 닫으면 이 자리로 내려온다. 가운데를 밀어 통계 칸이 다 보이지 않을 때는 잇지 않는다. 잘린 칸이나 화면 밖에 미리 만들어 둔 칸에서
 * 출발하면 엉뚱한 자리에서 날아오기 때문이다. 다 보이는지는 그려 보기 전에도 알 수 있게 스크롤 위치(저장됐다 되살아나는 값)로 정한다.
 * 통계에서 돌아올 때 셸이 다시 그려지는 첫 순간부터 짝이 맞아야 내려오는 연출이 끊기지 않는다.
 *
 * @param selected 고른 탭. 통계는 탭이 아니라 고른 칸이 되지 않는다.
 * @param middleState 가운데 칸의 스크롤. 셸이 들고 있다가 전체에서 메뉴를 고르면 그 칸이 보이게 민다.
 * @param linkStatistics 통계 칸을 통계 하위 메뉴와 이을지. 설정의 미리 보기는 잇지 않는다.
 * @param windowInsets 아래 시스템 막대를 피할 여백. 미리 보기에는 없다.
 */
@Composable
fun ShellBottomBar(
    menu: BottomMenu,
    selected: MenuItem?,
    onClick: (MenuItem) -> Unit,
    middleState: LazyListState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    linkStatistics: Boolean = true,
) {
    val colors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = BudgetTheme.colors.textTertiary,
            unselectedTextColor = BudgetTheme.colors.textTertiary,
            indicatorColor = Color.Transparent,
        )
    Surface(color = containerColor, modifier = modifier) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .defaultMinSize(minHeight = BAR_MIN_HEIGHT)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!menu.scrolls) {
                menu.items.forEach { item ->
                    MenuSlot(item, selected = item == selected, onClick = { onClick(item) }, colors = colors, linked = linkStatistics)
                }
            } else {
                MenuSlot(MenuItem.HOME, selected = selected == MenuItem.HOME, onClick = {
                    onClick(MenuItem.HOME)
                }, colors = colors, linked = false)
                ScrollingMiddle(
                    items = menu.shown,
                    state = middleState,
                    selected = selected,
                    onClick = onClick,
                    colors = colors,
                    linkStatistics = linkStatistics,
                    modifier = Modifier.weight(BottomMenu.VISIBLE_MIDDLE + PEEK),
                )
                MenuSlot(MenuItem.MORE, selected = selected == MenuItem.MORE, onClick = {
                    onClick(MenuItem.MORE)
                }, colors = colors, linked = false)
            }
        }
    }
}

/** 홈과 전체 사이에서 옆으로 밀리는 가운데 칸들. 네 칸이 다 보이고 다음 칸이 걸쳐 보이며, 놓으면 칸 경계에 멈춘다. */
@Composable
private fun ScrollingMiddle(
    items: List<MenuItem>,
    state: LazyListState,
    selected: MenuItem?,
    onClick: (MenuItem) -> Unit,
    colors: NavigationBarItemColors,
    linkStatistics: Boolean,
    modifier: Modifier = Modifier,
) {
    val statsIndex = items.indexOf(MenuItem.STATISTICS)
    // 통계 칸이 다 보이는지. 레이아웃을 기다리지 않고 스크롤 위치만으로 정한다(위 설명).
    val statsShown by remember(state, statsIndex, items.size) {
        derivedStateOf {
            statsIndex >= 0 && isFullyShown(statsIndex, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, items.size)
        }
    }
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Start),
        modifier = modifier.fadingPeek(state),
    ) {
        items(items, key = { it.key }) { item ->
            // 보이는 너비가 네 칸과 걸친 칸이라, 한 칸은 그 너비를 (4 + PEEK) 로 나눈 만큼이다
            Row(Modifier.fillParentMaxWidth(1f / (BottomMenu.VISIBLE_MIDDLE + PEEK))) {
                MenuSlot(
                    item = item,
                    selected = item == selected,
                    onClick = { onClick(item) },
                    colors = colors,
                    linked = linkStatistics && statsShown,
                )
            }
        }
    }
}

/**
 * 가운데 칸 [index] 가 다 보이는지. [first] 는 보이기 시작하는 칸, [offset] 은 그 칸이 왼쪽으로 밀려 잘린 만큼, [count] 는 가운데 칸 수다.
 *
 * 놓으면 두 자리 중 하나에 멈춘다. 칸 경계에 맞은 자리([offset] 0)면 [first] 부터 네 칸이 다 보이고 다음 칸이 오른쪽에 걸친다.
 * 끝까지 민 자리(마지막 칸이 오른쪽 끝에 닿음)면 마지막 네 칸이 다 보이고 그 앞 칸([first])이 왼쪽에 걸친다.
 * 미는 중이면 양 끝 칸이 잘려 있을 수 있어 가운데 세 칸만 다 보이는 것으로 본다.
 */
internal fun isFullyShown(index: Int, first: Int, offset: Int, count: Int): Boolean {
    val visible = BottomMenu.VISIBLE_MIDDLE
    val range =
        when {
            offset == 0 -> first until first + visible
            first + visible == count - 1 -> first + 1..first + visible
            else -> first + 1 until first + visible
        }
    return index in range
}

/**
 * 걸친 칸을 흐리게 한다. 밀어서 더 볼 칸이 있는 쪽 가장자리에서, 걸친 칸 너비만큼 안쪽으로 갈수록 덜 흐려진다.
 * 다 보이는 네 칸은 건드리지 않는다(걸친 칸 너비 = 보이는 너비 × PEEK / (4 + PEEK)). 끝까지 밀면 그쪽은 흐리지 않는다.
 */
private fun Modifier.fadingPeek(state: LazyListState): Modifier = this
    // 그려 둔 칸에 투명도만 덧씌우려면 따로 한 장으로 그려야 한다
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val peek = size.width * PEEK / (BottomMenu.VISIBLE_MIDDLE + PEEK)
        val inner = Color.Black.copy(alpha = PEEK_INNER_ALPHA)
        val edge = Color.Black.copy(alpha = PEEK_EDGE_ALPHA)
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(edge, inner), startX = 0f, endX = peek),
                size = Size(peek, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(inner, edge), startX = size.width - peek, endX = size.width),
                topLeft = Offset(size.width - peek, 0f),
                size = Size(peek, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * 아래 메뉴 한 칸. 통계 칸이면서 [linked] 면 아이콘과 글자를 통계 하위 메뉴의 첫 칸과 잇는다.
 */
@Composable
private fun RowScope.MenuSlot(item: MenuItem, selected: Boolean, onClick: () -> Unit, colors: NavigationBarItemColors, linked: Boolean) {
    val shared = linked && item == MenuItem.STATISTICS
    ShellNavItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                item.icon(),
                contentDescription = null,
                modifier = if (shared) Modifier.sharedNavElement("$STATS_ENTRY_SHARED_KEY-icon") else Modifier,
            )
        },
        label = item.label,
        labelSharedKey = if (shared) "$STATS_ENTRY_SHARED_KEY-label" else null,
        colors = colors,
    )
}

/**
 * 하단 탭 한 칸. 앱은 리플을 꺼 두었으므로(Theme) 누르면 아이콘과 글자가 눌려 들어가게 한다.
 * @param labelSharedKey 글자를 다른 화면의 같은 글자와 잇는 열쇠([sharedNavElement]). 없으면 null
 */
@Composable
private fun RowScope.ShellNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    colors: NavigationBarItemColors,
    labelSharedKey: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = {
            // 칸이 많으면 좁은 폰(320dp, 칸 너비 약 50dp)에서 네 글자('고정지출')가 빠듯하다.
            // 두 줄로 꺾이거나 잘리지 않게 한 줄에 들어갈 때까지 글자를 줄인다(아래 떠 있는 메뉴와 같은 방식).
            val style = MaterialTheme.typography.labelSmall
            Text(
                label,
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
                modifier = if (labelSharedKey != null) Modifier.sharedNavElement(labelSharedKey) else Modifier,
            )
        },
        colors = colors,
        interactionSource = interactionSource,
        modifier =
        Modifier.pressFeedback(
            interactionSource,
            RoundedCornerShape(BudgetTheme.radius.control),
            pressedScale = NAV_PRESSED_SCALE,
        ),
    )
}

/** Material 아래 메뉴의 높이(키 큰 막대)와 같게 둔다 */
private val BAR_MIN_HEIGHT = 80.dp

/**
 * 가운데가 넘칠 때 다음 칸이 가장자리에 걸쳐 보이는 만큼(한 칸에 대한 비율). 아이콘은 칸 가운데에 있어서
 * 이보다 적게 걸치면 아이콘이 거의 안 보인다(0.4칸이면 아이콘 끝만 비쳐 걸친 줄 몰랐다, 에뮬레이터 확인).
 */
private const val PEEK = 0.6f

/** 걸친 칸의 진하기. 안쪽(다 보이는 칸과 맞닿은 쪽)에서 가장자리로 갈수록 옅어진다. 다 보이는 칸과 구분되되 무엇인지는 알아보게 둔다. */
private const val PEEK_INNER_ALPHA = 0.5f
private const val PEEK_EDGE_ALPHA = 0.2f

/** 하단 탭은 바탕 없이 작은 아이콘과 글자뿐이라 버튼보다 더 줄인다 */
private const val NAV_PRESSED_SCALE = 0.9f

/** 좁은 화면에서 탭 글자를 줄이는 하한. 이보다 작으면 읽기 어렵다. */
private val MIN_LABEL_SIZE = 10.sp
