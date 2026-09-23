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

    @Insert
    suspend fun insert(paymentMethod: PaymentMethodEntity): Long
}
