package com.dong.budget.ui.fixed

import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.FixedMerchantRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryDao
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.FixedExpenseRow
import com.dong.budget.data.db.FixedKind
import com.dong.budget.data.db.FixedMerchantDao
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.testing.seoulInstant
import com.dong.budget.testing.tx
import com.dong.budget.ui.card.await
import com.dong.budget.ui.card.fakeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant

/**
 * 고정지출 화면 모델이 줄에서 누른 것을 목록의 어느 동작으로 보내는지 가짜 DAO 로 본다. 기기 없이 돌도록 화면 모델을 기본 풀
 * 범위([scope])에서 돌린다(Main 디스패처가 없다). 목록 표는 [table] 하나로, upsert · keepEnded 가 바꾸고 observeAll 이 따라간다(Room 처럼).
 * '안 함' 은 판을 그린 목록으로 해지한 곳인지 보므로, 판을 한 번 그린 뒤에 누른다.
 * 거래와 분류는 [expenses] · [categories] 로, 조회는 분류 코드로 그 분류 지출만 돌려준다(Room 처럼).
 */
class FixedExpenseViewModelTest {
    // 10월 16일 낮(서울)
    private val clock = Clock.fixed(Instant.parse("2026-10-16T03:00:00Z"), BudgetTime.ZONE)
    private val now = clock.instant()
    private val endedAt = seoulInstant("2026-09-20")
    private val table = MutableStateFlow<List<FixedMerchantEntity>>(emptyList())
    private val expenses = MutableStateFlow<Map<String, List<FixedExpenseRow>>>(emptyMap())
    private val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val merchants =
        fakeDao(FixedMerchantDao::class.java) { name, args ->
            when (name) {
                "observeAll" -> table.map { rows -> rows.filter { it.kind == args[0] } }

                "upsert" -> {
                    val row = args[0] as FixedMerchantEntity
                    table.update { rows -> rows.filterNot { it.kind == row.kind && it.merchantKey == row.merchantKey } + row }
                }

                // UPDATE … SET decidedAt = :at WHERE kind = :kind AND merchantKey = :key AND state = 'ENDED'
                "keepEnded" -> {
                    val (kind, key, at) = args
                    val hit = { row: FixedMerchantEntity ->
                        row.kind == kind && row.merchantKey == key && row.state == FixedMerchantState.ENDED
                    }
                    val count = table.value.count(hit)
                    table.update { rows -> rows.map { if (hit(it)) it.copy(decidedAt = at as Instant) else it } }
                    count
                }

                "prune" -> 0

                else -> null
            }
        }

    private fun open(vararg rows: FixedMerchantEntity, kind: FixedKind = FixedKind.EXPENSE): FixedExpenseViewModel {
        table.value = rows.toList()
        val model =
            FixedExpenseViewModel(
                TransactionRepository(
                    fakeDao(TransactionDao::class.java) { name, args ->
                        if (name == "observeExpensesWithCategoryCode") expenses.map { it[args[0]].orEmpty() } else null
                    },
                ),
                CategoryRepository(
                    fakeDao(CategoryDao::class.java) { name, _ ->
                        if (name == "observeByScope") categories else null
                    },
                ),
                FixedMerchantRepository(merchants, kind),
                clock,
                scope,
            )
        // 판은 그리는 동안만 계산한다(그리는 화면처럼 구독한다)
        scope.launch { model.uiState.collect {} }
        model.uiState.await { it.loaded }
        return model
    }

    private fun rowOf(key: String, kind: FixedKind = FixedKind.EXPENSE): FixedMerchantEntity? =
        table.value.singleOrNull { it.kind == kind && it.merchantKey == key }

    private fun category(id: Long, name: String, code: String) =
        CategoryEntity(id = id, uuid = code, scope = CategoryScope.EXPENSE, name = name, code = code)

    @After
    fun clear() = scope.cancel()

    @Test
    fun `해지한 곳에 다시 물은 줄의 안 함은 해지한 채로 두고 정한 때만 바꾼다`() {
        // 등록 안 한 곳으로 두면 해지한 때가 지워져 끝 달까지 보여야 할 지난 달 판에서도 통째로 빠진다
        val model = open(FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, endedAt, endedAt = endedAt))

        model.decline("넷플릭스")

