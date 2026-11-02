package com.dong.budget.data

import android.database.sqlite.SQLiteException
import com.dong.budget.data.db.FixedKind
import com.dong.budget.data.db.FixedMerchantDao
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * 고정 목록 저장소. [kind] 탭(고정지출 · 고정저축)에 어느 곳을 보일지(등록 · 등록 안 함 · 해지) 정해 둔다.
 * 분류와 따로다. 분류는 거래마다 그대로 두고, 탭은 등록한 곳의 그 분류([FixedKind.categoryCode]) 지출만 센다.
 * 두 종류가 한 표를 쓰고, 저장소는 제 종류의 줄만 읽고 바꾼다.
 */
class FixedMerchantRepository(private val dao: FixedMerchantDao, private val kind: FixedKind) {
    /** 가게 열쇠(fixedMerchantKey)마다 정해 둔 것. 정한 적 없는 곳은 들어 있지 않다. */
    fun observe(): Flow<Map<String, FixedMerchantEntity>> = dao.observeAll(kind).map { list -> list.associateBy { it.merchantKey } }

    /** [key] 곳을 탭에 보인다. 해지한 곳이었으면 해지가 풀린다(해지한 때를 지운다). */
    suspend fun register(key: String, at: Instant) = dao.upsert(FixedMerchantEntity(key, FixedMerchantState.REGISTERED, at, kind))

    /** [key] 곳을 탭에 넣지 않는다('안 함', 탭에서 빼기). [at] 뒤에 그곳의 그 분류 지출이 새로 생기면 다시 묻는다. */
    suspend fun decline(key: String, at: Instant) = dao.upsert(FixedMerchantEntity(key, FixedMerchantState.DECLINED, at, kind))

    /**
     * [key] 곳을 [at] 에 해지한다. 해지 전에 낸 마지막 결제의 몫 달까지는 탭에 그대로 보이고, 그다음 달부터는 '해지한 곳' 에만 있다.
     * [at] 뒤에 그곳의 그 분류 지출이 새로 생기면 다시 등록할지 묻는다.
     */
    suspend fun end(key: String, at: Instant) = dao.upsert(FixedMerchantEntity(key, FixedMerchantState.ENDED, at, kind, endedAt = at))

    /**
     * 해지한 [key] 곳에 다시 물은 것에 '안 함' 을 눌렀다. 해지한 채로 두고 정한 때만 [at] 으로 바꾼다. 해지한 때는 그대로라
     * 해지 전 결제는 그대로 보이고, [at] 뒤에 새로 내야 다시 묻는다. 그 사이 해지한 곳이 아니게 됐으면(복원 등) 등록 안 한 곳으로 둔다.
     */
    suspend fun keepEnded(key: String, at: Instant) {
        if (dao.keepEnded(kind, key, at) == 0) decline(key, at)
    }

    /**
     * 그 분류 지출이 하나도 남지 않은 곳을 목록에서 지운다([FixedMerchantDao.prune]). 탭을 열 때 한 번 한다.
     * 실패해도 탭은 그대로 쓴다(남은 줄은 탭에 보이지 않고, 다음에 탭을 열 때 다시 정리한다).
     */
    suspend fun prune() {
        try {
            val pruned = dao.prune(kind)
            if (pruned > 0) DevLog.info(LogTag.APP, "${kind.categoryName} 지출이 없는 곳 ${pruned}곳을 ${kind.listName} 목록에서 지웠어요")
        } catch (e: SQLiteException) {
            DevLog.warn(LogTag.APP, "${kind.listName} 목록을 정리하지 못했어요. 다음에 탭을 열 때 다시 해요", e)
        }
    }
}

/** 기록에 적는 목록 이름 */
private val FixedKind.listName: String
    get() =
        when (this) {
            FixedKind.EXPENSE -> "고정지출"
            FixedKind.SAVINGS -> "고정저축"
        }

/** 기록에 적는 분류 이름(따옴표까지) */
private val FixedKind.categoryName: String
    get() =
        when (this) {
            FixedKind.EXPENSE -> "'고정지출'"
            FixedKind.SAVINGS -> "'저축'"
        }
