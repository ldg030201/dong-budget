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
    /** 분류·결제수단별 통계에서 묶는 기준. 이름은 같을 수 있어도 id 는 다르다. 지웠거나 비웠으면 null */
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    /** 결제수단별 통계의 뱃지와 차트 색 */
    val paymentMethodIcon: String? = null,
    val paymentMethodColor: String? = null,
    /** 설정의 분류 목록 순서. 날마다 쓴 돈이 이 순서로 쌓는다. 비웠거나 지웠으면 null */
    val categorySortOrder: Int? = null,
)

/** [TransactionListItem] 을 읽는 SELECT. 뒤에 WHERE·ORDER BY 를 붙여 쓴다. 목록·상세가 같은 값을 읽게 한 곳에 둔다. */
private const val LIST_ITEM_SELECT =
    """
        SELECT t.id, t.type, t.amount, t.occurredAt, t.occurredDate,
               t.merchant, t.memo,
               c.name AS categoryName,
               c.icon AS categoryIcon,
               c.color AS categoryColor,
               p.name AS paymentMethodName,
               t.categoryId, t.paymentMethodId,
               p.icon AS paymentMethodIcon,
               p.color AS paymentMethodColor,
               c.sortOrder AS categorySortOrder
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN payment_methods p ON p.id = t.paymentMethodId
    """

/**
 * 기간 안([start], [end])에 코드가 붙은 분류(기본 분류)의 수입이 있는지. 직접 적은 월급을 찾는다.
 * 월급날 알림·월급 탭으로 등록한 월급(열쇠가 'salary:' 로 시작)은 자기 달의 열쇠로만 센다. 월급날을 바꿔 기간이 옮겨 가도
 * 다른 달 월급이 이번 달 월급으로 잡히지 않게 하기 위함이다.
 */
private const val INCOME_WITH_CODE_EXISTS =
    """
        SELECT EXISTS(
            SELECT 1 FROM transactions t JOIN categories c ON c.id = t.categoryId
            WHERE t.type = 'INCOME' AND c.code = :code AND t.occurredAt >= :start AND t.occurredAt < :end
              AND (t.dedupKey IS NULL OR t.dedupKey NOT LIKE 'salary:%')
        )
    """

/** 결제수단 목록에 거래 건수를 붙인 것 */
data class PaymentMethodWithCount(@Embedded val paymentMethod: PaymentMethodEntity, val transactionCount: Int)

/** 분류 목록에 거래 건수를 붙인 것. 지울 때 '몇 건이 옮겨진다' 를 알려주는 데 쓴다. */
data class CategoryWithCount(@Embedded val category: CategoryEntity, val transactionCount: Int)

/** 결제수단 하나를 지출·환불에 처음 쓴 시각. 카드실적이 그 카드로 기록하기 전 기간을 가릴 때 쓴다. */
data class PaymentMethodFirstUse(val paymentMethodId: Long, val firstAt: Instant)

/** 카드실적 탭이 읽는 거래 한 줄. 쓴 돈(지출 − 환불)을 세는 칸만 둔다. */
data class CardSpendRow(val paymentMethodId: Long, val type: TransactionType, val amount: Long, val occurredAt: Instant)

/** 기본 분류 코드를 돌려줄지 볼 분류 한 줄([reclaimedCodes]) */
data class CategoryCodeRow(val id: Long, val scope: CategoryScope, val name: String, val code: String?)

