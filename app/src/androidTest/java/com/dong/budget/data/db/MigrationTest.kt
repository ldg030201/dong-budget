package com.dong.budget.data.db

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 0.1.2 이하(DB 1)·1.6.1 이하(DB 2)·1.7.0(DB 4)·1.8.1 이하(DB 5)·1.9.0(DB 6)에서 올리는 사용자의 가계부가 마이그레이션 뒤에도 그대로 남는지 실제 SQLite 로 확인한다.
 *
 * 옛 앱이 첫 설치 때 넣던 기본 분류·결제수단(v0.1.2 의 SeedCallback)을 그대로 만들고, 거래를 몇 건 넣은 뒤 2·3·4 로 올린다.
 * 원칙은 '거래는 하나도 잃지 않는다' 다(Migration1To2).
 *
 * 기기에서 돈다. 테스트용 DB 이름을 따로 써서 기기에 있는 실제 가계부(dong-budget.db)는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BudgetDatabase::class.java,
            listOf(Migration1To2(), Migration4To5(), Migration5To6(), Migration6To7()),
        )

    @Test
    fun `옛 가계부를 올려도 거래는 하나도 잃지 않는다`() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedLikeVersion1()
            // 옛 기본 분류로 적은 거래(식비, 카페·간식, 부수입), 옛 '기타', 사용자가 만든 분류로 적은 거래
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem) VALUES ('user:1', 'EXPENSE', '데이트', NULL, 20, 0)",
            )
            insertTransaction("t1", "EXPENSE", 12_000, categoryCode = "FOOD", payment = "CASH")
            insertTransaction("t2", "EXPENSE", 4_500, categoryCode = "CAFE", payment = "CHECK_CARD")
            insertTransaction("t3", "INCOME", 300_000, categoryCode = "SIDE", payment = "ACCOUNT")
            insertTransaction("t4", "EXPENSE", 7_000, categoryCode = "ETC_EXPENSE", payment = null)
            insertTransaction("t5", "EXPENSE", 55_000, categoryName = "데이트", payment = "CREDIT_CARD")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        // 거래 5건이 모두 남고, 가리키는 분류·결제수단이 모두 실제로 있다
        assertEquals(5, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals(
            0,
            db.count("SELECT COUNT(*) FROM transactions WHERE categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories)"),
        )
        assertEquals(
            0,
            db.count(
                "SELECT COUNT(*) FROM transactions WHERE paymentMethodId IS NOT NULL AND paymentMethodId NOT IN (SELECT id FROM payment_methods)",
            ),
        )

        // 거래가 걸린 옛 기본 분류 '식비' 는 같은 줄에 새 기본 분류의 코드·모양이 입혀진다
        assertEquals("식비", db.categoryNameOf("t1"))
        db.row("SELECT code, icon, color, isSystem FROM categories WHERE scope = 'EXPENSE' AND name = '식비'") {
            assertEquals("FOOD", it.getString(0))
            assertEquals("restaurant", it.getString(1))
            assertEquals("orange", it.getString(2))
            assertEquals(0, it.getInt(3))
        }

        // 거래가 걸린 옛 분류 '카페·간식' 은 지울 수 있는 사용자 분류로 남고 어울리는 모양을 얻는다
        assertEquals("카페·간식", db.categoryNameOf("t2"))
        db.row("SELECT code, icon, color, isSystem FROM categories WHERE name = '카페·간식'") {
            assertNull(it.getString(0))
            assertEquals("local_cafe", it.getString(1))
            assertEquals("amber", it.getString(2))
            assertEquals(0, it.getInt(3))
        }
        assertEquals("부수입", db.categoryNameOf("t3"))

        // 옛 '기타' 는 그대로 지울 수 없는 '기타' 로 남고 맨 뒤로 간다
        assertEquals("기타", db.categoryNameOf("t4"))
        db.row("SELECT code, isSystem, sortOrder FROM categories WHERE scope = 'EXPENSE' AND name = '기타'") {
            assertEquals(ETC_EXPENSE_CODE, it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals(ETC_SORT_ORDER, it.getInt(2))
        }

        // 사용자가 만든 분류는 건드리지 않는다
        assertEquals("데이트", db.categoryNameOf("t5"))

        // 거래가 없던 옛 기본 분류는 지워지고, 새 기본 분류가 모두 들어간다
        assertFalse(db.exists("SELECT 1 FROM categories WHERE name IN ('교통', '쇼핑', '교육', '금융수입')"))
        DEFAULT_CATEGORIES.forEach { c ->
            assertTrue("${c.name} 가 없다", db.exists("SELECT 1 FROM categories WHERE scope = '${c.scope.name}' AND code = '${c.code}'"))
        }

        // 기본 결제수단은 모양을 얻고 지울 수 있게 바뀐다
        DEFAULT_PAYMENT_METHODS.forEach { m ->
            db.row("SELECT icon, color, isSystem FROM payment_methods WHERE uuid = '${m.uuid}'") {
                assertEquals(m.icon, it.getString(0))
                assertEquals(m.color, it.getString(1))
                assertEquals(0, it.getInt(2))
            }
        }
    }

    @Test
    fun `거래가 없는 옛 가계부도 올라간다`() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedLikeVersion1()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)
        // 옛 기본 분류는 '기타' 두 개만 남고 새 기본 분류로 바뀐다
        assertEquals(DEFAULT_CATEGORIES.size, db.count("SELECT COUNT(*) FROM categories"))
        assertEquals(DEFAULT_PAYMENT_METHODS.size, db.count("SELECT COUNT(*) FROM payment_methods"))
    }

    @Test
    fun `2 에서 3 으로 올리면 결제수단에 실적 칸이 비어 있는 채로 생기고 거래는 그대로다`() {
        helper.createDatabase(TEST_DB, 2).apply {
            // 1.6.1 의 SeedCallback 이 넣던 모양(아이콘·색 포함)과 알림으로 생긴 카드
            DEFAULT_PAYMENT_METHODS.forEach { m ->
                execSQL(
                    "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, 0, ?, ?)",
                    arrayOf<Any?>(m.uuid, m.name, m.type.name, m.sortOrder, m.icon, m.color),
                )
            }
            execSQL(
                "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color) " +
                    "VALUES ('user:hana', '하나카드', 'OTHER', 10, 0, 'credit_card', 'red')",
            )
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem, icon, color) " +
                    "VALUES ('seed:v2:category:FIXED', 'EXPENSE', '고정지출', 'FIXED', 4, 0, 'event_repeat', 'purple')",
            )
            execSQL(
                "INSERT INTO transactions " +
                    "(uuid, type, amount, occurredAt, occurredDate, categoryId, paymentMethodId, merchant, createdAt, updatedAt) " +
                    "VALUES ('t1', 'EXPENSE', 17000, 1758783600000, 20250925, (SELECT id FROM categories WHERE code = 'FIXED'), " +
                    "(SELECT id FROM payment_methods WHERE uuid = 'user:hana'), '넷플릭스', 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        assertEquals(1, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals(DEFAULT_PAYMENT_METHODS.size + 1, db.count("SELECT COUNT(*) FROM payment_methods"))
        // 거래가 가리키던 결제수단·분류가 그대로 있다
        db.row(
            "SELECT p.name, c.code FROM transactions t JOIN payment_methods p ON p.id = t.paymentMethodId " +
                "JOIN categories c ON c.id = t.categoryId WHERE t.uuid = 't1'",
        ) {
            assertEquals("하나카드", it.getString(0))
            assertEquals(FIXED_CATEGORY_CODE, it.getString(1))
        }
        // 모든 결제수단이 실적 없음·1일부터로 시작한다(PaymentMethodEntity 의 Kotlin 기본값과 같다)
        assertEquals(0, db.count("SELECT COUNT(*) FROM payment_methods WHERE performanceTiers IS NOT NULL OR performanceStartDay != 1"))
    }

    @Test
    fun `옛 가계부를 1 에서 3 까지 한 번에 올려도 거래는 그대로다`() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedLikeVersion1()
            insertTransaction("t1", "EXPENSE", 12_000, categoryCode = "FOOD", payment = "CHECK_CARD")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)
        assertEquals(1, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals("식비", db.categoryNameOf("t1"))
        assertEquals(
            DEFAULT_PAYMENT_METHODS.size,
            db.count("SELECT COUNT(*) FROM payment_methods WHERE performanceTiers IS NULL AND performanceStartDay = 1"),
        )
    }

    @Test
    fun `3 에서 4 로 올리면 결제수단·시각 인덱스가 생기고 거래는 그대로다`() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color, performanceTiers, performanceStartDay) " +
                    "VALUES ('user:hana', '하나카드', 'OTHER', 10, 0, 'credit_card', 'red', '300000', 15)",
            )
            execSQL(
                "INSERT INTO transactions " +
                    "(uuid, type, amount, occurredAt, occurredDate, paymentMethodId, merchant, createdAt, updatedAt) " +
                    "VALUES ('t1', 'EXPENSE', 17000, 1758783600000, 20250925, " +
                    "(SELECT id FROM payment_methods WHERE uuid = 'user:hana'), '넷플릭스', 0, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)
        assertEquals(1, db.count("SELECT COUNT(*) FROM transactions WHERE paymentMethodId IS NOT NULL"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM payment_methods WHERE performanceTiers = '300000' AND performanceStartDay = 15"))
        assertTrue(db.exists(INDEX_SQL + "'index_transactions_paymentMethodId_occurredAt'"))
        assertFalse(db.exists(INDEX_SQL + "'index_transactions_paymentMethodId'"))
    }

    @Test
    fun `옛 가계부를 1 이나 2 에서 4 까지 한 번에 올려도 거래는 그대로다`() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedLikeVersion1()
            insertTransaction("t1", "EXPENSE", 12_000, categoryCode = "FOOD", payment = "CHECK_CARD")
            close()
        }
        val fromOne = helper.runMigrationsAndValidate(TEST_DB, 4, true)
        assertEquals("식비", fromOne.categoryNameOf("t1"))
        assertTrue(fromOne.exists(INDEX_SQL + "'index_transactions_paymentMethodId_occurredAt'"))
        fromOne.close()

        helper.createDatabase(TEST_DB_2, 2).apply {
            DEFAULT_PAYMENT_METHODS.forEach { m ->
                execSQL(
                    "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, 0, ?, ?)",
                    arrayOf<Any?>(m.uuid, m.name, m.type.name, m.sortOrder, m.icon, m.color),
                )
            }
            execSQL(
                "INSERT INTO transactions (uuid, type, amount, occurredAt, occurredDate, paymentMethodId, createdAt, updatedAt) " +
                    "VALUES ('t2', 'EXPENSE', 5000, 1758783600000, 20250925, (SELECT MIN(id) FROM payment_methods), 0, 0)",
            )
            close()
        }
        val fromTwo = helper.runMigrationsAndValidate(TEST_DB_2, 4, true)
        assertEquals(1, fromTwo.count("SELECT COUNT(*) FROM transactions WHERE uuid = 't2' AND paymentMethodId IS NOT NULL"))
        assertEquals(DEFAULT_PAYMENT_METHODS.size, fromTwo.count("SELECT COUNT(*) FROM payment_methods WHERE performanceStartDay = 1"))
    }

    @Test
    fun `4 에서 5 로 올리면 같은 이름의 게임은 기본 분류로 바뀌고 저축이 생기며 둘 다 고정지출 다음에 선다`() {
        helper.createDatabase(TEST_DB, 4).apply {
            seedLikeVersion4()
            // 사용자가 만든 '데이트'(5)·'게임'(7, 다른 모양)과 그 게임으로 적은 거래
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem, icon, color) " +
                    "VALUES ('user:date', 'EXPENSE', '데이트', NULL, 5, 0, 'redeem', 'pink')",
            )
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem, icon, color) " +
                    "VALUES ('user:game', 'EXPENSE', '게임', NULL, 7, 0, 'movie', 'red')",
            )
            execSQL(
                "INSERT INTO transactions (uuid, type, amount, occurredAt, occurredDate, categoryId, createdAt, updatedAt) " +
                    "VALUES ('t1', 'EXPENSE', 5500, 1758783600000, 20250925, (SELECT id FROM categories WHERE uuid = 'user:game'), 0, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true)

        // '게임' 은 하나만 있고, 사용자가 만든 그 줄이 기본 분류(코드·아이콘·색·자리)로 바뀐다. 거래는 그대로 걸려 있다.
        assertEquals(1, db.count("SELECT COUNT(*) FROM categories WHERE scope = 'EXPENSE' AND name = '게임'"))
        db.row("SELECT uuid, code, icon, color, sortOrder FROM categories WHERE name = '게임'") {
            assertEquals("user:game", it.getString(0))
            assertEquals(GAME_CATEGORY_CODE, it.getString(1))
            assertEquals("sports_esports", it.getString(2))
            assertEquals("indigo", it.getString(3))
            assertEquals(5, it.getInt(4))
        }
        assertEquals("게임", db.categoryNameOf("t1"))
        // '저축' 은 기본값으로 새로 생긴다
        db.row("SELECT code, sortOrder, isSystem, icon, color FROM categories WHERE scope = 'EXPENSE' AND name = '저축'") {
            assertEquals(SAVINGS_CATEGORY_CODE, it.getString(0))
            assertEquals(6, it.getInt(1))
            assertEquals(0, it.getInt(2))
            assertEquals("savings", it.getString(3))
            assertEquals("teal", it.getString(4))
        }
        // 그 자리에 있던 '데이트' 는 두 칸 뒤로 밀리고, 앞의 기본 분류와 '기타' 는 그대로다
        db.row("SELECT sortOrder FROM categories WHERE name = '데이트'") { assertEquals(7, it.getInt(0)) }
        db.row("SELECT sortOrder FROM categories WHERE code = '$FIXED_CATEGORY_CODE'") { assertEquals(4, it.getInt(0)) }
        db.row("SELECT sortOrder FROM categories WHERE code = '$ETC_EXPENSE_CODE'") { assertEquals(ETC_SORT_ORDER, it.getInt(0)) }
    }

    @Test
    fun `게임·저축이 없던 가계부를 4 에서 5 로 올리면 둘 다 고정지출 다음에 생긴다`() {
        helper.createDatabase(TEST_DB, 4).apply {
            seedLikeVersion4()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true)
        assertEquals(DEFAULT_CATEGORIES.size, db.count("SELECT COUNT(*) FROM categories"))
        db.row("SELECT sortOrder FROM categories WHERE code = '$GAME_CATEGORY_CODE'") { assertEquals(5, it.getInt(0)) }
        db.row("SELECT sortOrder FROM categories WHERE code = '$SAVINGS_CATEGORY_CODE'") { assertEquals(6, it.getInt(0)) }
    }

    @Test
    fun `5 에서 6 으로 올리면 있던 결제수단은 결제수단으로 남고 현금·계좌이체로 적은 수입은 들어온 곳으로 옮겨 간다`() {
        helper.createDatabase(TEST_DB, 5).apply {
            seedPaymentMethodsLikeVersion5()
            // 지출 둘, 수입 다섯(현금·계좌이체·체크카드·직접 만든 카드·비움)
            insertTransactionWith("e-cash", "EXPENSE", "seed:payment:CASH")
            insertTransactionWith("e-account", "EXPENSE", "seed:payment:ACCOUNT")
            insertTransactionWith("i-cash", "INCOME", "seed:payment:CASH")
            insertTransactionWith("i-account", "INCOME", "seed:payment:ACCOUNT")
            insertTransactionWith("i-check", "INCOME", "seed:payment:CHECK_CARD")
            insertTransactionWith("i-hana", "INCOME", "user:hana")
            insertTransactionWith("i-none", "INCOME", null)
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true)

        // 결제수단 표를 새로 만들어도(칸 추가) 거래가 가리키던 번호는 하나도 비지 않는다
        assertEquals(7, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals(6, db.count("SELECT COUNT(*) FROM transactions WHERE paymentMethodId IS NOT NULL"))
        assertEquals(
            0,
            db.count(
                "SELECT COUNT(*) FROM transactions WHERE paymentMethodId IS NOT NULL AND paymentMethodId NOT IN (SELECT id FROM payment_methods)",
            ),
        )

        // 있던 결제수단은 모두 결제수단(지출)이고, 기본 들어온 곳 둘이 새로 생긴다
        assertEquals(DEFAULT_PAYMENT_METHODS.size + 1, db.count("SELECT COUNT(*) FROM payment_methods WHERE scope = 'EXPENSE'"))
        DEFAULT_INCOME_SOURCES.forEach { m ->
            db.row("SELECT scope, name, type, icon, color, sortOrder, isSystem FROM payment_methods WHERE uuid = '${m.uuid}'") {
                assertEquals("INCOME", it.getString(0))
                assertEquals(m.name, it.getString(1))
                assertEquals(m.type.name, it.getString(2))
                assertEquals(m.icon, it.getString(3))
                assertEquals(m.color, it.getString(4))
                assertEquals(m.sortOrder, it.getInt(5))
                assertEquals(0, it.getInt(6))
            }
        }
        assertEquals(DEFAULT_INCOME_SOURCES.size, db.count("SELECT COUNT(*) FROM payment_methods WHERE scope = 'INCOME'"))

        // 지출은 그대로, 현금·계좌이체로 적은 수입은 들어온 곳 '현금'·'통장' 으로, 카드로 적은 수입은 그대로다
        assertEquals("EXPENSE 현금", db.paymentOf("e-cash"))
        assertEquals("EXPENSE 계좌이체", db.paymentOf("e-account"))
        assertEquals("INCOME 현금", db.paymentOf("i-cash"))
        assertEquals("INCOME 통장", db.paymentOf("i-account"))
        assertEquals("EXPENSE 체크카드", db.paymentOf("i-check"))
        assertEquals("EXPENSE 하나카드", db.paymentOf("i-hana"))
        assertNull(db.paymentOf("i-none"))

        // 이름은 목록 안에서만 겹치지 않으면 된다
        assertTrue(db.exists(INDEX_SQL + "'index_payment_methods_scope_name'"))
        assertFalse(db.exists(INDEX_SQL + "'index_payment_methods_name'"))
    }

    @Test
    fun `들어온 곳 정리는 같은 이름의 들어온 곳이 있으면 그것을 두고 두 번 돌려도 같다`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.seedPaymentMethodsLikeVersion5()
            // 사용자가 먼저 만든 들어온 곳 '통장'(다른 모양)
            db.execSQL(
                "INSERT INTO payment_methods (uuid, scope, name, type, sortOrder, isSystem, icon, color) " +
                    "VALUES ('user:bank', 'INCOME', '통장', 'OTHER', 0, 0, 'savings', 'blue')",
            )
            db.insertTransactionWith("i-account", "INCOME", "seed:payment:ACCOUNT")

            settleIncomeSources(db)
            settleIncomeSources(db)

            assertEquals(1, db.count("SELECT COUNT(*) FROM payment_methods WHERE scope = 'INCOME' AND name = '통장'"))
            db.row("SELECT uuid, icon, color FROM payment_methods WHERE scope = 'INCOME' AND name = '통장'") {
                assertEquals("user:bank", it.getString(0))
                assertEquals("savings", it.getString(1))
                assertEquals("blue", it.getString(2))
            }
            assertEquals(1, db.count("SELECT COUNT(*) FROM payment_methods WHERE scope = 'INCOME' AND name = '현금'"))
            db.row("SELECT p.uuid FROM transactions t JOIN payment_methods p ON p.id = t.paymentMethodId WHERE t.uuid = 'i-account'") {
                assertEquals("user:bank", it.getString(0))
            }
        }
    }

    @Test
    fun `옛 가계부를 1 에서 6 까지 한 번에 올리면 계좌이체로 적은 수입은 통장으로 간다`() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedLikeVersion1()
            insertTransaction("t1", "EXPENSE", 12_000, categoryCode = "FOOD", payment = "ACCOUNT")
            insertTransaction("t2", "INCOME", 300_000, categoryCode = "SALARY", payment = "ACCOUNT")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true)
        assertEquals(2, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals("EXPENSE 계좌이체", db.paymentOf("t1"))
        assertEquals("INCOME 통장", db.paymentOf("t2"))
        assertEquals("급여", db.categoryNameOf("t2"))
    }

    @Test
    fun `6 에서 7 로 올리면 고정지출 탭에 보이던 곳을 모두 등록하고 거래는 그대로다`() {
        val thisMonth = YearMonth.now(BudgetTime.ZONE)
        helper.createDatabase(TEST_DB, 6).apply {
            seedCategoriesLikeVersion6()
            // 이번 달 · 지난달(띄어쓰기만 다른 같은 곳) · 20달 전(한동안 안 냈어요) · 이름 없음은 탭에 보인다
            insertExpense("f1", thisMonth, "넷플릭스")
            insertExpense("f2", thisMonth.minusMonths(1), " 넷 플릭스 ")
            insertExpense("f3", thisMonth.minusMonths(20), "자동차보험")
            insertExpense("f4", thisMonth.minusMonths(2), null)
            // 탭이 읽는 기간(27달) 앞 · 다른 분류 · 환불은 탭에 안 보인다
            insertExpense("f5", thisMonth.minusMonths(FIXED_HISTORY_MONTHS + 1), "오래된 구독")
            insertExpense("e1", thisMonth, "스타벅스", categoryCode = "FOOD")
            insertExpense("r1", thisMonth, "환불받은 곳", type = "REFUND")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true)

        assertEquals(7, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals(setOf("넷플릭스", "자동차보험", NO_MERCHANT_KEY), db.strings("SELECT merchantKey FROM fixed_merchants").toSet())
        assertEquals(0, db.count("SELECT COUNT(*) FROM fixed_merchants WHERE state != 'REGISTERED'"))
        // 거래의 분류는 그대로다
        assertEquals("고정지출", db.categoryNameOf("f5"))
    }

    @Test
    fun `보이던 곳 등록은 이미 정한 곳을 그대로 두고 두 번 돌려도 같다`() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.seedCategoriesLikeVersion6()
            db.insertExpense("f1", YearMonth.of(2026, 10), "넷플릭스")
            db.insertExpense("f2", YearMonth.of(2026, 9), "헬스장")
            db.execSQL("INSERT INTO fixed_merchants (merchantKey, state, decidedAt) VALUES ('헬스장', 'DECLINED', 1000)")
            val today = LocalDate.of(2026, 10, 16)

            assertEquals(1, registerShownFixedMerchants(db, today, Instant.ofEpochMilli(5000)))
            assertEquals(0, registerShownFixedMerchants(db, today, Instant.ofEpochMilli(6000)))

            db.row("SELECT state, decidedAt FROM fixed_merchants WHERE merchantKey = '넷플릭스'") {
                assertEquals("REGISTERED", it.getString(0))
                assertEquals(5000, it.getLong(1))
            }
            db.row("SELECT state, decidedAt FROM fixed_merchants WHERE merchantKey = '헬스장'") {
                assertEquals("DECLINED", it.getString(0))
                assertEquals(1000, it.getLong(1))
            }
        }
    }

    @Test
    fun `고정지출 목록 정리는 고정지출 지출이 하나도 남지 않은 곳만 지운다`() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.seedCategoriesLikeVersion6()
            // 탭이 읽는 기간 앞에만 낸 곳도 지출이 남아 있으면 둔다
            db.insertExpense("f1", YearMonth.of(2020, 1), "오래된 구독")
            db.insertExpense("f2", YearMonth.of(2026, 10), "넷플릭스")
            // 다른 분류로 옮긴 곳
            db.insertExpense("e1", YearMonth.of(2026, 10), "헬스장", categoryCode = "FOOD")
            listOf("오래된 구독", "넷플릭스", "헬스장", "지운 곳").forEach { key ->
                db.execSQL("INSERT INTO fixed_merchants (merchantKey, state, decidedAt) VALUES (?, 'DECLINED', 0)", arrayOf<Any?>(key))
            }

            assertEquals(2, pruneFixedMerchants(db))

            assertEquals(setOf("오래된 구독", "넷플릭스"), db.strings("SELECT merchantKey FROM fixed_merchants").toSet())
        }
    }

    @Test
    fun `옛 가계부를 5 에서 7 까지 한 번에 올려도 거래는 그대로이고 고정지출은 등록된다`() {
        helper.createDatabase(TEST_DB, 5).apply {
            seedCategoriesLikeVersion6()
            seedPaymentMethodsLikeVersion5()
            insertExpense("f1", YearMonth.now(BudgetTime.ZONE), "넷플릭스")
            insertTransactionWith("i-account", "INCOME", "seed:payment:ACCOUNT")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true)
        assertEquals(2, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals("INCOME 통장", db.paymentOf("i-account"))
        assertEquals(listOf("넷플릭스"), db.strings("SELECT merchantKey FROM fixed_merchants"))
    }

    /** 1.9.0 이 첫 설치 때 넣던 기본 분류(고정지출 · 식비 포함) */
    private fun SupportSQLiteDatabase.seedCategoriesLikeVersion6() {
        DEFAULT_CATEGORIES.forEach { c ->
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "seed:v2:category:${c.code}",
                    c.scope.name,
                    c.name,
                    c.code,
                    c.sortOrder,
                    if (c.isSystem) 1 else 0,
                    c.icon,
                    c.color,
                ),
            )
        }
    }

    /** [month] 1일 낮 12시(서울)에 [merchant] 에서 낸 [categoryCode] 분류 거래 한 건 */
    private fun SupportSQLiteDatabase.insertExpense(
        uuid: String,
        month: YearMonth,
        merchant: String?,
        categoryCode: String = FIXED_CATEGORY_CODE,
        type: String = "EXPENSE",
    ) {
        val at = month.atDay(1).atTime(12, 0).atZone(BudgetTime.ZONE).toInstant()
        execSQL(
            "INSERT INTO transactions (uuid, type, amount, occurredAt, occurredDate, categoryId, merchant, createdAt, updatedAt) " +
                "VALUES (?, ?, 17000, ?, ?, (SELECT id FROM categories WHERE code = ?), ?, 0, 0)",
            arrayOf<Any?>(uuid, type, at.toEpochMilli(), BudgetTime.toDateKey(at), categoryCode, merchant),
        )
    }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use {
        buildList { while (it.moveToNext()) add(it.getString(0)) }
    }

    /** 1.8.1 이 첫 설치 때 넣던 기본 결제수단과 알림으로 생긴 카드 */
    private fun SupportSQLiteDatabase.seedPaymentMethodsLikeVersion5() {
        DEFAULT_PAYMENT_METHODS.forEach { m ->
            execSQL(
                "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, 0, ?, ?)",
                arrayOf<Any?>(m.uuid, m.name, m.type.name, m.sortOrder, m.icon, m.color),
            )
        }
        execSQL(
            "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color) " +
                "VALUES ('user:hana', '하나카드', 'OTHER', 10, 0, 'credit_card', 'red')",
        )
    }

    /** [paymentUuid] 결제수단으로 적은 거래 한 건. 분류는 비운다. */
    private fun SupportSQLiteDatabase.insertTransactionWith(uuid: String, type: String, paymentUuid: String?) {
        execSQL(
            "INSERT INTO transactions (uuid, type, amount, occurredAt, occurredDate, paymentMethodId, createdAt, updatedAt) " +
                "VALUES (?, ?, 10000, 1758783600000, 20250925, (SELECT id FROM payment_methods WHERE uuid = ?), 0, 0)",
            arrayOf<Any?>(uuid, type, paymentUuid),
        )
    }

    /** 거래가 가리키는 결제수단의 '목록 이름'. 비웠으면 null */
    private fun SupportSQLiteDatabase.paymentOf(transactionUuid: String): String? = query(
        "SELECT p.scope, p.name FROM transactions t JOIN payment_methods p ON p.id = t.paymentMethodId WHERE t.uuid = ?",
        arrayOf<Any?>(transactionUuid),
    ).use { if (it.moveToFirst()) "${it.getString(0)} ${it.getString(1)}" else null }

    /** 1.7.0 이 첫 설치 때 넣던 기본 분류(게임·저축 없음) */
    private fun SupportSQLiteDatabase.seedLikeVersion4() {
        DEFAULT_CATEGORIES.filterNot { it.code == GAME_CATEGORY_CODE || it.code == SAVINGS_CATEGORY_CODE }.forEach { c ->
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "seed:v2:category:${c.code}",
                    c.scope.name,
                    c.name,
                    c.code,
                    c.sortOrder,
                    if (c.isSystem) 1 else 0,
                    c.icon,
                    c.color,
                ),
            )
        }
    }

    /** v0.1.2 의 SeedCallback 이 첫 설치 때 넣던 그대로 */
    private fun SupportSQLiteDatabase.seedLikeVersion1() {
        V1_CATEGORIES.forEachIndexed { index, (scope, code, name) ->
            execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem) VALUES (?, ?, ?, ?, ?, 1)",
                arrayOf<Any?>("seed:category:$code", scope, name, code, index),
            )
        }
        V1_PAYMENT_METHODS.forEachIndexed { index, (code, name, type) ->
            execSQL(
                "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem) VALUES (?, ?, ?, ?, 1)",
                arrayOf<Any?>("seed:payment:$code", name, type, index),
            )
        }
    }

    private fun SupportSQLiteDatabase.insertTransaction(
        uuid: String,
        type: String,
        amount: Long,
        categoryCode: String? = null,
        categoryName: String? = null,
        payment: String?,
    ) {
        val categorySql =
            if (categoryCode != null) {
                "(SELECT id FROM categories WHERE code = '$categoryCode')"
            } else {
                "(SELECT id FROM categories WHERE name = '$categoryName')"
            }
        val paymentSql = payment?.let { "(SELECT id FROM payment_methods WHERE uuid = 'seed:payment:$it')" } ?: "NULL"
        execSQL(
            "INSERT INTO transactions (uuid, type, amount, occurredAt, occurredDate, categoryId, paymentMethodId, createdAt, updatedAt) " +
                "VALUES ('$uuid', '$type', $amount, 1758783600000, 20250925, $categorySql, $paymentSql, 0, 0)",
        )
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int = query(sql).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun SupportSQLiteDatabase.exists(sql: String): Boolean = query(sql).use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.row(sql: String, check: (Cursor) -> Unit) = query(sql).use {
        assertTrue("행이 없다: $sql", it.moveToFirst())
        check(it)
    }

    private fun SupportSQLiteDatabase.categoryNameOf(transactionUuid: String): String? =
        query("SELECT c.name FROM transactions t JOIN categories c ON c.id = t.categoryId WHERE t.uuid = '$transactionUuid'").use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_2 = "migration-test-2.db"

        /** 이름이 뒤에 붙는 인덱스가 있는지 보는 SELECT */
        const val INDEX_SQL = "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = "

        /** v0.1.2 의 기본 분류(범위, 코드, 이름) */
        val V1_CATEGORIES =
            listOf(
                Triple("EXPENSE", "FOOD", "식비"),
                Triple("EXPENSE", "CAFE", "카페·간식"),
                Triple("EXPENSE", "TRANSPORT", "교통"),
                Triple("EXPENSE", "LIVING", "생활용품"),
                Triple("EXPENSE", "HOUSING", "주거·통신"),
                Triple("EXPENSE", "HEALTH", "의료·건강"),
                Triple("EXPENSE", "SHOPPING", "쇼핑"),
                Triple("EXPENSE", "CULTURE", "문화·여가"),
                Triple("EXPENSE", "EDUCATION", "교육"),
                Triple("EXPENSE", "SOCIAL", "경조사"),
                Triple("EXPENSE", "ETC_EXPENSE", "기타"),
                Triple("INCOME", "SALARY", "급여"),
                Triple("INCOME", "ALLOWANCE", "용돈"),
                Triple("INCOME", "SIDE", "부수입"),
                Triple("INCOME", "FINANCE", "금융수입"),
                Triple("INCOME", "ETC_INCOME", "기타"),
            )

        /** v0.1.2 의 기본 결제수단(코드, 이름, 종류) */
        val V1_PAYMENT_METHODS =
            listOf(
                Triple("CASH", "현금", "CASH"),
                Triple("CHECK_CARD", "체크카드", "CARD"),
                Triple("CREDIT_CARD", "신용카드", "CARD"),
                Triple("ACCOUNT", "계좌이체", "ACCOUNT"),
            )
    }
}
