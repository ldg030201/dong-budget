package com.dong.budget.ui.shell

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.components.STATS_ENTRY_SHARED_KEY
import com.dong.budget.ui.components.sharedNavElement
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressFeedback

/**
 * 아래 메뉴. 홈이 맨 왼쪽, 전체가 맨 오른쪽에 붙어 있고, 그 사이 칸은 [BottomMenu.shown] 순서로 놓인다.
 *
 * 가운데가 [BottomMenu.VISIBLE_MIDDLE] 칸 이하면 모든 칸이 너비를 똑같이 나눈다.
 * 넘치면 홈·전체와 가운데 네 칸이 같은 너비(여섯 칸 기준)를 쓰고, 가운데만 옆으로 밀어 바꾼다. 밀다 놓으면 칸 경계에 맞춰 멈춰서
 * 반쯤 잘린 칸이 남지 않는다. 밀어서 더 볼 칸이 있는 쪽 가장자리는 흐리게 해 더 있다는 것을 알린다.
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
                    modifier = Modifier.weight(BottomMenu.VISIBLE_MIDDLE.toFloat()),
                )
                MenuSlot(MenuItem.MORE, selected = selected == MenuItem.MORE, onClick = {
                    onClick(MenuItem.MORE)
                }, colors = colors, linked = false)
            }
        }
    }
}

/** 홈과 전체 사이에서 옆으로 밀리는 가운데 칸들. 한 번에 [BottomMenu.VISIBLE_MIDDLE] 칸이 보이고, 놓으면 칸 경계에 멈춘다. */
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
    val statsShown by remember(state, statsIndex) {
        derivedStateOf { statsIndex >= 0 && isFullyShown(statsIndex, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
    }
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Start),
        modifier = modifier.wholeSlots().fadingEdges(state, EDGE_FADE),
    ) {
        items(items, key = { it.key }) { item ->
            // 한 칸이 보이는 너비의 4분의 1. 너비를 칸 수로 나누어떨어지게 맞춰 두어(wholeSlots) 칸 경계와 스크롤 끝이 딱 맞는다.
            Row(Modifier.fillParentMaxWidth(1f / BottomMenu.VISIBLE_MIDDLE)) {
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
 * 가운데 칸 [index] 가 다 보이는지. [first] 는 보이기 시작하는 칸, [offset] 은 그 칸이 왼쪽으로 밀려 잘린 만큼이다.
 * 칸 너비가 보이는 너비의 4분의 1 이라 [first] 부터 네 칸이 보이고, 잘린 칸이 있으면 그 칸은 빼고 본다.
 */
internal fun isFullyShown(index: Int, first: Int, offset: Int): Boolean {
    val start = if (offset == 0) first else first + 1
    return index in start..(first + BottomMenu.VISIBLE_MIDDLE - 1)
}

/**
 * 너비를 [BottomMenu.VISIBLE_MIDDLE] 로 나누어떨어지게 줄여 가운데에 둔다(남는 1~3px 은 양옆으로).
 * 나누어떨어지지 않으면 칸 너비가 반올림되어 스크롤 끝에서 첫 칸이 몇 px 잘린 채 멈추고, 통계 칸을 '다 보임' 으로 못 알아본다.
 */
private fun Modifier.wholeSlots(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth - constraints.maxWidth % BottomMenu.VISIBLE_MIDDLE
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place((constraints.maxWidth - width) / 2, 0) }
}

/** 밀어서 더 볼 칸이 있는 쪽 가장자리를 [width] 만큼 흐리게 한다. 끝까지 밀면 그쪽은 흐리지 않는다. */
private fun Modifier.fadingEdges(state: LazyListState, width: Dp): Modifier = this
    // 그려 둔 칸에 투명도만 덧씌우려면 따로 한 장으로 그려야 한다
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = width.toPx()
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
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
            // 칸이 여섯이면 좁은 폰(320dp, 칸 너비 약 53dp)에서 네 글자('고정지출')가 빠듯하다.
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

/** 밀어서 더 볼 칸이 있다는 것을 알리는 흐린 가장자리. 끝 칸의 바깥쪽 3분의 1쯤(글자 끝)이 흐려져 이어지는 칸이 있어 보이게 한다. */
private val EDGE_FADE = 20.dp

/** 하단 탭은 바탕 없이 작은 아이콘과 글자뿐이라 버튼보다 더 줄인다 */
private const val NAV_PRESSED_SCALE = 0.9f

/** 좁은 화면에서 탭 글자를 줄이는 하한. 이보다 작으면 읽기 어렵다. */
private val MIN_LABEL_SIZE = 10.sp
