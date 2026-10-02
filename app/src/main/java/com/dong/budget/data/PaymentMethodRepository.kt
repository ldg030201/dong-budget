package com.dong.budget.data

import android.database.sqlite.SQLiteConstraintException
import com.dong.budget.data.card.encodePerformanceTiers
import com.dong.budget.data.card.normalizePerformanceStartDay
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.PaymentMethodDao
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.PaymentMethodWithCount
import com.dong.budget.data.db.colors
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class PaymentMethodRepository(private val dao: PaymentMethodDao) {
    companion object {
        /** 알림에서 읽어 새로 만드는 결제수단은 모두 카드다. 등록창의 '새로 추가돼요' 미리보기도 이 아이콘을 쓴다. */
        const val NEW_CARD_ICON = "credit_card"

        /** 띄어쓰기와 대소문자를 무시하고 같은 이름인지 본다. '하나 카드' 와 '하나카드' 는 같다. */
        fun sameName(a: String, b: String): Boolean = a.replace(" ", "").equals(b.replace(" ", ""), ignoreCase = true)

        /**
         * 알림에서 읽은 카드 이름을 결제수단 이름으로 다듬는다. 이름 길이 제한에 맞춰 뒤를 자른다.
         * [findOrCreate] 와 등록창이 같은 이름으로 비교해야 '새로 추가돼요' 안내가 실제 동작과 맞는다.
         * @return 비어 있으면 null
         */
        fun normalizeName(raw: String): String? = raw.trim().take(MAX_NAME_LENGTH).trim().ifEmpty { null }

        /**
         * 알림에서 읽은 카드 이름([normalizeName] 으로 다듬은 것)과 같은 결제수단. 없으면 null.
         * 1.2.0 까지는 이름을 [LEGACY_NAME_LENGTH] 자로 잘라 만들었으므로('원더카드2.0 Li'), 그렇게 잘린 이름도 같은 카드로 본다.
         * 보지 않으면 잘린 결제수단을 두고 온전한 이름으로 하나 더 만든다. 이름이 딱 맞는 것이 있으면 그것이 먼저다.
         */
        fun findSameCard(methods: List<PaymentMethodEntity>, cardName: String): PaymentMethodEntity? {
            methods.firstOrNull { sameName(it.name, cardName) }?.let { return it }
            if (cardName.length <= LEGACY_NAME_LENGTH) return null
            val legacyName = cardName.take(LEGACY_NAME_LENGTH).trim()
            return methods.firstOrNull { sameName(it.name, legacyName) }
        }

        /** 1.2.0 까지의 이름 길이 제한 */
        private const val LEGACY_NAME_LENGTH = 10
    }

    fun observeAll(): Flow<List<PaymentMethodEntity>> = dao.observeAll()

    fun observeWithCount(): Flow<List<PaymentMethodWithCount>> = dao.observeWithCount()

    /** 결제수단 하나. 고치면 새 값을, 지우면 null 을 내보낸다. */
    fun observe(id: Long): Flow<PaymentMethodEntity?> = dao.observeById(id)

    /**
     * 카드 실적(구간 금액·시작일)을 저장한다. 구간은 [encodePerformanceTiers] 로 정리하고(비면 실적을 지운 것), 시작일은 1~31 로 맞춘다.
     * @return 없는 결제수단(그사이 지웠음)이면 false
     */
    suspend fun setPerformance(id: Long, tiers: List<Long>, startDay: Int): Boolean =
        dao.updatePerformance(id, encodePerformanceTiers(tiers), normalizePerformanceStartDay(startDay)) > 0

    suspend fun add(rawName: String, icon: String, color: String): AddResult {
        val name = rawName.trim()
        nameProblem(name)?.let { return it }

        val method =
            PaymentMethodEntity(
                uuid = UUID.randomUUID().toString(),
                name = name,
                // 사용자가 만든 결제수단의 세부 종류는 아직 쓰는 곳이 없다
                type = PaymentMethodType.OTHER,
                sortOrder = dao.maxSortOrder() + 1,
                icon = CategoryStyle.iconOrFallback(icon),
                color = CategoryStyle.colorOrFallback(color),
            )
        return try {
            AddResult.Added(dao.insert(method))
        } catch (e: SQLiteConstraintException) {
            // name 에 UNIQUE 인덱스가 있다
            AddResult.DuplicateName
        }
    }

    /**
     * 이름이 같은 결제수단을 찾고, 없으면 카드 아이콘으로 새로 만든다. 알림에서 읽은 카드 이름을 저장할 때 쓴다.
     * @param pickUnusedColor 새로 만들 때 아직 안 쓴 색을 고를지. 아니면 회색이다('새 분류·결제수단은 안 쓴 색으로').
     * @return 결제수단 id. 이름이 비어 있으면 null
     */
    suspend fun findOrCreate(rawName: String, pickUnusedColor: Boolean = true): Long? {
        val name = normalizeName(rawName) ?: return null
        val existing = dao.getAll()
        findSameCard(existing, name)?.let { return it.id }
        val color = if (pickUnusedColor) CategoryStyle.firstUnusedColor(existing.colors()) else CategoryStyle.FALLBACK_COLOR
        return when (val result = add(name, NEW_CARD_ICON, color)) {
            is AddResult.Added -> result.id

            // 그사이 같은 이름이 생겼으면 그것을 쓴다
            else -> findSameCard(dao.getAll(), name)?.id
        }
    }

    /** 결제수단 순서를 바꾼다. 거래 등록 화면의 결제수단 표도 이 순서를 따른다. */
    suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    /** 결제수단을 지운다. 그 결제수단으로 적힌 거래는 결제수단 없이 남는다. */
    suspend fun delete(id: Long): Boolean {
        val method = dao.findById(id) ?: return false
        if (method.isSystem) return false
        return dao.deleteClearingTransactions(id, Instant.now())
    }
}
