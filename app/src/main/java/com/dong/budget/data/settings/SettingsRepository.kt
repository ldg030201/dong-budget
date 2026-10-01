package com.dong.budget.data.settings

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dong.budget.data.backup.BackupInterval
import com.dong.budget.data.backup.BackupSchedule
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** 앱 설정 저장소. 화면 테마와 자동 기능 스위치([AutoOption]), 자동 백업 주기를 다룬다. */
class SettingsRepository(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val backupOnKey = booleanPreferencesKey("backup_schedule_on")
    private val backupIntervalKey = stringPreferencesKey("backup_schedule_interval")

    private val preferences: Flow<Preferences> =
        context.settingsDataStore.data
            // 저장 파일이 깨졌을 때 앱이 죽지 않고 기본값으로 시작하게 한다.
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }

    val themeMode: Flow<ThemeMode> = preferences.map { preferences -> ThemeMode.fromName(preferences[themeModeKey]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    /**
     * 자동 기능 스위치. 적힌 적 없는 스위치는 켜진 것이다(처음 설치, 새로 생긴 스위치).
     * 테마를 바꿔도 같은 파일이라 다시 내보내므로 값이 같으면 거른다.
     */
    val autoSettings: Flow<AutoSettings> =
        preferences
            .map { preferences -> AutoSettings(AutoOption.entries.filter { preferences[it.preferenceKey] == false }.toSet()) }
            .distinctUntilChanged()

    suspend fun setAuto(option: AutoOption, on: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[option.preferenceKey] = on }
        // 결제를 묻지 않았다거나 분류가 안 골라졌다는 신고를 받으면, 스위치를 끈 탓인지 로그에서 바로 보이게 한다
        DevLog.info(LogTag.SETTINGS, "${option.name} ${if (on) "켬" else "끔"}")
    }

    /** 자동 백업을 할지와 주기. 적힌 적 없으면 기본값(켜짐, 1주마다)이다. */
    val backupSchedule: Flow<BackupSchedule> =
        preferences
            .map { preferences ->
                BackupSchedule(
                    on = preferences[backupOnKey] ?: BackupSchedule.DEFAULT.on,
                    interval = BackupInterval.fromKey(preferences[backupIntervalKey]),
                )
            }.distinctUntilChanged()

    suspend fun setBackupSchedule(schedule: BackupSchedule) {
        context.settingsDataStore.edit { preferences ->
            preferences[backupOnKey] = schedule.on
            preferences[backupIntervalKey] = schedule.interval.key
        }
        DevLog.info(LogTag.SETTINGS, "자동 백업 ${if (schedule.on) "켬 · ${schedule.interval.name}" else "끔"}")
    }

    /** 설정 초기화. 테마는 기기 설정으로, 자동 기능은 모두 켜진 것으로, 자동 백업은 기본값으로 돌아간다(이 파일에는 이것들만 있다). 거래는 건드리지 않는다. */
    suspend fun resetAll() {
        context.settingsDataStore.edit { preferences -> preferences.clear() }
        DevLog.info(LogTag.SETTINGS, "설정 초기화")
    }

    private val AutoOption.preferenceKey: Preferences.Key<Boolean> get() = booleanPreferencesKey(key)
}
