package com.dong.budget.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

// ─────────────────────────────────────────────────────────────────────
// 들어온 곳(수입 쪽 결제수단) 정리. 1.8.1 까지는 수입에도 결제수단(현금·계좌이체 등)을 골랐다.
// DB 6 에서 수입 전용 목록 '들어온 곳' 을 두면서, 이미 쓰던 가계부를 한 번 정리한다.
// - DB 를 5 → 6 으로 올릴 때: Migration5To6 이 [settleIncomeSources] 를 부른다.
// - 1.8.1 까지 만든 백업(판 1)을 되살린 뒤: BackupRepository.restore 가 같은 거래 안에서 [settleIncomeSources] 를 부른다.
// 사용자가 나중에 지우거나 옮긴 들어온 곳을 되돌리지 않게, 그 밖에는 돌리지 않는다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 수입 거래가 가리키는 결제수단 중 종류가 (둘째 값) 인 것을 이름이 (첫째 값) 인 들어온 곳으로 옮기는 UPDATE.
 * 그 들어온 곳이 없으면 바꾸지 않는다(COALESCE 로 원래 값을 둔다. 결제수단이 비지 않게).
 */
private const val MOVE_INCOME_TO_SOURCE_SQL =
    """
        UPDATE transactions
        SET paymentMethodId = COALESCE(
            (SELECT id FROM payment_methods WHERE scope = 'INCOME' AND name = ?),
            paymentMethodId
        )
        WHERE type = 'INCOME'
          AND paymentMethodId IN (SELECT id FROM payment_methods WHERE scope = 'EXPENSE' AND type = ?)
    """

/**
 * 기본 들어온 곳을 넣고, 옛 수입 거래를 들어온 곳으로 옮긴다. DB 를 5 → 6 으로 올릴 때와 판 1 백업을 되살린 뒤 한 번 돈다.
 * 1. 기본 들어온 곳 '현금'·'통장'([DEFAULT_INCOME_SOURCES])을 넣는다. 같은 이름의 들어온 곳이 이미 있으면 그것을 둔다(UNIQUE (scope, name)).
 * 2. 현금 종류 결제수단으로 적은 수입은 '현금' 으로, 계좌 종류('계좌이체')로 적은 수입은 '통장' 으로 옮긴다.
 *    카드나 직접 만든 결제수단(종류 CARD·OTHER)으로 적은 수입은 어디로 옮길지 알 수 없어 그대로 둔다(거래는 하나도 잃지 않는다).
 */
internal fun settleIncomeSources(db: SupportSQLiteDatabase) {
    DEFAULT_INCOME_SOURCES.forEach { insertDefaultPaymentMethod(db, it, orIgnore = true) }
    DEFAULT_INCOME_SOURCES.forEach { m -> db.execSQL(MOVE_INCOME_TO_SOURCE_SQL, arrayOf<Any?>(m.name, m.type.name)) }
}
