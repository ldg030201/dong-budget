package com.dong.budget.data

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.data.db.TransactionEntity
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** 금액 자리수 상한. 원 단위라 12자리면 조 단위까지 들어간다. 등록 화면과 결제 알림 읽기가 같이 쓴다. */
const val MAX_AMOUNT_DIGITS = 12

/**
 * 거래 저장소.
 *
 * 인터페이스를 따로 만들지 않는다. 구현이 하나뿐인데 인터페이스를 두면
 * 파일만 늘고 읽을 때 한 번 더 따라가야 한다.
 */
class TransactionRepository(private val transactionDao: TransactionDao) {
    /** [first] 달 1일부터 [last] 달 말일까지의 거래. 통계가 여러 달을 한 번에 읽을 때 쓴다. */
    fun observeMonths(first: YearMonth, last: YearMonth): Flow<List<TransactionListItem>> =
        transactionDao.observeBetween(BudgetTime.monthRange(first).first, BudgetTime.monthRange(last).second)

    /** 통계의 기록 시작일(서울 기준). 이체 말고 거래가 하나도 없으면 null */
    fun observeFirstRecordDate(): Flow<LocalDate?> = transactionDao.observeFirstOccurredAt()
        .map { it?.let(BudgetTime::toLocalDate) }
        // 거래를 쓸 때마다 Room 이 다시 내보내지만 시작일은 거의 그대로다. 같은 값이면 통계를 다시 계산하지 않게 거른다.
        .distinctUntilChanged()

    fun observeMonth(month: YearMonth): Flow<List<TransactionListItem>> {
        val (start, end) = BudgetTime.monthRange(month)
        return transactionDao.observeBetween(start, end)
    }

    suspend fun findById(id: Long): TransactionEntity? = transactionDao.findById(id)

    /** 거래 한 건(분류·결제수단 이름 포함). 고치면 새로 내보내고, 지우면 null 을 내보낸다. */
    fun observeItem(id: Long): Flow<TransactionListItem?> = transactionDao.observeItem(id)

    /** [start] 날(서울 0시)부터 가게 이름이 있는 거래. 앞으로의 날짜로 적은 거래도 들어온다. 거래 상세의 같은 곳 내역이 쓴다. */
    fun observeWithMerchantSince(start: LocalDate): Flow<List<TransactionListItem>> =
        transactionDao.observeWithMerchantSince(start.atStartOfDay(BudgetTime.ZONE).toInstant())

    /** 이 가게로 가장 최근에 등록한 지출의 분류. 없으면 null */
    suspend fun lastCategoryIdForMerchant(merchant: String): Long? = transactionDao.lastCategoryIdForMerchant(merchant)

    /** 알림에서 읽은 결제가 이미 등록됐는지 */
    suspend fun isRegistered(dedupKey: String): Boolean = transactionDao.existsByDedupKey(dedupKey)

    /** [keys] 중 이미 등록된 결제의 열쇠. 등록하거나 지우면 새로 내보낸다. */
    fun observeRegisteredKeys(keys: List<String>): Flow<Set<String>> =
        if (keys.isEmpty()) flowOf(emptySet()) else transactionDao.observeRegisteredKeys(keys).map { it.toSet() }

    /**
     * 거래를 저장한다.
     *
     * @param dedupKey 알림에서 읽은 결제면 그 열쇠. 같은 열쇠로 두 번 저장하면 UNIQUE 제약에 걸려
     *   SQLiteConstraintException 이 난다. 같은 결제가 두 번 등록되지 않게 하기 위함이다.
     */
    suspend fun add(
        type: TransactionType,
        amount: Long,
        occurredAt: Instant,
        categoryId: Long?,
        paymentMethodId: Long?,
        merchant: String?,
        memo: String?,
        dedupKey: String? = null,
    ): Long {
        require(amount > 0) { "금액은 0보다 커야 한다. 부호는 type 이 결정한다." }
        val now = Instant.now()
        val id = transactionDao.insert(
            TransactionEntity(
                uuid = UUID.randomUUID().toString(),
                type = type,
                amount = amount,
                occurredAt = occurredAt,
                occurredDate = BudgetTime.toDateKey(occurredAt),
                categoryId = categoryId,
                paymentMethodId = paymentMethodId,
                merchant = merchant.trimmedOrNull(),
                memo = memo.trimmedOrNull(),
                dedupKey = dedupKey,
                createdAt = now,
                updatedAt = now,
            ),
        )
        DevLog.info(LogTag.TRANSACTION, "#$id 등록 · ${type.name}" + if (dedupKey != null) " · 알림에서 ($dedupKey)" else "")
        return id
    }

    suspend fun update(
        id: Long,
        type: TransactionType,
        amount: Long,
        occurredAt: Instant,
        categoryId: Long?,
        paymentMethodId: Long?,
        merchant: String?,
        memo: String?,
    ) {
        require(amount > 0) { "금액은 0보다 커야 한다." }
        val existing = transactionDao.findById(id)
        if (existing == null) {
            DevLog.warn(LogTag.TRANSACTION, "#$id 가 이미 없어 고치지 못했어요")
            return
        }
        transactionDao.update(
            existing.copy(
                type = type,
                amount = amount,
                occurredAt = occurredAt,
                occurredDate = BudgetTime.toDateKey(occurredAt),
                categoryId = categoryId,
                paymentMethodId = paymentMethodId,
                merchant = merchant.trimmedOrNull(),
                memo = memo.trimmedOrNull(),
                updatedAt = Instant.now(),
            ),
        )
        DevLog.info(LogTag.TRANSACTION, "#$id 수정 · ${type.name}")
    }

    /** 거래를 지운다. 없으면 아무것도 하지 않는다. */
    suspend fun delete(id: Long) {
        val existing = transactionDao.findById(id) ?: return
        transactionDao.delete(existing)
        DevLog.info(LogTag.TRANSACTION, "#$id 삭제")
    }
}

/**
 * 가게 이름·메모는 앞뒤 빈칸을 떼고 저장한다. 비면 null.
 * 가게 이름으로 지난 분류를 찾으므로 등록과 수정이 같은 규칙을 써야 한다.
 */
private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
