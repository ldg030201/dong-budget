package com.dong.budget.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.ui.components.KoreanLocale
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.flow.drop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

// 두 입력판 모두 상태를 저장(rememberSaveable)하지 않고 열 때마다 화면의 값에서 새로 만든다.
// 저장하면 앱이 다시 만들어질 때 입력판은 옛 값으로 돌아오는데 칸의 값은 처음부터 다시 읽어서,
// 보이는 시계와 실제로 저장될 시간이 서로 달라진다.

/**
 * 날짜 입력판. 달력에서 날을 누르면 바로 반영하고 [onPick] 을 부른다.
 * 이미 고른 날을 다시 눌러도 부른다. 그래야 그대로 두고 싶을 때도 눌러서 닫을 수 있다.
 *
 * 달력은 날짜를 'UTC 자정의 밀리초' 로 주고받는다. 기기 시간대로 바꾸면
 * 서쪽 시간대 기기에서는 하루 앞날로 바뀌므로 반드시 UTC 로 바꾼다.
 */
@Composable
internal fun DatePanel(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val latestOnPick by rememberUpdatedState(onPick)
    val state =
        remember {
            // 달력은 고른 값이 바뀔 때만 알려준다. 같은 날을 다시 누른 것도 알아야 해서 값을 넣는 순간을 가로챈다.
            val inner =
                DatePickerState(
                    // 요일 순서와 월 표기를 기기 언어와 상관없이 한국어로 고정한다
                    locale = Locale.KOREA,
                    initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                )
            object : DatePickerState by inner {
                override var selectedDateMillis: Long?
                    get() = inner.selectedDateMillis
                    set(value) {
                        inner.selectedDateMillis = value
                        if (value != null) latestOnPick(Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate())
                    }
            }
        }
    // 달력이 '오늘' 테두리를 그릴 때는 기기 시간대를 쓴다. 이 앱의 날짜는 모두 서울 기준이라
    // 해외에서는 테두리가 엉뚱한 날에 그려진다. 두 날짜가 다를 때는 테두리를 그리지 않는다.
    val deviceTodayMatches = LocalDate.now() == LocalDate.now(BudgetTime.ZONE)

    // 달력의 버튼 설명 등 글자도 한국어로 고정한다
    KoreanLocale {
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = BudgetTheme.spacing.ctaTopGap),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 제목과 입력 방식 전환 버튼은 뺀다. 날짜는 위 입력칸에 이미 보이고, 글자로 입력할 일은 없다.
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors =
                if (deviceTodayMatches) {
                    DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                } else {
                    DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        todayDateBorderColor = Color.Transparent,
                        todayContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }
    }
}

/**
 * 시간 입력판. 시계에서 시와 분을 고르는 대로 바로 반영한다.
 * 닫는 버튼은 따로 없다. 다른 칸을 누르거나 등록 버튼을 누르면 된다.
 *
 * 시계(TimePicker)는 아직 실험 단계 API 다. 라이브러리를 올릴 때 모양이나 이름이 바뀌었는지 확인할 것.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePanel(hour: Int, minute: Int, onChange: (hour: Int, minute: Int) -> Unit) {
    val latestOnChange by rememberUpdatedState(onChange)
    val state = remember { TimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false) }
    LaunchedEffect(state) {
        // 처음 값은 이미 칸에 있는 시간이라서 건너뛴다
        snapshotFlow { state.hour to state.minute }
            .drop(1)
            .collect { (h, m) -> latestOnChange(h, m) }
    }
    // 오전·오후 표시와 시계 읽기 설명을 한국어로 고정한다
    KoreanLocale {
        Box(
            // 시계는 위쪽 여백이 없어서 그대로 두면 입력칸 밑줄에 붙는다
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = BudgetTheme.spacing.itemGap, bottom = BudgetTheme.spacing.ctaTopGap),
            contentAlignment = Alignment.TopCenter,
        ) {
            TimePicker(
                state = state,
                colors =
                TimePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    // 기본값은 회색이라 오전·오후 중 무엇이 골라졌는지 잘 안 보인다. 시·분 칸과 같은 색으로 맞춘다.
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
