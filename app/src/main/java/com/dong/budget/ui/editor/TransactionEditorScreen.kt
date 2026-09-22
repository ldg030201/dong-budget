package com.dong.budget.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.components.BudgetChip
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTextField
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.NavButtonStyle
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDay
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorScreen(
    state: EditorUiState,
    onClose: () -> Unit,
    onSelectType: (TransactionType) -> Unit,
    onDigit: (String) -> Unit,
    onDeleteDigit: () -> Unit,
    onClearAmount: () -> Unit,
    onSelectCategory: (Long) -> Unit,
    onSelectPaymentMethod: (Long) -> Unit,
    onMerchantChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 글자를 입력하는 동안에는 시스템 키보드가 올라오므로 숫자 키패드를 숨긴다.
    var textFieldFocused by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "이 거래를 지울까요?",
            message = "지운 거래는 되돌릴 수 없어요.",
            confirmLabel = "지우기",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteTransaction()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onClose,
                title = if (state.isEditing) "거래 수정" else "거래 등록",
                style = NavButtonStyle.CLOSE,
                actions = {
                    if (state.isEditing) {
                        DeleteAction(onClick = { showDeleteConfirm = true })
                    }
                },
            )

            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                TypeToggle(selected = state.type, onSelect = onSelectType)

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

                AmountDisplay(amount = state.amount, hasInput = state.amountDigits.isNotEmpty())

                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))

                Text(
                    text = formatDay(state.occurredAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textSecondary,
                )

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

                SectionLabel("카테고리")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                    verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                ) {
                    state.categories.forEach { category ->
                        BudgetChip(
                            label = category.name,
                            selected = category.id == state.categoryId,
                            onClick = { onSelectCategory(category.id) },
                        )
                    }
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

                SectionLabel("결제수단")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                    verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                ) {
                    state.paymentMethods.forEach { method ->
                        BudgetChip(
                            label = method.name,
                            selected = method.id == state.paymentMethodId,
                            onClick = { onSelectPaymentMethod(method.id) },
                        )
                    }
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

                SectionLabel("내용")
                BudgetTextField(
                    value = state.merchant,
                    onValueChange = onMerchantChange,
                    placeholder = "어디에 썼나요",
                    onFocusChanged = { focused -> if (focused) textFieldFocused = true },
                )

                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))

                BudgetTextField(
                    value = state.memo,
                    onValueChange = onMemoChange,
                    placeholder = "메모 (선택)",
                    imeAction = ImeAction.Done,
                    onFocusChanged = { focused -> if (focused) textFieldFocused = true },
                )

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }

            Column(
                modifier =
                Modifier
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                AnimatedVisibility(visible = !textFieldFocused) {
                    Column {
                        NumberKeypad(
                            onDigit = { digit ->
                                textFieldFocused = false
                                onDigit(digit)
                            },
                            onDelete = onDeleteDigit,
                            onClear = onClearAmount,
                        )
                        Spacer(Modifier.height(BudgetTheme.spacing.ctaTopGap))
                    }
                }
                BudgetPrimaryButton(
                    text = if (state.isEditing) "수정하기" else "등록하기",
                    onClick = onSave,
                    enabled = state.canSave,
                )
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BudgetTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap),
    )
}

@Composable
private fun AmountDisplay(amount: Long, hasInput: Boolean) {
    Text(
        text = "${formatAmount(amount)}원",
        style = BudgetTheme.amount.hero,
        color = if (hasInput) BudgetTheme.colors.textPrimary else BudgetTheme.colors.textTertiary,
    )
}

@Composable
private fun TypeToggle(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(BudgetTheme.colors.sectionBackground, shape)
            .padding(BudgetTheme.spacing.tightGap),
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        ToggleCell(
            label = "지출",
            selected = selected == TransactionType.EXPENSE,
            onClick = { onSelect(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f),
        )
        ToggleCell(
            label = "수입",
            selected = selected == TransactionType.INCOME,
            onClick = { onSelect(TransactionType.INCOME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleCell(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BudgetTheme.radius.chip)
    Row(
        modifier =
        modifier
            .pressScaleClickable(shape = shape, onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.background else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            ).height(BudgetTheme.size.minTouchTarget),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) BudgetTheme.colors.textPrimary else BudgetTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun DeleteAction(onClick: () -> Unit) {
    Text(
        text = "삭제",
        style = MaterialTheme.typography.labelLarge,
        color = BudgetTheme.colors.danger,
        modifier =
        Modifier
            .pressScaleClickable(
                shape = RoundedCornerShape(BudgetTheme.radius.chip),
                onClick = onClick,
            ).padding(
                horizontal = BudgetTheme.spacing.itemGap,
                vertical = BudgetTheme.spacing.inlineGap,
            ),
    )
}
