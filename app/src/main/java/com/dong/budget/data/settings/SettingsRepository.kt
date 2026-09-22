package com.dong.budget.data.settings

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** 앱 설정 저장소. 지금은 테마만 다루고, 이후 설정 항목이 늘어나면 여기에 추가한다. */
class SettingsRepository(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> =
        context.settingsDataStore.data
            // 저장 파일이 깨졌을 때 앱이 죽지 않고 기본값으로 시작하게 한다.
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }.map { preferences -> ThemeMode.fromName(preferences[themeModeKey]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }
}
