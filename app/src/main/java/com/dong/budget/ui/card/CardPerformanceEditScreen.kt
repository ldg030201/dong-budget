package com.dong.budget.ui.card

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dong.budget.R
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.components.AnimatedInputPanel
import com.dong.budget.ui.components.BringPanelRowIntoView
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.DayOfMonthGrid
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.InputPanelBox
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.settings.SettingsGroup
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable
import kotlinx.coroutines.flow.first

/**
 * 카드 실적 정하기. 카드실적 탭의 '실적 추가' 나 상세의 '수정' 으로 들어온다. 월급 설정과 같은 모양이다.
 * 구간 줄을 누르면 화면 아래에 금액 키패드가, 시작일 줄을 누르면 1~31일 날짜판이 열린다. 바꾸는 대로 바로 저장한다.
 * 실적이 없는 카드로 처음 열면 화면이 다 밀려 들어온 뒤 빈 1구간 줄의 키패드를 연다.
 *
 * @param state 지금 값. 결제수단을 다 읽기 전에는 null 이다.
 * @param onDigit 구간 줄(줄 id)의 금액 키패드 누름. 지움·전체 지움도 같다.
 * @param onAddRow 빈 구간 줄을 더한다. 더한 줄의 id 를 돌려주면 그 키패드를 연다.
 * @param onRemoveRow 구간 줄(줄 id)을 지운다. 지웠으면 true(그 줄의 키패드가 열려 있었으면 닫는다)
 * @param onClear '실적 지우기' 를 확인했을 때. 구간을 비우고 시작일을 1일로 돌린 뒤 화면을 닫는다.
 */
