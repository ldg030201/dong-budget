package com.dong.budget.ui.category

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 전체 → 분류 관리. 지출·수입 분류와 결제수단을 추가하고 지우고, 길게 눌러 끌어서 순서를 바꾼다.
 * 바꾼 순서는 거래 등록 화면의 분류·결제수단 표에도 그대로 쓰인다.
 */
@Composable
fun CategoryManageScreen(
    state: CategoryManageUiState,
    onTabChange: (ManageTab) -> Unit,
    onOpenAdd: () -> Unit,
    onDismissAdd: () -> Unit,
    onSubmitAdd: (name: String, icon: String, color: String) -> Unit,
    onRequestDelete: (ManagedItem) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onReorder: (List<ManagedItem>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPayment = state.tab == ManageTab.PAYMENT
    val noun = if (isPayment) "결제수단" else "분류"
    // 조사를 이어 붙이지 않고 통째로 둔다. 받침 유무에 따라 '분류를/결제수단을',
    // '분류로/결제수단으로' 처럼 달라져서 붙이면 '분류을' 같은 틀린 문장이 된다.
    val nounObject = if (isPayment) "결제수단을" else "분류를"
    val nounBy = if (isPayment) "결제수단으로" else "분류로"

    state.pendingDelete?.let { target ->
        ConfirmDialog(
            title = "'${target.name}' $nounObject 지울까요?",
            message =
            when {
                target.transactionCount == 0 -> "이 $nounBy 적힌 거래는 없어요."

                // 분류는 '기타' 로 옮기고, 결제수단은 비워둘 수 있는 칸이라 비운다
                isPayment -> "이 결제수단으로 적힌 거래 ${target.transactionCount}건은 결제수단 없이 남아요."

                else -> "이 분류로 적힌 거래 ${target.transactionCount}건은 '기타'로 옮겨져요."
            },
            confirmLabel = "지우기",
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
        )
    }

    if (state.showAdd) {
        AddItemSheet(
            target = if (isPayment) AddTarget.PAYMENT else AddTarget.CATEGORY,
            usedColors = state.usedColors,
            error = state.addError,
            onDismiss = onDismissAdd,
            onSubmit = onSubmitAdd,
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "분류 관리")

            SegmentedToggle(
                options = ManageTab.entries.map { it.label },
                selectedIndex = state.tab.ordinal,
                onSelect = { onTabChange(ManageTab.entries[it]) },
                modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            )
            Text(
                text = "길게 눌러서 끌면 순서를 바꿀 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
                modifier =
                Modifier.padding(
                    horizontal = BudgetTheme.spacing.screenHorizontal,
                    vertical = BudgetTheme.spacing.inlineGap,
                ),
            )

            ReorderableList(
                tab = state.tab,
                items = state.items,
                onRequestDelete = onRequestDelete,
                onReorder = onReorder,
                modifier = Modifier.weight(1f),
            )

            Column(
                modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                BudgetPrimaryButton(text = "$noun 추가", onClick = onOpenAdd)
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            }
        }
    }
}

/**
 * 끌어서 순서를 바꾸는 목록.
 *
 * 끄는 동안에는 화면 안의 목록([ordered])만 바꾸고, 손을 떼면 한 번에 저장한다.
 * 한 칸 옮길 때마다 저장하면 DB 가 새 목록을 내보내면서 끄는 중인 항목이 제자리로 튄다.
 * '기타' 는 늘 맨 뒤라 끌 수 없고, 다른 항목도 그 아래로 내려가지 않는다([moved]).
 */
