package com.dong.budget.data.backup

import com.dong.budget.data.settings.AppSettings
import com.dong.budget.data.settings.AutoOption
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────
// 백업에 담는 앱 설정(app_settings). 다른 폰에 되살려도 화면과 자동 기능이 그대로 따라가게 한다.
//
// 잠금(앱 잠금·월급 잠금의 PIN·지문)은 담지 않는다. 백업은 사람들이 옮겨 두는 평범한 글이라 짧은 PIN 의 해시는 쉽게 풀린다.
// 새 폰에서 다시 켠다. 알림 목록(7일만 두고, 등록한 것은 이미 거래다)과 기기마다 다른 기록(마지막 백업, 권한 안내,
// 월급날 알림을 보냈는지, 개발자 모드)도 담지 않는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 백업의 앱 설정. 값은 enum 이 아니라 설정 저장소에 적는 글 그대로 둔다. 더 새 앱이 만든 모르는 값(새 테마, 새 스위치)이 와도
 * 백업을 거절하지 않고 그 값만 건너뛴다(SettingsRepository.restore). 비어 있는 칸도 되살릴 때 지금 값을 그대로 둔다.
 * @property themeMode 화면 테마(ThemeMode 이름). "SYSTEM" · "LIGHT" · "DARK"
 * @property weekStart 한 주를 시작하는 요일(DayOfWeek 이름). "SUNDAY" · "MONDAY"
 * @property bottomMenu 하단 메뉴 차림. 설정 저장소와 같은 글(BottomMenu.encode, "넣은 칸|뺀 칸")
 * @property autoOptions 자동 기능 스위치. 열쇠(AutoOption.key) → 켰는지. 내보낼 때는 모든 스위치를 적는다.
 * @property autoBackup 자동 백업을 할지와 주기
 */
@Serializable
data class BackupSettings(
    val themeMode: String? = null,
    val weekStart: String? = null,
    val bottomMenu: String? = null,
    val autoOptions: Map<String, Boolean> = emptyMap(),
    val autoBackup: BackupAutoBackup? = null,
)

/**
 * @property on 자동 백업을 하는지
 * @property interval 주기(BackupInterval.key). "daily" · "every_3_days" · "weekly" · "monthly"
 */
@Serializable
data class BackupAutoBackup(val on: Boolean? = null, val interval: String? = null)

/** 지금 설정을 백업에 담는 모양으로. 자동 기능은 켠 스위치도 적는다. 되살리는 폰에서 꺼 둔 스위치도 백업 때처럼 켜지게. */
fun backupSettingsOf(settings: AppSettings): BackupSettings = BackupSettings(
    themeMode = settings.themeMode.name,
    weekStart = settings.weekStart.name,
    bottomMenu = settings.bottomMenu.encode(),
    autoOptions = AutoOption.entries.associate { it.key to settings.auto.chosen(it) },
    autoBackup = BackupAutoBackup(on = settings.backupSchedule.on, interval = settings.backupSchedule.interval.key),
)
