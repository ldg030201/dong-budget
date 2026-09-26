package com.dong.budget.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.category.AddItemSheet
import com.dong.budget.ui.category.AddTarget
import com.dong.budget.ui.category.PickerGrid
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.ErrorText
import com.dong.budget.ui.components.FormField
import com.dong.budget.ui.components.FormIconValue
import com.dong.budget.ui.components.FormPlaceholder
import com.dong.budget.ui.components.FormTextField
import com.dong.budget.ui.components.FormValue
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.NavButtonStyle
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDate
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

/** 화면 아래에 열리는 입력판. 한 번에 하나만 열린다. */
enum class EditorPanel { AMOUNT, CATEGORY, PAYMENT, DATE, TIME }

/**
 * 칸 목록의 높이가 이만큼 멈춰 있으면 입력판이 다 열렸다고 본다.
 * 애니메이션 중에는 매 프레임(16ms) 높이가 바뀌므로 그보다 넉넉하면 된다.
 */
private const val PANEL_SETTLE_MS = 100L

/** 저장을 눌렀는데 이 칸이 비어 있을 때 칸 밑에 나오는 안내. 무엇을 해야 하는지 칸 이름으로 알려준다. */
private fun RequiredField.missingMessage(): String = when (this) {
    RequiredField.AMOUNT -> "금액을 입력해주세요"
    RequiredField.CATEGORY -> "분류를 선택해주세요"
    RequiredField.PAYMENT -> "결제수단을 선택해주세요"
    RequiredField.MERCHANT -> "내용을 입력해주세요"
}

/** 이 칸을 채우는 입력판. 글자 칸(내용)은 입력판 대신 시스템 키보드를 쓴다. */
private val RequiredField.panel: EditorPanel?
    get() = when (this) {
        RequiredField.AMOUNT -> EditorPanel.AMOUNT
        RequiredField.CATEGORY -> EditorPanel.CATEGORY
        RequiredField.PAYMENT -> EditorPanel.PAYMENT
        RequiredField.MERCHANT -> null
    }

private val TYPE_OPTIONS = listOf(TransactionType.EXPENSE to "지출", TransactionType.INCOME to "수입")

