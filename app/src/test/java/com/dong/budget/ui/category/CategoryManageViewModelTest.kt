package com.dong.budget.ui.category

import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.db.CategoryDao
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.CategoryWithCount
import com.dong.budget.data.db.ETC_EXPENSE_CODE
import com.dong.budget.data.db.ETC_SORT_ORDER
import com.dong.budget.data.db.PaymentMethodDao
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.PaymentMethodWithCount
import com.dong.budget.ui.card.await
import com.dong.budget.ui.card.fakeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 분류 관리 화면 모델이 지우기 · 순서 저장을 화면에 그려진 목록의 탭 저장소로 하는지 가짜 DAO 로 본다.
 * 분류 '식비'(id 5)와 결제수단 '하나카드'(id 5)처럼 두 표의 id 는 겹칠 수 있다. 탭을 누른 뒤 새 목록이 오기 전에는
 * 옛 탭의 목록이 그대로 그려져 있어서, 그 목록에서 누른 것을 새 탭의 저장소로 처리하면 다른 표의 같은 id 를 건드린다.
 */
class CategoryManageViewModelTest {
    private val food = CategoryEntity(id = 5, uuid = "food", scope = CategoryScope.EXPENSE, name = "식비")
    private val cafe = CategoryEntity(id = 6, uuid = "cafe", scope = CategoryScope.EXPENSE, name = "카페", sortOrder = 1)
    private val etc =
        CategoryEntity(
            id = 9,
            uuid = "etc",
            scope = CategoryScope.EXPENSE,
            name = "기타",
            code = ETC_EXPENSE_CODE,
            sortOrder = ETC_SORT_ORDER,
            isSystem = true,
        )
    private val card = PaymentMethodEntity(id = 5, uuid = "card", name = "하나카드", type = PaymentMethodType.OTHER)

    /** 저장소가 받은 쓰기. 화면 모델은 기본 풀([scope])에서 돌아서(Main 디스패처가 없다) 기다려 본다. */
    private val writes = MutableStateFlow(emptyList<String>())

    /** 결제수단 목록. 내보내기 전까지는 DB 가 아직 읽는 중인 것과 같다. */
    private val payments = MutableSharedFlow<List<PaymentMethodWithCount>>(replay = 1)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun write(what: String) = writes.update { it + what }

    private fun open(): CategoryManageViewModel {
        val categories =
            fakeDao(CategoryDao::class.java) { name, args ->
                when (name) {
                    "observeWithCount" ->
                        if (args[0] == CategoryScope.EXPENSE) {
                            flowOf(listOf(CategoryWithCount(food, 3), CategoryWithCount(cafe, 0), CategoryWithCount(etc, 0)))
                        } else {
                            null
                        }

                    "findById" -> listOf(food, cafe).firstOrNull { it.id == args[0] }

                    "findByCode" -> etc

                    "deleteMovingTransactions" -> true.also { write("분류 ${args[0]} 지움") }

                    "reorder" -> Unit.also { write("분류 순서 ${args[0]}") }

                    else -> null
                }
            }
        val methods =
            fakeDao(PaymentMethodDao::class.java) { name, args ->
                when (name) {
                    "observeWithCount" -> if (args[0] == CategoryScope.EXPENSE) payments else null
                    "findById" -> card.takeIf { it.id == args[0] }
                    "deleteClearingTransactions" -> true.also { write("결제수단 ${args[0]} 지움") }
                    "reorder" -> Unit.also { write("결제수단 순서 ${args[0]}") }
                    else -> null
                }
            }
        return CategoryManageViewModel(CategoryRepository(categories), PaymentMethodRepository(methods), scope)
    }

    @Test
    fun `탭을 바꾼 뒤 첫 결과 전에 옛 목록 항목을 지우면 옛 탭 저장소에서 지운다`() {
        val viewModel = open()
        val shown = viewModel.uiState.await { it.items.isNotEmpty() }
        viewModel.selectTab(ManageTab.PAYMENT)
        // 결제수단 목록은 아직 오지 않아 화면에는 지출 분류 목록이 그대로 있다
        assertEquals(ManageTab.EXPENSE, viewModel.uiState.value.itemsTab)
        val foodRow = shown.items.first { it.id == food.id }
        viewModel.requestDelete(ManageTab.EXPENSE, foodRow)
        // 확인 창도 그 항목이 있던 분류 탭의 글로 묻는다
        assertEquals(PendingDelete(ManageTab.EXPENSE, foodRow), viewModel.uiState.value.pendingDelete)
        viewModel.confirmDelete()
        writes.await { it.isNotEmpty() }
        // 결제수단 5 를 지우고 분류 5 의 거래를 남겨 두면 안 된다
        assertEquals(listOf("분류 5 지움"), writes.value)
    }

    @Test
    fun `탭을 바꾼 뒤 첫 결과 전에 옛 목록 순서를 바꾸면 옛 탭 저장소에 저장하고 그 목록을 바로 바꾼다`() {
        val viewModel = open()
        val shown = viewModel.uiState.await { it.items.isNotEmpty() }
        viewModel.selectTab(ManageTab.PAYMENT)
        val reordered = shown.items.moved(fromId = cafe.id, toId = food.id)!!
        viewModel.reorder(ManageTab.EXPENSE, reordered)
        writes.await { it.isNotEmpty() }
        assertEquals(listOf("분류 순서 [6, 5]"), writes.value)
        // 그려진 목록이 그 탭의 것이라 저장을 기다리지 않고 바뀐 순서로 보인다
        assertEquals(listOf(6L, 5L, 9L), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun `새 탭 목록이 온 뒤에 옛 목록 순서가 들어와도 옛 탭에 저장하고 새 목록은 그대로 둔다`() {
        val viewModel = open()
        val shown = viewModel.uiState.await { it.items.isNotEmpty() }
        viewModel.selectTab(ManageTab.PAYMENT)
        payments.tryEmit(listOf(PaymentMethodWithCount(card, 1)))
        viewModel.uiState.await { it.itemsTab == ManageTab.PAYMENT }
        // 사라지는 중인 옛 목록(AnimatedContent)에서 끌기를 마쳤다
        viewModel.reorder(ManageTab.EXPENSE, shown.items.moved(fromId = cafe.id, toId = food.id)!!)
        writes.await { it.isNotEmpty() }
        assertEquals(listOf("분류 순서 [6, 5]"), writes.value)
        assertEquals(listOf(card.id), viewModel.uiState.value.items.map { it.id })
    }
}
