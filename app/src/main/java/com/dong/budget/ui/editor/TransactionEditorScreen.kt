package com.dong.budget.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionType
import com.dong.budget.navigation.PrefillSource
import com.dong.budget.ui.category.AddItemSheet
import com.dong.budget.ui.category.AddTarget
import com.dong.budget.ui.category.PickerGrid
import com.dong.budget.ui.category.PickerPreview
import com.dong.budget.ui.components.AnimatedErrorText
import com.dong.budget.ui.components.AnimatedHintText
import com.dong.budget.ui.components.AnimatedInputPanel
import com.dong.budget.ui.components.BringPanelRowIntoView
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
import com.dong.budget.ui.components.InputPanelBox
import com.dong.budget.ui.components.NavButtonStyle
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDate
import com.dong.budget.ui.format.formatKoreanWon
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 화면 아래에 열리는 입력판. 한 번에 하나만 열린다. */
enum class EditorPanel { AMOUNT, CATEGORY, PAYMENT, DATE, TIME }

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
 *
 * @param acceptsTaps '삭제' 를 받아도 되는지. 화면이 올라오는 중에는 false 라서, 아래 화면에서 연달아 누른 탭이 '삭제' 로 새지 않는다.
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
    onSelectPendingPayment: () -> Unit,
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
    acceptsTaps: () -> Boolean = { true },
) {
    // 새로 등록할 때는 금액부터 받으므로 키패드를 열어둔다('금액 키패드 바로 열기' 를 껐으면 닫아둔다).
    // 수정할 때는 값을 먼저 훑어보게 모두 닫아둔다.
    // 알림에서 채워 들어온 경우도 값부터 확인하게 닫아둔다.
    var panel by rememberSaveable {
        mutableStateOf(if (state.isEditing || state.isPrefilled || !state.keypadOnStart) null else EditorPanel.AMOUNT)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun toggle(target: EditorPanel) {
        // 글자 칸에 커서가 있으면 시스템 키보드가 떠 있다. 입력판과 겹치지 않게 먼저 내린다.
        focusManager.clearFocus()
        panel = if (panel == target) null else target
    }

    // 날짜·시간처럼 목록 아래쪽 칸을 누르면 방금 누른 칸이 입력판에 밀려 가려질 수 있어서, 열린 칸이 보이도록 스크롤을 맞춘다.
    val scrollState = rememberScrollState()
    val requesters = remember { EditorPanel.entries.associateWith { BringIntoViewRequester() } }
    BringPanelRowIntoView(panel = panel, scrollState = scrollState, requesters = requesters)

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

    AddItemSheet(
        target = state.addTarget,
        usedColors = state.addTarget?.let(state::usedColors).orEmpty(),
        pickUnusedColor = state.pickUnusedColor,
        error = state.addError,
        onDismiss = onDismissAdd,
        onSubmit = onSubmitAdd,
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onClose,
                title = if (state.isEditing) "거래 수정" else "거래 등록",
                style = NavButtonStyle.CLOSE,
                actions = { if (state.isEditing) DeleteAction(onClick = { if (acceptsTaps()) showDeleteConfirm = true }) },
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
                state.prefillSource?.let { source ->
                    HintText(
                        text =
                        when (source) {
                            PrefillSource.PAYMENT_ALERT -> "결제 알림에서 가져왔어요. 확인하고 등록해 주세요."
                            PrefillSource.PAYDAY -> "월급 설정의 금액으로 채웠어요. 실제로 들어온 금액과 다르면 고쳐 주세요."
                            PrefillSource.FIXED_EXPENSE -> "지난번 고정지출로 채웠어요. 이번 달 금액이나 날짜가 다르면 고쳐 주세요."
                        },
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
                    Column {
                        Text(
                            text = "${formatAmount(state.amount)}원",
                            style = BudgetTheme.amount.large,
                            color = if (state.amountDigits.isEmpty()) BudgetTheme.colors.textTertiary else BudgetTheme.colors.textPrimary,
                        )
                        // 자리가 많으면 몇 원인지 세기 어렵다. 1만원부터 밑에 '1억 2,345만원' 처럼 끊어 적는다.
                        AnimatedHintText(
                            text = state.amount.takeIf { it >= KOREAN_READING_MIN }?.let(::formatKoreanWon),
                            modifier = Modifier.padding(top = BudgetTheme.spacing.tightGap),
                        )
                    }
                }

                FormField(
                    label = "분류",
                    active = panel == EditorPanel.CATEGORY,
                    onClick = { toggle(EditorPanel.CATEGORY) },
                    modifier = fieldModifier(EditorPanel.CATEGORY),
                    error = missingMessage(RequiredField.CATEGORY),
                ) {
                    // 고르거나 바꾸면 칸 값이 겹쳐 바뀐다(지출/수입을 바꿔 분류가 비는 때도)
                    FieldCrossfade(state.selectedCategory) { category ->
                        if (category == null) {
                            FormPlaceholder("분류를 골라주세요")
                        } else {
                            FormIconValue(
                                icon = { CategoryBadge(category.icon, category.color, size = BudgetTheme.size.badgeSmall) },
                                text = category.name,
                            )
                        }
                    }
                }

                FormField(
                    label = "결제수단",
                    active = panel == EditorPanel.PAYMENT,
                    onClick = { toggle(EditorPanel.PAYMENT) },
                    modifier = fieldModifier(EditorPanel.PAYMENT),
                    error = missingMessage(RequiredField.PAYMENT),
                ) {
                    val pendingName = state.pendingPaymentName
                    // 고르거나 바꾸면 칸 값이 겹쳐 바뀐다(새로 추가될 카드 ↔ 있는 결제수단도)
                    FieldCrossfade(state.isPendingPaymentSelected to state.selectedPaymentMethod) { (pendingSelected, method) ->
                        if (pendingSelected && pendingName != null) {
                            // 알림에서 읽은 카드가 아직 결제수단에 없다. 저장할 때 새로 만든다.
                            // 저장할 때 만들어질 모양(아이콘·색)과 똑같이 보여준다
                            FormIconValue(
                                icon = {
                                    CategoryBadge(
                                        PaymentMethodRepository.NEW_CARD_ICON,
                                        state.pendingPaymentColor,
                                        size = BudgetTheme.size.badgeSmall,
                                    )
                                },
                                // 긴 카드 이름이면 두 줄이 된다. 안내가 '(새로 / 추가돼요)' 로 갈라지지 않게 붙는 빈칸을 쓴다.
                                text = "$pendingName (새로\u00A0추가돼요)",
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
                AnimatedInputPanel(panel = panel) { current ->
                    when (current) {
                        EditorPanel.AMOUNT ->
                            InputPanelBox {
                                NumberKeypad(onDigit = onDigit, onDelete = onDeleteDigit, onClear = onClearAmount)
                            }

                        EditorPanel.CATEGORY ->
                            InputPanelBox {
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
                            InputPanelBox {
                                PickerGrid(
                                    items = state.paymentMethods,
                                    selectedId = state.paymentMethodId,
                                    onSelect = { id ->
                                        onSelectPaymentMethod(id)
                                        panel = null
                                    },
                                    onAdd = { onOpenAdd(AddTarget.PAYMENT) },
                                    modifier = Modifier.fillMaxSize(),
                                    // 알림에서 읽은 카드는 저장해야 생기지만, 다른 것을 골랐다가도 되돌아올 수 있게 표에 미리 둔다
                                    preview =
                                    state.pendingPaymentName?.let { name ->
                                        PickerPreview(
                                            name = name,
                                            icon = PaymentMethodRepository.NEW_CARD_ICON,
                                            color = state.pendingPaymentColor,
                                            selected = state.isPendingPaymentSelected,
                                            onSelect = {
                                                onSelectPendingPayment()
                                                panel = null
                                            },
                                        )
                                    },
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
                    }
                }
                AnimatedErrorText(
                    text = state.saveError,
                    modifier =
                    Modifier
                        .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                        .padding(bottom = BudgetTheme.spacing.inlineGap),
                )
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

/** 되돌릴 수 없는 삭제로 이어지는 버튼이라 최소 터치 크기(48dp)를 지키는 공용 글자 버튼을 쓴다 */
@Composable
private fun DeleteAction(onClick: () -> Unit) {
    BudgetTextButton(text = "삭제", onClick = onClick, color = BudgetTheme.colors.danger)
}

/** 금액 밑에 만·억 단위로 끊어 적기 시작하는 금액. 만 원 아래는 위 숫자와 똑같아 적지 않는다. */
private const val KOREAN_READING_MIN = 10_000L

/** 입력칸 값이 바뀔 때 겹쳐 바뀐다. 글자 길이가 달라져도 칸이 한 번에 튀지 않는다. */
@Composable
private fun <T> FieldCrossfade(value: T, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = { (fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick())).using(SizeTransform(clip = false)) },
        contentAlignment = Alignment.CenterStart,
        label = "fieldValue",
    ) { shown -> content(shown) }
}
