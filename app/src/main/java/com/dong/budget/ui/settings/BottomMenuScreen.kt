package com.dong.budget.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.dong.budget.R
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.components.SectionLabel
import com.dong.budget.ui.shell.ShellBottomBar
import com.dong.budget.ui.shell.color
import com.dong.budget.ui.shell.description
import com.dong.budget.ui.shell.icon
import com.dong.budget.ui.shell.label
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 설정 > 하단 메뉴. 아래 메뉴에 둘 메뉴를 넣고 빼고, 길게 눌러 끌어서 순서를 바꾼다.
 *
 * 맨 위에 지금 차림 그대로의 아래 메뉴를 미리 보여 준다(가운데가 넘치면 거기서도 옆으로 밀어 볼 수 있다).
 * 그 아래 목록은 '하단 메뉴'(홈 · 넣은 메뉴 · 전체)와 '넣을 수 있는 메뉴' 두 구역이다.
 * 홈과 전체는 자물쇠가 달려 끌 수 없다. 메뉴를 전체 아래로 끌어 내리면 빠지고, 위로 끌어 올리면 들어간다.
 * 끌기가 어려우면 줄 끝의 −·+ 로도 넣고 뺀다. 화면 읽기로는 위로/아래로 옮기기 동작을 준다.
 *
 * 고친 차림은 바로 저장한다(끄는 동안에는 손을 뗄 때 한 번). 아래 메뉴의 칸 수에는 제한이 없다.
 * 가운데가 [BottomMenu.VISIBLE_MIDDLE] 칸을 넘으면 아래 메뉴의 가운데만 옆으로 밀려 바뀐다.
 *
 * @param menu 들어올 때의 차림. 이 화면이 열려 있는 동안은 여기서 고친 차림이 기준이다.
 * @param onChange 고친 차림을 저장한다
 */
