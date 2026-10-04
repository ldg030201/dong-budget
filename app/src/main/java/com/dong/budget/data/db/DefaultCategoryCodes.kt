package com.dong.budget.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag

// ─────────────────────────────────────────────────────────────────────
// 기능이 코드로 찾는 기본 분류('고정지출'·'급여')를 지운 뒤 같은 이름으로 다시 만들었을 때 코드를 돌려주는 규칙.
// 고정지출 탭과 월급 등록은 이름이 아니라 코드로 분류를 찾는다. 다시 만든 분류에 코드가 없으면 '분류가 없어요' 로 보인다.
// - 새로 만들 때: CategoryRepository.add 가 붙인다(defaultCodeFor).
// - 이미 코드 없이 만들어 둔 것(0.1.3 ~ 1.6.1 에서 지우고 다시 만든 것, 그런 백업을 되살린 것):
//   DB 를 열 때 · 백업을 되살린 뒤 · 분류를 지운 뒤에 [reclaimedCodes] 로 골라 돌려준다.
// ─────────────────────────────────────────────────────────────────────

/** 지운 뒤 다시 만들면 코드를 돌려줄 기본 분류. 기능이 코드로 찾는 것만 둔다. */
internal val RECLAIMABLE_DEFAULTS = DEFAULT_CATEGORIES.filter { it.code == FIXED_CATEGORY_CODE || it.code == SALARY_CATEGORY_CODE }

/** 분류 이름을 견줄 때 쓰는 꼴. 앞뒤 · 가운데 띄어쓰기를 모두 뺀다('고정 지출' 도 '고정지출' 이다). */
internal fun String.withoutSpaces(): String = filterNot { it.isWhitespace() }

/**
 * 코드를 돌려받을 분류(분류 id → 코드). [RECLAIMABLE_DEFAULTS] 마다 같은 종류에 그 코드를 가진 분류가 하나도 없을 때,
 * 코드 없는 같은 이름(띄어쓰기 무시) 분류 하나에 그 코드를 준다. 여럿이면 [rows] 에서 먼저 나온 것(id 가 가장 작은 것)이다.
 * 코드는 종류마다 하나라서, 그 코드를 가진 분류가 이미 있으면 아무것도 바꾸지 않는다.
 */
internal fun reclaimedCodes(rows: List<CategoryCodeRow>): Map<Long, String> = RECLAIMABLE_DEFAULTS
    .filter { d -> rows.none { it.scope == d.scope && it.code == d.code } }
    .mapNotNull { d ->
        val name = d.name.withoutSpaces()
        rows.firstOrNull { it.scope == d.scope && it.code == null && it.name.withoutSpaces() == name }?.let { it.id to d.code }
    }.toMap()

/**
 * DB 를 열 때(onOpen) 부른다. [reclaimDefaultCodes] 가 실패해도(저장공간이 꽉 차 쓰지 못하는 등) DB 열기를 막지 않는다.
 * 여기서 던지면 Room 이 DB 를 열지 못해 모든 화면·알림이 멈춘다. 코드 돌려주기는 다음에 열 때 다시 해도 되니 로그만 남기고 넘어간다.
 */
internal fun reclaimDefaultCodesOnOpen(db: SupportSQLiteDatabase) {
    try {
        reclaimDefaultCodes(db)
    } catch (e: Exception) {
        DevLog.warn(LogTag.APP, "기본 분류 코드를 돌려주지 못했어요. 다음에 DB 를 열 때 다시 해요", e)
    }
}

/**
 * DB 를 열 때 [CategoryDao.reclaimDefaultCodes] 와 같은 일을 한다. 여는 중에는 DAO 를 쓸 수 없어서 같은 조회를 직접 돌린다.
 * 종류 칸이 알 수 없는 값인 줄은 건너뛴다(여기서 멈추면 앱이 열리지 않는다).
 */
internal fun reclaimDefaultCodes(db: SupportSQLiteDatabase) {
    val rows =
        db.query(CATEGORY_CODE_ROWS_SQL).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val scope = CategoryScope.entries.firstOrNull { it.name == c.getString(1) } ?: continue
                    add(
                        CategoryCodeRow(
                            id = c.getLong(0),
                            scope = scope,
                            name = c.getString(2),
                            code = if (c.isNull(3)) null else c.getString(3),
                        ),
                    )
                }
            }
        }
    reclaimedCodes(rows).forEach { (id, code) ->
        db.execSQL("UPDATE categories SET code = ? WHERE id = ? AND code IS NULL", arrayOf<Any?>(code, id))
    }
}
