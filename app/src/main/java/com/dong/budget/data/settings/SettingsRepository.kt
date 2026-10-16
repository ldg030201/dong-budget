package com.dong.budget.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * 앱 설정 저장소. 화면 테마와 자동 기능 스위치([AutoOption]), 자동 백업 주기, 아래 메뉴 차림을 다룬다.
 * 백업은 이 파일의 설정을 통째로 담고 되살린다([current], [restore]).
 * @param store 설정 파일. 테스트는 임시 파일을 넘긴다.
 */
class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val weekStartKey = stringPreferencesKey("week_start")
    private val backupOnKey = booleanPreferencesKey("backup_schedule_on")
    private val backupIntervalKey = stringPreferencesKey("backup_schedule_interval")
    private val bottomMenuKey = stringPreferencesKey("bottom_menu")

    private val preferences: Flow<Preferences> =
        store.data
            // 저장 파일이 깨졌을 때 앱이 죽지 않고 기본값으로 시작하게 한다.
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }

    val themeMode: Flow<ThemeMode> = preferences.map { it.themeMode() }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    /**
     * 자동 기능 스위치. 적힌 적 없는 스위치는 켜진 것이다(처음 설치, 새로 생긴 스위치).
     * 테마를 바꿔도 같은 파일이라 다시 내보내므로 값이 같으면 거른다.
     */
    val autoSettings: Flow<AutoSettings> = preferences.map { it.autoSettings() }.distinctUntilChanged()

    suspend fun setAuto(option: AutoOption, on: Boolean) {
        store.edit { preferences -> preferences[option.preferenceKey] = on }
        // 결제를 묻지 않았다거나 분류가 안 골라졌다는 신고를 받으면, 스위치를 끈 탓인지 로그에서 바로 보이게 한다
        DevLog.info(LogTag.SETTINGS, "${option.name} ${if (on) "켬" else "끔"}")
    }

    /** 한 주를 시작하는 요일. 일요일이나 월요일이고, 적힌 적 없으면 일요일이다. */
    val weekStart: Flow<DayOfWeek> = preferences.map { it.weekStart() }.distinctUntilChanged()

    suspend fun setWeekStart(day: DayOfWeek) {
        require(day in WEEK_STARTS) { "한 주는 일요일이나 월요일에 시작한다." }
        store.edit { preferences -> preferences[weekStartKey] = day.name }
    }

    /** 자동 백업을 할지와 주기. 적힌 적 없으면 기본값(켜짐, 1주마다)이다. */
    val backupSchedule: Flow<BackupSchedule> = preferences.map { it.backupSchedule() }.distinctUntilChanged()

    suspend fun setBackupSchedule(schedule: BackupSchedule) {
        store.edit { preferences ->
            preferences[backupOnKey] = schedule.on
            preferences[backupIntervalKey] = schedule.interval.key
        }
        DevLog.info(LogTag.SETTINGS, "자동 백업 ${if (schedule.on) "켬 · ${schedule.interval.name}" else "끔"}")
    }

    /** 아래 메뉴 차림. 적힌 적 없으면 처음 차림([BottomMenu.DEFAULT])이다. */
    val bottomMenu: Flow<BottomMenu> = preferences.map { it.bottomMenu() }.distinctUntilChanged()

    suspend fun setBottomMenu(menu: BottomMenu) {
        store.edit { preferences -> preferences[bottomMenuKey] = menu.encode() }
        DevLog.info(LogTag.SETTINGS, "아래 메뉴 ${menu.items.joinToString(" · ") { it.key }}")
    }

    /**
     * 지금 설정 한 벌. 백업이 담는다. 저장소를 한 번만 읽어 칸끼리 어긋나지 않는다.
     * 설정 파일을 읽지 못하면 null 이다. 화면처럼 기본값으로 채우면 백업이 기본 설정을 담고, 그 백업을 되살릴 때 쓰던 설정을 기본값으로 덮어쓴다.
     */
    suspend fun current(): AppSettings? = try {
        store.data.first().let { AppSettings(it.themeMode(), it.weekStart(), it.bottomMenu(), it.autoSettings(), it.backupSchedule()) }
    } catch (e: IOException) {
        DevLog.warn(LogTag.SETTINGS, "설정 파일을 읽지 못해 백업에 설정을 담지 않아요", e)
        null
    }

    /**
     * 백업의 설정으로 바꾼다. 한 번에 적어서 중간에 실패하면 아무것도 바뀌지 않는다. [settings] 에 없는 것은 그대로 둔다.
     * 자동 백업을 켜도 여기서 바로 백업하지 않는다. 주기가 됐는지는 앱을 열 때만 본다(ScheduledBackup).
     */
    suspend fun restore(settings: SettingsRestore) {
        settings.weekStart?.let { require(it in WEEK_STARTS) { "한 주는 일요일이나 월요일에 시작한다." } }
        store.edit { preferences ->
            settings.themeMode?.let { preferences[themeModeKey] = it.name }
            settings.weekStart?.let { preferences[weekStartKey] = it.name }
            settings.bottomMenu?.let { preferences[bottomMenuKey] = it.encode() }
            settings.auto.forEach { (option, on) -> preferences[option.preferenceKey] = on }
            settings.backupOn?.let { preferences[backupOnKey] = it }
            settings.backupInterval?.let { preferences[backupIntervalKey] = it.key }
        }
        // 설정이 이상하다는 신고를 받으면 복원 때 무엇이 바뀌었는지 보려고 남긴다. 그대로 둔 칸은 '-' 다.
        val off = settings.auto.filterValues { !it }.keys.joinToString(",") { it.name }.ifEmpty { "없음" }
        DevLog.info(
            LogTag.SETTINGS,
            "백업의 설정으로 바꿈 · 테마 ${settings.themeMode ?: "-"} · 한 주 ${settings.weekStart ?: "-"} · " +
                "아래 메뉴 ${settings.bottomMenu?.items?.joinToString(",") { it.key } ?: "-"} · " +
                "스위치 ${settings.auto.size}개 중 끔 $off · 자동 백업 ${settings.backupOn ?: "-"} ${settings.backupInterval ?: "-"}",
        )
    }

    /**
     * 설정 초기화. 테마는 기기 설정으로, 한 주 시작은 일요일로, 자동 기능은 모두 켜진 것으로, 자동 백업과 아래 메뉴는 기본값으로 돌아간다
     * (이 파일에는 이것들만 있다). 거래는 건드리지 않는다.
     */
    suspend fun resetAll() {
        store.edit { preferences -> preferences.clear() }
        DevLog.info(LogTag.SETTINGS, "설정 초기화")
    }

    private fun Preferences.themeMode(): ThemeMode = ThemeMode.fromName(this[themeModeKey])

    private fun Preferences.autoSettings(): AutoSettings {
        val off = AutoOption.entries.filter { this[it.preferenceKey] == false }
        return AutoSettings(off.toSet())
    }

    private fun Preferences.weekStart(): DayOfWeek = if (this[weekStartKey] == DayOfWeek.MONDAY.name) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    private fun Preferences.backupSchedule(): BackupSchedule = BackupSchedule(
        on = this[backupOnKey] ?: BackupSchedule.DEFAULT.on,
        interval = BackupInterval.fromKey(this[backupIntervalKey]),
    )

    private fun Preferences.bottomMenu(): BottomMenu = BottomMenu.decode(this[bottomMenuKey])

    private val AutoOption.preferenceKey: Preferences.Key<Boolean> get() = booleanPreferencesKey(key)
}
