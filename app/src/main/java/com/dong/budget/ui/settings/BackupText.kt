package com.dong.budget.ui.settings

import com.dong.budget.data.backup.Backup
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDate
import com.dong.budget.ui.format.formatTime
import java.time.Instant
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────
// 설정의 백업·복원·초기화 글자. 무엇이 지워지고 무엇이 남는지 쓰는 사람 눈높이로 적는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 되살릴지 묻는 중인 백업
 * @property exportedAt 백업을 만든 때
 * @property currentTransactions 지금 있는 거래 수. 되살리면 이만큼 지워진다는 것을 알린다.
 */
data class RestorePreview(val backup: Backup, val exportedAt: Instant, val currentTransactions: Int)

/** 다운로드 폴더에 저장할 이름. 같은 날 또 저장하면 시스템이 뒤에 번호를 붙인다. */
internal fun backupFileName(today: LocalDate): String = "동계부-백업-$today.json"

/** 되살릴지 묻는 창의 글. 무엇을 담은 백업인지, 지금 데이터가 어떻게 되는지 알린다. */
internal fun restoreMessage(preview: RestorePreview, today: LocalDate): String {
    val backup = preview.backup
    val made = "${formatDate(preview.exportedAt, today)} ${formatTime(preview.exportedAt)}"
    return "${made}에 만든 백업이에요.\n" +
        "거래 ${formatAmount(backup.transactions.size.toLong())}건 · 분류 ${backup.categories.size}개 · " +
        "결제수단 ${backup.paymentMethods.size}개${if (backup.salary != null) " · 월급 설정" else ""}\n\n" +
        (if (preview.currentTransactions > 0) "지금 있는 거래 ${formatAmount(preview.currentTransactions.toLong())}건과 " else "지금 있는 ") +
        "분류·결제수단은 모두 지우고 백업 내용으로 바꿔요. 되돌릴 수 없어요."
}

internal fun restoredMessage(backup: Backup): String = "백업으로 복원했어요 · 거래 ${formatAmount(backup.transactions.size.toLong())}건"

/** 데이터 초기화를 묻는 창의 글 */
internal fun resetDataMessage(transactionCount: Int): String =
    "거래 ${formatAmount(transactionCount.toLong())}건과 직접 만든 분류·결제수단, 월급 설정, 알림 목록을 모두 지우고 " +
        "처음 설치한 상태로 돌려요. 되돌릴 수 없으니 먼저 설정의 '파일로 내려받기'로 백업해 두세요.\n\n" +
        "화면 테마와 자동 기능 설정은 그대로예요."

internal const val RESET_SETTINGS_MESSAGE =
    "화면 테마는 기기 설정으로, 고급 설정의 자동 기능은 모두 켜진 상태로 돌아가요. 거래·분류·결제수단과 월급 설정은 그대로예요."

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

/** 다운로드 폴더에 저장했을 때. 파일 관리자에서 찾을 수 있게 이름을 알린다. */
internal fun savedMessage(fileName: String): String = "다운로드 폴더에 저장했어요 · $fileName"
