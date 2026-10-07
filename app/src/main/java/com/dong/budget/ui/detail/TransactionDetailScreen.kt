package com.dong.budget.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.MonthHeader
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.components.animatedItems
import com.dong.budget.ui.components.transactionAmountColor
import com.dong.budget.ui.components.transactionTitle
import com.dong.budget.ui.editor.DeleteTransactionButton
import com.dong.budget.ui.editor.DeleteTransactionDialog
import com.dong.budget.ui.format.formatDate
import com.dong.budget.ui.format.formatDayShort
import com.dong.budget.ui.format.formatSignedAmount
import com.dong.budget.ui.format.formatTime
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.SectionNote
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.stats.calc.Grouping
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 거래 상세. 홈·통계에서 거래 줄을 누르면 열린다.
 *
 * 위에서부터
 * - 상단 바: 뒤로, 오른쪽 위 '삭제'(한 번 더 묻고 지운다)와 '수정'(그 거래의 등록창)
 * - 머리: 분류 뱃지, 가게 이름, 큰 금액
 * - 칸: 분류, 결제수단, 날짜, 시간, 메모(적었을 때만)
 * - 최근 내역: 같은 가게에서 최근 1년 동안 쓴(받은) 거래를 달별로. 지금 보는 거래는 바탕색만 달리하고 누를 수 없다.
 *   다른 거래를 누르면 그 거래의 상세가 위에 열린다.
 *
 * @param onEdit 오른쪽 위 '수정'. 그 거래의 등록창을 연다.
 * @param onDelete '삭제' 를 한 번 더 확인했다. 그 거래를 지운다. 지우면 [TransactionDetailUiState.Gone] 이 되어 이 화면이 닫힌다.
 * @param onOpenTransaction 같은 곳 내역의 다른 거래를 누르면 그 거래의 상세
 * @param acceptsTaps '삭제' 를 받아도 되는지. 화면이 올라오는 중에는 false 라서, 거래 줄을 연달아 누른 탭이 '삭제' 로 새지 않는다.
 */
@Composable
fun TransactionDetailScreen(
    state: TransactionDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    acceptsTaps: () -> Boolean = { true },
) {
    var askDelete by rememberSaveable { mutableStateOf(false) }
    // 지운 거래는 곧 닫히므로 물을 것도 없다
    if (askDelete && state != TransactionDetailUiState.Gone) {
        DeleteTransactionDialog(
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false },
        )
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onBack,
                // 지운 거래는 곧 닫히므로 고치거나 지울 수 없게 한다. '수정' 은 원래 자리(맨 오른쪽)에 두고 '삭제' 를 그 앞에 둔다.
                actions = {
                    if (state != TransactionDetailUiState.Gone) {
                        DeleteTransactionButton(onClick = { if (acceptsTaps()) askDelete = true })
                        BudgetTextButton(text = "수정", onClick = onEdit)
                    }
                },
            )
            // 첫 조회 전에는 비워 둔다. Room 이 바로 주므로 거의 보이지 않는다.
            if (state is TransactionDetailUiState.Shown) DetailContent(state = state, onOpenTransaction = onOpenTransaction)
        }
    }
}

@Composable
private fun DetailContent(state: TransactionDetailUiState.Shown, onOpenTransaction: (Long) -> Unit) {
    val item = state.item
    val samePlace = state.samePlace
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = BudgetTheme.spacing.inlineGap, bottom = BudgetTheme.spacing.sectionGap),
    ) {
        animatedItem(key = HEADER_KEY) { DetailHeader(item) }
        animatedItem(key = FIELDS_KEY) { DetailFields(item = item, state = state) }

        if (samePlace != null) {
            animatedItem(key = SAME_PLACE_KEY) {
                StatsSection(
                    modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap),
                    title = SAME_PLACE_TITLE,
                    subtitle = samePlaceSummary(samePlace),
                ) {
                    if (samePlace.months.isEmpty()) SectionNote(samePlaceEmptyText(samePlace))
                }
            }
            samePlace.months.forEach { group ->
                animatedItem(key = "month-${group.month}") { MonthHeader(month = group.month, today = state.today) }
                animatedItems(items = group.items, key = { "tx-${it.id}" }) { row ->
                    SamePlaceRow(row = row, current = row.id == item.id, onOpenTransaction = onOpenTransaction)
                }
            }
        }
    }
}

