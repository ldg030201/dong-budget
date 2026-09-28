package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.rememberScopedSaveable
import com.dong.budget.ui.stats.tab.daily.DailySpendingSection
import com.dong.budget.ui.stats.tab.daily.WeekdaySection
import com.dong.budget.ui.stats.tab.daily.focusKey
import com.dong.budget.ui.stats.tab.daily.showsDayRecords
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.flow.first

/** 통계 탭에서 일별 탭의 어디를 보여 달라고 한 것. 일별 탭이 받아 처리하고 비운다. */
sealed interface DailyRequest {
    /** 날마다 쓴 돈에서 그날(일)을 골라 보여 준다(하루 기록의 '가장 많이 쓴 날') */
    data class Day(val day: Int) : DailyRequest

    /** 요일별 하루 평균으로 내려간다(눈에 띄는 점의 요일 줄) */
    data object Weekdays : DailyRequest
}

/**
 * 일별 탭. 언제, 어떤 날 쓰는지 자세히 본다.
 * 위에서부터 날마다 쓴 돈(분류별로 쌓은 막대와 읽기 판), 요일별 하루 평균이다. 하루 기록(평균·가장 많이 쓴 날·돈 안 쓴 날)은 통계 탭에 있다.
 *
 * 고른 날과 '하나만 보기' 필터는 달마다 처음 상태로 돌아간다. 스크롤 위치는 달을 바꿔도 그대로다(섹션 key 가 고정이다).
 * 고른 날은 사용자가 직접 고르기 전까지 기본 날(이번 달은 오늘, 지나간 달은 가장 많이 쓴 날)을 따라가서,
 * 이번 달을 켜 둔 채 자정이 지나면 새 오늘로 옮겨 간다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param request 통계 탭이 보여 달라고 한 곳. 처리하면 [onRequestHandled] 를 부른다.
 * @param onOpenTransaction 읽기 판의 거래 줄을 누르면 거래 상세
 */
@Composable
fun DailyTab(
    state: StatsUiState,
    contentPadding: PaddingValues,
    request: DailyRequest?,
    onRequestHandled: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = state.daily
    val lastDay = daily.days.size
    val listState = rememberLazyListState()
    // 사용자가 고른 날(일). null 이면 기본 날을 따라간다. 달이 바뀌면 처음으로 돌아간다.
    var pickedDay by rememberScopedSaveable<Int>(state.month)
    // '하나만 보기' 로 고른 계열. 번호가 아니라 분류를 들고 있어서 거래를 고쳐 순위가 바뀌어도 같은 분류를 따라간다.
    var focusKey by rememberScopedSaveable<String>(state.month)
    val selectedDay = (pickedDay ?: daily.defaultDay).coerceIn(1, maxOf(lastDay, 1))
    val focused = focusKey?.let { key -> daily.series.indexOfFirst { it.focusKey() == key }.takeIf { it >= 0 } }
    val showWeekdays = state.showsDayRecords()

    // 통계 탭에서 넘어오며 부탁한 곳을 보여 준다. 탭이 바뀌며 목록이 막 그려지는 중이라 줄이 놓인 뒤에 스크롤한다.
    LaunchedEffect(request) {
        val target = request ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        when (target) {
            is DailyRequest.Day -> {
                pickedDay = target.day.coerceIn(1, maxOf(lastDay, 1))
                listState.animateScrollToItem(SPENDING_INDEX)
            }

            DailyRequest.Weekdays -> if (showWeekdays) listState.animateScrollToItem(WEEKDAY_INDEX)
        }
        onRequestHandled()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // 달 줄 바로 아래 붙지 않게 조금 띄운다
        contentPadding = PaddingValues(top = BudgetTheme.spacing.inlineGap, bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionGap),
    ) {
        animatedItem(key = SPENDING_KEY, slide = false) {
            DailySpendingSection(
                state = state,
                selectedDay = selectedDay,
                onSelectDay = { day -> pickedDay = day.coerceIn(1, maxOf(lastDay, 1)) },
                focused = focused,
                onFocus = { index -> focusKey = index?.let { daily.series.getOrNull(it)?.focusKey() } },
                onOpenTransaction = onOpenTransaction,
            )
        }
        if (showWeekdays) {
            animatedItem(key = WEEKDAY_KEY, slide = false) {
                WeekdaySection(weekday = daily.weekday, noPastDays = daily.counted.isEmpty())
            }
        }
    }
}

// 섹션 줄의 key. 달마다 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않게 고정한다.
private const val SPENDING_KEY = "spending"
private const val WEEKDAY_KEY = "weekday"
private const val SPENDING_INDEX = 0
private const val WEEKDAY_INDEX = 1
