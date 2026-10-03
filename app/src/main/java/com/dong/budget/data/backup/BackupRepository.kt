package com.dong.budget.data.backup

import androidx.room.withTransaction
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.db.DEFAULT_CATEGORIES
import com.dong.budget.data.db.DEFAULT_PAYMENT_METHODS
import com.dong.budget.data.db.toEntity
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import java.time.Instant

/**
 * 거래·분류·결제수단을 통째로 다룬다. 백업으로 내보내고, 백업으로 바꿔 넣고, 처음 설치한 상태로 되돌린다.
 * 바꿔 넣기와 되돌리기는 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다.
 */
class BackupRepository(private val database: BudgetDatabase) {
    private val dao = database.backupDao()

    /** 지금 DB 를 백업 한 벌로. 읽는 사이 거래가 새로 들어와도 섞이지 않게 한 번에 읽는다. */
    suspend fun export(appVersion: String, now: Instant = Instant.now()): Backup = database.withTransaction {
        backupOf(dao.categories(), dao.paymentMethods(), dao.transactions(), appVersion, now)
    }

    suspend fun transactionCount(): Int = dao.transactionCount()

    /** 지금 거래·분류·결제수단을 모두 지우고 [backup] 으로 채운다. [backup] 은 [BackupCodec.decode] 의 검사를 마친 것이어야 한다. */
    suspend fun restore(backup: Backup) {
        database.withTransaction {
            dao.deleteAll()
            val categoryIds = backup.categories.associate { it.uuid to dao.insertCategory(it.toEntity()) }
            val paymentMethodIds = backup.paymentMethods.associate { it.uuid to dao.insertPaymentMethod(it.toEntity()) }
            val transactionIds =
                backup.transactions.associate { it.uuid to dao.insertTransaction(it.toEntity(categoryIds, paymentMethodIds)) }
            backup.transactions.forEach { t ->
                t.related?.let { dao.setRelated(transactionIds.getValue(t.uuid), transactionIds.getValue(it)) }
            }
            // 기본 분류를 지우고 코드 없이 다시 만든 때의 백업이면 그 분류에 코드를 돌려준다(고정지출 탭·월급 등록이 알아보게)
            database.categoryDao().reclaimDefaultCodes()
        }
        DevLog.info(
            LogTag.BACKUP,
            "복원 · ${backup.exportedAt}(${backup.appVersion})에 만든 백업 · 거래 ${backup.transactions.size}건 · " +
                "분류 ${backup.categories.size}개 · 결제수단 ${backup.paymentMethods.size}개",
        )
    }

    /** 거래·분류·결제수단을 모두 지우고 기본 분류·결제수단을 다시 넣는다. 새로 설치한 것과 같다. */
    suspend fun resetToDefaults() {
        database.withTransaction {
            dao.deleteAll()
            DEFAULT_CATEGORIES.forEach { dao.insertCategory(it.toEntity()) }
            DEFAULT_PAYMENT_METHODS.forEach { dao.insertPaymentMethod(it.toEntity()) }
        }
        DevLog.info(LogTag.BACKUP, "데이터 초기화 · 처음 설치한 상태로")
    }
}
