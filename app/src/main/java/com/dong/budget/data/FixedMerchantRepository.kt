package com.dong.budget.data

import android.database.sqlite.SQLiteException
import com.dong.budget.data.db.FixedMerchantDao
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * 고정지출 목록 저장소. 고정지출 탭에 어느 곳을 보일지(등록 · 등록 안 함) 정해 둔다.
 * '고정지출' 분류와 따로다. 분류는 거래마다 그대로 두고, 탭은 등록한 곳의 '고정지출' 분류 지출만 센다.
 */
class FixedMerchantRepository(private val dao: FixedMerchantDao) {
    /** 가게 열쇠(fixedMerchantKey)마다 정해 둔 것. 정한 적 없는 곳은 들어 있지 않다. */
    fun observe(): Flow<Map<String, FixedMerchantEntity>> = dao.observeAll().map { list -> list.associateBy { it.merchantKey } }

    /** [key] 곳을 고정지출 탭에 보인다 */
    suspend fun register(key: String, at: Instant) = dao.upsert(FixedMerchantEntity(key, FixedMerchantState.REGISTERED, at))

    /** [key] 곳을 고정지출 탭에 넣지 않는다('안 함', 탭에서 빼기). [at] 뒤에 그곳의 '고정지출' 지출이 새로 생기면 다시 묻는다. */
    suspend fun decline(key: String, at: Instant) = dao.upsert(FixedMerchantEntity(key, FixedMerchantState.DECLINED, at))

    /**
     * '고정지출' 분류 지출이 하나도 남지 않은 곳을 목록에서 지운다([FixedMerchantDao.prune]). 고정지출 탭을 열 때 한 번 한다.
     * 실패해도 탭은 그대로 쓴다(남은 줄은 탭에 보이지 않고, 다음에 탭을 열 때 다시 정리한다).
     */
    suspend fun prune() {
        try {
            val pruned = dao.prune()
            if (pruned > 0) DevLog.info(LogTag.APP, "'고정지출' 지출이 없는 곳 ${pruned}곳을 고정지출 목록에서 지웠어요")
        } catch (e: SQLiteException) {
            DevLog.warn(LogTag.APP, "고정지출 목록을 정리하지 못했어요. 다음에 탭을 열 때 다시 해요", e)
        }
    }
}
