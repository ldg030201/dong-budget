package com.dong.budget.data.backup

import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.card.MAX_PERFORMANCE_START_DAY
import com.dong.budget.data.card.encodePerformanceTiers
import com.dong.budget.data.card.normalizePerformanceStartDay
import com.dong.budget.data.card.performanceTierList
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionEntity
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.etcCodeFor
import com.dong.budget.data.salary.SalaryRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// ─────────────────────────────────────────────────────────────────────
// 백업 한 벌의 모양. 설정의 'JSON으로 복사'·'파일로 내려받기' 가 이 모양으로 내보내고, 복원이 이 모양을 읽는다.
//
// 서로를 DB 번호(id) 대신 uuid 로 가리킨다. 번호는 복원할 때마다 새로 매겨지기 때문이다.
// 시각은 사람이 읽을 수 있게 서울 기준 ISO-8601(2026-09-29T14:03:00+09:00)로 적는다.
// 칸 이름을 바꾸면 예전 백업을 못 읽는다. 모양을 바꿔야 하면 [BACKUP_FORMAT] 을 올리고 옛 모양도 읽게 한다.
// ─────────────────────────────────────────────────────────────────────

/** 동계부 백업이라는 표시 */
const val BACKUP_APP = "dong-budget"

/** 백업 모양의 판. 이 앱이 읽을 수 있는 가장 새 판이기도 하다. */
const val BACKUP_FORMAT = 1

/**
 * 거래·분류·결제수단 전부와 월급 설정. 화면 테마·자동 기능과 알림 목록은 담지 않는다.
 * @property appVersion 만든 앱 버전. 읽을 때 쓰지는 않고, 문제가 생겼을 때 어느 버전에서 만든 것인지 보려고 남긴다.
 * @property salary 월급 설정. 정한 적 없으면 없다(월급 탭이 생기기 전 백업에도 없다). 없으면 되살릴 때 지금 설정을 그대로 둔다.
 */
@Serializable
data class Backup(
    val app: String = BACKUP_APP,
    val format: Int = BACKUP_FORMAT,
    val appVersion: String = "",
    val exportedAt: String,
    val categories: List<BackupCategory>,
    val paymentMethods: List<BackupPaymentMethod>,
    val transactions: List<BackupTransaction>,
    val salary: SalaryRecord? = null,
)

@Serializable
data class BackupCategory(
    val uuid: String,
    val scope: CategoryScope,
    val name: String,
    val code: String? = null,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val icon: String = CategoryStyle.FALLBACK_ICON,
    val color: String = CategoryStyle.FALLBACK_COLOR,
)

/**
 * @property performanceTiers 카드 실적 구간 금액(원, 오름차순). 안 적었으면 비어 있다(실적 칸이 생기기 전 백업에도 없다).
 * @property performanceStartDay 카드 실적을 세기 시작하는 날(매달 N일)
 */
@Serializable
data class BackupPaymentMethod(
    val uuid: String,
    val name: String,
    val type: PaymentMethodType,
    val sortOrder: Int = 0,
    val icon: String = CategoryStyle.FALLBACK_ICON,
    val color: String = CategoryStyle.FALLBACK_COLOR,
    val performanceTiers: List<Long> = emptyList(),
    val performanceStartDay: Int = 1,
)

/**
 * @property category 분류의 uuid. 비웠으면 null
 * @property paymentMethod 결제수단의 uuid. 비웠으면 null
 * @property related 짝 거래(환불·이체)의 uuid
 */
