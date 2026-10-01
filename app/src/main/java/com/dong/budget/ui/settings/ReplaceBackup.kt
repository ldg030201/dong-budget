package com.dong.budget.ui.settings

import com.dong.budget.data.backup.BackupExporter
import com.dong.budget.data.backup.BackupKind
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────
// 복원·데이터 초기화처럼 지금 데이터를 지우는 일 앞의 자동 백업. 설정(복원)과 고급 설정(데이터 초기화)이 같이 쓴다.
// 규칙: 스위치가 켜져 있고 지울 것이 있으면 먼저 파일로 남기고, 남기지 못하면 지우지 않는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 지우기 전에 자동으로 백업할지. 묻는 창의 글과 실제 동작이 이 값 하나를 같이 쓴다.
 * @param transactionCount 부르는 쪽이 묻는 창에 쓰려고 이미 센 거래 수
 */
internal suspend fun shouldBackupBeforeReplace(settings: SettingsRepository, exporter: BackupExporter, transactionCount: Int): Boolean =
    settings.autoSettings.first()[AutoOption.BACKUP_BEFORE_REPLACE] && exporter.hasData(transactionCount)

/**
 * [autoBackup] 이면 지금 데이터를 다운로드 폴더에 먼저 저장한 뒤 [replace] 를 한다. 저장하지 못하면 [replace] 하지 않는다.
 * @param replace 지우고 바꾸는 일. 끝나면 알릴 글을 준다.
 * @return 알릴 글. 저장하지 못했으면 [failedMessage], 자동 백업을 했으면 아래 줄에 지운 데이터가 어디 있는지 붙인다.
 */
internal suspend fun BackupExporter.replaceWithBackup(
    autoBackup: Boolean,
    reason: AutoBackupReason,
    failedMessage: String,
    replace: suspend () -> String,
): String {
    if (autoBackup && !saveBeforeReplace(reason)) return failedMessage
    val result = replace()
    return if (autoBackup) "$result\n$AUTO_BACKUP_SAVED_NOTE" else result
}

/** 지우기 전 자동 백업을 저장한다. 저장했으면 true */
private suspend fun BackupExporter.saveBeforeReplace(reason: AutoBackupReason): Boolean =
    saveFile(autoBackupFileName(LocalDate.now(BudgetTime.ZONE), reason), BackupKind.BEFORE_REPLACE).fold(
        onSuccess = { name ->
            DevLog.info(LogTag.BACKUP, "지우기 전 자동 백업 · $name")
            true
        },
        onFailure = {
            DevLog.warn(LogTag.BACKUP, "지우기 전 자동 백업을 저장하지 못했어요", it)
            false
        },
    )
