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

    /** 결제수단을 지운다. 그 결제수단으로 적힌 거래는 결제수단 없이 남는다. */
    suspend fun delete(id: Long): Boolean {
        val method = dao.findById(id) ?: return false
        if (method.isSystem) return false
        return dao.deleteClearingTransactions(id, Instant.now())
    }
}
