package com.dong.budget.ui.components

import androidx.compose.runtime.compositionLocalOf
import java.time.DayOfWeek

/**
 * 한 주를 시작하는 요일(설정 > 한 주 시작). 달력·한 주 줄·통계 요일별 줄이 이 요일부터 늘어선다.
 * 앱 전체(DongBudgetApp)가 설정 값을 넣어 준다. 넣지 않은 곳(미리보기·테스트)은 일요일이다.
 */
val LocalWeekStart = compositionLocalOf { DayOfWeek.SUNDAY }
