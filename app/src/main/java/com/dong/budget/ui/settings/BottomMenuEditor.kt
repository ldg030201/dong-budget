package com.dong.budget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.SectionLabel
import com.dong.budget.ui.shell.icon
import com.dong.budget.ui.shell.label
import com.dong.budget.ui.theme.BudgetTheme
import kotlin.math.roundToInt

/**
 * 설정의 하단 메뉴 편집기. 아이콘을 직접 끌어서 넣고 빼고 순서를 바꾼다.
 *
 * 위 판은 하단 메뉴 차림 그대로(홈 · 넣은 메뉴 · 전체를 모두 한 줄에), 아래 테두리 칸은 넣을 수 있는 메뉴다.
 * 아이콘을 길게 눌러 들면 손가락을 따라오고, 놓일 자리는 흐린 빈칸으로 미리 보인다. 놓으면 바로 저장한다.
 * 위 판 쪽에서 놓으면 들어가고, 아래 칸 쪽에서 놓으면 빠진다. 홈과 전체는 자물쇠가 붙어 들 수 없다.
 *
 * 끌기는 칸마다가 아니라 편집기 전체가 받는다. 끄는 메뉴의 칸이 위아래 줄을 오가며 새로 그려져도 끌기가 끊기지 않게 하기 위함이다.
 * 길게 누른 자리의 칸을 찾아 들고, 놓일 자리는 같은 줄 다른 칸들의 가운데와 견주어 정한다([spotIndex]).
 * 끌기는 화면 읽기로 할 수 없어 칸마다 옮기기·넣기·빼기 동작을 따로 준다.
 *
 * @param menu 지금 차림. 이 편집기가 그려져 있는 동안은 여기서 고친 차림이 기준이다(저장이 돌아오기 전에 또 끌어도 튀지 않게).
 */
@Composable
internal fun BottomMenuEditor(menu: BottomMenu, onChange: (BottomMenu) -> Unit, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf(menu) }
    var drag by remember { mutableStateOf<MenuDrag?>(null) }
    val shownMenu = drag?.preview ?: current
    val layout = remember { EditorLayout() }
    val haptic = LocalHapticFeedback.current
    val change by rememberUpdatedState(onChange)

    fun save(next: BottomMenu) {
        if (next == current) return
        current = next
        change(next)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("하단 메뉴", modifier = Modifier.weight(1f))
            BudgetTextButton(text = "처음대로", onClick = { save(BottomMenu.DEFAULT) }, enabled = current != BottomMenu.DEFAULT)
        }
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { layout.editor = it }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val (item, rect) = layout.movableAt(start) ?: return@detectDragGesturesAfterLongPress
                            drag = MenuDrag(item = item, grab = start - rect.topLeft, slot = rect, pointer = start, preview = current)
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        },
                        onDrag = { pointerChange, amount ->
                            val held = drag ?: return@detectDragGesturesAfterLongPress
                            pointerChange.consume()
                            val pointer = held.pointer + amount
                            val next = current.moveTo(held.item, layout.spotAt(pointer, held.item, held.preview))
                            if (next != held.preview) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            drag = held.copy(pointer = pointer, preview = next)
                        },
                        onDragEnd = {
                            drag?.let {
                                save(it.preview)
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    )
                },
        ) {
            // 넣을 수 있는 메뉴 칸은 하단 메뉴 여섯 칸(홈 · 가운데 넷 · 전체)과 같은 너비로 왼쪽부터 늘어놓는다
            val traySlot = maxWidth / SLOTS_PER_ROW
            Column {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(BudgetTheme.colors.sectionBackground, RoundedCornerShape(BudgetTheme.radius.block))
                        .onGloballyPositioned { layout.bar = it.boundsInRoot() }
                        .padding(vertical = BudgetTheme.spacing.inlineGap),
                ) {
                    shownMenu.items.forEach { item ->
                        key(item) {
                            MenuIcon(
                                item = item,
                                lifted = item == drag?.item,
                                actions = shownMenu.actionsFor(item, ::save),
                                position = shownMenu.items.indexOf(item) + 1,
                                modifier = Modifier.weight(1f).onGloballyPositioned { layout.slots[item] = it.boundsInRoot() },
                            )
                        }
                    }
                }
                Text(
                    text = "넣을 수 있는 메뉴",
                    style = MaterialTheme.typography.labelMedium,
                    color = BudgetTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = BudgetTheme.spacing.sectionPadding, bottom = BudgetTheme.spacing.inlineGap),
                )
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TRAY_MIN_HEIGHT)
                        .border(BudgetTheme.size.underline, BudgetTheme.colors.divider, RoundedCornerShape(BudgetTheme.radius.block))
                        .onGloballyPositioned { layout.tray = it.boundsInRoot() }
                        .padding(vertical = BudgetTheme.spacing.inlineGap),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (shownMenu.hidden.isEmpty()) {
                        Text(
                            text = "모든 메뉴가 하단 메뉴에 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = BudgetTheme.colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row {
                        shownMenu.hidden.forEach { item ->
                            key(item) {
                                MenuIcon(
                                    item = item,
                                    lifted = item == drag?.item,
                                    actions = shownMenu.actionsFor(item, ::save),
                                    position = null,
                                    modifier = Modifier.width(traySlot).onGloballyPositioned { layout.slots[item] = it.boundsInRoot() },
                                )
                            }
                        }
                    }
                }
            }
            // 든 아이콘. 손가락을 따라오고, 다른 칸보다 위에 떠 있다.
            drag?.let { held -> HeldIcon(held) }
        }
        HintText(
            text = "아이콘을 길게 눌러 끌어서 넣고 빼거나 순서를 바꿔요. 가운데 메뉴가 ${BottomMenu.VISIBLE_MIDDLE}개를 넘으면 " +
                "하단 메뉴 끝에 걸친 칸이 보이고, 옆으로 밀어서 봐요.",
            modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap),
        )
    }
}

