package com.dong.budget.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.KoreanLocale
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 날짜 입력판. 달력에서 날을 누르면 바로 반영하고 [onPick] 을 부른다.
 *
 * 달력은 날짜를 'UTC 자정의 밀리초' 로 주고받는다. 기기 시간대로 바꾸면
 * 서쪽 시간대 기기에서는 하루 앞날로 바뀌므로 반드시 UTC 로 바꾼다.
 */
@Composable
internal fun DatePanel(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val latestOnPick by rememberUpdatedState(onPick)
    // 달력의 월·요일 이름과 버튼 설명은 기기 언어를 따른다. 어느 기기에서든 같게 보이도록 한국어로 고정한다.
    // 상태도 이 안에서 만들어야 한다. 요일 순서와 날짜 표기가 상태를 만들 때의 언어로 정해진다.
    KoreanLocale {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        LaunchedEffect(state) {
            // 처음 값은 이미 고른 날짜라서 건너뛴다. 이후 바뀐 것만 반영한다.
            snapshotFlow { state.selectedDateMillis }
                .drop(1)
                .filterNotNull()
                .collect { millis -> latestOnPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
        }
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
                colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
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
    // 오전·오후 표시와 시계 읽기 설명을 한국어로 고정한다
    KoreanLocale {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        LaunchedEffect(state) {
            snapshotFlow { state.hour to state.minute }
                .drop(1)
                .collect { (h, m) -> latestOnChange(h, m) }
        }
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
