package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsTab
import com.dong.budget.ui.stats.StatsUiState
import java.time.YearMonth

/**
 * 월별 탭. 이번 달 얼마 썼고 수입에 비해 어땠는지 본다.
 * TODO(통계 탭): 자리 값. 요약, 수입과 지출, 눈에 띄는 점, 흐름, 최근 6개월, 큰 지출, 올해 모아 보기를 채운다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onShowMonth 6개월 표의 다른 달 줄을 누르면 통계 전체가 그 달로 바뀐다
 * @param onShowTab '눈에 띄는 점' 의 요일·돈 안 쓴 날 줄을 누르면 일별 탭으로
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
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "placeholder") {
            StatsEmpty(title = "월별 통계를 준비하고 있어요", body = "곧 ${state.month.monthValue}월 요약을 여기서 볼 수 있어요")
        }
    }
}
