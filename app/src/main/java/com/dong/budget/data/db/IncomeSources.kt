package com.dong.budget.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

// ─────────────────────────────────────────────────────────────────────
// 들어온 곳(수입 쪽 결제수단) 정리. 1.8.1 까지는 수입에도 결제수단(현금·계좌이체 등)을 골랐다.
// DB 6 에서 수입 전용 목록 '들어온 곳' 을 두면서, 이미 쓰던 가계부를 한 번 정리한다.
// - DB 를 5 → 6 으로 올릴 때: Migration5To6 이 [settleIncomeSources] 를 부른다.
// - 1.8.1 까지 만든 백업(판 1)을 되살린 뒤: BackupDao.settleIncomeSources 가 같은 일을 DAO 로 한다.
// 사용자가 나중에 지우거나 옮긴 들어온 곳을 되돌리지 않게, 그 밖에는 돌리지 않는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 수입 거래가 가리키는 결제수단 중 종류가 [fromType] 인 것을 이름이 [sourceName] 인 들어온 곳으로 옮기는 UPDATE.
 * 그 들어온 곳이 없으면 바꾸지 않는다(COALESCE 로 원래 값을 둔다. 결제수단이 비지 않게).
 *
 * Room 의 @Query 와 SQLite 에 바로 넘기는 execSQL 이 같은 글을 쓴다. 이름 붙은 자리(:이름)는 SQLite 가 처음 나온 순서대로
 * 1, 2 번을 매기므로 execSQL 에는 (sourceName, fromType) 순서로 넘긴다. 자리마다 한 번씩만 쓴다(두 번 쓰면 번호가 겹친다).
 */
internal const val MOVE_INCOME_TO_SOURCE_SQL =
    """
        UPDATE transactions
        SET paymentMethodId = COALESCE(
            (SELECT id FROM payment_methods WHERE scope = 'INCOME' AND name = :sourceName),
            paymentMethodId
        )
        WHERE type = 'INCOME'
          AND paymentMethodId IN (SELECT id FROM payment_methods WHERE scope = 'EXPENSE' AND type = :fromType)
    """

/**
 * 기본 들어온 곳을 넣고, 옛 수입 거래를 들어온 곳으로 옮긴다. DB 를 5 → 6 으로 올릴 때 한 번 돈다.
 * 1. 기본 들어온 곳 '현금'·'통장'([DEFAULT_INCOME_SOURCES])을 넣는다. 같은 이름의 들어온 곳이 이미 있으면 그것을 둔다(UNIQUE (scope, name)).
 * 2. 현금 종류 결제수단으로 적은 수입은 '현금' 으로, 계좌 종류('계좌이체')로 적은 수입은 '통장' 으로 옮긴다.
 *    카드나 직접 만든 결제수단(종류 CARD·OTHER)으로 적은 수입은 어디로 옮길지 알 수 없어 그대로 둔다(거래는 하나도 잃지 않는다).
 */
internal fun settleIncomeSources(db: SupportSQLiteDatabase) {
    DEFAULT_INCOME_SOURCES.forEach { m ->
        db.execSQL(
            "INSERT OR IGNORE INTO payment_methods (uuid, scope, name, type, sortOrder, isSystem, icon, color) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
            arrayOf<Any?>(m.uuid, m.scope.name, m.name, m.type.name, m.sortOrder, m.icon, m.color),
        )
    }
    DEFAULT_INCOME_SOURCES.forEach { m -> db.execSQL(MOVE_INCOME_TO_SOURCE_SQL, arrayOf<Any?>(m.name, m.type.name)) }
}