@Composable
fun BottomMenuScreen(menu: BottomMenu, onChange: (BottomMenu) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // 저장이 돌아오기 전에 또 끌어도 줄이 튀지 않게, 열려 있는 동안은 이 화면이 들고 있는 차림을 기준으로 삼는다
    var current by remember { mutableStateOf(menu) }
    var saved by remember { mutableStateOf(menu) }

    fun save() {
        if (current == saved) return
        saved = current
        onChange(current)
    }

    fun commit(next: BottomMenu) {
        current = next
        save()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onBack,
                title = "하단 메뉴",
                actions = {
                    BudgetTextButton(text = "처음대로", onClick = { commit(BottomMenu.DEFAULT) }, enabled = current != BottomMenu.DEFAULT)
                },
            )
            // 미리 보기. 누르는 칸은 아무 일도 하지 않고, 화면 읽기에는 차림을 한 줄로 읽어 준다.
            val previewState = rememberLazyListState()
            ShellBottomBar(
                menu = current,
                selected = MenuItem.HOME,
                onClick = {},
                middleState = previewState,
                containerColor = BudgetTheme.colors.sectionBackground,
                windowInsets = WindowInsets(0, 0, 0, 0),
                linkStatistics = false,
                modifier =
                Modifier
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                    .clip(RoundedCornerShape(BudgetTheme.radius.block))
                    .clearAndSetSemantics { contentDescription = "하단 메뉴 미리 보기, " + current.items.joinToString(", ") { it.label } },
            )
            HintText(
                text = "길게 눌러 끌면 순서를 바꾸거나 넣고 뺄 수 있어요. 가운데 메뉴가 ${BottomMenu.VISIBLE_MIDDLE}개를 넘으면 " +
                    "하단 메뉴를 옆으로 밀어서 봐요.",
                modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal, vertical = BudgetTheme.spacing.itemGap),
            )
            MenuEditList(
                menu = current,
                onMove = { current = it },
                onDrop = ::save,
                onCommit = ::commit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 끌어서 넣고 빼고 순서를 바꾸는 목록. 끄는 동안에는 [onMove] 로 화면 안의 차림만 바꾸고, 손을 떼면 [onDrop] 으로 저장한다.
 * 모든 줄이 끄는 줄이 닿을 자리가 되고(머리·홈·전체 포함), 끌 수 있는 것은 넣고 뺄 수 있는 메뉴뿐이다. 닿았을 때의 규칙은 [dropOn].
 */
@Composable
private fun MenuEditList(
    menu: BottomMenu,
    onMove: (BottomMenu) -> Unit,
    onDrop: () -> Unit,
    onCommit: (BottomMenu) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = menu.editRows()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val latest by rememberUpdatedState(menu)
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val now = latest.editRows()
            val dragged = (now.firstOrNull { it.key == from.key } as? MenuEditRow.Item)?.item ?: return@rememberReorderableLazyListState
            val target = now.firstOrNull { it.key == to.key } ?: return@rememberReorderableLazyListState
            onMove(latest.dropOn(dragged, target) ?: return@rememberReorderableLazyListState)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    // 끌기 라이브러리는 손을 뗄 때 부를 함수를 그 줄을 처음 만졌을 때 한 번만 잡아 둔다. 늘 최신 함수를 부르도록 한 번 거쳐서 넘긴다.
    val drop by rememberUpdatedState(onDrop)

    LazyColumn(
        state = listState,
        modifier = modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = BudgetTheme.spacing.sectionGap),
    ) {
        items(rows, key = { it.key }) { row ->
            // 머리·안내 줄도 끄는 줄이 닿을 자리여야 구역을 넘을 수 있어 모두 감싼다
            ReorderableItem(reorderState, key = row.key) { isDragging ->
                when (row) {
                    is MenuEditRow.Header ->
                        SectionLabel(
                            text = if (row.shown) "하단 메뉴 · ${menu.items.size}개" else "넣을 수 있는 메뉴",
                            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
                        )

                    MenuEditRow.AllShown ->
                        HintText(
                            text = "모든 메뉴가 하단 메뉴에 있어요. 빼려면 메뉴를 여기로 끌어 내리세요.",
                            modifier =
                            Modifier.padding(
                                horizontal = BudgetTheme.spacing.screenHorizontal,
                                vertical = BudgetTheme.spacing.itemGap,
                            ),
                        )

                    is MenuEditRow.Item -> {
                        val item = row.item
                        // 잡은 줄은 살짝 커지며 떠오른다(분류 관리와 같은 모양)
                        val elevation by animateDpAsState(
                            if (isDragging) BudgetTheme.elevation.fab else BudgetTheme.elevation.none,
                            animationSpec = Motion.standard(),
                            label = "dragElevation",
                        )
                        val lift by animateFloatAsState(if (isDragging) DRAG_LIFT_SCALE else 1f, Motion.standard(), label = "dragLift")
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(BudgetTheme.radius.control),
                            shadowElevation = elevation,
                            modifier =
                            Modifier.graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                            },
                        ) {
                            MenuEditItem(
                                item = item,
                                shown = item in menu,
                                onRemove = { onCommit(menu.remove(item)) },
                                onAdd = { onCommit(menu.add(item)) },
                                modifier =
                                Modifier
                                    .longPressDraggableHandle(
                                        enabled = !item.fixed,
                                        onDragStarted = { _: Offset ->
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            drop()
                                        },
                                    )
                                    // 끌기는 화면 읽기로 할 수 없다. 같은 구역 안에서 한 칸씩 옮기는 동작을 따로 준다(넣고 빼기는 줄 끝 단추).
                                    .semantics(mergeDescendants = true) {
                                        if (!item.fixed) {
                                            customActions =
                                                listOfNotNull(
                                                    menu.moveBy(item, -1)?.let { moved ->
                                                        CustomAccessibilityAction("위로 옮기기") {
                                                            onCommit(moved)
                                                            true
                                                        }
                                                    },
                                                    menu.moveBy(item, 1)?.let { moved ->
                                                        CustomAccessibilityAction("아래로 옮기기") {
                                                            onCommit(moved)
                                                            true
                                                        }
                                                    },
                                                )
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 메뉴 한 줄. 아이콘·이름·설명, 끝에 자물쇠(홈·전체)나 −(넣은 메뉴)·+(뺀 메뉴).
 * @param shown 지금 하단 메뉴에 있는지
 */
@Composable
private fun MenuEditItem(item: MenuItem, shown: Boolean, onRemove: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    BudgetListItem(
        title = item.label,
        subtitle =
        when (item) {
            MenuItem.HOME -> "늘 맨 앞에 있어요"
            MenuItem.MORE -> "늘 맨 뒤에 있어요"
            else -> item.description
        },
        leading = { IconBadge(icon = item.icon(), swatch = BudgetTheme.categoryPalette[item.color]) },
        trailing = {
            when {
                // 자물쇠는 꾸밈이다. 설명이 '늘 맨 앞에 있어요' 라고 말한다.
                item.fixed ->
                    Box(modifier = Modifier.size(BudgetTheme.size.minTouchTarget), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = BudgetTheme.colors.textTertiary,
                            modifier = Modifier.size(BudgetTheme.size.iconSmall),
                        )
                    }

                shown ->
                    BudgetIconButton(
                        icon = ImageVector.vectorResource(R.drawable.ic_sym_remove),
                        contentDescription = "${item.label} 하단 메뉴에서 빼기",
                        onClick = onRemove,
                        tint = BudgetTheme.colors.danger,
                        shape = CircleShape,
                    )

                else ->
                    BudgetIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "${item.label} 하단 메뉴에 넣기",
                        onClick = onAdd,
                        tint = BudgetTheme.colors.brandText,
                        shape = CircleShape,
                    )
            }
        },
        modifier = modifier,
    )
}

/** 끌려고 잡은 줄의 크기. 손가락 밑에서 떠오른 게 보일 만큼만 키운다. */
private const val DRAG_LIFT_SCALE = 1.02f
