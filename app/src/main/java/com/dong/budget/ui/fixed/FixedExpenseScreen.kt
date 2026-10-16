package com.dong.budget.ui.fixed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.R
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetSmallButton
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.MonthStepper
import com.dong.budget.ui.components.TabEmptyState
import com.dong.budget.ui.components.TabHeader
import com.dong.budget.ui.components.ThisMonthButton
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.components.animatedItems
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.editor.fixedExpensePrefill
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.stats.SectionTitle
import com.dong.budget.ui.stats.chart.Meter
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressScaleClickable
import com.dong.budget.ui.theme.slideByDirection
import java.time.LocalDate

/**
 * 고정지출 탭. '고정지출' 분류로 등록한 지출을 가게별로 묶어 얼마 내는지 · 고른 달에 냈는지 · 평소 언제 내는지를 보여 준다.
 *
 * '고정지출' 분류와 탭의 목록은 따로다. 분류로 적은 곳 가운데 고정지출 목록에 등록한 곳만 판에 보이고, 처음 보는 곳은 등록할지 묻는다.
 *
 * 위에서부터
 * - 탭 머리와 달 줄. 이번 달보다 뒤로는 넘기지 않고, 지난 달을 보면 오른쪽에 '이번 달' 이 나온다.
 * - 고정지출로 등록할까요?: '고정지출' 분류로 적었는데 목록에 없는 곳(등록 안 한 뒤 새로 낸 곳 포함). 줄마다 '등록' · '안 함'. 없으면 숨긴다.
 * - 요약: "6개 중 4개 냈어요", 낸 만큼 차는 막대, 낸 돈 · 낼 돈. 목록에 넣은 곳이 아직 없으면 그렇다고만 적는다.
 * - 아직 안 냈어요 · 냈어요 · 이번 달엔 안 내요 · 한동안 안 냈어요(접힌 채 시작). 빈 묶음은 숨긴다.
 *   이번 달에 아직 안 낸 줄에는 지난번 값으로 채운 등록창을 여는 '등록하기' 가 있다.
 *   줄을 누르면 그 가게의 가장 최근 거래 상세가 열린다(거기서 같은 가게의 최근 1년 내역을 본다). 길게 누르면 목록에서 뺄지 묻는다.
 * - 등록 안 한 곳(접힌 채 시작): '안 함' 을 눌렀거나 뺀 곳. 줄마다 '등록' 이 있어 잘못 뺀 것을 되돌린다.
 *
 * 상단 인셋은 이 화면이, 하단은 셸의 아래 메뉴가 처리한다(HomeShell).
 *
 * @param onRegister '등록하기'. 채울 값을 넘긴다. 사용자가 확인하고 저장해야 거래가 된다.
 * @param onOpenTransaction 줄을 누르면 그 가게의 가장 최근 고정지출 거래
 * @param onOpenCategories '고정지출' 분류를 지웠을 때 다시 만들러 분류 관리로
 * @param onRegisterMerchant 묻는 줄 · 등록 안 한 줄의 '등록'. 그곳의 열쇠를 넘긴다.
 * @param onDeclineMerchant 묻는 줄의 '안 함', 판의 줄을 빼기. 그곳의 열쇠를 넘긴다.
 */
