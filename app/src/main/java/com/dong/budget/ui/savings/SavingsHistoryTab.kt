package com.dong.budget.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.components.ThisMonthButton
import com.dong.budget.ui.components.YearStepper
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.history.HistoryList
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 내역 탭. 맨 위 해 줄로 해를 고르고, 그 아래 그해 모은 돈과 그해 저축 거래를 달마다 늘어놓는다(내역 화면과 같은 목록).
 * 달 머리에는 그 달 모은 돈('저축 300,000원')을 적고, 그 달을 지나는 동안 위에 붙어 있다. 거래 줄을 누르면 거래 상세.
 * 그해에 저축이 없으면 빈 상태 안내 하나만 둔다(다른 해에도 없으면 저축 화면이 '아직 저축이 없어요' 를 대신 그린다).
 * 찾는 칸은 두지 않는다. 저축 안에서 찾을 것은 많지 않고, 내역 메뉴에서 '저축' 으로 찾으면 모두 나온다.
 *
 * 해를 넘기면 목록을 맨 위(그해 모은 돈)부터 보여 준다. 해마다 달 묶음이 통째로 바뀌어 보던 자리를 이어 갈 수 없다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 목록의 contentPadding 으로 쓴다
 * @param onThisYear 지난해를 보고 있을 때 해 줄 오른쪽에 나오는 '올해'
 */
@Composable
internal fun SavingsHistoryTab(
    state: SavingsUiState,
    contentPadding: PaddingValues,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onThisYear: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = state.history
    Column(modifier = modifier.fillMaxSize()) {
        // 저축은 앞날을 보지 않아서 올해에서는 '다음 해' 를 막는다(통계 탭의 달 줄과 같다)
        YearStepper(
            year = history.year,
            onPreviousYear = onPreviousYear,
            onNextYear = onNextYear,
            nextEnabled = !state.isThisYear,
            trailing = { ThisMonthButton(visible = !state.isThisYear, onClick = onThisYear, text = THIS_YEAR_TEXT) },
        )
        val listState = rememberLazyListState()
        // 거래 상세에 다녀와 이 탭이 다시 그려질 때는 보던 자리를 지키고, 해가 바뀌었을 때만 맨 위로 올린다
        var shownYear by rememberSaveable { mutableIntStateOf(history.year) }
        LaunchedEffect(history.year) {
            if (history.year != shownYear) {
                shownYear = history.year
                listState.scrollToItem(0)
            }
        }
        HistoryList(
            months = history.months,
            today = state.today,
            onOpenTransaction = onOpenTransaction,
            listState = listState,
            contentPadding = contentPadding,
        ) {
            if (history.yearIsEmpty) {
                animatedItem(key = EMPTY_KEY) { StatsEmpty(title = emptyYearTitle(history.year), body = EMPTY_PERIOD_BODY) }
            } else {
                animatedItem(key = SUMMARY_KEY) { HistorySummary(history = history) }
            }
        }
    }
}

/**
 * 머리. 라벨과 큰 숫자(그해 모은 돈), 올해를 볼 때만 그 아래 이번 달 모은 돈.
 * 한 번에 읽는다: "2026년 모은 돈, 1,200,000원, 이번 달 300,000원"
 * 모은 돈은 쓴 돈이 아니라 부호 없이 본문색으로 적는다(꺼낸 돈이 더 많을 때만 "-").
 */
@Composable
private fun HistorySummary(history: SavingsHistory) {
    StatsSection(modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap)) {
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(text = yearTitle(history.year), style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
            Text(text = savedAmount(history.total), style = BudgetTheme.amount.large, color = BudgetTheme.colors.textPrimary)
            history.thisMonth?.let { amount ->
                Text(
                    text = "$THIS_MONTH_LABEL ${savedAmount(amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
        }
    }
}

// 목록 맨 위 줄 key. 달 머리('month-') · 날짜 줄('day-') · 거래 번호와 겹치지 않는다.
private const val SUMMARY_KEY = "summary"
private const val EMPTY_KEY = "empty"
