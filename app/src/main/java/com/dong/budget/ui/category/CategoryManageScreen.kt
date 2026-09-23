package com.dong.budget.ui.category

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/** 전체 → 분류 관리. 지출·수입 분류와 결제수단을 추가하고 지운다. */
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
            title = "$noun 추가",
            namePlaceholder = if (isPayment) "예: 신한카드" else "예: 카페",
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
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = BudgetTheme.spacing.sectionPadding),
            ) {
                items(state.items, key = { "${state.tab}-${it.id}" }) { item ->
                    ManagedRow(item = item, onDelete = { onRequestDelete(item) })
                }
            }

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

@Composable
private fun ManagedRow(item: ManagedItem, onDelete: () -> Unit) {
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