@Composable
fun FixedExpenseScreen(
    state: FixedExpenseUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onRegister: (EditorPrefill) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onRegisterMerchant: (String) -> Unit,
    onDeclineMerchant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // '한동안 안 냈어요' · '등록 안 한 곳' 을 펼쳤는지. 잘 안 볼 것들이라 접은 채 시작하고, 달을 넘겨도 사용자가 고른 대로 둔다.
    var stoppedOpen by rememberSaveable { mutableStateOf(false) }
    var declinedOpen by rememberSaveable { mutableStateOf(false) }
    // 빼려고 길게 누른 줄의 열쇠. 확인 창을 띄운다. 그사이 그 줄이 판에서 사라졌으면(거래를 지움 등) 창을 닫고 잊는다.
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val removingItem = remember(removing, state.board) { removing?.let(state.board::itemOf) }
    if (removing != null && removingItem == null && state.loaded) {
        LaunchedEffect(removing) { removing = null }
    }
    // 줄에서 하는 일. 그릴 때마다 새로 만들면 줄들이 다시 그려져서(같은 값으로 보지 못해) 한 번만 만든다.
    val actions =
        remember(onRegister, onOpenTransaction, onRegisterMerchant, onDeclineMerchant) {
            RowActions(
                onRegister = onRegister,
                onOpenTransaction = onOpenTransaction,
                onRemove = { removing = it },
                onRegisterMerchant = onRegisterMerchant,
                onDeclineMerchant = onDeclineMerchant,
            )
        }
    removingItem?.let { item ->
        ConfirmDialog(
            title = removeTitle(item.name),
            message = REMOVE_MESSAGE,
            confirmLabel = REMOVE_CONFIRM,
            // 거래는 그대로이고 '등록 안 한 곳' 에서 되돌릴 수 있어 빨강으로 두지 않는다
            destructive = false,
            onConfirm = {
                removing = null
                onDeclineMerchant(item.key)
            },
            onDismiss = { removing = null },
        )
    }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = FIXED_TAB_TITLE)
        // 첫 계산 전에는 비워 둔다. 빈 상태 안내가 잠깐 보였다 사라지지 않게 한다.
        if (!state.loaded) return@Column
        if (!state.hasCategory) {
            FixedEmpty(title = NO_CATEGORY_TITLE, body = NO_CATEGORY_BODY) {
                BudgetPrimaryButton(text = OPEN_CATEGORIES_TEXT, onClick = onOpenCategories)
            }
            return@Column
        }
        // 고정지출은 앞날을 보지 않아서 이번 달에서는 '다음 달' 을 막는다
        MonthStepper(
            month = state.month,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            nextEnabled = !state.isThisMonth,
            trailing = { ThisMonthButton(visible = !state.isThisMonth, onClick = onThisMonth) },
        )
        // 달이 바뀌면 넘긴 방향으로 한 판이 밀려 바뀐다(홈과 같다). 같은 달 안에서 바뀐 것(등록해서 '냈어요' 로 옮김)은
        // 판을 바꾸지 않고 줄이 제자리로 미끄러진다. 나가는 판이 새 달 내용으로 바뀌지 않게 상태를 통째로 넘기고 달로만 구분한다.
        AnimatedContent(
            targetState = state,
            contentKey = { it.month },
            transitionSpec = { slideByDirection(forward = targetState.month > initialState.month) { it / MONTH_SHIFT_DIVISOR } },
            modifier = Modifier.weight(1f),
            label = "fixedMonth",
        ) { shown ->
            if (shown.isEmpty) {
                FixedEmpty(title = emptyTitle(shown.month, shown.today), body = EMPTY_BODY)
            } else {
                BoardList(
                    state = shown,
                    stoppedOpen = stoppedOpen,
                    declinedOpen = declinedOpen,
                    onToggleStopped = { stoppedOpen = !stoppedOpen },
                    onToggleDeclined = { declinedOpen = !declinedOpen },
                    actions = actions,
                )
            }
        }
    }
}

/**
 * 줄에서 하는 일들.
 * @property onRemove 판의 줄을 길게 눌러 빼려 한다(확인 창을 띄운다). 그곳의 열쇠를 넘긴다.
 */
private class RowActions(
    val onRegister: (EditorPrefill) -> Unit,
    val onOpenTransaction: (Long) -> Unit,
    val onRemove: (String) -> Unit,
    val onRegisterMerchant: (String) -> Unit,
    val onDeclineMerchant: (String) -> Unit,
)

