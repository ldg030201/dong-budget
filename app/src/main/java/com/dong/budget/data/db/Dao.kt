package com.dong.budget.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** 목록 화면에 필요한 값만 담은 조회 전용 모델 */
data class TransactionListItem(
    val id: Long,
    val type: TransactionType,
    val amount: Long,
    val occurredAt: Instant,
    val occurredDate: Int,
    val merchant: String?,
    val memo: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val paymentMethodName: String?,
)

/** 결제수단 목록에 거래 건수를 붙인 것 */
data class PaymentMethodWithCount(@Embedded val paymentMethod: PaymentMethodEntity, val transactionCount: Int)

/** 분류 목록에 거래 건수를 붙인 것. 지울 때 '몇 건이 옮겨진다' 를 알려주는 데 쓴다. */
data class CategoryWithCount(@Embedded val category: CategoryEntity, val transactionCount: Int)

@Dao
interface TransactionDao {
    /**
     * 기간 안의 거래를 최신순으로 돌려준다.
     *
     * 필터와 정렬을 모두 occurredAt 으로 맞춘 이유:
     * 필터를 occurredDate 로 걸고 정렬을 occurredAt 으로 하면 컬럼이 서로 달라
     * 인덱스가 범위 스캔에 쓰이지 못한다.
     */
    @Query(
        """
        SELECT t.id, t.type, t.amount, t.occurredAt, t.occurredDate,
               t.merchant, t.memo,
               c.name AS categoryName,
               c.icon AS categoryIcon,
               c.color AS categoryColor,
               p.name AS paymentMethodName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payment_methods p ON p.id = t.paymentMethodId
        WHERE t.occurredAt >= :start AND t.occurredAt < :end
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeBetween(start: Instant, end: Instant): Flow<List<TransactionListItem>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    /**
     * 이 가게로 가장 최근에 등록한 지출의 분류. 알림으로 들어온 결제의 분류를 미리 고를 때 쓴다.
     * 앞뒤 공백은 무시하고 비교한다. 예전에 공백째 저장된 가게 이름('스타벅스 ')도 찾기 위함이다.
     */
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE TRIM(merchant) = TRIM(:merchant) AND type = 'EXPENSE' AND categoryId IS NOT NULL
        ORDER BY occurredAt DESC LIMIT 1
        """,
    )
    suspend fun lastCategoryIdForMerchant(merchant: String): Long?

    /** 알림에서 읽은 결제가 이미 등록됐는지 */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dedupKey = :key)")
    suspend fun existsByDedupKey(key: String): Boolean

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE scope = :scope ORDER BY sortOrder, name")
    fun observeByScope(scope: CategoryScope): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM transactions t WHERE t.categoryId = c.id) AS transactionCount
        FROM categories c
        WHERE c.scope = :scope
        ORDER BY c.sortOrder, c.name
        """,
    )
    fun observeWithCount(scope: CategoryScope): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE scope = :scope AND code = :code LIMIT 1")
    suspend fun findByCode(scope: CategoryScope, code: String): CategoryEntity?

    /** 새 분류를 '기타' 바로 앞에 두기 위한 값 */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE scope = :scope AND isSystem = 0")
    suspend fun maxUserSortOrder(scope: CategoryScope): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE transactions SET categoryId = :toId, updatedAt = :now WHERE categoryId = :fromId")
    suspend fun moveTransactions(fromId: Long, toId: Long, now: Instant)

    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun deleteUserCategory(id: Long): Int

    /** '기타' 는 늘 맨 뒤에 둔다. 사용자가 순서를 바꿀 수 없다. */
    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id AND isSystem = 0")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** 주어진 순서대로 0, 1, 2… 를 매긴다. 한꺼번에 바꿔야 중간에 순서가 섞인 채로 보이지 않는다. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    /**
     * 거래를 먼저 옮기고 분류를 지운다. 둘 중 하나만 되는 일이 없도록 한 트랜잭션으로 묶는다.
     * 옮기기 전에 지우면 외래키 설정 때문에 거래의 분류가 빈 값이 된다.
     */
    @Transaction
    suspend fun deleteMovingTransactions(id: Long, fallbackId: Long, now: Instant): Boolean {
        moveTransactions(fromId = id, toId = fallbackId, now = now)
        return deleteUserCategory(id) > 0
    }
}

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<PaymentMethodEntity>>

    @Query(
        """
        SELECT p.*, (SELECT COUNT(*) FROM transactions t WHERE t.paymentMethodId = p.id) AS transactionCount
        FROM payment_methods p
        ORDER BY p.sortOrder, p.name
        """,
    )
    fun observeWithCount(): Flow<List<PaymentMethodWithCount>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun findById(id: Long): PaymentMethodEntity?

    @Query("SELECT * FROM payment_methods")
    suspend fun getAll(): List<PaymentMethodEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM payment_methods")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE payment_methods SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** 주어진 순서대로 0, 1, 2… 를 매긴다. 한꺼번에 바꿔야 중간에 순서가 섞인 채로 보이지 않는다. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(paymentMethod: PaymentMethodEntity): Long

    @Query("UPDATE transactions SET paymentMethodId = NULL, updatedAt = :now WHERE paymentMethodId = :id")
    suspend fun clearFromTransactions(id: Long, now: Instant)

    @Query("DELETE FROM payment_methods WHERE id = :id AND isSystem = 0")
    suspend fun deleteById(id: Long): Int

    /**
     * 거래에서 이 결제수단을 먼저 비우고 지운다.
     * 외래키의 SET NULL 에 기대지 않고 직접 비우는 이유: 거래의 수정 시각도 함께 남기기 위해서다.
     */
    @Transaction
    suspend fun deleteClearingTransactions(id: Long, now: Instant): Boolean {
        clearFromTransactions(id, now)
        return deleteById(id) > 0
    }
}
