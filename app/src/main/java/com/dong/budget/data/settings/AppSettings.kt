package com.dong.budget.data.settings

import com.dong.budget.data.backup.BackupInterval
import com.dong.budget.data.backup.BackupSchedule
import java.time.DayOfWeek

/** 한 주를 시작할 수 있는 요일. 일요일이나 월요일이다. */
val WEEK_STARTS: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)

/**
 * 앱 설정(app_settings) 한 벌. 백업이 통째로 담는다([SettingsRepository.current]).
 * 기본값은 처음 설치했을 때(설정 초기화한 때)와 같다.
 * @property auto 자동 기능 스위치. 사용자가 고른 그대로다(기대는 스위치가 꺼져 있어도 고른 값은 남는다).
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    val bottomMenu: BottomMenu = BottomMenu.DEFAULT,
    val auto: AutoSettings = AutoSettings(),
    val backupSchedule: BackupSchedule = BackupSchedule.DEFAULT,
)

/**
 * 백업에서 되살릴 설정([SettingsRepository.restore]). 비어 있는(null) 칸과 [auto] 에 없는 스위치는 지금 값을 그대로 둔다.
 * 백업이 모르던 것(옛 앱에 없던 스위치)과 이 앱이 모르는 값(더 새 앱의 테마 등)이 그렇게 빠진다.
 * @property auto 바꿀 스위치 → 켤지
 */
data class SettingsRestore(
    val themeMode: ThemeMode? = null,
    val weekStart: DayOfWeek? = null,
    val bottomMenu: BottomMenu? = null,
    val auto: Map<AutoOption, Boolean> = emptyMap(),
    val backupOn: Boolean? = null,
    val backupInterval: BackupInterval? = null,
)