/**
 * 거래 등록·수정 화면.
 *
 * 처음에는 금액, 분류, 결제수단, 내용, 메모, 날짜, 시간 칸만 보인다.
 * 칸을 누르면 그 칸에 맞는 입력판만 아래에 열린다.
 *   금액 → 숫자 키패드 / 분류·결제수단 → 아이콘 표 / 내용·메모 → 시스템 키보드
 *   날짜 → 달력 / 시간 → 시계
 * 모든 선택지를 한꺼번에 펼치지 않아서 화면이 복잡하지 않다.
 *
 * 메모를 뺀 칸은 모두 채워야 저장된다. 빈 칸이 있는데 저장을 누르면
 * 위에서부터 첫 빈 칸으로 옮겨 가고, 그 칸 밑에 '분류를 선택해주세요' 같은 안내가 나온다.
 * 안내는 저장을 누른 뒤에만 나온다. 입력하는 도중에 미리 빨갛게 표시하지 않는다.
 */
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
    onOpenAdd: (AddTarget) -> Unit,
    onDismissAdd: () -> Unit,
    onSubmitAdd: (name: String, icon: String, color: String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onSave: () -> Unit,
    effects: Flow<EditorEffect>,
    onDeleteTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 새로 등록할 때는 금액부터 받으므로 키패드를 열어둔다.
    // 수정할 때는 값을 먼저 훑어보게 모두 닫아둔다.
    // 알림에서 채워 들어온 경우도 값부터 확인하게 닫아둔다.
    var panel by rememberSaveable { mutableStateOf(if (state.isEditing || state.isPrefilled) null else EditorPanel.AMOUNT) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun toggle(target: EditorPanel) {
        // 글자 칸에 커서가 있으면 시스템 키보드가 떠 있다. 입력판과 겹치지 않게 먼저 내린다.
        focusManager.clearFocus()
        panel = if (panel == target) null else target
    }

    // 입력판이 열리면 칸 목록이 그만큼 줄어든다. 날짜·시간처럼 목록 아래쪽 칸을 누르면
    // 방금 누른 칸이 입력판에 밀려 가려질 수 있어서, 열린 칸이 보이도록 스크롤을 맞춘다.
    // 열리는 도중에 맞추면 줄어들기 전 높이로 계산해 여전히 가려지므로, 높이가 멈춘 뒤에 맞춘다.
    val scrollState = rememberScrollState()
    val requesters = remember { EditorPanel.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(panel) {
        val requester = requesters[panel] ?: return@LaunchedEffect
        snapshotFlow { scrollState.viewportSize }.collectLatest {
            delay(PANEL_SETTLE_MS)
            requester.bringIntoView()
        }
    }

    fun fieldModifier(target: EditorPanel) = Modifier.bringIntoViewRequester(requesters.getValue(target))

    fun missingMessage(field: RequiredField) = if (state.showsMissing(field)) field.missingMessage() else null

    // 뷰모델이 한 번씩 보내는 일을 처리한다.
    // - 새로 만든 분류·결제수단은 고른 상태로 들어오므로 입력판을 닫는다. 같은 이름이라 거절되면 오지 않아 시트가 남는다.
    // - 저장을 눌렀는데 빈 칸이 있으면 그 칸으로 옮겨 간다. 입력판이 있는 칸은 입력판을 열고,
    //   글자 칸은 커서를 넣어 키보드를 올린다. 칸 밑에는 그 칸에 맞는 안내가 붙는다.
    val merchantFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                EditorEffect.Added -> panel = null

                is EditorEffect.JumpTo -> {
                    val target = effect.field.panel
                    if (target != null) {
                        focusManager.clearFocus()
                        panel = target
                    } else {
                        panel = null
                        merchantFocus.requestFocus()
                        // 이미 커서가 있는 채로 키보드만 내려 둔 경우에는 초점이 그대로라 키보드가 다시 뜨지 않는다. 직접 올린다.
                        keyboard?.show()
                    }
                }
            }
        }
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

    state.addTarget?.let { target ->
        AddItemSheet(
            target = target,
            usedColors = state.usedColors(target),
            error = state.addError,
            onDismiss = onDismissAdd,
            onSubmit = onSubmitAdd,
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                SegmentedToggle(
                    options = TYPE_OPTIONS.map { it.second },
                    selectedIndex = TYPE_OPTIONS.indexOfFirst { it.first == state.type }.coerceAtLeast(0),
                    onSelect = { onSelectType(TYPE_OPTIONS[it].first) },
                )
                if (state.isPrefilled) {
                    HintText(
                        text = "결제 알림에서 가져왔어요. 확인하고 등록해 주세요.",
                        modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap),
                    )
                }

                FormField(
                    label = "금액",
                    active = panel == EditorPanel.AMOUNT,
                    onClick = { toggle(EditorPanel.AMOUNT) },
                    modifier = fieldModifier(EditorPanel.AMOUNT),
                    error = missingMessage(RequiredField.AMOUNT),
                ) {
                    Text(
                        text = "${formatAmount(state.amount)}원",
                        style = BudgetTheme.amount.large,
                        color = if (state.amountDigits.isEmpty()) BudgetTheme.colors.textTertiary else BudgetTheme.colors.textPrimary,
                    )
                }

                FormField(
                    label = "분류",
                    active = panel == EditorPanel.CATEGORY,
                    onClick = { toggle(EditorPanel.CATEGORY) },
                    modifier = fieldModifier(EditorPanel.CATEGORY),
                    error = missingMessage(RequiredField.CATEGORY),
                ) {
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

                FormField(
                    label = "결제수단",
                    active = panel == EditorPanel.PAYMENT,
                    onClick = { toggle(EditorPanel.PAYMENT) },
                    modifier = fieldModifier(EditorPanel.PAYMENT),
                    error = missingMessage(RequiredField.PAYMENT),
                ) {
                    val method = state.selectedPaymentMethod
                    val pendingName = state.pendingPaymentName
                    if (method == null && pendingName != null) {
                        // 알림에서 읽은 카드가 아직 결제수단에 없다. 저장할 때 새로 만든다.
                        // 저장할 때 만들어질 모양(아이콘·색)과 똑같이 보여준다
                        FormIconValue(
                            icon = {
                                CategoryBadge(
                                    PaymentMethodRepository.NEW_CARD_ICON,
                                    CategoryStyle.firstUnusedColor(state.usedPaymentColors),
                                    size = BudgetTheme.size.badgeSmall,
                                )
                            },
                            text = "$pendingName (새로 추가돼요)",
                        )
                    } else if (method == null) {
                        FormPlaceholder("결제수단을 골라주세요")
                    } else {
                        FormIconValue(
                            icon = { CategoryBadge(method.icon, method.color, size = BudgetTheme.size.badgeSmall) },
                            text = method.name,
                        )
                    }
                }

                FormTextField(
                    label = "내용",
                    value = state.merchant,
                    onValueChange = onMerchantChange,
                    placeholder = "어디에 썼나요",
                    error = missingMessage(RequiredField.MERCHANT),
                    focusRequester = merchantFocus,
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

                FormField(
                    label = "날짜",
                    active = panel == EditorPanel.DATE,
                    onClick = { toggle(EditorPanel.DATE) },
                    modifier = fieldModifier(EditorPanel.DATE),
                ) {
                    FormValue(formatDate(state.occurredAt))
                }

                FormField(
                    label = "시간",
                    active = panel == EditorPanel.TIME,
                    onClick = { toggle(EditorPanel.TIME) },
                    modifier = fieldModifier(EditorPanel.TIME),
                ) {
                    FormValue(formatTime(state.occurredAt))
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }

            Column(
                modifier =
                Modifier
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                AnimatedContent(targetState = panel, label = "editorPanel") { current ->
                    when (current) {
                        EditorPanel.AMOUNT ->
                            PanelBox {
                                NumberKeypad(onDigit = onDigit, onDelete = onDeleteDigit, onClear = onClearAmount)
                            }

                        EditorPanel.CATEGORY ->
                            PanelBox {
                                PickerGrid(
                                    items = state.categories,
                                    selectedId = state.categoryId,
                                    onSelect = { id ->
                                        onSelectCategory(id)
                                        panel = null
                                    },
                                    onAdd = { onOpenAdd(AddTarget.CATEGORY) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                        EditorPanel.PAYMENT ->
                            PanelBox {
                                PickerGrid(
                                    items = state.paymentMethods,
                                    selectedId = state.paymentMethodId,
                                    onSelect = { id ->
                                        onSelectPaymentMethod(id)
                                        panel = null
                                    },
                                    onAdd = { onOpenAdd(AddTarget.PAYMENT) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                        EditorPanel.DATE ->
                            DatePanel(
                                date = BudgetTime.toLocalDate(state.occurredAt),
                                onPick = { date ->
                                    onDateChange(date)
                                    panel = null
                                },
                            )

                        EditorPanel.TIME -> {
                            val time = BudgetTime.toLocalTime(state.occurredAt)
                            TimePanel(hour = time.hour, minute = time.minute, onChange = onTimeChange)
                        }

                        null -> Box(Modifier.fillMaxWidth())
                    }
                }
                state.saveError?.let { error ->
                    ErrorText(
                        text = error,
                        modifier =
                        Modifier
                            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                            .padding(bottom = BudgetTheme.spacing.inlineGap),
                    )
                }
                BudgetPrimaryButton(
                    text = if (state.isEditing) "수정하기" else "등록하기",
                    // 빈 칸이 있어도 누를 수 있다. 누르면 비어 있는 칸을 알려준다.
                    onClick = onSave,
                    modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
                )
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            }
        }
    }
}

/**
 * 키패드와 아이콘 표 입력판의 높이를 맞춘다.
 * 둘 사이를 오갈 때 등록 버튼이 위아래로 출렁이지 않게 하기 위함이다.
 * 달력과 시계는 이 높이에 들어가지 않아서 제 크기대로 둔다.
 */
@Composable
private fun PanelBox(content: @Composable () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.inputPanelHeight)
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(bottom = BudgetTheme.spacing.ctaTopGap),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}

/** 되돌릴 수 없는 삭제로 이어지는 버튼이라 최소 터치 크기(48dp)를 지키는 공용 글자 버튼을 쓴다 */
@Composable
private fun DeleteAction(onClick: () -> Unit) {
    BudgetTextButton(text = "삭제", onClick = onClick, color = BudgetTheme.colors.danger)
}
