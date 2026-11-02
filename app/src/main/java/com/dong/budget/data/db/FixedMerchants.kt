package com.dong.budget.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 목록(fixed_merchants) 정리. DB 7 부터 고정지출 탭은 '고정지출' 분류 지출을 모두 보이지 않고, 탭에서 등록한 곳만 보인다.
// - DB 를 6 → 7 로 올릴 때: 그날 탭에 보이던 곳을 모두 등록한다([registerShownFixedMerchants]). 올린 뒤에도 탭이 그대로다.
// - 목록이 없는 옛 백업을 되살린 뒤: BackupRepository.restore 가 같은 거래 안에서 같은 함수를 부른다.
// - 고정지출 탭을 열 때: '고정지출' 분류 지출이 하나도 남지 않은 곳을 목록에서 지운다(FixedMerchantDao.prune, [staleFixedMerchants]).
//   앱을 켤 때마다 하지 않는다. 목록은 탭에서만 보이니 탭을 열 때 정리하면 된다.
// DB 8 부터 같은 표에 고정저축 목록도 종류([FixedKind])로 나눠 둔다(열쇠가 (종류, 가게)). 고정저축은 처음부터 탭이 묻고 등록해서
// 보이던 곳 등록은 고정지출에만 있고, 정리는 종류마다 그 분류 코드로 한다. 해지한 곳(ENDED)과 해지한 때(endedAt)도 DB 8 부터다.
// 열쇠는 고정지출 탭이 가게를 묶는 열쇠([fixedMerchantKey])와 같아야 한다. 띄어쓰기 · 대소문자를 무시하는 규칙이 SQL 로는 같게 안 되어
// 행을 읽어 앱에서 만든다. 어느 거래를 보는지는 탭의 조회와 같은 조건([EXPENSE_WITH_CATEGORY_CODE])을 쓴다.
// ─────────────────────────────────────────────────────────────────────

/** 가게 이름 없이 적은 지출(백업으로만 들어온다)을 묶는 열쇠. [merchantKey] 는 빈 글을 내지 않으므로 어느 가게와도 겹치지 않는다. */
const val NO_MERCHANT_KEY = ""

/**
 * 고정지출 탭이 가게를 묶는 열쇠. 많이 쓴 곳과 같은 열쇠([merchantKey])이고, 이름이 없으면 모두 [NO_MERCHANT_KEY] 하나다.
 * 고정지출 목록이 이 열쇠를 저장하므로, 규칙을 바꾸면 저장해 둔 목록과 어긋난다.
 */
fun fixedMerchantKey(merchant: String?): String = merchantKey(merchant) ?: NO_MERCHANT_KEY

/** 고정지출 탭이 고른 달 앞으로 읽는 달 수. 매년(직전 간격 최대 13) + 한 달 늦음(1) + 그 앞 해(12) + 첫 달 몫을 전달 말에 낸 것(1) */
internal const val FIXED_HISTORY_MONTHS = 27L

/**
 * [month] 를 계산하려면 읽어야 하는 첫 달. 매년 내는 것이 한 달 늦어진 때에도 그 앞 해의 결제까지 보여야 매년인 줄 알 수 있어서,
 * 고른 달 앞으로 [FIXED_HISTORY_MONTHS] 달을 읽는다. 첫 달 몫을 전달 말에 미리 냈을 수도 있어 한 달 더 읽는다.
 * 고정지출 계산(ui/fixed)과 목록 등록([registerShownFixedMerchants])이 같이 쓴다.
 */
fun fixedHistoryStart(month: YearMonth): YearMonth = month.minusMonths(FIXED_HISTORY_MONTHS)

/**
 * [month] 를 [today] 에 계산하려면 읽어야 하는 마지막 달. 오늘이 든 달이다. 말일에 낼 것이 쉬는 날로 다음 달에 밀렸으면
 * 그 결제도 고른 달 몫으로 세고, 날짜를 앞으로 적어 둔 이번 달 거래도 센다. 일정은 오늘이 든 달까지의 기록으로 짐작한다.
 */
fun fixedHistoryEnd(month: YearMonth, today: LocalDate): YearMonth = maxOf(month, YearMonth.from(today))

/**
 * [today] 에 고정지출 탭에 보였을 곳을 모두 등록한다(아직 안 냈어요 · 냈어요 · 이번 달엔 안 내요 · 한동안 안 냈어요 모두).
 * 탭이 이번 달을 볼 때 읽는 첫 달([fixedHistoryStart]) 뒤로 '고정지출' 분류 지출이 있는 곳이다. 날짜를 앞으로 적어 둔 다음 달 이후의
 * 지출만 있는 곳도 넣는다(그 달이 오면 탭에 보였을 곳이다). 이미 정해 둔 곳은 그대로 둔다.
 * DB 를 6 → 7 로 올릴 때와 목록이 없는 옛 백업을 되살린 뒤에 돈다.
 *
 * 6 → 7 에서는 DB 7 표(종류 · 해지한 때 칸이 없다)에서 돈다(6 에서 8 로 한 번에 올려도 7 → 8 은 그 뒤다). 그래서 넣는 칸과
 * 정해 둔 열쇠 읽기([FIXED_MERCHANT_KEYS_SQL])에 종류를 쓰지 않는다. DB 8 에서는 넣은 줄이 SQL 기본값으로 고정지출이 된다.
 * 열쇠 읽기가 종류를 가리지 않으므로, 복원은 고정저축 줄을 이 함수 뒤에 넣는다(먼저 넣으면 같은 가게의 고정지출 등록을 막는다).
 * @return 새로 등록한 곳의 수
 */
internal fun registerShownFixedMerchants(db: SupportSQLiteDatabase, today: LocalDate, now: Instant): Int {
    val start = BudgetTime.monthRange(fixedHistoryStart(YearMonth.from(today))).first
    // 첫째 자리(:code)가 분류 코드, 둘째가 첫 달 1일이다
    val keys =
        db.query(
            "$MERCHANTS_WITH_CATEGORY_CODE_SQL AND t.occurredAt >= ?",
            arrayOf<Any>(FIXED_CATEGORY_CODE, start.toEpochMilli()),
        ).use { c ->
            buildSet { while (c.moveToNext()) add(fixedMerchantKey(if (c.isNull(0)) null else c.getString(0))) }
        }
    val stored = db.query(FIXED_MERCHANT_KEYS_SQL).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
    val added = keys - stored
    added.forEach { key ->
        db.execSQL(
            "INSERT INTO fixed_merchants (merchantKey, state, decidedAt) VALUES (?, ?, ?)",
            arrayOf<Any?>(key, FixedMerchantState.REGISTERED.name, now.toEpochMilli()),
        )
    }
    return added.size
}

/**
 * 한 종류의 목록에 정해 둔 곳([stored]) 가운데 그 분류('고정지출' · '저축') 지출이 하나도 남지 않은 곳.
 * [merchants] 는 그 분류로 적은 지출의 가게 이름들이다. 거래를 지웠거나 다른 분류로 옮긴 곳이다(FixedMerchantDao.prune 이 지운다).
 */
internal fun staleFixedMerchants(stored: List<String>, merchants: List<String?>): List<String> {
    val alive = merchants.mapTo(HashSet(), ::fixedMerchantKey)
    return stored.filterNot { it in alive }
}