        table.await { rowOf("넷플릭스")?.decidedAt == now }
        assertEquals(FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, now, endedAt = endedAt), rowOf("넷플릭스"))
    }

    @Test
    fun `정한 적 없는 곳과 등록 안 한 곳의 안 함은 등록 안 한 곳으로 둔다`() {
        val model = open(FixedMerchantEntity("헬스장", FixedMerchantState.DECLINED, seoulInstant("2026-09-01")))

        model.decline("헬스장")
        model.decline("통신비")

        table.await { rows -> rows.size == 2 && rows.all { it.decidedAt == now } }
        assertEquals(FixedMerchantEntity("헬스장", FixedMerchantState.DECLINED, now), rowOf("헬스장"))
        assertEquals(FixedMerchantEntity("통신비", FixedMerchantState.DECLINED, now), rowOf("통신비"))
    }

    @Test
    fun `해지한 곳을 길게 눌러 빼면 해지한 때를 지우고 등록 안 한 곳이 된다`() {
        val model = open(FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, endedAt, endedAt = endedAt))

        model.remove("넷플릭스")

        table.await { rowOf("넷플릭스")?.decidedAt == now }
        assertEquals(FixedMerchantEntity("넷플릭스", FixedMerchantState.DECLINED, now), rowOf("넷플릭스"))
    }

    @Test
    fun `되돌리면 해지한 때를 지우고 등록한 곳이 된다`() {
        val model = open(FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, endedAt, endedAt = endedAt))

        model.restore("넷플릭스")

        table.await { rowOf("넷플릭스")?.decidedAt == now }
        assertEquals(FixedMerchantEntity("넷플릭스", FixedMerchantState.REGISTERED, now), rowOf("넷플릭스"))
    }

    @Test
    fun `해지하면 정한 때와 해지한 때가 모두 지금이다`() {
        val model = open(FixedMerchantEntity("넷플릭스", FixedMerchantState.REGISTERED, seoulInstant("2026-10-01")))

        model.end("넷플릭스")

        table.await { rowOf("넷플릭스")?.decidedAt == now }
        assertEquals(FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, now, endedAt = now), rowOf("넷플릭스"))
    }

    @Test
    fun `해지한 곳만 남아 판이 빈 달은 해지한 곳이 있다고 알려 요약이 낼 것이 없다고 적는다`() {
        // 7월 5일까지 매달 내다 그만둔 넷플릭스를 9월 20일에 해지했다(끝 달 7월). 8월부터 판은 비지만 목록에 넣은 적은 있다.
        categories.value = listOf(category(4, "고정지출", FIXED_CATEGORY_CODE))
        expenses.value =
            mapOf(
                FIXED_CATEGORY_CODE to
                    listOf("2026-05-05", "2026-06-05", "2026-07-05").map {
                        FixedExpenseRow(tx(it, 17_000, categoryId = 4, merchant = "넷플릭스"), now)
                    },
            )
        val model = open(FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, endedAt, endedAt = endedAt))

        val october = model.uiState.value
        assertTrue(october.board.isEmpty)
        assertTrue(october.hasEnded)
        assertEquals("이번 달에 낼 고정지출이 없어요", october.texts.countSentence(october.board, october.month, october.today, october.hasEnded))

        model.showPreviousMonth()

        val september = model.uiState.await { it.month.monthValue == 9 }
        assertTrue(september.board.isEmpty)
        assertEquals(
            "9월엔 낼 고정지출이 없었어요",
            september.texts.countSentence(september.board, september.month, september.today, september.hasEnded),
        )
    }

    @Test
    fun `고정저축 화면 모델은 저축 분류 지출을 고정저축 목록으로만 나누고 등록도 고정저축 목록에 한다`() {
        categories.value = listOf(category(4, "고정지출", FIXED_CATEGORY_CODE), category(7, "저축", SAVINGS_CATEGORY_CODE))
        expenses.value =
            mapOf(
                FIXED_CATEGORY_CODE to listOf(FixedExpenseRow(tx("2026-10-03", 17_000, categoryId = 4, merchant = "넷플릭스"), now)),
                SAVINGS_CATEGORY_CODE to listOf(FixedExpenseRow(tx("2026-10-05", 300_000, categoryId = 7, merchant = "적금"), now)),
            )
        // 같은 곳을 고정지출 목록에 등록해 두었어도 고정저축 목록과는 따로다
        val model =
            open(
                FixedMerchantEntity("적금", FixedMerchantState.REGISTERED, seoulInstant("2026-09-01")),
                FixedMerchantEntity("넷플릭스", FixedMerchantState.REGISTERED, seoulInstant("2026-09-01")),
                kind = FixedKind.SAVINGS,
            )

        val state = model.uiState.value
        assertEquals(FixedKind.SAVINGS, state.kind)
        assertTrue(state.hasCategory)
        assertEquals(listOf("적금"), state.candidates.map { it.key })
        assertTrue(state.board.isEmpty)

        model.register("적금")

        table.await { rowOf("적금", FixedKind.SAVINGS) != null }
        assertEquals(FixedMerchantEntity("적금", FixedMerchantState.REGISTERED, now, FixedKind.SAVINGS), rowOf("적금", FixedKind.SAVINGS))
        assertEquals(FixedMerchantEntity("적금", FixedMerchantState.REGISTERED, seoulInstant("2026-09-01")), rowOf("적금"))
        model.uiState.await { it.board.itemOf("적금") != null }
    }

    @Test
    fun `고정저축 화면 모델은 고정지출 분류만 있으면 저축 분류가 없다고 본다`() {
        categories.value = listOf(category(4, "고정지출", FIXED_CATEGORY_CODE))

        assertFalse(open(kind = FixedKind.SAVINGS).uiState.value.hasCategory)
    }
}
