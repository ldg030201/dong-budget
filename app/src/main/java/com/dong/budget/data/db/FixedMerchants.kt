package com.dong.budget.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 목록(fixed_merchants) 정리. DB 7 부터 고정지출 탭은 '고정지출' 분류 지출을 모두 보이지 않고, 탭에서 등록한 곳만 보인다.
// - DB 를 6 → 7 로 올릴 때: 그날 탭에 보이던 곳을 모두 등록한다([registerShownFixedMerchants]). 올린 뒤에도 탭이 그대로다.
// - 목록이 없는 옛 백업을 되살린 뒤: BackupRepository.restore 가 같은 거래 안에서 같은 함수를 부른다.
// - DB 를 열 때: '고정지출' 분류 지출이 하나도 남지 않은 곳을 목록에서 지운다([pruneFixedMerchantsOnOpen]).
// 열쇠는 고정지출 탭이 가게를 묶는 열쇠([fixedMerchantKey])와 같아야 한다. 띄어쓰기 · 대소문자를 무시하는 규칙이 SQL 로는 같게 안 되어
// 행을 읽어 앱에서 만든다.
// ─────────────────────────────────────────────────────────────────────

/** 가게 이름 없이 적은 지출(백업으로만 들어온다)을 묶는 열쇠. [merchantKey] 는 빈 글을 내지 않으므로 어느 가게와도 겹치지 않는다. */
const val NO_MERCHANT_KEY = ""

/**
 * 고정지출 탭이 가게를 묶는 열쇠. 많이 쓴 곳과 같은 열쇠([merchantKey])이고, 이름이 없으면 모두 [NO_MERCHANT_KEY] 하나다.
 * 고정지출 목록이 이 열쇠를 저장하므로, 규칙을 바꾸면 저장해 둔 목록과 어긋난다.
 */
fun fixedMerchantKey(merchant: String?): String = merchantKey(merchant) ?: NO_MERCHANT_KEY

/**
 * 고정지출 탭이 고른 달 앞으로 읽는 달 수. 매년(직전 간격 최대 13) + 한 달 늦음(1) + 그 앞 해(12) + 첫 달 몫을 전달 말에 낸 것(1).
 * 고정지출 계산(ui/fixed 의 fixedHistoryStart)과 목록 정리([registerShownFixedMerchants])가 같이 쓴다.
 */
const val FIXED_HISTORY_MONTHS = 27L

/**
 * [today] 에 고정지출 탭이 이번 달을 볼 때 읽는 기간(앞은 들고, 뒤는 들지 않는다). 이번 달 앞 [FIXED_HISTORY_MONTHS] 달 1일부터
 * 이번 달 말일까지다(fixedHistoryStart ~ fixedHistoryEnd). 날짜를 앞으로 적어 둔 이번 달 거래도 든다.
 */
internal fun fixedShownRange(today: LocalDate): Pair<Instant, Instant> {
    val month = YearMonth.from(today)
    return BudgetTime.monthRange(month.minusMonths(FIXED_HISTORY_MONTHS)).first to BudgetTime.monthRange(month).second
}

/** '고정지출' 분류(코드 FIXED) 지출의 가게 이름. 뒤에 조건을 붙여 쓴다. 고정지출 탭의 조회(observeExpensesWithCategoryCode)와 같은 거래다. */
private const val FIXED_MERCHANTS_SQL =
    "SELECT DISTINCT merchant FROM transactions " +
        "WHERE categoryId IN (SELECT id FROM categories WHERE code = '$FIXED_CATEGORY_CODE') AND type = 'EXPENSE'"

/**
 * [today] 에 고정지출 탭에 보였을 곳을 모두 등록한다(아직 안 냈어요 · 냈어요 · 이번 달엔 안 내요 · 한동안 안 냈어요 모두).
 * 탭이 이번 달을 볼 때 읽는 기간([fixedShownRange])에 '고정지출' 분류 지출이 있는 곳이다. 이미 정해 둔 곳은 그대로 둔다.
 * DB 를 6 → 7 로 올릴 때와 목록이 없는 옛 백업을 되살린 뒤에 돈다.
 * @return 새로 등록한 곳의 수
 */
internal fun registerShownFixedMerchants(db: SupportSQLiteDatabase, today: LocalDate, now: Instant): Int {
    val (start, end) = fixedShownRange(today)
    val keys =
        fixedMerchantKeys(db, "$FIXED_MERCHANTS_SQL AND occurredAt >= ? AND occurredAt < ?", start.toEpochMilli(), end.toEpochMilli())
    val added = keys - storedKeys(db)
    added.forEach { key ->
        db.execSQL(
            "INSERT INTO fixed_merchants (merchantKey, state, decidedAt) VALUES (?, ?, ?)",
            arrayOf<Any?>(key, FixedMerchantState.REGISTERED.name, now.toEpochMilli()),
        )
    }
    return added.size
}

/**
 * '고정지출' 분류 지출이 하나도 남지 않은 곳(거래를 지웠거나 다른 분류로 옮겼다)을 목록에서 지운다.
 * 그 뒤에 그곳을 다시 '고정지출' 분류로 적으면 처음 보는 곳처럼 등록할지 묻는다. 기간은 보지 않는다(오래전에만 낸 곳도 남긴다).
 * @return 지운 곳의 수
 */
internal fun pruneFixedMerchants(db: SupportSQLiteDatabase): Int {
    val gone = storedKeys(db) - fixedMerchantKeys(db, FIXED_MERCHANTS_SQL)
    gone.forEach { db.execSQL("DELETE FROM fixed_merchants WHERE merchantKey = ?", arrayOf<Any?>(it)) }
    return gone.size
}

/**
 * DB 를 열 때(onOpen) 부른다. 기본 분류 코드를 돌려준 뒤에 불러야 다시 만든 '고정지출' 분류의 지출을 센다.
 * 실패해도 DB 열기를 막지 않는다([reclaimDefaultCodesOnOpen] 과 같은 까닭이다). 다음에 열 때 다시 하면 된다.
 */
internal fun pruneFixedMerchantsOnOpen(db: SupportSQLiteDatabase) {
    try {
        val pruned = pruneFixedMerchants(db)
        if (pruned > 0) DevLog.info(LogTag.APP, "'고정지출' 지출이 없는 곳 ${pruned}곳을 고정지출 목록에서 지웠어요")
    } catch (e: Exception) {
        DevLog.warn(LogTag.APP, "고정지출 목록을 정리하지 못했어요. 다음에 DB 를 열 때 다시 해요", e)
    }
}

/** [sql] (가게 이름 한 칸)이 읽은 가게들의 열쇠 */
private fun fixedMerchantKeys(db: SupportSQLiteDatabase, sql: String, vararg args: Any): Set<String> =
    db.query(sql, arrayOf(*args)).use { c ->
        buildSet { while (c.moveToNext()) add(fixedMerchantKey(if (c.isNull(0)) null else c.getString(0))) }
    }

/** 목록에 정해 둔 곳들의 열쇠 */
private fun storedKeys(db: SupportSQLiteDatabase): Set<String> = db.query("SELECT merchantKey FROM fixed_merchants").use { c ->
    buildSet { while (c.moveToNext()) add(c.getString(0)) }
}
