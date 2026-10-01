package com.dong.budget.ui.settings

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.BuildConfig
import com.dong.budget.data.backup.BackupCodec
import com.dong.budget.data.backup.BackupExporter
import com.dong.budget.data.backup.BackupKind
import com.dong.budget.data.backup.BackupRead
import com.dong.budget.data.backup.BackupRepository
import com.dong.budget.data.backup.BackupSchedule
import com.dong.budget.data.backup.BackupStorage
import com.dong.budget.data.backup.LastBackup
import com.dong.budget.data.backup.parseBackupTime
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.salary.toSettings
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 설정의 백업·복원 상태
 * @property busy 내보내거나 읽거나 되살리는 중. 줄을 막아 두 번 누르지 않게 한다.
 * @property restore 되살릴지 묻는 중인 백업. 없으면 null
 */
data class BackupUiState(val busy: Boolean = false, val restore: RestorePreview? = null)

/** 설정 첫 화면. 화면 테마와 백업·복원을 다룬다. 새 버전 확인·설치는 앱 정보([AppInfoViewModel])가 맡는다. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    updateChecker: UpdateChecker,
    private val backupRepository: BackupRepository,
    private val backupStorage: BackupStorage,
    private val backupExporter: BackupExporter,
    private val salaryRepository: SalaryRepository,
    /** 마지막으로 백업한 때. 백업 묶음 맨 위에 보인다. */
    val lastBackup: StateFlow<LastBackup?> = MutableStateFlow(null),
    /** 자동 백업 설정. 앱이 켜질 때부터 따라가는 값이라 스위치가 처음부터 저장된 자리에 있다(AppContainer.backupSchedule). */
    val backupSchedule: StateFlow<BackupSchedule> = MutableStateFlow(BackupSchedule.DEFAULT),
    /** 한 주를 시작하는 요일(AppContainer.weekStart) */
    val weekStart: StateFlow<DayOfWeek> = MutableStateFlow(DayOfWeek.SUNDAY),
    appThemeMode: StateFlow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM),
) : ViewModel() {
    /** 앱이 켜질 때부터 따라가는 테마 값이라 처음 그릴 때부터 저장된 칸에 있다 */
    val themeMode: StateFlow<ThemeMode> = appThemeMode

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** 아직 설치하지 않은 가장 새 버전. 앱 정보 줄에서 알린다. 없으면 null */
    val newerVersion: StateFlow<String?> =
        updateChecker.newer
            .map { releases -> releases.firstOrNull()?.version }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), updateChecker.newer.value.firstOrNull()?.version)

    fun selectThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun selectWeekStart(day: DayOfWeek) {
        viewModelScope.launch { settingsRepository.setWeekStart(day) }
    }

    fun setBackupSchedule(schedule: BackupSchedule) {
        viewModelScope.launch { settingsRepository.setBackupSchedule(schedule) }
    }

    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** 한 번 띄우고 마는 알림(토스트). 복사·저장·복원 결과를 알린다. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** 백업을 클립보드에 한 줄 JSON 으로 넣는다. 안드로이드 13 부터는 시스템이 복사했다고 알려 주므로 그 아래에서만 직접 알린다. */
    fun copyBackup() = runBackup(EXPORT_FAILED_MESSAGE) {
        when {
            !backupExporter.copy() -> _messages.send(COPY_FAILED_MESSAGE)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> _messages.send(COPIED_MESSAGE)
        }
    }

    /** 백업을 다운로드 폴더에 파일로 저장한다. 사람이 열어 볼 수 있게 줄을 나눈다. */
    fun saveBackup() = runBackup(EXPORT_FAILED_MESSAGE) {
        backupExporter.saveFile(backupFileName(LocalDate.now(BudgetTime.ZONE))).fold(
            onSuccess = { name ->
                DevLog.info(LogTag.BACKUP, "파일로 저장 · $name")
                _messages.send(savedMessage(name))
            },
            onFailure = {
                DevLog.warn(LogTag.BACKUP, "파일로 저장하지 못했어요", it)
                _messages.send(SAVE_FAILED_MESSAGE)
            },
        )
    }

    /** 고른 파일을 읽어 되살릴지 묻는다 */
    fun readRestoreFile(uri: Uri) = runBackup(READ_FAILED_MESSAGE) {
        backupStorage.read(uri).fold(
            onSuccess = { prepareRestore(it) },
            onFailure = {
                DevLog.warn(LogTag.BACKUP, "복원할 파일을 읽지 못했어요", it)
                _messages.send(if (it is BackupStorage.TooLargeException) TOO_LARGE_MESSAGE else READ_FAILED_MESSAGE)
            },
        )
    }

    /** 클립보드의 글을 읽어 되살릴지 묻는다 */
    fun readRestorePasted() = runBackup(READ_FAILED_MESSAGE) {
        val text = backupStorage.pasted()
        if (text == null) _messages.send(NOTHING_PASTED_MESSAGE) else prepareRestore(text)
    }

    /** 묻던 백업으로 되살린다. 실패하면 한 트랜잭션이라 지금 데이터가 그대로 남는다. */
    fun confirmRestore() {
        val preview = _backup.value.restore ?: return
        _backup.update { it.copy(restore = null) }
        runBackup(RESTORE_FAILED_MESSAGE) {
            // 되살리는 중에 화면을 떠나 뷰모델이 치워져도 끝까지 한다. 멈추면 거래만 바뀌고 월급 설정은 옛것으로 남는다.
            val done =
                withContext(NonCancellable) {
                    // 지우기 전에 지금 데이터를 파일로 남긴다. 남기지 못하면 되살리지 않는다(지금 데이터는 그대로).
                    val savedAs =
                        if (preview.autoBackup) {
                            backupExporter.saveBeforeReplace(AutoBackupReason.RESTORE) ?: return@withContext AUTO_BACKUP_FAILED_RESTORE
                        } else {
                            null
                        }
                    backupRepository.restore(preview.backup)
                    // 월급 설정은 DB 밖이라 거래를 되살린 뒤에 따로 넣는다. 백업에 없으면(월급을 정하기 전 백업) 지금 설정을 그대로 둔다.
                    val salaryRestored =
                        runCatching { preview.backup.salary?.let { salaryRepository.save(it.toSettings()) } }
                            .onFailure { DevLog.warn(LogTag.BACKUP, "월급 설정을 되살리지 못했어요", it) }
                            .isSuccess
                    val result = if (salaryRestored) restoredMessage(preview.backup) else RESTORE_SALARY_FAILED_MESSAGE
                    listOfNotNull(result, savedAs?.let { AUTO_BACKUP_SAVED_NOTE }).joinToString("\n")
                }
            _messages.send(done)
        }
    }

    fun dismissRestore() {
        _backup.update { it.copy(restore = null) }
    }

    private suspend fun prepareRestore(text: String) {
        when (val read = withContext(Dispatchers.Default) { BackupCodec.decode(text) }) {
            is BackupRead.Invalid -> {
                DevLog.warn(LogTag.BACKUP, "복원할 수 없는 백업 · ${read.reason}${read.detail?.let { " · $it" } ?: ""}")
                _messages.send(read.reason)
            }

            is BackupRead.Valid -> {
                val exportedAt = checkNotNull(parseBackupTime(read.backup.exportedAt))
                // 지울 것이 없으면(새로 깐 폰에서 처음 복원) 빈 백업 파일을 만들지 않는다
                val autoBackup = settingsRepository.autoSettings.first()[AutoOption.BACKUP_BEFORE_REPLACE] && backupExporter.hasData()
                val preview = RestorePreview(read.backup, exportedAt, backupRepository.transactionCount(), autoBackup)
                _backup.update { it.copy(restore = preview) }
            }
        }
    }

    /**
     * 백업 일을 하나씩만 한다. 하는 동안 줄이 막힌다.
     * 예상 못 한 실패(DB 오류 등)는 앱을 죽이지 않고 [failure] 로 알린다.
     */
    private fun runBackup(failure: String, block: suspend () -> Unit) {
        if (_backup.value.busy) return
        _backup.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLog.warn(LogTag.BACKUP, failure, e)
                _messages.send(failure)
            } finally {
                _backup.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * 복원·데이터 초기화로 지우기 전에 지금 데이터를 다운로드 폴더에 저장한다.
 * @return 저장한 파일 이름. 저장하지 못했으면 null 이고, 그때는 지우지 않는다.
 */
internal suspend fun BackupExporter.saveBeforeReplace(reason: AutoBackupReason): String? =
    saveFile(autoBackupFileName(LocalDate.now(BudgetTime.ZONE), reason), BackupKind.BEFORE_REPLACE).fold(
        onSuccess = { name ->
            DevLog.info(LogTag.BACKUP, "지우기 전 자동 백업 · $name")
            name
        },
        onFailure = {
            DevLog.warn(LogTag.BACKUP, "지우기 전 자동 백업을 저장하지 못했어요", it)
            null
        },
    )