/** [CategoryCodeRow] 를 읽는 SELECT. 같은 이름이 여럿이면 id 가 작은 것을 고르게 id 순서다. DB 를 열 때도 같은 글로 읽는다. */
internal const val CATEGORY_CODE_ROWS_SQL = "SELECT id, scope, name, code FROM categories ORDER BY id"

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
        LIST_ITEM_SELECT +
            """
        WHERE t.occurredAt >= :start AND t.occurredAt < :end
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeBetween(start: Instant, end: Instant): Flow<List<TransactionListItem>>

    /**
     * 기간 안에 코드가 [code] 인 분류(기본 분류)의 지출을 최신순으로. 고정지출 탭이 쓴다.
     * 그 분류를 지웠으면 거래가 '기타' 로 옮겨 가서 아무것도 안 나온다. 날짜를 앞으로 적어 둔 거래도 [end] 앞이면 들어온다.
     * 분류는 번호 목록으로 걸러 categoryId 인덱스로 그 분류 거래만 읽는다(코드로 이어 붙여 거르면 기간 안의 모든 거래를 읽었다).
     */
    @Query(
        LIST_ITEM_SELECT +
            """
        WHERE t.categoryId IN (SELECT id FROM categories WHERE code = :code)
          AND t.type = 'EXPENSE' AND t.occurredAt >= :start AND t.occurredAt < :end
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeExpensesWithCategoryCode(code: String, start: Instant, end: Instant): Flow<List<TransactionListItem>>

    /** 기간 안에 결제수단 [paymentMethodId] 로 쓴 지출과 환불을 최신순으로. 카드실적 상세가 쓴다(쓴 돈 = 지출 − 환불). */
    @Query(
        LIST_ITEM_SELECT +
            """
        WHERE t.paymentMethodId = :paymentMethodId AND t.type IN ('EXPENSE', 'REFUND')
          AND t.occurredAt >= :start AND t.occurredAt < :end
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeByPaymentMethod(paymentMethodId: Long, start: Instant, end: Instant): Flow<List<TransactionListItem>>

    /** 모든 거래, 최근 것부터. 내역 화면이 한 번에 읽어 검색한다. */
    @Query(LIST_ITEM_SELECT + " ORDER BY t.occurredAt DESC, t.id DESC")
    fun observeAll(): Flow<List<TransactionListItem>>

    /** 거래 한 건(분류·결제수단 이름과 색 포함). 고치면 새 값을, 지우면 null 을 내보낸다. 거래 상세가 쓴다. */
    @Query(LIST_ITEM_SELECT + " WHERE t.id = :id")
    fun observeItem(id: Long): Flow<TransactionListItem?>

    /**
     * [start] 부터 끝 없이, 가게 이름이 있는 거래를 최신순으로. 거래 상세의 '같은 곳' 내역이 쓴다.
     * 날짜를 앞으로 적어 둔 거래도 들어온다. 같은 가게인지(띄어쓰기·대소문자 무시)는 SQL 로 같은 규칙을 만들 수 없어 앱이 가린다.
     */
    @Query(
        LIST_ITEM_SELECT +
            """
        WHERE t.occurredAt >= :start AND t.merchant IS NOT NULL
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeWithMerchantSince(start: Instant): Flow<List<TransactionListItem>>

    /** [end] 앞의 지출을 최신순으로 [limit] 건. 월급 탭의 최근 내역이 쓴다. 날짜를 앞으로 적어 둔 거래는 [end] 로 뺀다. */
    @Query(
        LIST_ITEM_SELECT +
            """
        WHERE t.type = 'EXPENSE' AND t.occurredAt < :end
        ORDER BY t.occurredAt DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecentExpenses(end: Instant, limit: Int): Flow<List<TransactionListItem>>

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

    /** 통계의 기록 시작 시각. 이체는 통계에서 빼므로 여기서도 뺀다. 거래가 없으면 null */
    @Query("SELECT MIN(occurredAt) FROM transactions WHERE type != 'TRANSFER'")
    fun observeFirstOccurredAt(): Flow<Instant?>

    /**
     * 결제수단마다 지출·환불을 처음 쓴 시각. 카드실적의 쓴 돈과 같은 거래만 센다(이체·수입은 뺀다).
     * 한 번도 안 쓴 결제수단과 결제수단이 없는 거래는 들어 있지 않다. 날짜를 앞으로 적어 둔 거래도 센다.
     */
    @Query(
        """
        SELECT paymentMethodId, MIN(occurredAt) AS firstAt FROM transactions
        WHERE paymentMethodId IS NOT NULL AND type IN ('EXPENSE', 'REFUND')
        GROUP BY paymentMethodId
        """,
    )
    fun observeFirstUseByPaymentMethod(): Flow<List<PaymentMethodFirstUse>>

    /**
     * 기간 안에 결제수단으로 쓴 지출과 환불. 카드실적 탭이 카드마다 쓴 돈을 센다.
     * 목록 조회와 달리 분류·결제수단을 붙이지 않고 필요한 칸만 읽어, 분류·결제수단을 고쳐도 다시 읽지 않는다.
     */
    @Query(
        """
        SELECT paymentMethodId, type, amount, occurredAt FROM transactions
        WHERE paymentMethodId IS NOT NULL AND type IN ('EXPENSE', 'REFUND') AND occurredAt >= :start AND occurredAt < :end
        """,
    )
    fun observeCardSpending(start: Instant, end: Instant): Flow<List<CardSpendRow>>

    /**
     * 결제수단 [paymentMethodId] 하나를 지출·환불에 처음 쓴 시각. 한 번도 안 썼으면 null. 카드실적 상세가 쓴다.
     * 모든 결제수단을 묶어 세는 위 조회와 달리 (결제수단, 시각) 인덱스로 그 카드 기록만 본다.
     */
    @Query("SELECT MIN(occurredAt) FROM transactions WHERE paymentMethodId = :paymentMethodId AND type IN ('EXPENSE', 'REFUND')")
    fun observeFirstUseOf(paymentMethodId: Long): Flow<Instant?>

    /** 알림에서 읽은 결제가 이미 등록됐는지 */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dedupKey = :key)")
    suspend fun existsByDedupKey(key: String): Boolean

    /** [keys] 중 이미 등록된 결제의 열쇠. 알림 목록에 '등록함' 을 붙일 때 쓴다. */
    @Query("SELECT dedupKey FROM transactions WHERE dedupKey IN (:keys)")
    fun observeRegisteredKeys(keys: List<String>): Flow<List<String>>

    /** 기간 안에 코드가 [code] 인 분류의 수입이 있는지. 월급을 직접 적어 등록했는지 볼 때 쓴다. */
    @Query(INCOME_WITH_CODE_EXISTS)
    fun observeIncomeWithCategoryCode(code: String, start: Instant, end: Instant): Flow<Boolean>

    @Query(INCOME_WITH_CODE_EXISTS)
    suspend fun hasIncomeWithCategoryCode(code: String, start: Instant, end: Instant): Boolean

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
     * 지운 것이 '고정지출'·'급여' 였고 띄어쓰기만 다른 같은 이름의 분류가 남아 있으면 그 분류가 코드를 잇는다(DB 를 다시 열 때와 같게).
     */
    @Transaction
    suspend fun deleteMovingTransactions(id: Long, fallbackId: Long, now: Instant): Boolean {
        moveTransactions(fromId = id, toId = fallbackId, now = now)
        val deleted = deleteUserCategory(id) > 0
        if (deleted) reclaimDefaultCodes()
        return deleted
    }

    @Query(CATEGORY_CODE_ROWS_SQL)
    suspend fun codeRows(): List<CategoryCodeRow>

    @Query("UPDATE categories SET code = :code WHERE id = :id AND code IS NULL")
    suspend fun setCodeIfMissing(id: Long, code: String)

    /**
     * 지운 뒤 코드 없이 다시 만든 기본 분류('고정지출'·'급여')에 코드를 돌려준다([reclaimedCodes]).
     * 백업을 되살린 뒤 · 분류를 지운 뒤에 부른다. DB 를 열 때는 같은 일을 reclaimDefaultCodes(db) 가 한다.
     */
    @Transaction
    suspend fun reclaimDefaultCodes() {
        reclaimedCodes(codeRows()).forEach { (id, code) -> setCodeIfMissing(id, code) }
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

    /** 결제수단 하나. 고치면 새 값을, 지우면 null 을 내보낸다. 카드실적 상세·편집이 쓴다. */
    @Query("SELECT * FROM payment_methods WHERE id = :id")
    fun observeById(id: Long): Flow<PaymentMethodEntity?>

    @Query("SELECT * FROM payment_methods")
    suspend fun getAll(): List<PaymentMethodEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM payment_methods")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE payment_methods SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** 카드 실적 칸만 고친다. 정리는 부르는 쪽(PaymentMethodRepository.setPerformance)이 한다. @return 고친 줄 수 */
    @Query("UPDATE payment_methods SET performanceTiers = :tiers, performanceStartDay = :startDay WHERE id = :id")
    suspend fun updatePerformance(id: Long, tiers: String?, startDay: Int): Int

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

/**
 * 백업·복원·데이터 초기화. 표를 통째로 읽고, 비우고, 채운다.
 * 한 번에 끝나야 하는 묶음(비우고 채우기)은 BackupRepository 가 한 트랜잭션 안에서 부른다.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories ORDER BY scope, sortOrder, name")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM payment_methods ORDER BY sortOrder, name")
    suspend fun paymentMethods(): List<PaymentMethodEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt, id")
    suspend fun transactions(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    /** 거래를 먼저 지운다. 분류·결제수단을 먼저 지우면 외래키(SET NULL)가 곧 지울 거래를 하나하나 고친다. */
    @Transaction
    suspend fun deleteAll() {
        deleteTransactions()
        deleteCategories()
        deletePaymentMethods()
    }

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Query("DELETE FROM payment_methods")
    suspend fun deletePaymentMethods()

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert
    suspend fun insertPaymentMethod(paymentMethod: PaymentMethodEntity): Long

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    /** 환불·이체의 짝. 짝이 될 거래가 먼저 들어가 있어야 번호를 알 수 있어서, 모두 넣은 뒤에 잇는다. */
    @Query("UPDATE transactions SET relatedTransactionId = :relatedId WHERE id = :id")
    suspend fun setRelated(id: Long, relatedId: Long)
}