/** [stoppedOpen] · [declinedOpen] 은 접는 두 묶음('한동안 안 냈어요' · '등록 안 한 곳')을 펼쳤는지 */
@Composable
private fun BoardList(
    state: FixedExpenseUiState,
    stoppedOpen: Boolean,
    declinedOpen: Boolean,
    onToggleStopped: () -> Unit,
    onToggleDeclined: () -> Unit,
    actions: RowActions,
) {
    val board = state.board
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.sectionGap),
    ) {
        askGroup(state, actions)
        animatedItem(key = SUMMARY_KEY) { SummaryBlock(state = state) }
        statusGroup(FixedStatus.DUE, board.due, state, actions)
        statusGroup(FixedStatus.PAID, board.paid, state, actions)
        statusGroup(FixedStatus.NOT_THIS_MONTH, board.notThisMonth, state, actions)
        if (board.stopped.isNotEmpty()) {
            animatedItem(key = STOPPED_KEY) {
                FoldHeader(
                    title = STOPPED_TITLE,
                    count = board.stopped.size,
                    expanded = stoppedOpen,
                    onToggle = onToggleStopped,
                )
            }
            if (stoppedOpen) {
                animatedItems(items = board.stopped, key = ::itemKey) { item -> FixedRow(item = item, state = state, actions = actions) }
            }
        }
        if (state.declined.isNotEmpty()) {
            animatedItem(key = DECLINED_KEY) {
                FoldHeader(
                    title = DECLINED_TITLE,
                    count = state.declined.size,
                    expanded = declinedOpen,
                    onToggle = onToggleDeclined,
                )
            }
            if (declinedOpen) {
                animatedItems(items = state.declined, key = { "declined-${it.key}" }) { candidate ->
                    CandidateRow(candidate = candidate, today = state.today, asking = false, actions = actions)
                }
            }
        }
        animatedItem(key = FOOTNOTE_KEY) {
            HintText(
                text = FOOTNOTE,
                modifier =
                Modifier
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                    .padding(top = BudgetTheme.spacing.sectionGap),
            )
        }
    }
}

/**
 * '고정지출로 등록할까요?' 묶음. 제목과 안내, 물을 곳들. 없으면 아무것도 두지 않는다.
 * 줄의 key 가 가게마다 하나라서, '등록' 을 누르면 이 줄이 사라지고 판의 줄이 나타난다.
 */
private fun LazyListScope.askGroup(state: FixedExpenseUiState, actions: RowActions) {
    if (state.candidates.isEmpty()) return
    animatedItem(key = ASK_KEY) {
        Column(modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal)) {
            SectionTitle(text = ASK_TITLE)
            HintText(text = ASK_HINT, modifier = Modifier.padding(top = BudgetTheme.spacing.tightGap))
        }
    }
    animatedItems(items = state.candidates, key = { "ask-${it.key}" }) { candidate ->
        CandidateRow(candidate = candidate, today = state.today, asking = true, actions = actions)
    }
    // 요약 판과 띄운다(판은 위 여백이 없다)
    animatedItem(key = ASK_GAP_KEY) { Spacer(Modifier.height(BudgetTheme.spacing.sectionGap)) }
}

/**
 * 상태 하나의 묶음. 제목과 줄들. 비었으면 아무것도 두지 않는다.
 * 줄의 key 는 가게마다 하나라서, 등록해서 '아직 안 냈어요' 에서 '냈어요' 로 옮겨 가면 그 줄이 제자리로 미끄러진다.
 */
private fun LazyListScope.statusGroup(status: FixedStatus, items: List<FixedExpenseItem>, state: FixedExpenseUiState, actions: RowActions) {
    if (items.isEmpty()) return
    animatedItem(key = "title-${status.name}") {
        // 줄은 스스로 좌우 여백을 가진다(거래 줄과 같다). 제목만 화면 여백 안에 넣는다.
        SectionTitle(
            text = statusTitle(status, state.month, state.today, isStillOpen(state.board, state.month, state.today)),
            modifier =
            Modifier
                .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                .padding(top = BudgetTheme.spacing.sectionGap),
        )
    }
    animatedItems(items = items, key = ::itemKey) { item -> FixedRow(item = item, state = state, actions = actions) }
}

