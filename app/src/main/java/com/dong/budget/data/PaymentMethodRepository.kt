package com.dong.budget.data

import android.database.sqlite.SQLiteConstraintException
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.PaymentMethodDao
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.PaymentMethodWithCount
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class PaymentMethodRepository(private val dao: PaymentMethodDao) {
    companion object {
        /** 알림에서 읽어 새로 만드는 결제수단은 모두 카드다 */
        private const val NEW_CARD_ICON = "credit_card"

        /** 띄어쓰기와 대소문자를 무시하고 같은 이름인지 본다. '하나 카드' 와 '하나카드' 는 같다. */
        fun sameName(a: String, b: String): Boolean = a.replace(" ", "").equals(b.replace(" ", ""), ignoreCase = true)
    }

    fun observeWithCount(): Flow<List<PaymentMethodWithCount>> = dao.observeWithCount()

    suspend fun add(rawName: String, icon: String, color: String): AddResult {
        val name = rawName.trim()
        if (name.isEmpty()) return AddResult.BlankName
        if (name.length > MAX_NAME_LENGTH) return AddResult.NameTooLong

        val method =
            PaymentMethodEntity(
                uuid = UUID.randomUUID().toString(),
                name = name,
                // 사용자가 만든 결제수단의 세부 종류는 아직 쓰는 곳이 없다
                type = PaymentMethodType.OTHER,
                sortOrder = dao.maxSortOrder() + 1,
                icon = icon.takeIf { it in CategoryStyle.ICONS } ?: CategoryStyle.FALLBACK_ICON,
                color = color.takeIf { it in CategoryStyle.COLORS } ?: CategoryStyle.FALLBACK_COLOR,
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
        val name = rawName.trim().take(MAX_NAME_LENGTH)
        if (name.isEmpty()) return null
        val existing = dao.getAll()
        existing.firstOrNull { sameName(it.name, name) }?.let { return it.id }
        val used = existing.mapTo(mutableSetOf()) { it.color }
        val color = CategoryStyle.COLORS.firstOrNull { it != CategoryStyle.FALLBACK_COLOR && it !in used } ?: CategoryStyle.FALLBACK_COLOR
        return when (val result = add(name, NEW_CARD_ICON, color)) {
            is AddResult.Added -> result.id

            // 그사이 같은 이름이 생겼으면 그것을 쓴다
            else -> dao.getAll().firstOrNull { sameName(it.name, name) }?.id
        }
    }

    /** 결제수단을 지운다. 그 결제수단으로 적힌 거래는 결제수단 없이 남는다. */
    suspend fun delete(id: Long): Boolean {
        val method = dao.findById(id) ?: return false
        if (method.isSystem) return false
        return dao.deleteClearingTransactions(id, Instant.now())
    }
}
