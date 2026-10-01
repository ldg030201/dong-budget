package com.dong.budget.ui.settings

import com.dong.budget.data.backup.Backup
import com.dong.budget.data.backup.BackupInterval
import com.dong.budget.data.backup.BackupKind
import com.dong.budget.data.backup.BackupSchedule
import com.dong.budget.data.backup.LastBackup
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDate
import com.dong.budget.ui.format.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────────────
// 설정의 백업·복원·초기화 글자. 무엇이 지워지고 무엇이 남는지 쓰는 사람 눈높이로 적는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 되살릴지 묻는 중인 백업
 * @property exportedAt 백업을 만든 때
 * @property currentTransactions 지금 있는 거래 수. 되살리면 이만큼 지워진다는 것을 알린다.
 * @property autoBackup 되살리기 전에 지금 데이터를 파일로 저장하는지(자동 백업 스위치가 켜져 있고 지울 것이 있을 때)
 */
data class RestorePreview(val backup: Backup, val exportedAt: Instant, val currentTransactions: Int, val autoBackup: Boolean = false)

/**
 * 백업 묶음 맨 위의 한 줄. "마지막 백업 · 3일 전 · 파일로 저장"
 * 날짜는 서울 날짜로 센다. 자정을 넘기면 하루 전이다.
 */
internal fun lastBackupText(last: LastBackup?, today: LocalDate): String {
    if (last == null) return "아직 백업한 적 없어요"
    val days = ChronoUnit.DAYS.between(BudgetTime.toLocalDate(last.at), today)
    val ago =
        when {
            // 시계가 뒤로 가 미래로 적힌 것도 오늘로 본다
            days <= 0L -> "오늘"

            days == 1L -> "어제"

            else -> "${formatAmount(days)}일 전"
        }
    return "마지막 백업 · $ago · ${last.kind.label}"
}

private val BackupKind.label: String
    get() = when (this) {
        BackupKind.COPY -> "JSON 복사"
        BackupKind.FILE -> "파일로 저장"
        BackupKind.BEFORE_REPLACE -> "지우기 전 백업"
        BackupKind.SCHEDULED -> "자동 백업"
    }

/** 주기를 고르는 칸 순서. 짧은 것부터(적은 순서 그대로) */
internal val INTERVAL_ORDER = BackupInterval.entries

/** 주기 칸 글자 */
internal val BackupInterval.shortLabel: String
    get() = when (this) {
        BackupInterval.DAILY -> "매일"
        BackupInterval.EVERY_3_DAYS -> "3일"
        BackupInterval.WEEKLY -> "1주"
        BackupInterval.MONTHLY -> "1달"
    }

/** 스위치 설명에 들어가는 주기. "1주마다" */
private val BackupInterval.everyLabel: String
    get() = when (this) {
        BackupInterval.DAILY -> "하루에 한 번"
        BackupInterval.EVERY_3_DAYS -> "3일마다"
        BackupInterval.WEEKLY -> "1주마다"
        BackupInterval.MONTHLY -> "1달마다"
    }

/** 자동 백업 스위치 아래 설명. 켜져 있으면 주기와 몇 개를 남기는지 알린다. */
internal fun scheduleDescription(schedule: BackupSchedule): String {
    if (!schedule.on) return "켜면 정한 주기마다 다운로드 폴더에 백업 파일을 저장해요"
    return "앱을 열 때 ${schedule.interval.everyLabel} 다운로드 폴더에 저장해요. 자동 백업 파일은 최근 ${BackupSchedule.KEEP_FILES}개만 남기고 지워요"
}

/** 다운로드 폴더에 저장할 이름. 같은 날 또 저장하면 시스템이 뒤에 번호를 붙인다. */
internal fun backupFileName(today: LocalDate): String = "동계부-백업-$today.json"

/** 무엇 때문에 지우기 전에 자동으로 백업하는지. 파일 이름 끝에 붙여 손으로 만든 백업과 구분한다. */
internal enum class AutoBackupReason(val suffix: String) {
    RESTORE("복원전"),
    RESET("초기화전"),
}

/** 지우기 전 자동 백업의 파일 이름. "동계부-백업-2026-10-01-복원전.json" */
internal fun autoBackupFileName(today: LocalDate, reason: AutoBackupReason): String = "동계부-백업-$today-${reason.suffix}.json"

/**
 * 지우기 전에 자동으로 저장했다고 알린다. 지운 뒤에 알리는 글 아래 줄에 붙인다.
 * 토스트는 두 줄까지만 보여서 파일 이름은 넣지 않는다(이름은 날짜와 '복원전·초기화전' 이라 다운로드 폴더에서 바로 보인다).
 */
