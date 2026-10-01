package com.dong.budget.data.backup

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

/**
 * 정한 주기마다 다운로드 폴더에 백업 파일을 저장하고, 자동 백업 파일은 최근 [BackupSchedule.KEEP_FILES] 개만 남긴다.
 *
 * 백그라운드에서 따로 돌지 않고 앱을 열 때 [runIfDue] 로 확인한다. 가계부 데이터는 앱을 열어야 바뀌므로
 * 앱을 열지 않는 동안 백업을 건너뛰어도 잃는 것이 없다.
 */
class ScheduledBackup(
    private val schedule: suspend () -> BackupSchedule,
    /** 파일로 남은 마지막 백업 때(BackupHistory.lastFileAt) */
    private val lastFileBackup: () -> Instant?,
    /** 백업할 것이 있는지(BackupExporter.hasData). 없으면 빈 파일을 만들지 않는다. */
    private val hasData: suspend () -> Boolean,
    /** 파일로 저장한다(BackupExporter.saveFile). 실제로 저장된 이름을 준다. */
    private val save: suspend (fileName: String) -> Result<String>,
    /** 오래된 자동 백업 파일을 지운다(BackupStorage.deleteOld). 지운 수를 준다. */
    private val deleteOld: suspend () -> Int,
    private val today: () -> LocalDate = { LocalDate.now(BudgetTime.ZONE) },
) {
    /** 앱을 빠르게 껐다 켜도 같은 날 두 번 저장하지 않게 한 번에 하나만 한다 */
    private val mutex = Mutex()

    /** @return 저장했으면 저장한 파일 이름 */
    suspend fun runIfDue(): String? = mutex.withLock {
        val day = today()
        val last = lastFileBackup()?.let(BudgetTime::toLocalDate)
        if (!schedule().isDue(last, day) || !hasData()) return@withLock null
        save(BackupSchedule.fileName(day)).fold(
            onSuccess = { name ->
                val deleted = runCatching {
                    deleteOld()
                }.onFailure { DevLog.warn(LogTag.BACKUP, "오래된 자동 백업을 지우지 못했어요", it) }.getOrDefault(0)
                DevLog.info(LogTag.BACKUP, "자동 백업 · $name${if (deleted > 0) " · 오래된 파일 ${deleted}개 지움" else ""}")
                name
            },
            onFailure = {
                // 다음에 앱을 열 때 다시 해 본다
                DevLog.warn(LogTag.BACKUP, "자동 백업을 저장하지 못했어요", it)
                null
            },
        )
    }
}
