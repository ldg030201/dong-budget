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
    BEFORE_REPLACE,

    /** 정한 주기마다 앱을 열 때 파일로 저장 */
    SCHEDULED,
    ;

    /** 다운로드 폴더에 파일이 남는 백업인지. 자동 백업 주기는 이것만 센다(복사한 글은 어디 남았는지 알 수 없다). */
    val isFile: Boolean get() = this != COPY
}

/** 마지막으로 한 백업 */
data class LastBackup(val at: Instant, val kind: BackupKind)

/**
 * 마지막으로 백업한 때. 설정의 백업 묶음에 '마지막 백업 · 3일 전' 으로 보인다.
 * 파일로 남은 마지막 백업([lastFileAt])은 따로 적어 자동 백업 주기를 센다.
 * 기기 안(SharedPreferences)에만 둔다. 안드로이드 자동 백업에도 담기지 않아 새 기기에서는 백업한 적 없는 것으로 시작한다.
 */
class BackupHistory(private val prefs: SharedPreferences, private val now: () -> Instant = Instant::now) {
    private val _last = MutableStateFlow(load())
    val last: StateFlow<LastBackup?> = _last.asStateFlow()

    /** 파일로 남은 마지막 백업 때. 없으면 null */
    val lastFileAt: Instant?
        get() = if (prefs.contains(KEY_FILE_AT)) Instant.ofEpochMilli(prefs.getLong(KEY_FILE_AT, 0L)) else null

    fun record(kind: BackupKind) {
        val backup = LastBackup(now(), kind)
        prefs.edit {
            putLong(KEY_AT, backup.at.toEpochMilli())
            putString(KEY_KIND, kind.name)
            if (kind.isFile) putLong(KEY_FILE_AT, backup.at.toEpochMilli())
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
        private const val KEY_FILE_AT = "last_file_at"
    }
}
