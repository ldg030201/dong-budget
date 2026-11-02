package com.dong.budget.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.R
import com.dong.budget.data.settings.MenuItem
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.DayHeader
import com.dong.budget.ui.components.FormPlaceholder
import com.dong.budget.ui.components.TabEmptyState
import com.dong.budget.ui.components.TabHeader
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.monthLabel
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.shell.color
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import kotlinx.coroutines.flow.filter
import java.time.LocalDate

/**
 * 내역. 모든 거래를 최근 것부터 달·날짜로 묶어 늘어놓고, 맨 위 입력칸으로 찾는다.
 *
 * - 머리: '내역'(아래 메뉴에 없어 따로 열었으면 왼쪽에 뒤로 가기)
 * - 검색: 가게·메모·분류·결제수단·거래 종류·금액으로 찾는다. 빈칸으로 나눈 낱말이 모두 맞아야 한다([matches]).
 *   찾는 중이면 목록 위에 맞은 건수와 지출·수입 합을 적는다.
 * - 목록: 달 머리(이번 달·9월·2025년 12월, 그 달 지출·수입 합)는 스크롤해도 위에 붙어 있고, 그 아래 날짜 구분선과 거래 줄.
 *   거래 줄을 누르면 거래 상세.
 * 목록을 끌면 키보드를 내린다. 검색어가 바뀌면 맨 위로 올린다.
 *
 * 상단 인셋은 이 화면이, 하단은 셸의 아래 메뉴나 따로 연 틀(MenuPage)이 처리한다.
 *
 * @param query 검색어. 입력칸이 바로 읽는다.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = "내역")
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
        )
        // 첫 조회 전에는 비워 둔다. '아직 거래가 없어요' 가 잠깐 비쳤다가 바뀌지 않게 한다.
        if (!state.loaded) return@Column
        when {
            !state.hasAny ->
                TabEmptyState(
                    iconRes = R.drawable.ic_sym_receipt_long,
                    color = MenuItem.HISTORY.color,
                    title = "아직 거래가 없어요",
                    body = "홈의 거래 등록 버튼(+)으로 남기거나\n결제 알림으로 등록하면 여기에 모여요.",
                )

            state.months.isEmpty() ->
                TabEmptyState(
                    iconRes = R.drawable.ic_sym_receipt_long,
                    color = MenuItem.HISTORY.color,
                    title = "맞는 내역이 없어요",
                    body = "'${state.query.trim()}'(으)로 찾은 거래가 없어요.\n가게 이름이나 금액을 다르게 적어 보세요.",
                )

            else -> HistorySearchList(state = state, onOpenTransaction = onOpenTransaction)
        }
    }
}

/** 내역 탭의 목록. 찾는 중이면 맨 위에 맞은 건수와 합을 적는다. */
@Composable
private fun HistorySearchList(state: HistoryUiState, onOpenTransaction: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // 목록을 끌면 키보드를 내린다. 찾은 것을 훑어볼 때 키보드가 절반을 가리지 않게 한다.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { focusManager.clearFocus() }
    }
    // 검색어가 바뀌면 맨 위부터 보여 준다. 거래 상세에 다녀와 이 화면이 다시 그려질 때는 보던 자리를 지킨다.
    var shownQuery by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.query) {
        if (state.query != shownQuery) {
            shownQuery = state.query
            listState.scrollToItem(0)
        }
    }
    HistoryList(months = state.months, today = state.today, onOpenTransaction = onOpenTransaction, listState = listState) {
        if (state.searching) {
            item(key = SUMMARY_KEY) { SearchSummary(count = state.count, totals = state.totals) }
        }
    }
}

