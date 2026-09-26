package com.dong.budget.ui.stats.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.StatsUiState
import com.dong.budget.ui.stats.tab.daily.DailySpendingSection
import com.dong.budget.ui.stats.tab.daily.DayRecordsSection
import com.dong.budget.ui.stats.tab.daily.WeekdaySection
import com.dong.budget.ui.stats.tab.daily.focusKey
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 일별 탭. 언제, 어떤 날 쓰는지 본다.
 * 위에서부터 하루 기록(평균·가장 많이 쓴 날·돈 안 쓴 날), 날마다 쓴 돈(분류별로 쌓은 막대와 읽기 판), 요일별 하루 평균이다.
 *
 * 고른 날과 '하나만 보기' 필터는 달마다 처음 상태로 돌아간다. 스크롤 위치는 달을 바꿔도 그대로다(섹션 key 가 고정이다).
 * 고른 날은 사용자가 직접 고르기 전까지 기본 날(이번 달은 오늘, 지나간 달은 가장 많이 쓴 날)을 따라가서,
 * 이번 달을 켜 둔 채 자정이 지나면 새 오늘로 옮겨 간다.
 *
 * @param contentPadding 아래 떠 있는 메뉴에 가리지 않게 LazyColumn 의 contentPadding 으로 쓴다
 * @param onOpenTransaction 읽기 판의 거래 줄을 누르면 등록창
 */
@Composable
fun DailyTab(state: StatsUiState, contentPadding: PaddingValues, onOpenTransaction: (Long) -> Unit, modifier: Modifier = Modifier) {
    val daily = state.daily
    val lastDay = daily.days.size
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 사용자가 고른 날. null 이면 기본 날을 따라간다.
    // 날짜(달 포함)로 들고 있는다. rememberSaveable 은 복원할 때 inputs 를 보지 않아서, 다른 탭에 있는 동안 달이 바뀌었거나
    // 프로세스가 다시 뜬 뒤 옛 달 값이 돌아올 수 있다. 그때는 버린다.
    var pickedDate by rememberSaveable(state.month) { mutableStateOf<LocalDate?>(null) }
    val pickedDay = pickedDate?.takeIf { YearMonth.from(it) == state.month }?.dayOfMonth
    // '하나만 보기' 로 고른 계열. 번호가 아니라 분류를 들고 있어서 거래를 고쳐 순위가 바뀌어도 같은 분류를 따라간다. 달도 함께 들고 있는다(위와 같은 이유).
    var focusPick by rememberSaveable(state.month) { mutableStateOf<Pair<YearMonth, String>?>(null) }
    val focusKey = focusPick?.takeIf { it.first == state.month }?.second
    val selectedDay = (pickedDay ?: daily.defaultDay).coerceIn(1, maxOf(lastDay, 1))
    val focused = focusKey?.let { key -> daily.series.indexOfFirst { it.focusKey() == key }.takeIf { it >= 0 } }
    // 기록을 시작하기 전의 지나간 달은 셀 날이 없다. '아직 지나간 날이 없어요' 나 '2주 넘게 기록하면' 은 맞지 않아서
    // 하루 기록과 요일별 하루 평균을 빼고 보여준다.
    val showRecords = !(daily.counted.isEmpty() && state.period == Period.PAST)
    val spendingIndex = if (showRecords) 1 else 0

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionGap),
    ) {
        if (showRecords) {
            item(key = RECORDS_KEY) {
                DayRecordsSection(
                    month = state.month,
                    daily = daily,
                    // 쌓은 막대가 없으면 차트도 없으니 고를 곳이 없다
                    onShowPeak =
                    if (daily.stackMax > 0) {
                        { day ->
                            pickedDate = state.month.atDay(day)
                            scope.launch { listState.animateScrollToItem(spendingIndex) }
                        }
                    } else {
                        null
                    },
                )
            }
        }
        item(key = SPENDING_KEY) {
            DailySpendingSection(
                state = state,
                selectedDay = selectedDay,
                onSelectDay = { day -> pickedDate = state.month.atDay(day.coerceIn(1, maxOf(lastDay, 1))) },
                focused = focused,
                onFocus = { index -> focusPick = index?.let { daily.series.getOrNull(it)?.focusKey() }?.let { state.month to it } },
                onOpenTransaction = onOpenTransaction,
            )
        }
        if (showRecords) {
            item(key = WEEKDAY_KEY) {
                WeekdaySection(weekday = daily.weekday, noPastDays = daily.counted.isEmpty())
            }
        }
    }
}

// 섹션 줄의 key. 달마다 섹션이 숨었다 나타나도 스크롤 기준이 흔들리지 않게 고정한다.
private const val RECORDS_KEY = "records"
private const val SPENDING_KEY = "spending"
private const val WEEKDAY_KEY = "weekday"
