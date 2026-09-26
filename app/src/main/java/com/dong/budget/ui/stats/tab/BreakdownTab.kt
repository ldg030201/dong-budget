package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.navigation.StatsDetailKey
import com.dong.budget.ui.stats.StatsEmpty
import com.dong.budget.ui.stats.StatsUiState

/** 분류 탭과 결제수단 탭은 같은 틀을 쓴다. 무엇으로 나눠 보는지 */
enum class BreakdownKind {
    /** 분류별. 지출/수입을 바꿔 볼 수 있다. */
    CATEGORY,

    /** 결제수단별. 지출만 본다. */
    PAYMENT,
}

/**
 * 분류 탭과 결제수단 탭. 무엇에(무엇으로) 썼는지 본다.
 * TODO(통계 탭): 자리 값. 머리, 도넛과 범례, 순위 목록, 많이 쓴 곳(분류의 지출만)을 채운다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onOpenDetail 순위 목록의 줄을 누르면 그 분류나 결제수단의 상세
 */
@Composable
fun BreakdownTab(
    state: StatsUiState,
    kind: BreakdownKind,
    contentPadding: PaddingValues,
    onOpenDetail: (StatsDetailKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name =
        when (kind) {
            BreakdownKind.CATEGORY -> "분류"
            BreakdownKind.PAYMENT -> "결제수단"
        }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "placeholder") {
            StatsEmpty(title = "${name}별 통계를 준비하고 있어요", body = "곧 ${state.month.monthValue}월 ${name}별로 쓴 돈을 여기서 볼 수 있어요")
        }
    }
}
