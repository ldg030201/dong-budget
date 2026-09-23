package com.dong.budget.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.category.CategoryAddSheet
import com.dong.budget.ui.category.CategoryGrid
import com.dong.budget.ui.components.BudgetChip
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.FormField
import com.dong.budget.ui.components.FormIconValue
import com.dong.budget.ui.components.FormPlaceholder
import com.dong.budget.ui.components.FormTextField
import com.dong.budget.ui.components.FormValue
import com.dong.budget.ui.components.NavButtonStyle
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDay
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/** 화면 아래에 열리는 입력판. 한 번에 하나만 열린다. */
enum class EditorPanel { AMOUNT, CATEGORY, PAYMENT }

private val TYPE_OPTIONS = listOf(TransactionType.EXPENSE to "지출", TransactionType.INCOME to "수입")

/**
 * 거래 등록·수정 화면.
 *
 * 처음에는 금액, 분류, 결제수단, 내용, 메모 칸만 보인다.
 * 칸을 누르면 그 칸에 맞는 입력판만 아래에 열린다.
 *   금액 → 숫자 키패드 / 분류 → 분류 표 / 결제수단 → 선택지 / 내용·메모 → 시스템 키보드
 * 모든 선택지를 한꺼번에 펼치지 않아서 화면이 복잡하지 않다.
 */
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
    onOpenAddCategory: () -> Unit,
    onDismissAddCategory: () -> Unit,
    onSubmitCategory: (name: String, icon: String, color: String) -> Unit,
    onSelectPaymentMethod: (Long) -> Unit,
    onMerchantChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 새로 등록할 때는 금액부터 받으므로 키패드를 열어둔다.
    // 수정할 때는 값을 먼저 훑어보게 모두 닫아둔다.
    var panel by rememberSaveable { mutableStateOf(if (state.isEditing) null else EditorPanel.AMOUNT) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun toggle(target: EditorPanel) {
        // 글자 칸에 커서가 있으면 시스템 키보드가 떠 있다. 입력판과 겹치지 않게 먼저 내린다.
        focusManager.clearFocus()
        panel = if (panel == target) null else target
    }

    // 입력판이 열려 있으면 뒤로가기는 화면이 아니라 입력판을 닫는다.
    BackHandler(enabled = panel != null) { panel = null }

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

    if (state.showAddCategory) {
        CategoryAddSheet(
            usedColors = state.usedColors,
            error = state.addCategoryError,
            onDismiss = onDismissAddCategory,
            onSubmit = { name, icon, color ->
                onSubmitCategory(name, icon, color)
                // 새 분류가 고른 상태로 들어오므로 분류 표는 닫는다
                panel = null
            },
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onClose,
                title = if (state.isEditing) "거래 수정" else "거래 등록",
                style = NavButtonStyle.CLOSE,
                actions = { if (state.isEditing) DeleteAction(onClick = { showDeleteConfirm = true }) },
            )

            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                SegmentedToggle(
                    options = TYPE_OPTIONS.map { it.second },
                    selectedIndex = TYPE_OPTIONS.indexOfFirst { it.first == state.type }.coerceAtLeast(0),
                    onSelect = { onSelectType(TYPE_OPTIONS[it].first) },
                )
                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
                Text(
                    text = formatDay(state.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.textSecondary,
                )

                FormField(label = "금액", active = panel == EditorPanel.AMOUNT, onClick = { toggle(EditorPanel.AMOUNT) }) {
                    Text(
                        text = "${formatAmount(state.amount)}원",
                        style = BudgetTheme.amount.large,
                        color = if (state.amountDigits.isEmpty()) BudgetTheme.colors.textTertiary else BudgetTheme.colors.textPrimary,
                    )
                }

                FormField(label = "분류", active = panel == EditorPanel.CATEGORY, onClick = { toggle(EditorPanel.CATEGORY) }) {
                    val category = state.selectedCategory
                    if (category == null) {
                        FormPlaceholder("분류를 골라주세요")
                    } else {
                        FormIconValue(
                            icon = { CategoryBadge(category.icon, category.color, size = BudgetTheme.size.badgeSmall) },
                            text = category.name,
                        )
                    }
                }

                FormField(label = "결제수단", active = panel == EditorPanel.PAYMENT, onClick = { toggle(EditorPanel.PAYMENT) }) {
                    val method = state.selectedPaymentMethod
                    if (method == null) FormPlaceholder("고르지 않아도 돼요") else FormValue(method.name)
                }

                FormTextField(
                    label = "내용",
                    value = state.merchant,
                    onValueChange = onMerchantChange,
                    placeholder = "어디에 썼나요",
                    onFocusChanged = { focused -> if (focused) panel = null },
                )

                FormTextField(
                    label = "메모",
                    value = state.memo,
                    onValueChange = onMemoChange,
                    placeholder = "메모 (선택)",
                    imeAction = ImeAction.Done,
                    onFocusChanged = { focused -> if (focused) panel = null },
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
                AnimatedContent(targetState = panel, label = "editorPanel") { current ->
                    when (current) {
                        EditorPanel.AMOUNT ->
                            PanelBox {
                                NumberKeypad(onDigit = onDigit, onDelete = onDeleteDigit, onClear = onClearAmount)
                            }

                        EditorPanel.CATEGORY ->
                            PanelBox {
                                CategoryGrid(
                                    categories = state.categories,
                                    selectedId = state.categoryId,
                                    onSelect = { id ->
                                        onSelectCategory(id)
                                        panel = null
                                    },
                                    onAdd = onOpenAddCategory,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                        EditorPanel.PAYMENT ->
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = BudgetTheme.spacing.ctaTopGap),
                                horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                                verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                            ) {
                                state.paymentMethods.forEach { method ->
                                    BudgetChip(
                                        label = method.name,
                                        selected = method.id == state.paymentMethodId,
                                        onClick = {
                                            onSelectPaymentMethod(method.id)
                                            panel = null
                                        },
                                    )
                                }
                            }

                        null -> Box(Modifier.fillMaxWidth())
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

/**
 * 키패드와 분류 표의 높이를 맞춘다.
 * 둘 사이를 오갈 때 등록 버튼이 위아래로 출렁이지 않게 하기 위함이다.
 */
@Composable
private fun PanelBox(content: @Composable () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.inputPanelHeight)
            .padding(bottom = BudgetTheme.spacing.ctaTopGap),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
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
            .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onClick)
            .padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.inlineGap),
    )
}
