package com.dong.budget.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: 분류에 아이콘·색 칸을 추가하고 기본 분류를 새 목록으로 바꾼다.
        // 칸 추가는 Room 이 스키마 JSON 을 비교해서 만들고, 데이터 정리는 Migration1To2 가 한다.
        AutoMigration(from = 1, to = 2, spec = Migration1To2::class),
        // 2 → 3: 결제수단에 카드 실적 칸(구간·시작일)을 추가한다. 칸 추가뿐이라 정리할 데이터가 없다(기존 줄은 기본값: 실적 없음, 1일).
        AutoMigration(from = 2, to = 3),
        // 3 → 4: 거래에 (결제수단, 시각) 복합 인덱스를 두고 결제수단 단일 인덱스를 지운다. 카드실적 상세가 카드 하나의 6기간을
        // 그 카드 기록 전부를 읽지 않고 바로 찾는다. 인덱스만 바뀌어 데이터는 그대로다.
        AutoMigration(from = 3, to = 4),
        // 4 → 5: 기본 지출 분류 '게임'·'저축' 을 이미 쓰던 가계부에도 둔다(같은 이름이 있으면 그 분류를 기본값으로 바꾼다).
        // 스키마는 그대로고 줄만 바꾼다(Migration4To5).
        AutoMigration(from = 4, to = 5, spec = Migration4To5::class),
    ],
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun paymentMethodDao(): PaymentMethodDao

    abstract fun backupDao(): BackupDao

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
            // 빠뜨리면 차라리 앱이 켜지지 않는 편이 낫다. 데이터는 남아 있으니 고쳐서 다시 내면 된다.
            .addCallback(SeedCallback)
            .build()
    }
}

/**
 * 새 기본 분류의 uuid.
 *
 * 1.x 의 기본 분류는 "seed:category:<코드>" 를 썼다. 같은 값을 다시 쓰면,
 * 거래가 걸려 있어서 남겨둔 옛 분류(예: '교통')와 새 분류('교통/차량')의 uuid 가 겹쳐
 * 새 분류가 아예 들어가지 않는다. 그래서 새 목록은 다른 이름공간을 쓴다.
 */
private fun seedUuid(code: String) = "seed:v2:category:$code"

/** 기본 분류를 DB 줄로. 데이터 초기화가 새로 설치할 때([SeedCallback])와 같은 줄을 넣게 한다. */
fun DefaultCategory.toEntity(): CategoryEntity = CategoryEntity(
    uuid = seedUuid(code),
    scope = scope,
    name = name,
    code = code,
    sortOrder = sortOrder,
    isSystem = isSystem,
    icon = icon,
    color = color,
)

/** 기본 결제수단을 DB 줄로. [DefaultCategory.toEntity] 와 같은 까닭이다. */
fun DefaultPaymentMethod.toEntity(): PaymentMethodEntity = PaymentMethodEntity(
    uuid = uuid,
    name = name,
    type = type,
    sortOrder = sortOrder,
    icon = icon,
    color = color,
)

private fun insertDefaultCategory(db: SupportSQLiteDatabase, c: DefaultCategory, orIgnore: Boolean) {
    db.execSQL(
        "INSERT ${if (orIgnore) "OR IGNORE " else ""}INTO categories " +
            "(uuid, scope, name, code, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(seedUuid(c.code), c.scope.name, c.name, c.code, c.sortOrder, if (c.isSystem) 1 else 0, c.icon, c.color),
    )
}

/**
 * 첫 설치 때 기본 분류와 결제수단을 넣는다(onCreate, 옛 DB 를 올리는 경우에는 불리지 않는다).
 * DB 를 열 때마다 지운 뒤 코드 없이 다시 만든 '고정지출'·'급여' 에 코드를 돌려준다(onOpen, 마이그레이션 뒤에 불린다).
 *
 * 주의: 둘 다 SupportSQLiteDatabase 버전이다. Room 은 onCreate/onOpen(SQLiteConnection) 을 부르고, 그 기본 구현은 연결이
 * SupportSQLiteConnection 일 때만 이 버전으로 넘긴다. 지금은 드라이버를 따로 지정하지 않아서(호환 모드) 넘어오지만,
 * setDriver(...) 를 더하면 에러 없이 조용히 건너뛰어진다([Migration1To2] 와 같은 함정). 드라이버를 바꿀 때는 SQLiteConnection 버전으로 옮긴다.
 */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DEFAULT_CATEGORIES.forEach { insertDefaultCategory(db, it, orIgnore = false) }
        DEFAULT_PAYMENT_METHODS.forEach { m ->
            db.execSQL(
                "INSERT INTO payment_methods (uuid, name, type, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, 0, ?, ?)",
                arrayOf<Any?>(m.uuid, m.name, m.type.name, m.sortOrder, m.icon, m.color),
            )
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        reclaimDefaultCodesOnOpen(db)
    }
}

/**
 * 1 → 2 데이터 정리.
 *
 * 원칙: 거래 데이터는 하나도 잃지 않는다.
 * 1. 옛 기본 분류 중 거래가 한 건도 없는 것은 지운다. ('기타' 는 남긴다)
 * 2. 거래가 걸려 있어 지울 수 없는 옛 기본 분류는 사용자 분류로 바꾸고
 *    어울리는 아이콘·색을 붙인다. 이제 사용자가 지울 수 있다.
 * 3. 새 기본 분류를 넣는다. 이름이 이미 있으면(예: '식비', '기타') 그 행에 아이콘·색만 입힌다.
 * 4. 기본 결제수단에 아이콘·색을 입히고 지울 수 있게 바꾼다.
 *
 * 주의: Room 이 생성한 코드는 onPostMigrate(SQLiteConnection) 을 부른다.
 * 그 기본 구현은 연결이 SupportSQLiteConnection 일 때만 아래 SupportSQLiteDatabase 버전으로 넘긴다.
 * 지금은 드라이버를 따로 지정하지 않아서 조건에 맞지만,
 * setDriver(BundledSQLiteDriver()) 같은 것을 추가하면 이 정리 코드가 에러 없이 조용히 건너뛰어진다.
 * 드라이버를 바꿀 때는 SQLiteConnection 버전으로 옮겨야 한다.
 */
