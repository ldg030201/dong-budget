package com.dong.budget.data

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.data.db.TransactionEntity
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import com.dong.budget.data.salary.salaryMonthOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** 금액 자리수 상한. 원 단위라 12자리면 9,999억 9,999만 9,999원까지다(1조는 13자리). 등록 화면과 결제 알림 읽기가 같이 쓴다. */
const val MAX_AMOUNT_DIGITS = 12

/**
 * 금액 상한. [MAX_AMOUNT_DIGITS] 자리를 9로 채운 999,999,999,999원으로, 등록 화면이 받는 가장 큰 금액이다.
 * 백업 검사·카드 실적 구간·미리 채우기(월급·고정지출)가 모두 이것을 써서 한 곳이 받는 금액을 다른 곳이 버리지 않게 한다.
 */
const val MAX_AMOUNT = 999_999_999_999L

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

    /**
     * 결제수단마다 지출·환불을 처음 쓴 날(서울 기준). 카드실적이 그 카드로 기록하기 전 기간을 '기록 없음' 으로 둔다.
     * 한 번도 안 쓴 결제수단은 들어 있지 않다.
     */
    fun observeFirstUseDates(): Flow<Map<Long, LocalDate>> = transactionDao.observeFirstUseByPaymentMethod()
        .map { rows -> rows.associate { it.paymentMethodId to BudgetTime.toLocalDate(it.firstAt) } }
        // 거래를 쓸 때마다 Room 이 다시 내보내지만 첫 사용일은 거의 그대로다. 같은 값이면 카드실적을 다시 계산하지 않게 거른다.
        .distinctUntilChanged()

    /** 결제수단 [paymentMethodId] 를 지출·환불에 처음 쓴 날(서울 기준). 한 번도 안 썼으면 null. 카드실적 상세가 쓴다. */
    fun observeFirstUseDate(paymentMethodId: Long): Flow<LocalDate?> = transactionDao.observeFirstUseOf(paymentMethodId)
        .map { it?.let(BudgetTime::toLocalDate) }
        // 거래를 쓸 때마다 Room 이 다시 내보내지만 첫 사용일은 거의 그대로다. 같은 값이면 다시 계산하지 않게 거른다.
        .distinctUntilChanged()

    /** [from] 날(서울 0시)부터 [until] 날 0시 전까지의 거래. 카드실적 기간처럼 달 경계를 넘는 범위를 읽을 때 쓴다. */
    fun observeBetween(from: LocalDate, until: LocalDate): Flow<List<TransactionListItem>> =
        transactionDao.observeBetween(from.startInstant(), until.startInstant())

    /**
     * [first] 달 1일부터 [last] 달 말일까지, 코드가 [code] 인 분류(고정지출 'FIXED')의 지출. 고정지출 탭이 쓴다.
     * 그 분류를 지웠으면 빈 목록이다.
     */
    fun observeExpensesWithCategoryCode(code: String, first: YearMonth, last: YearMonth): Flow<List<TransactionListItem>> =
        transactionDao.observeExpensesWithCategoryCode(code, BudgetTime.monthRange(first).first, BudgetTime.monthRange(last).second)

    /** [from] 날(서울 0시)부터 [until] 날 0시 전까지 결제수단 [paymentMethodId] 로 쓴 지출·환불. 카드실적 상세가 쓴다. */
    fun observeByPaymentMethod(paymentMethodId: Long, from: LocalDate, until: LocalDate): Flow<List<TransactionListItem>> =
        transactionDao.observeByPaymentMethod(paymentMethodId, from.startInstant(), until.startInstant())

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

    /** [date] 하루(서울 0시~다음 날 0시)의 거래. 월급 탭의 '오늘 쓴 돈' 이 쓴다. */
    fun observeDay(date: LocalDate): Flow<List<TransactionListItem>> = transactionDao.observeBetween(
        date.atStartOfDay(BudgetTime.ZONE).toInstant(),
        date.plusDays(1).atStartOfDay(BudgetTime.ZONE).toInstant(),
    )

    /** [today] 까지(앞으로의 날짜로 적은 거래는 빼고) 최근 지출 [limit] 건. 월급 탭의 최근 내역이 쓴다. */
    fun observeRecentExpenses(today: LocalDate, limit: Int): Flow<List<TransactionListItem>> =
        transactionDao.observeRecentExpenses(today.plusDays(1).atStartOfDay(BudgetTime.ZONE).toInstant(), limit)

    /**
     * 한 달 치 월급을 등록했는지. 월급날 알림·월급 탭으로 등록한 열쇠([key])가 있거나,
     * 그 월급을 받는 기간([from]~[until] 전날)에 급여 분류([salaryCode])의 수입을 직접 적었으면 등록한 것으로 본다.
     */
    fun observeSalaryRegistered(key: String, salaryCode: String, from: LocalDate, until: LocalDate): Flow<Boolean> = combine(
        transactionDao.observeRegisteredKeys(listOf(key)).map { it.isNotEmpty() },
        transactionDao.observeIncomeWithCategoryCode(salaryCode, from.startInstant(), until.startInstant()),
    ) { byKey, byCategory -> byKey || byCategory }.distinctUntilChanged()

    /** [observeSalaryRegistered] 를 한 번만 본다(월급날 알림을 띄우기 전) */
    suspend fun isSalaryRegistered(key: String, salaryCode: String, from: LocalDate, until: LocalDate): Boolean =
        transactionDao.existsByDedupKey(key) ||
            transactionDao.hasIncomeWithCategoryCode(salaryCode, from.startInstant(), until.startInstant())

    private fun LocalDate.startInstant(): Instant = atStartOfDay(BudgetTime.ZONE).toInstant()

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
                // 월급으로 등록한 거래를 수입이 아닌 것으로 고치면 더는 월급이 아니다. 그달 월급을 등록한 것으로 치지 않게 열쇠를 뗀다.
                dedupKey = existing.dedupKey.takeUnless { salaryMonthOf(it.orEmpty()) != null && type != TransactionType.INCOME },
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