private fun itemKey(item: FixedExpenseItem): String = "item-${item.key}"

/**
 * 요약. 고른 달에 낼 차례였던 것 중 몇 개를 냈는지 문장과 막대로, 그 아래 낸 돈과 (아직) 안 낸 돈.
 * 낼 것이 하나도 없는 달은 문장만 둔다. 화면 읽기는 판 전체를 한 문장으로 읽는다(막대는 꾸밈이다).
 */
@Composable
private fun SummaryBlock(state: FixedExpenseUiState) {
    val board = state.board
    val spoken = summarySpoken(board, state.month, state.today)
    Column(
        modifier =
        Modifier
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .fillMaxWidth()
            // 낼 것이 생기거나 없어져 막대와 금액 줄이 나타나고 사라질 때 판이 한 번에 늘지 않고 펼쳐진다
            .animateContentSize(Motion.standard())
            .sectionBlock()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            text = summaryTitle(state.month, state.today),
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        Text(
            text = countSentence(board, state.month, state.today),
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        if (board.dueCount > 0) {
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            // 낸 개수만큼 찬다. 1 을 넘지 않으므로 경고색으로 바뀌지 않는다.
            Meter(ratio = board.paid.size.toFloat() / board.dueCount)
            Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            Column(verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
                SummaryLine(label = PAID_LABEL, amount = board.paidTotal)
                SummaryLine(label = dueLabel(board, state.month, state.today), amount = board.dueTotal)
            }
        }
    }
}

/** 요약 판의 금액 한 줄. 왼쪽에 이름, 오른쪽 끝에 금액. 좁은 화면에서도 큰 금액이 꺾이지 않게 두 칸으로 나누지 않고 줄로 쌓는다. */
@Composable
private fun SummaryLine(label: String, amount: Long) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = "${formatAmount(amount)}원", style = BudgetTheme.amount.medium, color = BudgetTheme.colors.textPrimary)
    }
}

/**
 * 가게 한 줄. 뱃지(지난번 결제수단, 없으면 분류), 이름, 부제(언제·어느 카드), 덧붙임 한 줄(지났어요·올랐어요 등), 오른쪽에 금액.
 * 이번 달에 아직 안 냈으면 금액 아래에 '등록하기' 를 둔다. 가게 이름 없이 묶인 것은 무엇을 등록할지 몰라 두지 않는다.
 * 길게 누르면 고정지출 목록에서 뺄지 묻는다(지난 거래의 분류는 그대로다).
 *
 * 화면 읽기는 줄을 한 번에 읽고 상태(냈어요·아직 안 냈어요 등)를 덧붙인다. 묶음 제목뿐 아니라 줄마다 상태를 글로 알린다.
 * '등록하기' 는 따로 읽히고, 줄마다 같은 버튼이라 가게 이름을 붙여 "넷플릭스 등록하기" 로 읽는다. 길게 누르기는 '고정지출 목록에서 빼기' 로 알린다.
 */
