package com.dong.budget.data.backup

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** 백업을 어떻게 했는지 */
enum class BackupKind {
    /** JSON 으로 복사 */
    COPY,

    /** 다운로드 폴더에 파일로 저장 */
    FILE,

    /** 복원·데이터 초기화 전에 앱이 알아서 파일로 저장 */
    AUTO,
}

/** 마지막으로 한 백업 */
data class LastBackup(val at: Instant, val kind: BackupKind)

/**
 * 마지막으로 백업한 때. 설정의 백업 묶음에 '마지막 백업 · 3일 전' 으로 보인다.
 * 기기 안(SharedPreferences)에만 둔다. 안드로이드 자동 백업에도 담기지 않아 새 기기에서는 백업한 적 없는 것으로 시작한다.
 */
class BackupHistory(private val prefs: SharedPreferences, private val now: () -> Instant = Instant::now) {
    private val _last = MutableStateFlow(load())
    val last: StateFlow<LastBackup?> = _last.asStateFlow()

    fun record(kind: BackupKind) {
        val backup = LastBackup(now(), kind)
        prefs.edit {
            putLong(KEY_AT, backup.at.toEpochMilli())
            putString(KEY_KIND, kind.name)
        }
        _last.value = backup
    }

    private fun load(): LastBackup? {
        if (!prefs.contains(KEY_AT)) return null
        // 종류를 읽을 수 없으면(나중 버전이 새 종류를 적은 뒤 되돌린 경우) 파일로 본다
        val kind = BackupKind.entries.firstOrNull { it.name == prefs.getString(KEY_KIND, null) } ?: BackupKind.FILE
        return LastBackup(Instant.ofEpochMilli(prefs.getLong(KEY_AT, 0L)), kind)
    }

    companion object {
        /** SharedPreferences 파일 이름 */
        const val PREFS_NAME = "backup_history"

        private const val KEY_AT = "last_at"
        private const val KEY_KIND = "last_kind"
    }
}