internal const val AUTO_BACKUP_SAVED_NOTE = "지운 데이터는 다운로드 폴더에 있어요"

/** 묻는 창에서 지우기 전에 자동으로 백업한다고 알리는 말 */
private const val AUTO_BACKUP_NOTICE = "지우기 전에 지금 데이터를 다운로드 폴더에 백업 파일로 저장해 둬요."

/** 되살릴지 묻는 창의 글. 무엇을 담은 백업인지, 지금 데이터가 어떻게 되는지 알린다. */
internal fun restoreMessage(preview: RestorePreview, today: LocalDate): String {
    val backup = preview.backup
    val made = "${formatDate(preview.exportedAt, today)} ${formatTime(preview.exportedAt)}"
    return "${made}에 만든 백업이에요.\n" +
        "거래 ${formatAmount(backup.transactions.size.toLong())}건 · 분류 ${backup.categories.size}개 · " +
        "결제수단 ${backup.paymentMethods.size}개${if (backup.salary != null) " · 월급 설정" else ""}\n\n" +
        (if (preview.currentTransactions > 0) "지금 있는 거래 ${formatAmount(preview.currentTransactions.toLong())}건과 " else "지금 있는 ") +
        "분류·결제수단은 모두 지우고 백업 내용으로 바꿔요. " +
        if (preview.autoBackup) AUTO_BACKUP_NOTICE else "되돌릴 수 없어요."
}

internal fun restoredMessage(backup: Backup): String = "백업으로 복원했어요 · 거래 ${formatAmount(backup.transactions.size.toLong())}건"

/** 데이터 초기화를 묻는 창의 글. [autoBackup] 이면 지우기 전에 자동으로 백업한다고 알리고, 아니면 먼저 백업해 두라고 권한다. */
internal fun resetDataMessage(transactionCount: Int, autoBackup: Boolean = false): String =
    "거래 ${formatAmount(transactionCount.toLong())}건과 직접 만든 분류·결제수단, 월급 설정, 알림 목록을 모두 지우고 " +
        "처음 설치한 상태로 돌려요. " +
        (if (autoBackup) AUTO_BACKUP_NOTICE else "되돌릴 수 없으니 먼저 설정의 '파일로 내려받기'로 백업해 두세요.") +
        "\n\n화면 테마와 자동 기능 설정은 그대로예요."

internal const val RESET_SETTINGS_MESSAGE =
    "화면 테마는 기기 설정으로, 한 주 시작은 일요일로, 자동 백업은 켜고 1주마다로, 고급 설정의 자동 기능은 모두 켜진 상태로 돌아가요. " +
        "거래·분류·결제수단과 월급 설정은 그대로예요."

internal const val COPIED_MESSAGE = "백업을 복사했어요"
internal const val COPY_FAILED_MESSAGE = "거래가 많아 복사할 수 없어요. '파일로 내려받기'를 써 주세요"
internal const val SAVE_FAILED_MESSAGE = "파일로 저장하지 못했어요"
internal const val READ_FAILED_MESSAGE = "파일을 읽지 못했어요"
internal const val TOO_LARGE_MESSAGE = "백업 파일이 아니에요. 동계부에서 저장한 JSON 파일을 골라 주세요"
internal const val NOTHING_PASTED_MESSAGE = "복사한 글이 없어요. 백업 JSON을 먼저 복사해 주세요"
internal const val RESTORE_FAILED_MESSAGE = "복원하지 못했어요. 지금 데이터는 그대로예요"
internal const val EXPORT_FAILED_MESSAGE = "백업을 만들지 못했어요"
internal const val RESET_SETTINGS_DONE = "설정을 처음대로 돌렸어요"
internal const val RESET_DATA_DONE = "처음 설치한 상태로 돌렸어요"
internal const val RESET_DATA_FAILED = "데이터를 지우지 못했어요. 지금 데이터는 그대로예요"
internal const val RESET_SETTINGS_FAILED = "설정을 되돌리지 못했어요"
internal const val RESET_DATA_PARTLY_FAILED = "거래는 지웠지만 알림 목록이나 월급 설정을 다 지우지 못했어요. 다시 해 주세요"
internal const val RESTORE_SALARY_FAILED_MESSAGE = "거래는 되살렸지만 월급 설정을 되살리지 못했어요"
internal const val AUTO_BACKUP_FAILED_RESTORE = "백업 파일을 저장하지 못해 복원하지 않았어요"
internal const val AUTO_BACKUP_FAILED_RESET = "백업 파일을 저장하지 못해 지우지 않았어요"

/** 다운로드 폴더에 저장했을 때. 파일 관리자에서 찾을 수 있게 이름을 알린다. */
internal fun savedMessage(fileName: String): String = "다운로드 폴더에 저장했어요 · $fileName"