@Composable
private fun FixedRow(item: FixedExpenseItem, state: FixedExpenseUiState, actions: RowActions) {
    val note = rowNote(item, state.month, state.today)
    val status = rowStatus(item, state.month, state.today)
    val canRegister = item.status == FixedStatus.DUE && state.isThisMonth && item.merchant != null
    // 이번 달 돈이 아닌 줄(안 내는 달, 그만둔 것)은 금액을 흐리게 둔다
    val quiet = item.status == FixedStatus.NOT_THIS_MONTH || item.status == FixedStatus.STOPPED
    BudgetListItem(
        title = item.name,
        subtitle = rowSubtitle(item, state.month),
        modifier = Modifier.semantics { stateDescription = status },
        // 모두 같은 '고정지출' 분류라 분류 뱃지는 줄마다 같다. 어느 카드로 내는지가 더 잘 가려 준다.
        leading = {
            LatestBadge(item.paymentMethodId, item.paymentMethodIcon, item.paymentMethodColor, item.categoryIcon, item.categoryColor)
        },
        // 덧붙임은 두 줄까지 둔다(부제는 한 줄)
        note =
        note?.let { shown ->
            {
                Text(
                    text = shown.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = noteColor(shown.tone),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClickLabel = ROW_CLICK_LABEL,
        onClick = { actions.onOpenTransaction(item.latestId) },
        onLongClick = { actions.onRemove(item.key) },
        onLongClickLabel = REMOVE_ACTION_LABEL,
        trailing = { RowTrailing(item = item, state = state, quiet = quiet, canRegister = canRegister, onRegister = actions.onRegister) },
    )
}

/**
 * 줄의 뱃지. 가장 최근 거래의 결제수단, 없으면(비웠거나 지웠다) 분류. 판의 줄과 묻는 줄이 같이 써서
 * 묻는 줄에서 등록해 판으로 옮겨 가도 아이콘이 그대로다.
 */
@Composable
private fun LatestBadge(
    paymentMethodId: Long?,
    paymentIcon: String?,
    paymentColor: String?,
    categoryIcon: String?,
    categoryColor: String?,
) {
    if (paymentMethodId != null) {
        CategoryBadge(icon = paymentIcon, color = paymentColor)
    } else {
        CategoryBadge(icon = categoryIcon, color = categoryColor)
    }
}

/**
 * 고정지출 목록에 없는 한 곳. 판의 줄과 같은 뱃지 · 이름에, 부제는 마지막으로 낸 날과 결제수단, 오른쪽에 그때 낸 돈.
 * 부제 아래에 '등록' 과([asking] 이면) '안 함' 을 둔다. 버튼 둘을 금액 옆에 두면 좁은 화면(320dp)에서 이름이 너무 줄어 줄을 나눴다.
 * 줄을 누르면 가장 최근 거래 상세가 열린다(판의 줄과 같다).
 *
 * @param asking 묻는 줄이면 true, 등록 안 한 곳의 줄이면 false('안 함' 없이 '등록' 만, 금액을 흐리게, 상태를 '등록 안 함' 으로 알린다)
 */
@Composable
private fun CandidateRow(candidate: FixedCandidate, today: LocalDate, asking: Boolean, actions: RowActions) {
    BudgetListItem(
        title = candidate.name,
        subtitle = candidateSubtitle(candidate, today),
        modifier = if (asking) Modifier else Modifier.semantics { stateDescription = DECLINED_STATE },
        leading = {
            val latest = candidate.latest
            LatestBadge(
                latest.paymentMethodId,
                latest.paymentMethodIcon,
                latest.paymentMethodColor,
                latest.categoryIcon,
                latest.categoryColor,
            )
        },
        note = {
            Row(horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap)) {
                BudgetSmallButton(
                    text = ASK_REGISTER_TEXT,
                    onClick = { actions.onRegisterMerchant(candidate.key) },
                    modifier = Modifier.semantics { contentDescription = askRegisterLabel(candidate.name) },
                    // 판의 '등록하기' 와 같은 연한 남색이다
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (asking) {
                    BudgetSmallButton(
                        text = ASK_DECLINE_TEXT,
                        onClick = { actions.onDeclineMerchant(candidate.key) },
                        modifier = Modifier.semantics { contentDescription = askDeclineLabel(candidate.name) },
                    )
                }
            }
        },
        onClickLabel = ROW_CLICK_LABEL,
        onClick = { actions.onOpenTransaction(candidate.latest.id) },
        trailing = {
            Text(
                text = candidateAmount(candidate),
                style = BudgetTheme.amount.medium,
                color = if (asking) BudgetTheme.colors.textPrimary else BudgetTheme.colors.textSecondary,
            )
        },
    )
}

/** 줄 오른쪽. 금액과, 이번 달에 낼 차례면 그 아래 '등록하기' */
@Composable
private fun RowTrailing(
    item: FixedExpenseItem,
    state: FixedExpenseUiState,
    quiet: Boolean,
    canRegister: Boolean,
    onRegister: (EditorPrefill) -> Unit,
) {
    // 줄과 띄우는 간격은 BudgetListItem 이 둔다
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = rowAmount(item),
            style = BudgetTheme.amount.medium,
            color = if (quiet) BudgetTheme.colors.textSecondary else BudgetTheme.colors.textPrimary,
        )
        if (canRegister) {
            Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
            BudgetSmallButton(
                text = REGISTER_TEXT,
                // 누를 때마다 같은 값이 나와서(평소 날짜·지난번 시각) 연달아 눌러도 등록창이 두 번 쌓이지 않는다
                onClick = { onRegister(fixedExpensePrefill(item, state.today)) },
                modifier = Modifier.semantics { contentDescription = registerLabel(item.name) },
                // 이 탭에서 할 일은 이것 하나라 회색 버튼들과 갈리게 연한 남색으로 둔다(알림 목록의 '모두 읽음' 과 같다)
                container = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 덧붙임 한 줄의 글자색. 지났거나 놓쳤으면 경고색, 오늘이면 브랜드색, 그 밖에는 보조 글자색 */
@Composable
private fun noteColor(tone: NoteTone): Color = when (tone) {
    NoteTone.WARNING -> BudgetTheme.colors.danger
    NoteTone.TODAY -> BudgetTheme.colors.brandText
    NoteTone.PLAIN -> BudgetTheme.colors.textSecondary
}

/**
 * 접는 묶음('한동안 안 냈어요' · '등록 안 한 곳')의 제목. 누르면 줄들을 펼치고 접는다. 오른쪽에 개수와 도는 화살표.
 * 화면 읽기는 제목으로 건너뛸 수 있고, 펼쳐졌는지 접혔는지 알려 준다.
 */
@Composable
private fun FoldHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    // 화살표가 줄이 펼쳐지는 것과 같은 빠르기로 돈다
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) EXPANDED_ROTATION else 0f,
        animationSpec = Motion.standard(),
        label = "foldArrow",
    )
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(top = BudgetTheme.spacing.sectionGap)
            .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onToggle)
            .semantics { stateDescription = if (expanded) EXPANDED_STATE else COLLAPSED_STATE }
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(text = countText(count), style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
        Spacer(Modifier.width(BudgetTheme.spacing.tightGap))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            // 펼침·접힘은 위 상태로 알린다
            contentDescription = null,
            tint = BudgetTheme.colors.textSecondary,
            modifier = Modifier.rotate(arrowRotation),
        )
    }
}

