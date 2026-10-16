package com.dong.budget.data.backup

import androidx.room.withTransaction
import com.dong.budget.data.db.ALL_DEFAULT_PAYMENT_METHODS
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.DEFAULT_CATEGORIES
import com.dong.budget.data.db.registerShownFixedMerchants
import com.dong.budget.data.db.settleIncomeSources
import com.dong.budget.data.db.toEntity
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import java.time.Instant

/**
 * 거래·분류·결제수단과 고정지출 목록을 통째로 다룬다. 백업으로 내보내고, 백업으로 바꿔 넣고, 처음 설치한 상태로 되돌린다.
 * 바꿔 넣기와 되돌리기는 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다.
 */
class BackupRepository(private val database: BudgetDatabase) {
    private val dao = database.backupDao()

    /** 지금 DB 를 백업 한 벌로. 읽는 사이 거래가 새로 들어와도 섞이지 않게 한 번에 읽는다. */
    suspend fun export(appVersion: String, now: Instant = Instant.now()): Backup = database.withTransaction {
        backupOf(dao.categories(), dao.paymentMethods(), dao.transactions(), appVersion, now, dao.fixedMerchants())
    }

    suspend fun transactionCount(): Int = dao.transactionCount()

    /**
     * 지금 거래·분류·결제수단과 고정지출 목록을 모두 지우고 [backup] 으로 채운다. [backup] 은 [BackupCodec.decode] 의 검사를 마친 것이어야 한다.
     * @param now 되살리는 때. 목록이 없는 옛 백업이면 이날 고정지출 탭에 보였을 곳을 이때 등록한 것으로 둔다.
     */
    suspend fun restore(backup: Backup, now: Instant = Instant.now()) {
        var registered = 0
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
            // 들어온 곳이 생기기 전(1.8.1 까지)의 백업이면 DB 를 올릴 때처럼 기본 들어온 곳을 넣고 옛 수입을 그리로 옮긴다.
            // 새 백업은 사용자가 지운 들어온 곳을 되살리지 않게 그대로 둔다.
            // 마이그레이션과 같은 함수를 쓴다. 이 블록은 Room 의 거래 스레드에서 돌아서 같은 거래 안에 든다(호환 모드).
            if (backup.format < BACKUP_FORMAT_INCOME_SOURCES) settleIncomeSources(database.openHelper.writableDatabase)
            // 고정지출 목록이 생기기 전(1.9.0 까지)의 백업이면 DB 를 6 → 7 로 올릴 때처럼 그날 탭에 보였을 곳을 등록한다.
            // 기본 분류 코드를 돌려준 뒤라 다시 만든 '고정지출' 분류의 지출도 센다. 새 백업은 사용자가 정한 목록 그대로다.
            val fixed = backup.fixedMerchants
            if (fixed == null) {
                registered = registerShownFixedMerchants(database.openHelper.writableDatabase, BudgetTime.toLocalDate(now), now)
            } else {
                fixed.forEach { dao.insertFixedMerchant(it.toEntity()) }
            }
        }
        DevLog.info(
            LogTag.BACKUP,
            "복원 · ${backup.exportedAt}(${backup.appVersion})에 만든 백업 · 거래 ${backup.transactions.size}건 · " +
                "분류 ${backup.categories.size}개 · 결제수단·들어온 곳 ${backup.paymentMethods.size}개 · " +
                "고정지출 목록 ${backup.fixedMerchants?.let { "${it.size}곳" } ?: "없음(보이던 ${registered}곳 등록)"} · 판 ${backup.format}",
        )
    }

    /** 거래·분류·결제수단과 고정지출 목록을 모두 지우고 기본 분류·결제수단·들어온 곳을 다시 넣는다. 새로 설치한 것과 같다. */
    suspend fun resetToDefaults() {
        database.withTransaction {
            dao.deleteAll()
            DEFAULT_CATEGORIES.forEach { dao.insertCategory(it.toEntity()) }
            ALL_DEFAULT_PAYMENT_METHODS.forEach { dao.insertPaymentMethod(it.toEntity()) }
        }
        DevLog.info(LogTag.BACKUP, "데이터 초기화 · 처음 설치한 상태로")
    }
}