class Migration1To2 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val etcCodes = "('$ETC_EXPENSE_CODE', '$ETC_INCOME_CODE')"

        // 1
        db.execSQL(
            "DELETE FROM categories WHERE isSystem = 1 AND code NOT IN $etcCodes " +
                "AND id NOT IN (SELECT categoryId FROM transactions WHERE categoryId IS NOT NULL)",
        )

        // 2
        LEGACY_STYLE.forEach { (code, style) ->
            db.execSQL(
                "UPDATE categories SET icon = ?, color = ? WHERE isSystem = 1 AND code = ?",
                arrayOf<Any?>(style.first, style.second, code),
            )
        }
        db.execSQL(
            "UPDATE categories SET isSystem = 0, code = NULL, sortOrder = 100 + sortOrder " +
                "WHERE isSystem = 1 AND code NOT IN $etcCodes",
        )

        // 3
        DEFAULT_CATEGORIES.forEach { c ->
            insertDefaultCategory(db, c, orIgnore = true)
            db.execSQL(
                "UPDATE categories SET code = ?, icon = ?, color = ?, sortOrder = ?, isSystem = ? " +
                    "WHERE scope = ? AND name = ?",
                arrayOf<Any?>(c.code, c.icon, c.color, c.sortOrder, if (c.isSystem) 1 else 0, c.scope.name, c.name),
            )
        }

        // 4
        DEFAULT_PAYMENT_METHODS.forEach { m ->
            db.execSQL(
                "UPDATE payment_methods SET icon = ?, color = ?, isSystem = 0 WHERE uuid = ?",
                arrayOf<Any?>(m.icon, m.color, m.uuid),
            )
        }
    }

    private companion object {
        /** 1.x 기본 분류 코드 → (아이콘, 색). 거래가 걸려 남게 된 옛 분류의 모양을 정한다. */
        val LEGACY_STYLE =
            mapOf(
                "CAFE" to ("local_cafe" to "amber"),
                "TRANSPORT" to ("directions_bus" to "blue"),
                "LIVING" to ("shopping_bag" to "teal"),
                "HOUSING" to ("home" to "indigo"),
                "HEALTH" to ("local_hospital" to "red"),
                "SHOPPING" to ("shopping_bag" to "pink"),
                "CULTURE" to ("movie" to "purple"),
                "EDUCATION" to ("school" to "blue"),
                "SOCIAL" to ("redeem" to "red"),
                "SIDE" to ("savings" to "green"),
                "FINANCE" to ("savings" to "teal"),
            )
    }
}

/**
 * 4 → 5 데이터 정리. 1.8.0 에서 더한 기본 지출 분류('게임'·'저축')를 이미 쓰던 가계부에도 기본값 그대로 둔다.
 *
 * - 자리: 새로 설치할 때와 같은 자리(고정지출 다음, 순서 5·6)에 둔다. 그 자리부터 있던 다른 분류는 그 수만큼 뒤로 민다
 *   (순서가 겹치면 이름순으로 섞이기 때문이다). '기타' 는 늘 맨 뒤라 건드리지 않는다.
 * - 같은 종류에 같은 이름이 이미 있으면(사용자가 직접 만든 '게임') 새로 넣지 않고 그 분류를 기본 분류로 바꾼다.
 *   걸린 거래는 그대로이고 코드·아이콘·색·자리만 기본값이 된다. 이름이 UNIQUE 라 같은 이름을 둘 둘 수 없다.
 * - 한 번만 돈다. 사용자가 나중에 지우거나 옮긴 것을 앱을 열 때마다 되돌리지 않는다.
 *
 * 주의: [Migration1To2] 와 같은 함정이 있다(드라이버를 바꾸면 onPostMigrate(SupportSQLiteDatabase) 가 조용히 건너뛰어진다).
 */
class Migration4To5 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val added = DEFAULT_CATEGORIES.filter { it.code == GAME_CATEGORY_CODE || it.code == SAVINGS_CATEGORY_CODE }
        // 1. 자리를 비운다. 옮겨 올 같은 이름의 분류는 2 에서 자리를 정하므로 밀지 않는다.
        added.groupBy { it.scope }.forEach { (scope, defaults) ->
            val names = defaults.joinToString(", ") { "?" }
            db.execSQL(
                "UPDATE categories SET sortOrder = sortOrder + ${defaults.size} " +
                    "WHERE scope = ? AND isSystem = 0 AND sortOrder >= ${defaults.minOf { it.sortOrder }} AND name NOT IN ($names)",
                (listOf(scope.name) + defaults.map { it.name }).toTypedArray<Any?>(),
            )
        }
        // 2. 없으면 넣고, 같은 이름이 있으면 그 줄을 기본 분류로 바꾼다(새로 넣은 줄에는 같은 값이라 그대로다)
        added.forEach { c ->
            insertDefaultCategory(db, c, orIgnore = true)
            db.execSQL(
                "UPDATE categories SET code = ?, icon = ?, color = ?, sortOrder = ?, isSystem = 0 WHERE scope = ? AND name = ?",
                arrayOf<Any?>(c.code, c.icon, c.color, c.sortOrder, c.scope.name, c.name),
            )
        }
    }
}
