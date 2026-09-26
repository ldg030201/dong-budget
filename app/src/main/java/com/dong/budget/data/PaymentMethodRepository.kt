package com.dong.budget.data

import android.database.sqlite.SQLiteConstraintException
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
    }

    fun observeAll(): Flow<List<PaymentMethodEntity>> = dao.observeAll()

    fun observeWithCount(): Flow<List<PaymentMethodWithCount>> = dao.observeWithCount()

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
     * @return 결제수단 id. 이름이 비어 있으면 null
     */
    suspend fun findOrCreate(rawName: String): Long? {
        val name = normalizeName(rawName) ?: return null
        val existing = dao.getAll()
        existing.firstOrNull { sameName(it.name, name) }?.let { return it.id }
        val color = CategoryStyle.firstUnusedColor(existing.colors())
        return when (val result = add(name, NEW_CARD_ICON, color)) {
            is AddResult.Added -> result.id

            // 그사이 같은 이름이 생겼으면 그것을 쓴다
            else -> dao.getAll().firstOrNull { sameName(it.name, name) }?.id
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
