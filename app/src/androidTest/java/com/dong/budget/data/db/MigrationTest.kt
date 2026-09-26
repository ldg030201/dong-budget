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

/**
 * 0.1.2 이하(DB 1)에서 올리는 사용자의 가계부가 마이그레이션 뒤에도 그대로 남는지 실제 SQLite 로 확인한다.
 *
 * 옛 앱이 첫 설치 때 넣던 기본 분류·결제수단(v0.1.2 의 SeedCallback)을 그대로 만들고, 거래를 몇 건 넣은 뒤 2 로 올린다.
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
            listOf(Migration1To2()),
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