/** 보여 줄 것이 없을 때(분류를 지웠거나 고정지출이 없을 때). 전체 목록의 '고정지출' 줄과 같은 아이콘·색이다. */
@Composable
private fun FixedEmpty(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    TabEmptyState(iconRes = R.drawable.ic_sym_event_repeat, color = EMPTY_BADGE_COLOR, title = title, body = body, action = action)
}

// 목록의 고정 줄 key. 달마다 묶음이 숨었다 나타나도 스크롤 기준이 흔들리지 않게 고정한다.
// 가게 줄의 key('item-' · 'ask-' · 'declined-' 뒤에 가게 열쇠)와 겹치지 않게 그 앞머리로 시작하지 않는다('gap' 이라는 가게가 있어도 된다).
private const val SUMMARY_KEY = "summary"
private const val STOPPED_KEY = "stopped"
private const val DECLINED_KEY = "declined"
private const val ASK_KEY = "ask"
private const val ASK_GAP_KEY = "askGap"
private const val FOOTNOTE_KEY = "footnote"

/** 달을 넘길 때 한 판을 옮기는 거리. 화면 폭의 1/5 만 옮기고 나머지는 흐려짐으로 보여 준다(홈과 같다). */
private const val MONTH_SHIFT_DIVISOR = 5

/** 펼친 묶음의 화살표 각도 */
private const val EXPANDED_ROTATION = 180f

/** 빈 화면 뱃지 색. 전체 목록의 '고정지출' 줄과 같다. */
private const val EMPTY_BADGE_COLOR = "green"