@Composable
private fun ReorderableList(
    tab: ManageTab,
    items: List<ManagedItem>,
    onRequestDelete: (ManagedItem) -> Unit,
    onReorder: (List<ManagedItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ordered by remember(items) { mutableStateOf(items) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    fun keyOf(item: ManagedItem) = "$tab-${item.id}"
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromId = ordered.firstOrNull { keyOf(it) == from.key }?.id ?: return@rememberReorderableLazyListState
            val toId = ordered.firstOrNull { keyOf(it) == to.key }?.id ?: return@rememberReorderableLazyListState
            ordered = ordered.moved(fromId, toId) ?: return@rememberReorderableLazyListState
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    // 손을 떼면 저장한다. 순서가 그대로면 저장하지 않는다.
    // 끌기 라이브러리는 손을 뗄 때 부를 함수를 그 줄을 처음 만졌을 때 한 번만 잡아 둔다. 그래서 목록이 바뀐 뒤
    // 같은 줄을 다시 끌면 옛 목록과 비교하는 함수가 불린다. 늘 최신 함수를 부르도록 한 번 거쳐서 넘긴다.
    val save by rememberUpdatedState {
        if (ordered.map { it.id } != items.map { it.id }) onReorder(ordered)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = BudgetTheme.spacing.sectionPadding),
    ) {
        itemsIndexed(ordered, key = { _, item -> keyOf(item) }) { index, item ->
            ReorderableItem(reorderState, key = keyOf(item), enabled = item.movable) { isDragging ->
                val elevation by animateDpAsState(
                    if (isDragging) BudgetTheme.elevation.fab else BudgetTheme.elevation.none,
                    label = "dragElevation",
                )
                val onDragStarted: (Offset) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
                Surface(color = MaterialTheme.colorScheme.background, shadowElevation = elevation) {
                    ManagedRow(
                        item = item,
                        onDelete = { onRequestDelete(item) },
                        modifier =
                        Modifier
                            .longPressDraggableHandle(enabled = item.movable, onDragStarted = onDragStarted, onDragStopped = { save() })
                            // 끌기는 화면 읽기로 할 수 없다. 한 칸씩 옮기는 동작을 따로 준다.
                            // 줄 전체(이름과 설명)를 한 덩어리로 묶어야 화면 읽기가 이 줄에 초점을 두고 동작을 보여준다.
                            // 옮길 수 없는 쪽('기타' 와 맞닿은 방향)의 동작은 누르면 아무 일도 없으니 아예 두지 않는다.
                            .semantics(mergeDescendants = true) {
                                customActions =
                                    listOfNotNull(
                                        ordered.getOrNull(index - 1)?.takeIf { item.movable && it.movable }?.let { above ->
                                            CustomAccessibilityAction("위로 옮기기") {
                                                ordered.moved(item.id, above.id)?.let(onReorder) != null
                                            }
                                        },
                                        ordered.getOrNull(index + 1)?.takeIf { item.movable && it.movable }?.let { below ->
                                            CustomAccessibilityAction("아래로 옮기기") {
                                                ordered.moved(item.id, below.id)?.let(onReorder) != null
                                            }
                                        },
                                    )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagedRow(item: ManagedItem, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    BudgetListItem(
        title = item.name,
        subtitle =
        when {
            !item.deletable -> "다른 분류를 지우면 거래가 여기로 옮겨와요"
            item.transactionCount > 0 -> "거래 ${item.transactionCount}건"
            else -> "아직 쓴 거래가 없어요"
        },
        leading = { CategoryBadge(icon = item.icon, color = item.color) },
        trailing = if (item.deletable) ({ DeleteButton(name = item.name, onClick = onDelete) }) else null,
        modifier = modifier,
    )
}

@Composable
private fun DeleteButton(name: String, onClick: () -> Unit) {
    Box(
        modifier =
        Modifier
            .size(BudgetTheme.size.minTouchTarget)
            .pressScaleClickable(shape = CircleShape, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            // 목록에 삭제 버튼이 여러 개라 어느 분류의 것인지 이름을 함께 읽어준다
            contentDescription = "$name 지우기",
            tint = BudgetTheme.colors.textSecondary,
            modifier = Modifier.size(BudgetTheme.size.icon),
        )
    }
}