/**
 * 끄는 중인 메뉴.
 * @property grab 든 칸의 왼쪽 위에서 손가락까지. 아이콘이 손가락 밑에서 튀지 않게 그만큼 떼어 그린다.
 * @property slot 처음 든 칸의 자리와 크기(편집기 기준). 든 아이콘을 같은 크기로 그린다.
 * @property preview 지금 놓으면 될 차림. 놓일 자리를 미리 보여 준다.
 */
private data class MenuDrag(val item: MenuItem, val grab: Offset, val slot: Rect, val pointer: Offset, val preview: BottomMenu)

/**
 * 편집기 안 칸·줄의 자리. 끄는 손가락이 어느 칸·줄 위인지 견주려고 둔다(그리기와 상관없어 화면 상태로 두지 않는다).
 * 자리는 화면 기준으로 받아 두고, 끌기가 쓰는 편집기 기준으로 바꿔 읽는다. 설정 화면을 스크롤해도 맞는다.
 */
private class EditorLayout {
    var editor: LayoutCoordinates? = null
    var bar: Rect = Rect.Zero
    var tray: Rect = Rect.Zero
    val slots: MutableMap<MenuItem, Rect> = mutableMapOf()

    private val origin: Offset get() = editor?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero

    private fun local(rect: Rect): Rect = rect.translate(-origin)

    /** [point] 에 있는 옮길 수 있는 메뉴와 그 칸. 홈·전체나 빈 곳이면 null */
    fun movableAt(point: Offset): Pair<MenuItem, Rect>? =
        slots.entries.firstNotNullOfOrNull { (item, rect) -> local(rect).takeIf { !item.fixed && it.contains(point) }?.let { item to it } }

    /** 손가락이 [point] 에 있을 때 [item] 이 놓일 자리. 위 판과 아래 칸의 가운데보다 위면 하단 메뉴, 아래면 넣을 수 있는 메뉴다. */
    fun spotAt(point: Offset, item: MenuItem, preview: BottomMenu): MenuSpot {
        val bar = local(bar)
        val tray = local(tray)
        fun centers(items: List<MenuItem>) = items.filter { it != item }.mapNotNull { slots[it]?.let(::local)?.center?.x }
        return if (point.y < (bar.bottom + tray.top) / 2) {
            MenuSpot.Shown(spotIndex(centers(preview.shown), point.x))
        } else {
            MenuSpot.Hidden(spotIndex(centers(preview.hidden), point.x))
        }
    }
}