@Serializable
data class BackupTransaction(
    val uuid: String,
    val type: TransactionType,
    val amount: Long,
    val occurredAt: String,
    val category: String? = null,
    val paymentMethod: String? = null,
    val merchant: String? = null,
    val memo: String? = null,
    val related: String? = null,
    val dedupKey: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** 복원할 글을 읽은 결과 */
sealed interface BackupRead {
    data class Valid(val backup: Backup) : BackupRead

    /**
     * @property reason 사용자에게 보여 줄 까닭
     * @property detail 어디가 틀렸는지. 개발자 모드 로그에만 적는다.
     */
    data class Invalid(val reason: String, val detail: String? = null) : BackupRead
}

object BackupCodec {
    /** 비어 있는 칸(null)은 적지 않는다. 읽을 때는 모르는 칸을 건너뛴다(같은 판 안에서 칸이 늘어도 옛 앱이 읽는다). */
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    /** 파일은 사람이 열어 볼 수 있게 줄을 나눈다. 복사는 클립보드 크기 한도가 있어 한 줄로 붙인다. */
    private val prettyJson = Json(json) { prettyPrint = true }

    fun encode(backup: Backup, pretty: Boolean): String = (if (pretty) prettyJson else json).encodeToString(Backup.serializer(), backup)

    /** 동계부 백업인지, 이 앱이 읽을 수 있는 판인지, 내용이 서로 맞는지 본다. 하나라도 어긋나면 아무것도 되살리지 않는다. */
    fun decode(text: String): BackupRead {
        val root =
            // 메모장 같은 편집기로 다시 저장하면 앞에 BOM(U+FEFF)이 붙는다. trim 으로는 안 떨어져서 따로 뗀다.
            runCatching { json.parseToJsonElement(text.trim().removePrefix(BOM).trim()) }.getOrNull() as? JsonObject
                ?: return BackupRead.Invalid(NOT_BACKUP)
        if ((root["app"] as? JsonPrimitive)?.contentOrNull != BACKUP_APP) return BackupRead.Invalid(NOT_BACKUP)
        val format = (root["format"] as? JsonPrimitive)?.intOrNull ?: return BackupRead.Invalid(BROKEN, "판(format)이 없어요")
        if (format > BACKUP_FORMAT) return BackupRead.Invalid(TOO_NEW, "판 $format")
        val backup =
            try {
                json.decodeFromJsonElement<Backup>(root)
            } catch (e: IllegalArgumentException) {
                // 칸이 빠졌거나 모르는 종류(enum)다. SerializationException 도 여기로 온다.
                return BackupRead.Invalid(BROKEN, e.message)
            }
        return problemOf(backup)?.let { BackupRead.Invalid(BROKEN, it) } ?: BackupRead.Valid(backup)
    }

    private const val BOM = "\uFEFF"

    const val NOT_BACKUP = "동계부 백업이 아니에요"
    const val TOO_NEW = "더 새 버전의 동계부에서 만든 백업이에요. 앱을 업데이트한 뒤 다시 해 주세요"
    const val BROKEN = "백업 내용이 망가져서 읽을 수 없어요"
}

/** 되살렸을 때 DB 가 받아 주지 않거나 화면이 깨질 내용. 없으면 null */
internal fun problemOf(backup: Backup): String? {
    if (parseBackupTime(backup.exportedAt) == null) return "만든 때를 읽을 수 없어요: ${backup.exportedAt}"

    val categories = backup.categories
    duplicateOf(categories.map { it.uuid })?.let { return "분류 uuid 가 겹쳐요: $it" }
    duplicateOf(categories.map { it.scope to it.name })?.let { return "분류 이름이 겹쳐요: ${it.second}" }
    categories.firstOrNull { it.name.isBlank() }?.let { return "이름 없는 분류가 있어요: ${it.uuid}" }
    // 분류를 지우면 그 거래가 '기타' 로 옮겨 가므로 '기타' 는 늘 있어야 한다
    CategoryScope.entries.forEach { scope ->
        if (categories.none { it.scope == scope && it.code == etcCodeFor(scope) && it.isSystem }) return "$scope '기타' 분류가 없어요"
    }

    val paymentMethods = backup.paymentMethods
    duplicateOf(paymentMethods.map { it.uuid })?.let { return "결제수단 uuid 가 겹쳐요: $it" }
    duplicateOf(paymentMethods.map { it.name })?.let { return "결제수단 이름이 겹쳐요: $it" }
    paymentMethods.firstOrNull { it.name.isBlank() }?.let { return "이름 없는 결제수단이 있어요: ${it.uuid}" }
    // 구간 개수는 보지 않는다. 되살릴 때 앞에서부터 잘라 넣으므로, 개수 상한이 늘어난 다음 판의 백업도 거절하지 않는다.
    paymentMethods.forEach { m ->
        if (m.performanceTiers.any { it !in 1..MAX_AMOUNT }) return "카드 실적 금액이 범위를 벗어나요: ${m.uuid}"
        if (m.performanceStartDay !in 1..MAX_PERFORMANCE_START_DAY) return "카드 실적 시작일이 범위를 벗어나요: ${m.uuid}"
    }

    val transactions = backup.transactions
    duplicateOf(transactions.map { it.uuid })?.let { return "거래 uuid 가 겹쳐요: $it" }
    duplicateOf(transactions.mapNotNull { it.dedupKey })?.let { return "알림 열쇠가 겹쳐요: $it" }
    val categoryUuids = categories.mapTo(HashSet()) { it.uuid }
    val paymentUuids = paymentMethods.mapTo(HashSet()) { it.uuid }
    val transactionUuids = transactions.mapTo(HashSet()) { it.uuid }
    backup.salary?.let { salary ->
        if (salary.amount !in 0..MAX_AMOUNT || salary.takeHome !in 0..MAX_AMOUNT) return "월급 금액이 범위를 벗어나요"
    }
    transactions.forEach { t ->
        val problem =
            when {
                t.amount !in 1..MAX_AMOUNT -> "금액이 범위를 벗어나요: ${t.amount}"
                listOf(t.occurredAt, t.createdAt, t.updatedAt).any { parseBackupTime(it) == null } -> "시각을 읽을 수 없어요"
                t.category != null && t.category !in categoryUuids -> "없는 분류를 가리켜요"
                t.paymentMethod != null && t.paymentMethod !in paymentUuids -> "없는 결제수단을 가리켜요"
                t.related != null && t.related !in transactionUuids -> "없는 짝 거래를 가리켜요"
                else -> null
            }
        if (problem != null) return "거래 ${t.uuid}: $problem"
    }
    return null
}

/** 등록 화면이 받는 가장 큰 금액(12자리) */
private val MAX_AMOUNT = "9".repeat(MAX_AMOUNT_DIGITS).toLong()

private fun <T> duplicateOf(values: List<T>): T? {
    val seen = HashSet<T>()
    return values.firstOrNull { !seen.add(it) }
}

// ── 시각 ──

/** 백업에 적는 시각. 서울 기준이라 사람이 열어 봐도 그날 그 시각으로 읽힌다. */
fun formatBackupTime(instant: Instant): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(BudgetTime.ZONE))

