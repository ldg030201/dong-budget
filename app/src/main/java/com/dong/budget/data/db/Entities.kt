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

/**
 * 지출 쪽 목록인지 수입 쪽 목록인지. 분류와 결제수단이 같이 쓴다.
 * 결제수단은 지출 쪽이 '결제수단', 수입 쪽이 '들어온 곳'(현금·통장 등) 목록이다(DB 6). 두 목록은 따로라 하나는 한 목록에만 든다.
 */
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
// 예외: 카드실적은 결제수단 하나(등치)의 기간을 읽어서 (paymentMethodId, occurredAt) 복합 인덱스를 둔다(DB 4).
// 선두가 paymentMethodId 라 외래키 인덱스도 겸해 단일 paymentMethodId 인덱스는 지웠다.
// ─────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "transactions",
    indices = [
        Index("occurredAt"),
        Index("occurredDate"),
        Index("categoryId"),
        Index("paymentMethodId", "occurredAt"),
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

/** 아이콘과 색이 붙은 분류·결제수단. 등록 화면의 표와 관리 목록이 둘을 같은 모양으로 보여준다. */
interface StyledItem {
    val id: Long
    val name: String

    /** 아이콘 이름. [CategoryStyle.ICONS] 중 하나 */
    val icon: String

    /** 색 이름. [CategoryStyle.COLORS] 중 하나 */
    val color: String

    /** 지울 수 없는 것인지 */
    val isSystem: Boolean
}

/** 이미 쓰고 있는 색들. 새로 만들 것의 색을 겹치지 않게 고를 때 쓴다. */
fun Iterable<StyledItem>.colors(): Set<String> = mapTo(mutableSetOf()) { it.color }

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["scope", "name"], unique = true),
        Index(value = ["scope", "sortOrder"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    val uuid: String,
    val scope: CategoryScope,
    override val name: String,
    /** 기본 제공 분류를 알아보기 위한 코드. 사용자가 만든 것은 null */
    val code: String? = null,
    val sortOrder: Int = 0,
    /**
     * 지울 수 없는 분류인지. 지금은 '기타' 만 true 다.
     * 다른 분류를 지우면 그 거래가 '기타' 로 옮겨가므로 '기타' 는 항상 있어야 한다.
     */
    override val isSystem: Boolean = false,
    @ColumnInfo(defaultValue = CategoryStyle.FALLBACK_ICON) override val icon: String = CategoryStyle.FALLBACK_ICON,
    @ColumnInfo(defaultValue = CategoryStyle.FALLBACK_COLOR) override val color: String = CategoryStyle.FALLBACK_COLOR,
) : StyledItem

/**
 * 결제수단(지출)과 들어온 곳(수입). 분류처럼 [scope] 로 두 목록을 나눈다(DB 6).
 * 이름은 목록 안에서만 겹치지 않으면 된다. 결제수단 '현금' 과 들어온 곳 '현금' 이 함께 있다.
 */
@Entity(
    tableName = "payment_methods",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["scope", "name"], unique = true),
        Index(value = ["scope", "sortOrder"]),
    ],
)
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    val uuid: String,
    /**
     * 어느 목록인지. 지출이면 결제수단, 수입이면 들어온 곳이다. DB 5 까지의 줄은 모두 결제수단이 된다(SQL 기본값).
     * SQL 기본값과 Kotlin 기본값이 같아야 칸을 더할 때와 새로 만들 때 같은 줄이 된다.
     */
    @ColumnInfo(defaultValue = "EXPENSE") val scope: CategoryScope = CategoryScope.EXPENSE,
    override val name: String,
    val type: PaymentMethodType,
    val sortOrder: Int = 0,
    /** 결제수단은 비워둘 수 있어서 지금은 전부 false 다. */
    override val isSystem: Boolean = false,
    @ColumnInfo(defaultValue = CategoryStyle.FALLBACK_ICON) override val icon: String = CategoryStyle.FALLBACK_ICON,
    @ColumnInfo(defaultValue = CategoryStyle.FALLBACK_COLOR) override val color: String = CategoryStyle.FALLBACK_COLOR,
    /**
     * 카드 실적 구간 금액(원)을 오름차순으로 쉼표로 이은 글("300000,700000"). 실적을 안 적었으면 null.
     * 읽고 쓰기는 data/card/CardPerformance.kt 의 함수를 거친다(망가진 조각을 버리고 정리한다).
     * SQL 기본값과 Kotlin 기본값이 같아야 새 설치(SeedCallback)와 데이터 초기화(toEntity)가 같은 줄을 넣는다.
     */
    @ColumnInfo(defaultValue = "NULL") val performanceTiers: String? = null,
    /** 카드 실적을 세기 시작하는 날(매달 N일, 1~31). 그 달에 없는 날이면 말일부터 센다. */
    @ColumnInfo(defaultValue = "1") val performanceStartDay: Int = 1,
) : StyledItem

/** 고정지출 목록에서 한 곳을 어떻게 정했는지 */
enum class FixedMerchantState {
    /** 고정지출 탭에 보인다 */
    REGISTERED,

    /** 탭에 넣지 않는다. 묻던 것에 '안 함' 을 눌렀거나 탭에서 뺐다. 정한 뒤에 그곳의 '고정지출' 지출이 새로 생기면 다시 묻는다. */
    DECLINED,
}

/**
 * 고정지출 목록(DB 7). '고정지출' 분류와 따로, 고정지출 탭에 어느 곳을 보일지 정해 둔다.
 * 분류는 거래마다 그대로 두고, 탭은 등록한 곳의 '고정지출' 분류 지출만 센다. 정한 적 없는 곳은 탭이 등록할지 묻는다.
 *
 * @property merchantKey 고정지출 탭이 가게를 묶는 열쇠([fixedMerchantKey]). 가게 이름 없이 적은 지출은 [NO_MERCHANT_KEY] 다.
 * @property decidedAt 정한 때. [FixedMerchantState.DECLINED] 면 이때 뒤에 낸(그리고 적은) '고정지출' 지출이 생겨야 다시 묻는다.
 */
@Entity(tableName = "fixed_merchants")
data class FixedMerchantEntity(@PrimaryKey val merchantKey: String, val state: FixedMerchantState, val decidedAt: Instant)
