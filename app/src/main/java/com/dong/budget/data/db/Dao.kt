package com.dong.budget.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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
    val paymentMethodName: String?,
)

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

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long
}

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<PaymentMethodEntity>>

    @Insert
    suspend fun insert(paymentMethod: PaymentMethodEntity): Long
}
