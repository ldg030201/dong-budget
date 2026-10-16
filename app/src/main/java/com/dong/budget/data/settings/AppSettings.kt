package com.dong.budget.data.settings

import com.dong.budget.data.backup.BackupSchedule
import java.time.DayOfWeek

/** 한 주를 시작할 수 있는 요일. 일요일이나 월요일이고, 설정 화면이 이 순서로 보인다. */
val WEEK_STARTS: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)

/**
 * 앱 설정(app_settings) 한 벌. 백업이 통째로 담고(backupSettingsOf) 되살린다([SettingsRepository.restore]).
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
