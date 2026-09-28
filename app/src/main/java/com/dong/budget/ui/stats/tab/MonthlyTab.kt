package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.calc.emptyMonthText
import com.dong.budget.ui.stats.rememberScopedSaveable
import com.dong.budget.ui.stats.tab.monthly.FlowSection
import com.dong.budget.ui.stats.tab.monthly.TrendSection
import com.dong.budget.ui.stats.tab.monthly.YearToDateSection
import com.dong.budget.ui.stats.tab.monthly.flowTitle
import com.dong.budget.ui.stats.tab.monthly.trendTableRows
import com.dong.budget.ui.theme.BudgetTheme
import java.time.YearMonth

/**
 * 월별 탭. 달 단위의 흐름을 자세히 본다.
 *
 * 위에서부터 ① 흐름(누적 선), ② 최근 6개월(막대와 표), ③ 올해 모아 보기. 섹션마다 조건이 맞을 때만 나온다.
 * 쓴 돈·수입과 지출·눈에 띄는 점·큰 지출 같은 요약은 통계 탭에 있다.
 * 이 달에 거래가 없으면(이체 말고) ① 대신 빈 상태 안내를 한 번 두고, ②와 ③은 조건이 맞으면 그대로 둔다.
 *
 * 섹션 item 은 key 를 고정한다. 달을 바꿔도 스크롤 위치는 그대로 두므로(탭마다 보관) 섹션이 숨었다 나타나도 기준이 흔들리지 않게 한다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onShowMonth 6개월 표의 다른 달 줄을 누르면 통계 전체가 그 달로 바뀐다
 */
@Composable
fun MonthlyTab(state: StatsUiState, contentPadding: PaddingValues, onShowMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val monthly = state.monthly
    // 흐름 차트에서 고른 날(일). 달을 바꾸면 고르기 전(이번 달 선의 마지막 날을 읽음)으로 돌아간다.
    var flowDay by rememberScopedSaveable<Int>(state.month)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // 달 줄 바로 아래 붙지 않게 조금 띄운다
        contentPadding = PaddingValues(top = BudgetTheme.spacing.inlineGap, bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionGap),
    ) {
        if (state.monthIsEmpty) {
            animatedItem(key = EMPTY_KEY) {
                val text = emptyMonthText(state.month, state.today)
                StatsEmpty(title = text.title, body = text.description)
            }
        } else {
            // 아직 오지 않은 달은 흐름이 없다
            monthly.flow?.let { flow ->
                animatedItem(key = FLOW_KEY) {
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
            animatedItem(key = TREND_KEY) {
                TrendSection(
                    trend = monthly.trend,
                    month = state.month,
                    today = state.today,
                    average = monthly.trendAverage,
                    onShowMonth = onShowMonth,
                )
            }
        }
        monthly.yearToDate?.let { ytd ->
            animatedItem(key = YEAR_KEY) { YearToDateSection(ytd = ytd) }
        }
    }
}

// 섹션 item key. 달마다 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않게 고정한다.
private const val EMPTY_KEY = "empty"
private const val FLOW_KEY = "flow"
private const val TREND_KEY = "trend"
private const val YEAR_KEY = "year"
