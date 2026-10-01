package com.dong.budget.data.backup

import java.time.LocalDate

/**
 * 자동 백업 주기. 앱을 열 때 마지막 파일 백업에서 이만큼 지났으면 저장한다.
 * @property key 설정 저장소에 적는 값. 바꾸지 않는다(바꾸면 고른 주기가 기본값으로 돌아간다).
 */
enum class BackupInterval(val key: String) {
    DAILY("daily"),
    EVERY_3_DAYS("every_3_days"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    /** [last] 에 백업했으면 다음에 백업할 날 */
    fun nextAfter(last: LocalDate): LocalDate = when (this) {
        DAILY -> last.plusDays(1)
        EVERY_3_DAYS -> last.plusDays(3)
        WEEKLY -> last.plusWeeks(1)
        MONTHLY -> last.plusMonths(1)
    }

    companion object {
        fun fromKey(key: String?): BackupInterval = entries.firstOrNull { it.key == key } ?: BackupSchedule.DEFAULT.interval
    }
}

/**
 * 자동 백업을 할지와 주기. 처음에는 켜져 있고 1주마다다.
 */
data class BackupSchedule(val on: Boolean = true, val interval: BackupInterval = BackupInterval.WEEKLY) {
    /**
     * 오늘 백업할 차례인지. 날짜는 서울 날짜로 센다. 백업한 적이 없으면 바로 한다.
     * 손으로 '파일로 내려받기' 한 것도 마지막 파일 백업으로 친다. 며칠 사이 같은 내용의 파일이 둘 생기지 않게.
     */
    fun isDue(lastFileBackup: LocalDate?, today: LocalDate): Boolean =
        on && (lastFileBackup == null || !today.isBefore(interval.nextAfter(lastFileBackup)))

    companion object {
        val DEFAULT = BackupSchedule()

        /** 다운로드 폴더에 남겨 두는 자동 백업 파일 수. 더 오래된 것은 지운다. */
        const val KEEP_FILES = 3

        /** 자동 백업 파일 이름 앞부분. 오래된 파일을 찾을 때 이걸로 찾으므로 바꾸지 않는다. */
        const val FILE_PREFIX = "동계부-자동백업-"

        /** 자동 백업 파일 이름. "동계부-자동백업-2026-10-01.json" */
        fun fileName(today: LocalDate): String = "$FILE_PREFIX$today.json"
    }
}
