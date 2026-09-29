package com.dong.budget.data.backup

import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.DEFAULT_CATEGORIES
import com.dong.budget.data.db.DEFAULT_PAYMENT_METHODS
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.TransactionEntity
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.toEntity
import com.dong.budget.data.salary.PayBasis
import com.dong.budget.data.salary.SalaryRecord
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.toRecord
import com.dong.budget.data.salary.toSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupCodecTest {
    // 기본 분류·결제수단에 번호를 매긴 DB 줄. 식비가 1, 급여가 7, 현금이 101, 체크카드가 102
    private val categories = DEFAULT_CATEGORIES.mapIndexed { i, c -> c.toEntity().copy(id = i + 1L) }
    private val paymentMethods = DEFAULT_PAYMENT_METHODS.mapIndexed { i, m -> m.toEntity().copy(id = i + 101L) }
    private val food = categories.first { it.code == "FOOD" }
    private val salary = categories.first { it.code == "SALARY" }
    private val check = paymentMethods.first { it.name == "체크카드" }

    private val lunch = tx(id = 11, uuid = "t-lunch", amount = 9_800, at = "2026-09-29T03:10:00.123Z", category = food, payment = check)
    private val refund = tx(id = 12, uuid = "t-refund", amount = 9_800, type = TransactionType.REFUND, related = lunch)
    private val pay = tx(id = 13, uuid = "t-pay", amount = 3_000_000, type = TransactionType.INCOME, category = salary, dedupKey = "toss:1")

    private fun tx(
        id: Long,
        uuid: String,
        amount: Long,
        type: TransactionType = TransactionType.EXPENSE,
        at: String = "2026-09-29T00:00:00Z",
        category: CategoryEntity? = null,
        payment: PaymentMethodEntity? = null,
        related: TransactionEntity? = null,
        dedupKey: String? = null,
    ): TransactionEntity {
        val instant = Instant.parse(at)
        return TransactionEntity(
            id = id,
            uuid = uuid,
            type = type,
            amount = amount,
            occurredAt = instant,
            occurredDate = com.dong.budget.data.db.BudgetTime.toDateKey(instant),
            categoryId = category?.id,
            paymentMethodId = payment?.id,
            merchant = "가게$id",
            memo = if (id == 11L) "3개월 할부" else null,
            relatedTransactionId = related?.id,
            dedupKey = dedupKey,
            createdAt = instant.plusSeconds(60),
            updatedAt = instant.plusSeconds(120),
        )
    }

    private fun backup(transactions: List<TransactionEntity> = listOf(lunch, refund, pay)) =
        backupOf(categories, paymentMethods, transactions, appVersion = "1.5.0", exportedAt = Instant.parse("2026-09-29T05:03:00Z"))

    private fun decodeValid(text: String): Backup {
        val read = BackupCodec.decode(text)
        assertTrue("$read", read is BackupRead.Valid)
        return (read as BackupRead.Valid).backup
    }

    private fun reason(text: String): String = (BackupCodec.decode(text) as BackupRead.Invalid).reason

    /** 백업을 JSON 으로 바꾸기 전에 고친다(손으로 고친 백업·망가진 백업 흉내) */
    private fun broken(edit: (Backup) -> Backup): String = BackupCodec.encode(edit(backup()), pretty = false)

    @Test
    fun `내보낸 백업을 읽으면 같은 내용으로 돌아온다`() {
        val original = backup()
        listOf(true, false).forEach { pretty ->
            assertEquals(original, decodeValid(BackupCodec.encode(original, pretty)))
        }
    }

    @Test
    fun `되살린 줄은 번호만 새로 매겨지고 나머지는 그대로다`() {
        val restored = decodeValid(BackupCodec.encode(backup(), pretty = true))
        // 복원처럼 새 번호를 매긴다(분류는 1001부터, 결제수단은 2001부터)
        val categoryIds = restored.categories.mapIndexed { i, c -> c.uuid to 1001L + i }.toMap()
        val paymentIds = restored.paymentMethods.mapIndexed { i, m -> m.uuid to 2001L + i }.toMap()

        assertEquals(categories.map { it.copy(id = 0) }, restored.categories.map { it.toEntity() })
        assertEquals(paymentMethods.map { it.copy(id = 0) }, restored.paymentMethods.map { it.toEntity() })
        val lunchBack = restored.transactions.first { it.uuid == "t-lunch" }.toEntity(categoryIds, paymentIds)
        assertEquals(
            lunch.copy(id = 0, categoryId = categoryIds.getValue(food.uuid), paymentMethodId = paymentIds.getValue(check.uuid)),
            lunchBack,
        )
        // 짝 거래는 번호가 아니라 uuid 로 가리킨다
        assertEquals("t-lunch", restored.transactions.first { it.uuid == "t-refund" }.related)
        assertEquals("toss:1", restored.transactions.first { it.uuid == "t-pay" }.dedupKey)
    }

    @Test
    fun `시각은 서울 기준으로 적고, 다른 시간대로 적혀 있어도 같은 순간으로 읽는다`() {
        val instant = Instant.parse("2026-09-29T03:10:00.123Z")
        assertEquals("2026-09-29T12:10:00.123+09:00", formatBackupTime(instant))
        assertEquals(instant, parseBackupTime("2026-09-29T03:10:00.123Z"))
        assertEquals(instant, parseBackupTime("2026-09-29T12:10:00.123+09:00"))
        assertEquals(null, parseBackupTime("2026-09-29 12:10"))
    }

    @Test
    fun `가리키던 분류나 짝 거래가 없어졌으면 비운 채로 내보낸다`() {
        val orphan = lunch.copy(categoryId = 999, relatedTransactionId = 999)
        val exported = backup(listOf(orphan)).transactions.single()
        assertEquals(null, exported.category)
        assertEquals(null, exported.related)
        assertEquals(check.uuid, exported.paymentMethod)
    }

    @Test
    fun `동계부 백업이 아니면 읽지 않는다`() {
        assertEquals(BackupCodec.NOT_BACKUP, reason(""))
        assertEquals(BackupCodec.NOT_BACKUP, reason("안녕하세요"))
        assertEquals(BackupCodec.NOT_BACKUP, reason("[1, 2]"))
        assertEquals(BackupCodec.NOT_BACKUP, reason("""{"app":"other","format":1}"""))
    }

    @Test
    fun `더 새 판의 백업은 앱을 업데이트하라고 한다`() {
        val text = BackupCodec.encode(backup(), pretty = false).replace("\"format\":1", "\"format\":2")
        assertEquals(BackupCodec.TOO_NEW, reason(text))
    }

    @Test
    fun `앞뒤 빈칸과 모르는 칸은 넘어간다`() {
        val text = BackupCodec.encode(backup(), pretty = false).replaceFirst("{", "{\"newField\":true,")
        assertEquals(backup(), decodeValid("\n  $text  \n"))
        // 편집기가 앞에 붙인 BOM 도 뗀다
        assertEquals(backup(), decodeValid("\uFEFF$text"))
    }

    @Test
    fun `칸이 빠졌거나 모르는 종류면 망가진 백업이다`() {
        val text = BackupCodec.encode(backup(), pretty = false)
        assertEquals(BackupCodec.BROKEN, reason("""{"app":"dong-budget"}"""))
        assertEquals(BackupCodec.BROKEN, reason(text.replace("\"transactions\":", "\"deals\":")))
        assertEquals(BackupCodec.BROKEN, reason(text.replace("\"REFUND\"", "\"GIFT\"")))
    }

    @Test
    fun `DB 가 받아 주지 않을 내용은 망가진 백업이다`() {
        val cases =
            listOf<(Backup) -> Backup>(
                { b -> b.copy(exportedAt = "어제") },
                { b -> b.copy(categories = b.categories + b.categories.first().copy(uuid = "dup-name")) },
                { b -> b.copy(categories = b.categories + b.categories.first().copy(name = "새 이름")) },
                { b -> b.copy(categories = b.categories.filterNot { it.code == "ETC_INCOME" }) },
                { b -> b.copy(categories = b.categories.map { if (it.code == "FOOD") it.copy(name = " ") else it }) },
                { b -> b.copy(paymentMethods = b.paymentMethods + b.paymentMethods.first().copy(uuid = "dup-name")) },
                { b -> b.copy(transactions = b.transactions + b.transactions.first()) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(dedupKey = "same") }) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(amount = 0) }) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(amount = 1_000_000_000_000) }) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(category = "없는 분류") }) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(paymentMethod = "없는 결제수단") }) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(related = "없는 거래") }) },
                { b -> b.copy(transactions = b.transactions.map { it.copy(updatedAt = "나중에") }) },
            )
        cases.forEachIndexed { i, edit -> assertEquals("${i}번째", BackupCodec.BROKEN, reason(broken(edit))) }
        // 12자리 끝까지는 받는다
        decodeValid(broken { b -> b.copy(transactions = b.transactions.map { it.copy(amount = 999_999_999_999) }) })
    }

    @Test
    fun `그릴 수 없는 아이콘·색은 기본 모양으로 되살린다`() {
        val odd = BackupCategory(uuid = "u", scope = food.scope, name = "이상한", icon = "rocket", color = "neon")
        val entity = odd.toEntity()
        assertEquals(CategoryStyle.FALLBACK_ICON, entity.icon)
        assertEquals(CategoryStyle.FALLBACK_COLOR, entity.color)
    }

    @Test
    fun `월급 설정도 담았다가 그대로 읽는다`() {
        val salary = SalarySettings(basis = PayBasis.YEARLY, amount = 48_000_000, payday = 10).toRecord()
        val withSalary = backup().copy(salary = salary)
        assertEquals(withSalary, decodeValid(BackupCodec.encode(withSalary, pretty = true)))
        // 월급을 정하지 않은 백업에는 칸이 아예 없다
        assertTrue("salary" !in BackupCodec.encode(backup(), pretty = false))
    }

    @Test
    fun `월급 금액이 범위를 벗어나면 망가진 백업이고, 모르는 급여 종류는 월급으로 읽는다`() {
        assertEquals(BackupCodec.BROKEN, reason(broken { it.copy(salary = SalaryRecord(amount = 1_000_000_000_000)) }))
        val odd = decodeValid(broken { it.copy(salary = SalaryRecord(basis = "WEEKLY", amount = 3_000_000)) })
        assertEquals(PayBasis.MONTHLY, odd.salary!!.toSettings().basis)
    }
}
