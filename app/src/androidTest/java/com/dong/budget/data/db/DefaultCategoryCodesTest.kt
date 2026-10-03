package com.dong.budget.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DB 를 열 때 돌리는 기본 분류 코드 돌려주기(reclaimDefaultCodes(db))가 실제 SQLite 에서도 고르는 규칙([reclaimedCodes]) 대로 바꾸는지 본다.
 * 기기에서 돈다. 테스트용 DB 이름을 따로 써서 기기에 있는 실제 가계부(dong-budget.db)는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class DefaultCategoryCodesTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BudgetDatabase::class.java,
            listOf(Migration1To2()),
        )

    @Test
    fun `코드 없이 다시 만든 고정지출·급여에 DB 를 열 때 코드를 돌려준다`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertCategory("u:etc", "EXPENSE", "기타", ETC_EXPENSE_CODE)
            db.insertCategory("u:fixed2", "EXPENSE", "고정 지출", null)
            db.insertCategory("u:fixed3", "EXPENSE", "고정지출", null)
            db.insertCategory("u:salary", "INCOME", "급여", null)
            db.insertCategory("u:other", "INCOME", "고정지출", null)

            reclaimDefaultCodes(db)

            // 먼저 만든 '고정 지출' 하나만 코드를 받는다
            assertEquals(FIXED_CATEGORY_CODE, db.codeOf("u:fixed2"))
            assertNull(db.codeOf("u:fixed3"))
            assertEquals(SALARY_CATEGORY_CODE, db.codeOf("u:salary"))
            // 수입 '고정지출' 은 다른 종류라 그대로다
            assertNull(db.codeOf("u:other"))

            // 다시 열어도 바뀌지 않는다(그 코드를 가진 분류가 이미 있다)
            reclaimDefaultCodes(db)
            assertNull(db.codeOf("u:fixed3"))
        }
    }

    @Test
    fun `그 코드를 가진 분류가 이미 있으면 같은 이름 분류를 건드리지 않는다`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertCategory("seed:v2:category:FIXED", "EXPENSE", "고정지출", FIXED_CATEGORY_CODE)
            db.insertCategory("u:fixed2", "EXPENSE", "고정 지출", null)

            reclaimDefaultCodes(db)

            assertEquals(FIXED_CATEGORY_CODE, db.codeOf("seed:v2:category:FIXED"))
            assertNull(db.codeOf("u:fixed2"))
        }
    }

    private fun SupportSQLiteDatabase.insertCategory(uuid: String, scope: String, name: String, code: String?) {
        execSQL(
            "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, 0, 0, 'more_horiz', 'gray')",
            arrayOf<Any?>(uuid, scope, name, code),
        )
    }

    private fun SupportSQLiteDatabase.codeOf(uuid: String): String? =
        query("SELECT code FROM categories WHERE uuid = ?", arrayOf<Any?>(uuid)).use {
            it.moveToFirst()
            if (it.isNull(0)) null else it.getString(0)
        }

    private companion object {
        const val TEST_DB = "default-category-codes-test.db"
    }
}
