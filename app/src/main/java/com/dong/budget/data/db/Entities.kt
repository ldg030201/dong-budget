package com.dong.budget.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** 거래 종류. 금액은 항상 양수로 저장하고 부호의 의미는 이 값이 결정한다. */
enum class TransactionType {
    /** 지출 */
    EXPENSE,

    /** 수입 */
    INCOME,

    /** 내 계좌 사이 이체. 수입도 지출도 아니라서 통계에서 제외한다 */
    TRANSFER,

    /** 환불. 원거래를 relatedTransactionId 로 가리킨다 */
    REFUND,
}

enum class CategoryScope {
    EXPENSE,
    INCOME,
}

enum class PaymentMethodType {
    CASH,
    CARD,
    ACCOUNT,
    OTHER,
}

// ─────────────────────────────────────────────────────────────────────
// 인덱스 설계 메모
//
// 통계 쿼리는 categoryId 나 type 에 등치 조건을 걸지 않고 GROUP BY 를 한다.
// 그래서 (categoryId, occurredDate) 같은 복합 인덱스를 만들면 선두 컬럼이 비어
// SQLite 가 skip-scan 으로 떨어진다. 복합 인덱스는 선두가 등치로 고정될 때만 의미가 있다.
// 결론: 기간 필터용 인덱스 + 외래키용 단일 컬럼 인덱스로 간다.
// ─────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "transactions",
    indices = [
        Index("occurredAt"),
        Index("occurredDate"),
        Index("categoryId"),
        Index("paymentMethodId"),
        Index("relatedTransactionId"),
        Index(value = ["uuid"], unique = true),
        Index(value = ["dedupKey"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 백업 복원과 기기 간 이동에서 같은 거래를 알아보기 위한 식별자 */
    val uuid: String,
    val type: TransactionType,
    /** 원 단위. 항상 0보다 크다 */
    val amount: Long,
    val occurredAt: Instant,
    /** yyyyMMdd. 일자 그룹핑 전용 비정규화 컬럼 */
    val occurredDate: Int,
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    /** 가맹점 또는 거래처 */
    val merchant: String? = null,
    val memo: String? = null,
    /** 환불이나 이체에서 짝이 되는 거래 */
    val relatedTransactionId: Long? = null,
    /**
     * 알림에서 자동 등록할 때 중복을 막는 키. 수동 입력은 null 이다.
     * UNIQUE 인덱스가 걸려 있지만 SQLite 에서 NULL 은 서로 달라서 수동 입력끼리는 충돌하지 않는다.
     */
    @ColumnInfo(defaultValue = "NULL") val dedupKey: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["scope", "name"], unique = true),
        Index(value = ["scope", "sortOrder"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val scope: CategoryScope,
    val name: String,
    /** 기본 제공 분류를 알아보기 위한 코드. 사용자가 만든 것은 null */
    val code: String? = null,
    val sortOrder: Int = 0,
    /**
     * 지울 수 없는 분류인지. 지금은 '기타' 만 true 다.
     * 다른 분류를 지우면 그 거래가 '기타' 로 옮겨가므로 '기타' 는 항상 있어야 한다.
     */
    val isSystem: Boolean = false,
    /** 아이콘 이름. [CategoryStyle.ICONS] 중 하나 */
    @ColumnInfo(defaultValue = CategoryStyle.FALLBACK_ICON) val icon: String = CategoryStyle.FALLBACK_ICON,
    /** 색 이름. [CategoryStyle.COLORS] 중 하나 */
    @ColumnInfo(defaultValue = CategoryStyle.FALLBACK_COLOR) val color: String = CategoryStyle.FALLBACK_COLOR,
)

@Entity(
    tableName = "payment_methods",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["name"], unique = true),
        Index(value = ["sortOrder"]),
    ],
)
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val type: PaymentMethodType,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
)