/**
 * 머리. 큰 분류 뱃지 아래에 가게 이름과 부호 붙은 큰 금액.
 * 화면 읽기는 이름과 금액을 한 번에 읽고, 상단 바에 제목이 없는 대신 이 머리를 화면 제목(heading)으로 건너뛸 수 있다.
 */
@Composable
private fun DetailHeader(item: TransactionListItem) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        CategoryBadge(icon = item.categoryIcon, color = item.categoryColor, size = BudgetTheme.size.badgeLarge)
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        Text(
            text = transactionTitle(item),
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        Text(
            text = formatSignedAmount(item.type, item.amount),
            style = BudgetTheme.amount.large,
            color = transactionAmountColor(item.type),
        )
    }
}

/** 분류·결제수단·날짜·시간·메모 칸. 메모는 적었을 때만 둔다. */
@Composable
private fun DetailFields(item: TransactionListItem, state: TransactionDetailUiState.Shown) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(top = BudgetTheme.spacing.sectionGap),
    ) {
        FieldRow(
            label = "분류",
            value = item.categoryName ?: Grouping.CATEGORY.noneName,
            badge = item.categoryName?.let { { CategoryBadge(item.categoryIcon, item.categoryColor, size = BudgetTheme.size.badgeSmall) } },
        )
        FieldRow(
            label = "결제수단",
            value = item.paymentMethodName ?: Grouping.PAYMENT_METHOD.noneName,
            badge =
            item.paymentMethodName?.let {
                { CategoryBadge(item.paymentMethodIcon, item.paymentMethodColor, size = BudgetTheme.size.badgeSmall) }
            },
        )
        FieldRow(label = "날짜", value = formatDate(item.occurredAt, state.today))
        FieldRow(label = "시간", value = formatTime(item.occurredAt))
        item.memo?.let { FieldRow(label = "메모", value = it) }
    }
}

/**
 * 칸 한 줄. 왼쪽에 이름, 오른쪽 끝에 값(과 그 앞의 작은 뱃지). 값이 길면(메모) 오른쪽 정렬로 여러 줄이 된다.
 * 화면 읽기는 한 줄을 한 번에 읽는다.
 */
@Composable
private fun FieldRow(label: String, value: String, badge: (@Composable () -> Unit)? = null) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            modifier = Modifier.padding(end = BudgetTheme.spacing.itemGap),
        )
        // 값은 오른쪽 끝에 붙인다. 뱃지는 값 바로 앞에 붙어 함께 움직인다.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badge != null) {
                badge()
                Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = BudgetTheme.colors.textPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * 같은 곳 내역 한 줄. 가게는 모두 같으니 이름 자리에 날짜를 적는다.
 * 지금 보는 거래는 바탕색만 달리하고 누를 수 없다(이미 이 화면이다). 화면 읽기에는 '지금 보는 내역' 이라고 알려 준다.
 */
@Composable
private fun SamePlaceRow(row: TransactionListItem, current: Boolean, onOpenTransaction: (Long) -> Unit) {
    TransactionRow(
        item = row,
        onClick = if (current) null else ({ onOpenTransaction(row.id) }),
        modifier =
        if (current) {
            Modifier
                .background(BudgetTheme.colors.sectionBackground)
                .semantics(mergeDescendants = true) { stateDescription = CURRENT_STATE }
        } else {
            Modifier
        },
        title = formatDayShort(row.localDate()),
    )
}

private const val HEADER_KEY = "header"
private const val FIELDS_KEY = "fields"
private const val SAME_PLACE_KEY = "same-place"