@Composable
fun CardPerformanceEditScreen(
    state: CardPerformanceEditState?,
    onDigit: (rowId: Int, digit: String) -> Unit,
    onDeleteDigit: (rowId: Int) -> Unit,
    onClearAmount: (rowId: Int) -> Unit,
    onAddRow: () -> Int?,
    onRemoveRow: (rowId: Int) -> Boolean,
    onStartDayChange: (Int) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var askClear by rememberSaveable { mutableStateOf(false) }
    if (askClear && state != null) {
        ConfirmDialog(
            title = "${state.card.name} 실적을 지울까요?",
            message = CLEAR_MESSAGE,
            confirmLabel = "지우기",
            onConfirm = {
                askClear = false
                onClear()
            },
            onDismiss = { askClear = false },
        )
    }

    // 열린 입력판. 구간 줄 id 이거나 START_DAY_PANEL. 화면을 돌리거나 프로세스가 죽었다 돌아와도 남게 Int 하나로 둔다.
    // 줄 id 는 화면 모델이 줄 목록과 함께 저장 상태에 두어 되살아난 뒤에도 같은 줄을 가리킨다.
    var panel by rememberSaveable { mutableStateOf<Int?>(null) }
    // 실적이 없는 카드로 처음 열었으면 빈 1구간 줄의 키패드를 연다. 화면을 돌리거나 되살아난 뒤에는 다시 열지 않는다.
    var started by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val loaded = state != null
    LaunchedEffect(loaded) {
        val first = state ?: return@LaunchedEffect
        if (started) return@LaunchedEffect
        panel =
            startPanel(panel = { panel }, keypadRow = first.startKeypadRow) {
                // 화면이 다 밀려 들어와야(RESUMED) 고침을 받는다(DongBudgetApp 의 settled). 그 전에 연 키패드를 누르면 숫자가 버려진다.
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            }
        started = true
    }
    val rows = state?.rows.orEmpty()
    // 지운 줄의 입력판은 닫힌 것으로 본다
    val shown = panel?.takeIf { open -> open == START_DAY_PANEL || rows.any { it.id == open } }

    fun toggle(target: Int) {
        panel = if (shown == target) null else target
    }

    // 입력판이 열려 있으면 뒤로가기는 화면이 아니라 입력판을 닫는다
    BackHandler(enabled = shown != null) { panel = null }

    // 아래쪽 줄을 누르면 열린 입력판에 가려질 수 있다. 입력판 높이가 멈춘 뒤 누른 줄이 보이게 올린다.
    val scrollState = rememberScrollState()
    // 줄 id 는 줄을 더할 때마다 늘어서 구간 줄의 것은 줄마다 그려질 때 넣고 지워질 때 뺀다
    val requesters = remember { mutableMapOf(START_DAY_PANEL to BringIntoViewRequester()) }
    BringPanelRowIntoView(panel = shown, scrollState = scrollState, requesters = requesters)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = state?.let { "${it.card.name} 실적" })
            if (state == null) return@Column

            // 입력판이 열린 줄은 값을 브랜드색으로 칠해 무엇을 고치는 중인지 보인다
            val activeColor = BudgetTheme.colors.brandText
            val idleColor = BudgetTheme.colors.textSecondary

            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                // 줄을 더하거나 지우면 판이 한 번에 늘고 줄지 않게 부드럽게 맞춘다
                SettingsGroup("실적 구간", modifier = Modifier.animateContentSize(Motion.standard())) {
                    val amounts = state.amounts
                    rows.forEachIndexed { index, row ->
                        key(row.id) {
                            // 입력판이 열린 줄을 끌어올릴 수 있게 줄 id 로 찾아 둔다
                            val requester = remember { BringIntoViewRequester() }
                            DisposableEffect(requester) {
                                requesters[row.id] = requester
                                onDispose { requesters.remove(row.id) }
                            }
                            TierEditRow(
                                index = index,
                                amount = row.amount,
                                valueColor = if (shown == row.id) activeColor else idleColor,
                                onClick = { toggle(row.id) },
                                onRemove = { if (onRemoveRow(row.id) && panel == row.id) panel = null },
                                modifier = Modifier.bringIntoViewRequester(requester),
                            )
                            tierRowHint(amounts, index)?.let {
                                HintText(it, modifier = Modifier.padding(bottom = BudgetTheme.spacing.tightGap))
                            }
                        }
                    }
                    if (state.canAddRow) {
                        AddTierRow(onClick = { onAddRow()?.let { panel = it } })
                    } else {
                        HintText(TIERS_FULL_HINT, modifier = Modifier.padding(vertical = BudgetTheme.spacing.inlineGap))
                    }
                    HintText(TIERS_HINT, modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap))
                }

                SettingsGroup("실적 기간") {
                    ActionRow(
                        title = "시작일",
                        value = startDayValue(state.startDay),
                        valueColor = if (shown == START_DAY_PANEL) activeColor else idleColor,
                        onClick = { toggle(START_DAY_PANEL) },
                        modifier = Modifier.bringIntoViewRequester(requesters.getValue(START_DAY_PANEL)),
                    )
                    HintText(startDayHint(state.today, state.startDay), modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap))
                }

                // 적은 것이 없으면 지울 것도 없다
                if (state.hasPerformance) {
                    SettingsGroup(title = null) {
                        ActionRow(
                            title = "실적 지우기",
                            description = CLEAR_DESCRIPTION,
                            onClick = {
                                panel = null
                                askClear = true
                            },
                            danger = true,
                        )
                    }
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }

            // 닫히며 내려가거나 다른 입력판으로 바뀌며 사라지는 판도 그동안 누름을 받는다. 누르는 순간 열린 입력판을 읽어
            // 그 판일 때만 받는다. 열린 줄을 지운 뒤 내려가는 키패드나, 키패드로 바뀌며 사라지는 날짜판의 칸이 눌려 값이 바뀌지 않게 한다.
            AnimatedInputPanel(panel = shown, modifier = Modifier.navigationBarsPadding()) { current ->
                if (current == START_DAY_PANEL) {
                    InputPanelBox {
                        DayOfMonthGrid(
                            selected = state.startDay,
                            onPick = { day ->
                                if (panelAccepts(panel, current)) {
                                    onStartDayChange(day)
                                    panel = null
                                }
                            },
                            describe = ::startDayCellDescription,
                        )
                    }
                } else {
                    InputPanelBox {
                        NumberKeypad(
                            onDigit = { if (panelAccepts(panel, current)) onDigit(current, it) },
                            onDelete = { if (panelAccepts(panel, current)) onDeleteDigit(current) },
                            onClear = { if (panelAccepts(panel, current)) onClearAmount(current) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 구간 한 줄. 왼쪽 '1구간', 오른쪽 금액(안 적었으면 '정해 주세요')과 지우기 버튼.
 * 누르면 금액 키패드를 열고, 지우기 버튼은 화면 읽기에 '1구간 지우기' 처럼 어느 줄의 것인지 읽힌다.
 */
@Composable
private fun TierEditRow(
    index: Int,
    amount: Long,
    valueColor: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ActionRow(
            title = tierOrder(index),
            value = tierRowValue(amount),
            valueColor = valueColor,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
        BudgetIconButton(
            icon = Icons.Filled.Close,
            contentDescription = deleteTierLabel(index),
            onClick = onRemove,
            tint = BudgetTheme.colors.textSecondary,
            iconSize = BudgetTheme.size.iconSmall,
        )
    }
}

/** '구간 추가' 줄. 맨 아래에 빈 구간 줄을 더하고 그 키패드를 연다. */
@Composable
private fun AddTierRow(onClick: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onClick)
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.inlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_sym_add),
            // 옆 글이 무엇을 하는지 말한다
            contentDescription = null,
            tint = BudgetTheme.colors.brandText,
            modifier = Modifier.size(BudgetTheme.size.iconSmall),
        )
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(text = "구간 추가", style = MaterialTheme.typography.bodyLarge, color = BudgetTheme.colors.brandText)
    }
}

private const val CLEAR_MESSAGE = "적은 구간을 모두 지우고 시작일을 매달 1일로 돌려요. 가계부에 등록한 거래는 그대로예요."

private const val CLEAR_DESCRIPTION = "적은 구간과 시작일을 지워요. 가계부 거래는 그대로예요"