/**
 * 달별 거래 목록. 달 머리(이번 달·9월·2025년 12월, 그 달 합)는 그 달을 지나는 동안 위에 붙어 있고, 그 아래 날짜 구분선과 거래 줄.
 * 거래 줄을 누르면 [onOpenTransaction]. 내역 탭과 저축의 내역 탭이 같이 쓴다.
 *
 * @param contentPadding 목록 둘레 여백. 떠 있는 메뉴 밑에 깔리면 아래를 그만큼(floatingBarClearance) 비운다.
 * @param header 달 묶음 위에 둘 줄(찾은 건수, 모은 돈 요약). key 는 달 머리 · 날짜 줄('month-' · 'day-')과 겹치지 않는 글로 준다.
 */
@Composable
internal fun HistoryList(
    months: List<HistoryMonth>,
    today: LocalDate,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = BudgetTheme.spacing.sectionGap),
    header: LazyListScope.() -> Unit = {},
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        header()
        months.forEach { month ->
            // 달 머리는 그 달을 지나는 동안 위에 붙어 있어 지금 몇 월을 보는지 알 수 있다
            stickyHeader(key = "month-${month.month}") { MonthTitle(month = month, today = today) }
            month.days.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(date = day.date, today = today) }
                // 거래 줄은 날마다 한 번에 넘긴다. 줄마다 하나씩 넘기면 검색어를 칠 때마다 모든 거래의 열쇠를 바로 만든다.
                // 열쇠는 거래 번호(Long)라 다른 줄의 글자 열쇠와 겹치지 않는다.
                items(day.items, key = { it.id }) { item -> TransactionRow(item = item, onClick = { onOpenTransaction(item.id) }) }
            }
        }
    }
}

/** 검색 입력칸. 왼쪽 돋보기, 적은 글이 있으면 오른쪽에 지우기. 키보드의 검색 단추는 키보드만 내린다(적는 대로 찾는다). */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .background(BudgetTheme.colors.sectionBackground, RoundedCornerShape(BudgetTheme.radius.control))
            .padding(start = BudgetTheme.spacing.itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = BudgetTheme.colors.textSecondary,
            modifier = Modifier.size(BudgetTheme.size.icon),
        )
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            SearchPlaceholder(visible = query.isEmpty())
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = BudgetTheme.colors.textPrimary),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                // 안내 글은 따로 그린 글자라 화면 읽기에 이름이 없다. 이름을 직접 붙인다.
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "내역 검색, $SEARCH_PLACEHOLDER" },
            )
        }
        if (query.isNotEmpty()) {
            BudgetIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "검색어 지우기",
                onClick = { onQueryChange("") },
                tint = BudgetTheme.colors.textSecondary,
                iconSize = BudgetTheme.size.iconSmall,
                shape = CircleShape,
            )
        } else {
            Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
        }
    }
}

/** 입력칸이 비었을 때의 안내. 첫 글자를 치면 스르르 빠진다. */
@Composable
private fun SearchPlaceholder(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(Motion.quick()), exit = fadeOut(Motion.quick())) {
        FormPlaceholder(SEARCH_PLACEHOLDER)
    }
}

/** 찾는 중일 때 목록 위 한 줄. "12건 · 지출 -45,000원 · 수입 +3,000원" */
@Composable
private fun SearchSummary(count: Int, totals: Totals) {
    Text(
        text = listOfNotNull("${formatAmount(count.toLong())}건", totalsText(totals)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = BudgetTheme.colors.textSecondary,
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(top = BudgetTheme.spacing.itemGap),
    )
}

/**
 * 달 머리. 왼쪽에 달 이름, 오른쪽에 그 달(찾는 중이면 맞은 거래) 지출·수입·저축 합. 위에 붙어 있는 동안 아래 줄이 비치지 않게 바탕을 칠한다.
 * 화면 읽기가 달마다 건너뛸 수 있게 제목으로 둔다.
 */
@Composable
private fun MonthTitle(month: HistoryMonth, today: LocalDate) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(top = BudgetTheme.spacing.sectionPadding, bottom = BudgetTheme.spacing.tightGap)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthLabel(month.month, today),
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        totalsText(month.totals)?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
        }
    }
}

private const val SUMMARY_KEY = "summary"

private const val SEARCH_PLACEHOLDER = "가게·분류·결제수단·메모·금액으로 찾기"
