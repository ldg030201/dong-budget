package com.dong.budget.data

import android.database.sqlite.SQLiteConstraintException
import com.dong.budget.data.db.CategoryDao
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.CategoryWithCount
import com.dong.budget.data.db.etcCodeFor
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class CategoryRepository(private val dao: CategoryDao) {
    fun observeWithCount(scope: CategoryScope): Flow<List<CategoryWithCount>> = dao.observeWithCount(scope)

    suspend fun add(scope: CategoryScope, rawName: String, icon: String, color: String): AddResult {
        val name = rawName.trim()
        if (name.isEmpty()) return AddResult.BlankName
        if (name.length > MAX_NAME_LENGTH) return AddResult.NameTooLong

        val category =
            CategoryEntity(
                uuid = UUID.randomUUID().toString(),
                scope = scope,
                name = name,
                // 목록에 없는 값이 들어오면 저장하지 않는다. 화면이 그릴 수 없는 이름이 DB 에 남는다.
                icon = icon.takeIf { it in CategoryStyle.ICONS } ?: CategoryStyle.FALLBACK_ICON,
                color = color.takeIf { it in CategoryStyle.COLORS } ?: CategoryStyle.FALLBACK_COLOR,
                // '기타' 바로 앞에 붙인다
                sortOrder = dao.maxUserSortOrder(scope) + 1,
            )
        return try {
            AddResult.Added(dao.insert(category))
        } catch (e: SQLiteConstraintException) {
            // (scope, name) 에 UNIQUE 인덱스가 있다. 미리 조회하지 않고 DB 가 막게 둔다.
            // 조회와 삽입 사이에 다른 곳에서 같은 이름을 넣는 경우까지 막아준다.
            AddResult.DuplicateName
        }
    }

    /**
     * 분류 순서를 바꾼다. 거래 등록 화면의 분류 표도 이 순서를 따른다.
     * '기타' 는 목록에 들어와도 옮기지 않고 늘 맨 뒤에 둔다(ETC_SORT_ORDER).
     */
    suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    /**
     * 분류를 지운다. 그 분류로 적힌 거래는 같은 종류의 '기타' 로 옮긴다.
     * '기타' 자체는 지울 수 없다.
     */
    suspend fun delete(id: Long): Boolean {
        val category = dao.findById(id) ?: return false
        if (category.isSystem) return false
        val fallback = dao.findByCode(category.scope, etcCodeFor(category.scope)) ?: return false
        return dao.deleteMovingTransactions(id = id, fallbackId = fallback.id, now = Instant.now())
    }
}
