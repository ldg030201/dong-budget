package com.dong.budget.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.history.HistoryList
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.theme.BudgetTheme
import java.time.LocalDate

/**
 * 내역 탭. 맨 위에 지금까지 모은 돈과 이번 달 모은 돈, 그 아래 저축 거래를 달마다 늘어놓는다(내역 화면과 같은 목록).
 * 달 머리에는 그 달 모은 돈('저축 300,000원')을 적고, 그 달을 지나는 동안 위에 붙어 있다. 거래 줄을 누르면 거래 상세.
 * 찾는 칸은 두지 않는다. 저축 안에서 찾을 것은 많지 않고, 내역 메뉴에서 '저축' 으로 찾으면 모두 나온다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 목록의 contentPadding 으로 쓴다
 */
@Composable
internal fun SavingsHistoryTab(
    history: SavingsHistory,
    today: LocalDate,
    contentPadding: PaddingValues,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    HistoryList(
        months = history.months,
        today = today,
        onOpenTransaction = onOpenTransaction,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item(key = SUMMARY_KEY) { HistorySummary(history = history) }
    }
}

/**
 * 머리. 라벨과 큰 숫자(지금까지 모은 돈), 그 아래 이번 달 모은 돈. 한 번에 읽는다: "지금까지 모은 돈, 1,200,000원, 이번 달 300,000원"
 * 모은 돈은 쓴 돈이 아니라 부호 없이 본문색으로 적는다(꺼낸 돈이 더 많을 때만 "-").
 */
@Composable
private fun HistorySummary(history: SavingsHistory) {
    StatsSection(modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap)) {
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
        ) {
            Text(text = TOTAL_LABEL, style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
            Text(text = savedAmount(history.total), style = BudgetTheme.amount.large, color = BudgetTheme.colors.textPrimary)
            Text(
                text = "$THIS_MONTH_LABEL ${savedAmount(history.thisMonth)}",
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
        }
    }
}

// 목록 맨 위 줄 key. 달 머리('month-') · 날짜 줄('day-') · 거래 번호와 겹치지 않는다.
private const val SUMMARY_KEY = "summary"
