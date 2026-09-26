package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsUiState

/**
 * 일별 탭. 언제, 어떤 날 쓰는지 본다.
 * TODO(통계 탭): 자리 값. 하루 기록, 날마다 쓴 돈(분류별로 쌓은 막대와 읽기 판), 요일별 하루 평균을 채운다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onOpenTransaction 읽기 판의 거래 줄을 누르면 등록창
 */
@Composable
fun DailyTab(state: StatsUiState, contentPadding: PaddingValues, onOpenTransaction: (Long) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "placeholder") {
            StatsEmpty(title = "일별 통계를 준비하고 있어요", body = "곧 ${state.month.monthValue}월 날마다 쓴 돈을 여기서 볼 수 있어요")
        }
    }
}