/** 백업의 시각. 다른 시간대로 적혀 있어도 같은 순간으로 읽는다. 읽을 수 없으면 null */
fun parseBackupTime(text: String): Instant? = try {
    OffsetDateTime.parse(text).toInstant()
} catch (_: DateTimeParseException) {
    null
}

// ── DB 줄과 백업 사이 ──

/** 지금 DB 의 줄들을 백업 한 벌로. 서로 가리키는 번호는 uuid 로 바꾼다(가리키던 줄이 없으면 비운다). */
fun backupOf(
    categories: List<CategoryEntity>,
    paymentMethods: List<PaymentMethodEntity>,
    transactions: List<TransactionEntity>,
    appVersion: String,
    exportedAt: Instant,
): Backup {
    val categoryUuids = categories.associate { it.id to it.uuid }
    val paymentUuids = paymentMethods.associate { it.id to it.uuid }
    val transactionUuids = transactions.associate { it.id to it.uuid }
    return Backup(
        appVersion = appVersion,
        exportedAt = formatBackupTime(exportedAt),
        categories =
        categories.map {
            BackupCategory(it.uuid, it.scope, it.name, it.code, it.sortOrder, it.isSystem, it.icon, it.color)
        },
        paymentMethods =
        paymentMethods.map {
            BackupPaymentMethod(
                uuid = it.uuid,
                name = it.name,
                type = it.type,
                sortOrder = it.sortOrder,
                icon = it.icon,
                color = it.color,
                performanceTiers = it.performanceTierList,
                performanceStartDay = it.performanceStartDay,
            )
        },
        transactions =
        transactions.map { t ->
            BackupTransaction(
                uuid = t.uuid,
                type = t.type,
                amount = t.amount,
                occurredAt = formatBackupTime(t.occurredAt),
                category = t.categoryId?.let(categoryUuids::get),
                paymentMethod = t.paymentMethodId?.let(paymentUuids::get),
                merchant = t.merchant,
                memo = t.memo,
                related = t.relatedTransactionId?.let(transactionUuids::get),
                dedupKey = t.dedupKey,
                createdAt = formatBackupTime(t.createdAt),
                updatedAt = formatBackupTime(t.updatedAt),
            )
        },
    )
}

/** 화면이 그릴 수 없는 아이콘·색 이름은 기본 모양으로 바꿔 넣는다(손으로 고친 백업). */
fun BackupCategory.toEntity(): CategoryEntity = CategoryEntity(
    uuid = uuid,
    scope = scope,
    name = name,
    code = code,
    sortOrder = sortOrder,
    isSystem = isSystem,
    icon = CategoryStyle.iconOrFallback(icon),
    color = CategoryStyle.colorOrFallback(color),
)

/** 카드 실적은 저장소와 같은 규칙으로 정리해 넣는다(손으로 고친 백업의 순서·중복·개수). */
fun BackupPaymentMethod.toEntity(): PaymentMethodEntity = PaymentMethodEntity(
    uuid = uuid,
    name = name,
    type = type,
    sortOrder = sortOrder,
    icon = CategoryStyle.iconOrFallback(icon),
    color = CategoryStyle.colorOrFallback(color),
    performanceTiers = encodePerformanceTiers(performanceTiers),
    performanceStartDay = normalizePerformanceStartDay(performanceStartDay),
)

/**
 * 검사를 마친 백업의 거래를 DB 줄로. 짝 거래는 모두 넣은 뒤에 잇는다(번호를 아직 모른다).
 * @param categoryIds 분류 uuid → 새로 넣은 번호
 * @param paymentMethodIds 결제수단 uuid → 새로 넣은 번호
 */
fun BackupTransaction.toEntity(categoryIds: Map<String, Long>, paymentMethodIds: Map<String, Long>): TransactionEntity {
    val occurred = checkNotNull(parseBackupTime(occurredAt))
    return TransactionEntity(
        uuid = uuid,
        type = type,
        amount = amount,
        occurredAt = occurred,
        occurredDate = BudgetTime.toDateKey(occurred),
        categoryId = category?.let(categoryIds::getValue),
        paymentMethodId = paymentMethod?.let(paymentMethodIds::getValue),
        merchant = merchant,
        memo = memo,
        dedupKey = dedupKey,
        createdAt = checkNotNull(parseBackupTime(createdAt)),
        updatedAt = checkNotNull(parseBackupTime(updatedAt)),
    )
}
