package com.dong.budget.data

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryDao
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.PaymentMethodDao
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.data.db.TransactionEntity
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * 거래 저장소.
 *
 * 인터페이스를 따로 만들지 않는다. 구현이 하나뿐인데 인터페이스를 두면
 * 파일만 늘고 읽을 때 한 번 더 따라가야 한다.
 */
class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val paymentMethodDao: PaymentMethodDao,
) {
    fun observeMonth(month: YearMonth): Flow<List<TransactionListItem>> {
        val (start, end) = BudgetTime.monthRange(month)
        return transactionDao.observeBetween(start, end)
    }

    fun observeCategories(scope: CategoryScope): Flow<List<CategoryEntity>> = categoryDao.observeByScope(scope)

    fun observePaymentMethods(): Flow<List<PaymentMethodEntity>> = paymentMethodDao.observeAll()

    suspend fun findById(id: Long): TransactionEntity? = transactionDao.findById(id)

    /** 사용자가 직접 입력한 거래를 저장한다. */
    suspend fun add(
        type: TransactionType,
        amount: Long,
        occurredAt: Instant,
        categoryId: Long?,
        paymentMethodId: Long?,
        merchant: String?,
        memo: String?,
    ): Long {
        require(amount > 0) { "금액은 0보다 커야 한다. 부호는 type 이 결정한다." }
        val now = Instant.now()
        return transactionDao.insert(
            TransactionEntity(
                uuid = UUID.randomUUID().toString(),
                type = type,
                amount = amount,
                occurredAt = occurredAt,
                occurredDate = BudgetTime.toDateKey(occurredAt),
                categoryId = categoryId,
                paymentMethodId = paymentMethodId,
                merchant = merchant?.takeIf { it.isNotBlank() },
                memo = memo?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            ),
        )
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
        val existing = transactionDao.findById(id) ?: return
        transactionDao.update(
            existing.copy(
                type = type,
                amount = amount,
                occurredAt = occurredAt,
                occurredDate = BudgetTime.toDateKey(occurredAt),
                categoryId = categoryId,
                paymentMethodId = paymentMethodId,
                merchant = merchant?.takeIf { it.isNotBlank() },
                memo = memo?.takeIf { it.isNotBlank() },
                updatedAt = Instant.now(),
            ),
        )
    }

    /**
     * 거래를 지운다.
     *
     * 지운 행을 그대로 돌려주므로 화면에서 '실행 취소'를 제공할 수 있다.
     * uuid 를 유지한 채 다시 넣으면 백업 기준으로도 같은 거래가 된다.
     */
    suspend fun delete(id: Long): TransactionEntity? {
        val existing = transactionDao.findById(id) ?: return null
        transactionDao.delete(existing)
        return existing
    }

    suspend fun restore(transaction: TransactionEntity) {
        transactionDao.insert(transaction)
    }
}