/** 칸 하나의 화면 읽기 동작. 하단 메뉴에 있으면 왼쪽·오른쪽으로 옮기기와 빼기, 없으면 넣기. */
private fun BottomMenu.actionsFor(item: MenuItem, save: (BottomMenu) -> Unit): List<CustomAccessibilityAction> {
    if (item.fixed) return emptyList()
    fun action(label: String, next: BottomMenu?) = next?.let {
        CustomAccessibilityAction(label) {
            save(it)
            true
        }
    }
    return if (item in shown) {
        listOfNotNull(
            action("왼쪽으로 옮기기", moveBy(item, -1)),
            action("오른쪽으로 옮기기", moveBy(item, 1)),
            action("하단 메뉴에서 빼기", moveTo(item, MenuSpot.Hidden(0))),
        )
    } else {
        listOfNotNull(action("하단 메뉴에 넣기", moveTo(item, MenuSpot.Shown(Int.MAX_VALUE))))
    }
}

/**
 * 메뉴 칸 하나. 아이콘과 이름. 홈·전체는 아이콘 오른쪽 위에 자물쇠를 단다.
 * @param lifted 지금 들고 있는 메뉴의 칸. 놓일 자리를 알리는 흐린 빈칸으로 그린다.
 * @param position 하단 메뉴에서 몇 번째 칸인지(왼쪽부터 1). 넣을 수 있는 메뉴면 null. 화면 읽기가 읽는다.
 */
@Composable
private fun MenuIcon(
    item: MenuItem,
    lifted: Boolean,
    actions: List<CustomAccessibilityAction>,
    position: Int?,
    modifier: Modifier = Modifier,
) {
    val where =
        when {
            item.fixed -> "옮길 수 없어요"
            position != null -> "하단 메뉴 ${position}번째"
            else -> "넣을 수 있는 메뉴"
        }
    Column(
        modifier =
        modifier
            .alpha(if (lifted) LIFTED_SLOT_ALPHA else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.label}, $where"
                customActions = actions
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MenuIconContent(item)
    }
}

/** 칸 안의 아이콘과 이름. 칸과 든 아이콘이 같이 쓴다. */
@Composable
private fun MenuIconContent(item: MenuItem) {
    Box {
        Icon(
            imageVector = item.icon(),
            contentDescription = null,
            tint = BudgetTheme.colors.textSecondary,
            modifier = Modifier.padding(BudgetTheme.spacing.tightGap).size(BudgetTheme.size.icon),
        )
        if (item.fixed) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = BudgetTheme.colors.textTertiary,
                modifier = Modifier.align(Alignment.TopEnd).size(LOCK_SIZE),
            )
        }
    }
    val style = MaterialTheme.typography.labelSmall
    Text(
        text = item.label,
        style = style,
        color = BudgetTheme.colors.textSecondary,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
    )
}

/** 손가락을 따라오는 든 아이콘. 처음 든 칸과 같은 크기로, 살짝 떠 보이게 그림자를 단다. */
@Composable
private fun HeldIcon(held: MenuDrag) {
    val density = LocalDensity.current
    val topLeft = held.pointer - held.grab
    Surface(
        color = BudgetTheme.colors.raised,
        shape = RoundedCornerShape(BudgetTheme.radius.control),
        shadowElevation = BudgetTheme.elevation.fab,
        modifier =
        Modifier
            .zIndex(1f)
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(with(density) { held.slot.width.toDp() }, with(density) { held.slot.height.toDp() }),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MenuIconContent(held.item)
        }
    }
}

/** 넣을 수 있는 메뉴 한 줄에 놓는 칸 수. 하단 메뉴가 한 번에 보여 주는 칸 수(홈 · 가운데 넷 · 전체)와 같다. */
private const val SLOTS_PER_ROW = BottomMenu.VISIBLE_MIDDLE + 2

/** 넣을 수 있는 메뉴 칸의 최소 높이. 다 넣어 비어도 끌어 내릴 자리가 남게 한다. */
private val TRAY_MIN_HEIGHT = 64.dp

/** 든 메뉴가 놓일 빈칸의 흐림 */
private const val LIFTED_SLOT_ALPHA = 0.2f

private val LOCK_SIZE = 12.dp

/** 칸이 일곱이면 좁은 폰에서 네 글자 이름이 빠듯해 한 줄에 들 때까지 줄인다(아래 메뉴와 같은 하한) */
private val MIN_LABEL_SIZE = 10.sp
