package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.stats.SectionTitle
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.calc.emptyMonthText
import com.dong.budget.ui.stats.calc.largestSubtitle
import com.dong.budget.ui.stats.tab.daily.DayRecordsSection
import com.dong.budget.ui.stats.tab.daily.showsDayRecords
import com.dong.budget.ui.stats.tab.monthly.IncomeExpenseSection
import com.dong.budget.ui.stats.tab.monthly.InsightsSection
import com.dong.budget.ui.stats.tab.monthly.SummaryHead
import com.dong.budget.ui.stats.tab.monthly.largestTitle
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.launch

/**
 * 통계 탭(한눈에 보기). 아래 메뉴의 '통계' 로 들어오면 처음 보는 탭이다.
 *
 * 위에서부터 ① 요약 머리(쓴 돈과 지난달 비교), ② 수입과 지출, ③ 하루 기록, ④ 눈에 띄는 점, ⑤ 큰 지출.
 * 더 자세한 것은 월별(흐름·최근 6개월·올해 모아 보기), 일별(날마다 쓴 돈·요일별 하루 평균), 분류, 결제수단 탭에서 본다.
 * 섹션마다 조건이 맞을 때만 나온다. 이 달에 거래가 없으면(이체 말고) 빈 상태 안내 하나만 둔다.
 *
 * 섹션 item 은 key 를 고정한다. 달을 바꿔도 스크롤 위치는 그대로 두므로(탭마다 보관) 섹션이 숨었다 나타나도 기준이 흔들리지 않게 한다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onRequestDaily 하루 기록의 '가장 많이 쓴 날' 이나 눈에 띄는 점의 요일 줄을 누르면 일별 탭의 그곳으로
 * @param onOpenDetail 눈에 띄는 점의 분류 줄을 누르면 그 분류 상세
 * @param onOpenTransaction 큰 지출 줄을 누르면 거래 상세
 */
@Composable
fun OverviewTab(
    state: StatsUiState,
    contentPadding: PaddingValues,
    onRequestDaily: (DailyRequest) -> Unit,
    onOpenDetail: (StatsDetailKey) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthly = state.monthly
    val daily = state.daily
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showRecords = state.showsDayRecords()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionGap),
    ) {
        if (state.monthIsEmpty) {
            animatedItem(key = EMPTY_KEY) {
                val text = emptyMonthText(state.month, state.today)
                StatsEmpty(title = text.title, body = text.description)
            }
            return@LazyColumn
        }
        animatedItem(key = SUMMARY_KEY) {
            SummaryHead(
                monthly = monthly,
                month = state.month,
                today = state.today,
                // 달 줄 바로 아래 붙지 않게 조금 띄운다
                modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap),
            )
        }
        animatedItem(key = INCOME_KEY) { IncomeExpenseSection(totals = monthly.totals, period = state.period) }
        if (showRecords) {
            animatedItem(key = RECORDS_KEY) {
                DayRecordsSection(
                    month = state.month,
                    daily = daily,
                    // 쌓은 막대가 없으면 일별 차트도 없으니 가서 고를 날이 없다
                    onShowPeak = if (daily.stackMax > 0) ({ day -> onRequestDaily(DailyRequest.Day(day)) }) else null,
                )
            }
        }
        if (monthly.insights.isNotEmpty()) {
            animatedItem(key = INSIGHTS_KEY) {
                InsightsSection(
                    insights = monthly.insights,
                    month = state.month,
                    onOpenDetail = onOpenDetail,
                    onShowWeekdays = { onRequestDaily(DailyRequest.Weekdays) },
                    // 돈 안 쓴 날은 바로 위 하루 기록에 있다. 이 줄은 센 날이 일주일 넘을 때만 생겨서 하루 기록도 늘 함께 있다.
                    onShowNoSpend = { scope.launch { listState.animateScrollToItem(RECORDS_INDEX) } },
                )
            }
        }
        if (monthly.largest.isNotEmpty()) {
            animatedItem(key = LARGEST_KEY) {
                LargestSection(
                    title = largestTitle(state.month, state.today),
                    items = monthly.largest,
                    onOpenTransaction = onOpenTransaction,
                )
            }
        }
    }
}

/**
 * ⑤ 큰 지출. 이 달 지출 중 큰 것 5건을 홈 목록과 같은 거래 줄로 보여 준다.
 * 여러 날이 섞이므로 부제에 시각 대신 날짜를 적는다. 누르면 그 거래의 상세가 열린다.
 */
@Composable
private fun LargestSection(title: String, items: List<TransactionListItem>, onOpenTransaction: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 거래 줄은 스스로 화면 좌우 여백을 두므로 제목만 여백 안에 넣는다
        SectionTitle(text = title, modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal))
        items.forEach { item ->
            key(item.id) {
                TransactionRow(item = item, onClick = { onOpenTransaction(item.id) }, subtitle = largestSubtitle(item), colorExpense = true)
            }
        }
    }
}

// 섹션 item key. 달마다 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않게 고정한다.
private const val EMPTY_KEY = "empty"
private const val SUMMARY_KEY = "summary"
private const val INCOME_KEY = "income"
private const val RECORDS_KEY = "records"
private const val INSIGHTS_KEY = "insights"
private const val LARGEST_KEY = "largest"

/** 하루 기록의 줄 번호. 요약 머리(0), 수입과 지출(1) 다음이다. 빈 달에는 하루 기록이 없다. */
private const val RECORDS_INDEX = 2
