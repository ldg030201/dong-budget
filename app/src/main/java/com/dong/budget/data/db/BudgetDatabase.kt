package com.dong.budget.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun paymentMethodDao(): PaymentMethodDao

    companion object {
        private const val NAME = "dong-budget.db"

        fun build(context: Context): BudgetDatabase = Room
            .databaseBuilder(context, BudgetDatabase::class.java, NAME)
            // WAL 을 끈다.
            // WAL 에서는 커밋된 데이터가 체크포인트 전까지 -wal 파일에만 있는데,
            // Auto Backup 은 include 로 지정한 db 파일만 담아서 -wal 을 빼먹는다.
            // 그 결과 최근 거래가 조용히 백업에서 빠진다.
            // 이 규모에서 WAL 의 동시성 이득은 없으므로 안전한 쪽을 택한다.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            // fallbackToDestructiveMigration 은 절대 넣지 않는다.
            // 마이그레이션을 빠뜨리면 사용자 가계부가 통째로 지워진다.
            .addCallback(SeedCallback)
            .build()
    }
}

/** 첫 설치 때 기본 카테고리와 결제수단을 넣는다. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultCategories.forEachIndexed { index, (scope, code, name) ->
            db.execSQL(
                "INSERT INTO categories (uuid, scope, name, code, sortOrder, isSystem) VALUES (?, ?, ?, ?, ?, 1)",
                arrayOf<Any?>("seed:category:$code", scope.name, name, code, index),
            )
        }
        DefaultPaymentMethods.forEachIndexed { index, (code, name, type) ->
            db.execSQL(
                "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem) VALUES (?, ?, ?, ?, 1)",
                arrayOf<Any?>("seed:payment:$code", name, type.name, index),
            )
        }
    }
}

private val DefaultCategories =
    listOf(
        Triple(CategoryScope.EXPENSE, "FOOD", "식비"),
        Triple(CategoryScope.EXPENSE, "CAFE", "카페·간식"),
        Triple(CategoryScope.EXPENSE, "TRANSPORT", "교통"),
        Triple(CategoryScope.EXPENSE, "LIVING", "생활용품"),
        Triple(CategoryScope.EXPENSE, "HOUSING", "주거·통신"),
        Triple(CategoryScope.EXPENSE, "HEALTH", "의료·건강"),
        Triple(CategoryScope.EXPENSE, "SHOPPING", "쇼핑"),
        Triple(CategoryScope.EXPENSE, "CULTURE", "문화·여가"),
        Triple(CategoryScope.EXPENSE, "EDUCATION", "교육"),
        Triple(CategoryScope.EXPENSE, "SOCIAL", "경조사"),
        Triple(CategoryScope.EXPENSE, "ETC_EXPENSE", "기타"),
        Triple(CategoryScope.INCOME, "SALARY", "급여"),
        Triple(CategoryScope.INCOME, "ALLOWANCE", "용돈"),
        Triple(CategoryScope.INCOME, "SIDE", "부수입"),
        Triple(CategoryScope.INCOME, "FINANCE", "금융수입"),
        Triple(CategoryScope.INCOME, "ETC_INCOME", "기타"),
    )

private val DefaultPaymentMethods =
    listOf(
        Triple("CASH", "현금", PaymentMethodType.CASH),
        Triple("CHECK_CARD", "체크카드", PaymentMethodType.CARD),
        Triple("CREDIT_CARD", "신용카드", PaymentMethodType.CARD),
        Triple("ACCOUNT", "계좌이체", PaymentMethodType.ACCOUNT),
    )
