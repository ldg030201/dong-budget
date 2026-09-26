package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.ui.components.TransactionRow
import com.dong.budget.ui.stats.SectionTitle
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsTab
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.calc.emptyMonthText
import com.dong.budget.ui.stats.calc.largestSubtitle
import com.dong.budget.ui.stats.tab.monthly.FlowSection
import com.dong.budget.ui.stats.tab.monthly.IncomeExpenseSection
import com.dong.budget.ui.stats.tab.monthly.InsightsSection
import com.dong.budget.ui.stats.tab.monthly.SummaryHead
import com.dong.budget.ui.stats.tab.monthly.TrendSection
import com.dong.budget.ui.stats.tab.monthly.YearToDateSection
import com.dong.budget.ui.stats.tab.monthly.flowTitle
import com.dong.budget.ui.stats.tab.monthly.largestTitle
import com.dong.budget.ui.stats.tab.monthly.trendTableRows
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

/**
 * 월별 탭. 이번 달 얼마 썼고 수입에 비해 어땠는지 본다.
 *
 * 위에서부터 ① 요약 머리, ② 수입과 지출, ③ 눈에 띄는 점, ④ 흐름(누적 선), ⑤ 최근 6개월(막대와 표),
 * ⑥ 큰 지출, ⑦ 올해 모아 보기. 섹션마다 조건이 맞을 때만 나온다.
 * 이 달에 거래가 없으면(이체 말고) ①~④와 ⑥ 대신 빈 상태 안내를 한 번 두고, ⑤와 ⑦은 조건이 맞으면 그대로 둔다.
 *
 * 섹션 item 은 key 를 고정한다. 달을 바꿔도 스크롤 위치는 그대로 두므로(탭마다 보관) 섹션이 숨었다 나타나도 기준이 흔들리지 않게 한다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onShowMonth 6개월 표의 다른 달 줄을 누르면 통계 전체가 그 달로 바뀐다
 * @param onShowTab '눈에 띄는 점' 의 요일·돈 안 쓴 날 줄을 누르면 일별 탭으로
 * @param onOpenDetail '눈에 띄는 점' 의 분류 줄을 누르면 그 분류 상세
 * @param onOpenTransaction 큰 지출 줄을 누르면 등록창
 */
@Composable
fun MonthlyTab(
    state: StatsUiState,
    contentPadding: PaddingValues,
    onShowMonth: (YearMonth) -> Unit,
    onShowTab: (StatsTab) -> Unit,
    onOpenDetail: (StatsDetailKey) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthly = state.monthly
    // 흐름 차트에서 고른 날. 달을 바꾸면 고르기 전(이번 달 선의 마지막 날을 읽음)으로 돌아간다.
    var flowDay by rememberSaveable(state.month) { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionGap),
    ) {
        if (state.monthIsEmpty) {
            item(key = EMPTY_KEY) {
                val text = emptyMonthText(state.month, state.today)
                StatsEmpty(title = text.title, body = text.description)
            }
        } else {
            item(key = SUMMARY_KEY) {
                SummaryHead(
                    monthly = monthly,
                    month = state.month,
                    today = state.today,
                    // 달 줄 바로 아래 붙지 않게 조금 띄운다
                    modifier = Modifier.padding(top = BudgetTheme.spacing.inlineGap),
                )
            }
            item(key = INCOME_KEY) { IncomeExpenseSection(totals = monthly.totals, period = state.period) }
            if (monthly.insights.isNotEmpty()) {
                item(key = INSIGHTS_KEY) {
                    InsightsSection(
                        insights = monthly.insights,
                        month = state.month,
                        onOpenDetail = onOpenDetail,
                        onShowDaily = { onShowTab(StatsTab.DAILY) },
                    )
                }
            }
            // 아직 오지 않은 달은 흐름이 없다
            monthly.flow?.let { flow ->
                item(key = FLOW_KEY) {
                    FlowSection(
                        title = flowTitle(state.month, state.today),
                        flow = flow,
                        pace = monthly.pace,
                        today = state.today,
                        selectedDay = flowDay,
                        onSelectDay = { flowDay = it },
                    )
                }
            }
        }
        // 기록을 시작하기 전 달을 보고 있으면 표에 줄이 없어 섹션째 뺀다
        if (trendTableRows(monthly.trend).isNotEmpty()) {
            item(key = TREND_KEY) {
                TrendSection(
                    trend = monthly.trend,
                    month = state.month,
                    today = state.today,
                    average = monthly.trendAverage,
                    onShowMonth = onShowMonth,
                )
            }
        }
        if (!state.monthIsEmpty && monthly.largest.isNotEmpty()) {
            item(key = LARGEST_KEY) {
                LargestSection(
                    title = largestTitle(state.month, state.today),
                    items = monthly.largest,
                    onOpenTransaction = onOpenTransaction,
                )
            }
        }
        monthly.yearToDate?.let { ytd ->
            item(key = YEAR_KEY) { YearToDateSection(ytd = ytd) }
        }
    }
}

/**
 * ⑥ 큰 지출. 이 달 지출 중 큰 것 5건을 홈 목록과 같은 거래 줄로 보여 준다.
 * 여러 날이 섞이므로 부제에 시각 대신 날짜를 적는다. 누르면 그 거래의 등록창이 열린다.
 */
@Composable
private fun LargestSection(title: String, items: List<TransactionListItem>, onOpenTransaction: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 거래 줄은 스스로 화면 좌우 여백을 두므로 제목만 여백 안에 넣는다
        SectionTitle(text = title, modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal))
        items.forEach { item ->
            key(item.id) {
                TransactionRow(item = item, onClick = { onOpenTransaction(item.id) }, subtitle = largestSubtitle(item))
            }
        }
    }
}

// 섹션 item key. 달마다 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않게 고정한다.
private const val EMPTY_KEY = "empty"
private const val SUMMARY_KEY = "summary"
private const val INCOME_KEY = "income"
private const val INSIGHTS_KEY = "insights"
private const val FLOW_KEY = "flow"
private const val TREND_KEY = "trend"
private const val LARGEST_KEY = "largest"
private const val YEAR_KEY = "year"
