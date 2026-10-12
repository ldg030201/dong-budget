package com.dong.budget.ui.editor

import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionType
import com.dong.budget.navigation.PrefillSource
import com.dong.budget.ui.category.AddTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiStateTest {
    private val hana = PaymentMethodEntity(id = 1, uuid = "u1", name = "하나카드", type = PaymentMethodType.OTHER)
    private val incomeCash =
        PaymentMethodEntity(id = 2, uuid = "u2", scope = CategoryScope.INCOME, name = "현금", type = PaymentMethodType.CASH, color = "green")
    private val bank =
        PaymentMethodEntity(
            id = 3,
            uuid = "u3",
            scope = CategoryScope.INCOME,
            name = "통장",
            type = PaymentMethodType.ACCOUNT,
            color = "teal",
        )
    private val methods = listOf(hana, incomeCash, bank)

    /** 알림에서 채워 연 등록창. '토스뱅크' 는 아직 결제수단에 없다. */
    private val prefilled = EditorUiState(pendingPaymentName = "토스뱅크", allPaymentMethods = listOf(hana))

    @Test
    fun `알림에서 읽은 새 카드는 다른 결제수단을 고르기 전까지 골라져 있다`() {
        assertTrue(prefilled.isPendingPaymentSelected)
        assertFalse(RequiredField.PAYMENT in prefilled.missingFields)
    }

    @Test
    fun `다른 결제수단을 골라도 새 카드 이름은 남아 다시 고를 수 있다`() {
        val switched = prefilled.copy(paymentMethodId = hana.id)
        assertFalse(switched.isPendingPaymentSelected)
        assertEquals(hana, switched.selectedPaymentMethod)
        assertEquals("토스뱅크", switched.pendingPaymentName)
        assertFalse(RequiredField.PAYMENT in switched.missingFields)

        assertTrue(switched.copy(paymentMethodId = null).isPendingPaymentSelected)
    }

    @Test
    fun `새로 만들 카드의 색은 안 쓴 색이고, 스위치를 끄면 회색이다`() {
        assertEquals(CategoryStyle.firstUnusedColor(setOf(hana.color)), prefilled.pendingPaymentColor)
        assertEquals(CategoryStyle.FALLBACK_COLOR, prefilled.copy(pickUnusedColor = false).pendingPaymentColor)
    }

    @Test
    fun `열쇠 없이 채워 연 고정지출도 채워 연 등록창이다`() {
        assertTrue(EditorUiState(prefillSource = PrefillSource.FIXED_EXPENSE, dedupKey = null).isPrefilled)
        assertTrue(EditorUiState(prefillSource = PrefillSource.PAYMENT_ALERT, dedupKey = "toss:1").isPrefilled)
        assertFalse(EditorUiState().isPrefilled)
    }

    @Test
    fun `번호로 고른 결제수단을 그사이 지웠으면 비어 있는 것으로 보고 고르게 한다`() {
        val fixed = EditorUiState(prefillSource = PrefillSource.FIXED_EXPENSE, paymentMethodId = 99, allPaymentMethods = listOf(hana))
        assertEquals(null, fixed.selectedPaymentMethod)
        assertFalse(fixed.isPendingPaymentSelected)
        assertTrue(RequiredField.PAYMENT in fixed.missingFields)
        assertEquals(hana, fixed.copy(paymentMethodId = hana.id).selectedPaymentMethod)
    }

    @Test
    fun `알림에서 읽은 카드가 없으면 결제수단을 골라야 한다`() {
        assertFalse(EditorUiState().isPendingPaymentSelected)
        assertTrue(RequiredField.PAYMENT in EditorUiState().missingFields)
    }

    @Test
    fun `지출은 결제수단만 수입은 들어온 곳만 표에 둔다`() {
        val expense = EditorUiState(allPaymentMethods = methods)
        assertEquals(listOf(hana), expense.paymentMethods)
        assertEquals(AddTarget.PAYMENT, expense.paymentAddTarget)

        val income = expense.copy(type = TransactionType.INCOME)
        assertEquals(listOf(incomeCash, bank), income.paymentMethods)
        assertEquals(AddTarget.INCOME_SOURCE, income.paymentAddTarget)
        // 추가 시트의 처음 색은 그 목록에서 안 쓴 색이다
        assertEquals(setOf("green", "teal"), income.usedColors(AddTarget.INCOME_SOURCE))
        assertEquals(setOf(hana.color), income.usedColors(AddTarget.PAYMENT))
    }

    @Test
    fun `수입은 들어온 곳을 비워도 저장할 수 있다`() {
        val income = EditorUiState(type = TransactionType.INCOME, allPaymentMethods = methods)
        assertFalse(RequiredField.PAYMENT in income.missingFields)
        assertTrue(RequiredField.PAYMENT in income.copy(type = TransactionType.EXPENSE).missingFields)
    }

    @Test
    fun `옛 수입이 가리키는 카드는 그 수입을 고칠 때 표에 남아 골라져 있다`() {
        val old =
            EditorUiState(
                isEditing = true,
                type = TransactionType.INCOME,
                allPaymentMethods = methods,
                paymentMethodId = hana.id,
                loadedPaymentMethodId = hana.id,
                loadedType = TransactionType.INCOME,
            )
        assertEquals(hana, old.selectedPaymentMethod)
        assertEquals(listOf(incomeCash, bank, hana), old.paymentMethods)
        // 새로 적는 수입에는 결제수단이 보이지 않는다
        assertEquals(null, old.copy(loadedType = null).selectedPaymentMethod)
    }

    @Test
    fun `새로 만들 들어온 곳은 수입에서만 보이고 통장 모양에 그 목록에서 안 쓴 색이다`() {
        val deposit =
            EditorUiState(
                type = TransactionType.INCOME,
                allPaymentMethods = methods,
                pendingPaymentName = "토스뱅크 통장",
                pendingPaymentScope = CategoryScope.INCOME,
            )
        assertTrue(deposit.isPendingPaymentSelected)
        assertEquals("account_balance", deposit.pendingPaymentIcon)
        assertEquals(CategoryStyle.firstUnusedColor(setOf("green", "teal")), deposit.pendingPaymentColor)
        // 비우기로 했으면 고르지 않은 것이다
        assertFalse(deposit.copy(paymentCleared = true).isPendingPaymentSelected)
        // 지출로 바꾸면 숨고 결제수단을 골라야 한다
        val expense = deposit.copy(type = TransactionType.EXPENSE)
        assertEquals(null, expense.visiblePendingPaymentName)
        assertFalse(expense.isPendingPaymentSelected)
        assertTrue(RequiredField.PAYMENT in expense.missingFields)
    }

    @Test
    fun `수입에서 고른 들어온 곳을 다시 누르면 비우고 지출의 결제수단은 그대로 둔다`() {
        val income = EditorUiState(type = TransactionType.INCOME, allPaymentMethods = methods).withPaymentMethodTapped(bank.id)
        assertEquals(bank, income.selectedPaymentMethod)
        assertEquals(null, income.withPaymentMethodTapped(bank.id).selectedPaymentMethod)

        val expense = EditorUiState(allPaymentMethods = methods).withPaymentMethodTapped(hana.id)
        assertEquals(hana, expense.withPaymentMethodTapped(hana.id).selectedPaymentMethod)
    }

    @Test
    fun `지출과 수입을 바꾸면 새 목록에 없는 결제수단은 비운다`() {
        val expense = EditorUiState(allPaymentMethods = methods).withPaymentMethodTapped(hana.id)
        assertEquals(null, expense.withType(TransactionType.INCOME).paymentMethodId)
        val income = EditorUiState(type = TransactionType.INCOME, allPaymentMethods = methods).withPaymentMethodTapped(bank.id)
        assertEquals(null, income.withType(TransactionType.EXPENSE).paymentMethodId)
        // 목록을 아직 읽기 전이면 그대로 둔다
        assertEquals(hana.id, EditorUiState(paymentMethodId = hana.id).withType(TransactionType.INCOME).paymentMethodId)
    }

    @Test
    fun `카드로 적은 옛 수입은 다시 눌러 비웠다가 다시 고를 수 있다`() {
        val old =
            EditorUiState(
                isEditing = true,
                type = TransactionType.INCOME,
                allPaymentMethods = methods,
                paymentMethodId = hana.id,
                loadedPaymentMethodId = hana.id,
                loadedType = TransactionType.INCOME,
            )
        val cleared = old.withPaymentMethodTapped(hana.id)
        assertEquals(null, cleared.selectedPaymentMethod)
        assertEquals(hana, cleared.withPaymentMethodTapped(hana.id).selectedPaymentMethod)
        // 지출로 바꾸면 결제수단 목록에 있으니 그대로 골라져 있다
        assertEquals(hana, old.withType(TransactionType.EXPENSE).selectedPaymentMethod)
    }

    @Test
    fun `입금 알림의 통장은 다시 눌러 비울 수 있고 다시 누르면 새로 만들 것으로 고른다`() {
        val deposit =
            EditorUiState(
                type = TransactionType.INCOME,
                allPaymentMethods = methods,
                pendingPaymentName = "토스뱅크 통장",
                pendingPaymentScope = CategoryScope.INCOME,
            )
        val cleared = deposit.withPendingPaymentTapped()
        assertFalse(cleared.isPendingPaymentSelected)
        assertFalse(RequiredField.PAYMENT in cleared.missingFields)
        assertTrue(cleared.withPendingPaymentTapped().isPendingPaymentSelected)
        // 다른 것을 골랐다가 돌아와도 고른다
        assertTrue(deposit.withPaymentMethodTapped(bank.id).withPendingPaymentTapped().isPendingPaymentSelected)
    }

    @Test
    fun `월급날의 통장은 들어온 곳에서 찾아 고르고 결제수단에서 찾지 않는다`() {
        val payday =
            EditorUiState(type = TransactionType.INCOME, pendingPaymentName = "통장", pendingPaymentScope = CategoryScope.INCOME)
        val loaded = payday.withPaymentMethods(methods, keepPending = false)
        assertEquals(bank, loaded.selectedPaymentMethod)
        assertEquals(null, loaded.pendingPaymentName)

        // 결제수단에만 같은 이름이 있으면 들어온 곳에는 없는 것이라 새로 만들지 않고 비워 둔다
        val expenseOnly = listOf(hana, bank.copy(scope = CategoryScope.EXPENSE))
        val none = payday.withPaymentMethods(expenseOnly, keepPending = false)
        assertEquals(null, none.selectedPaymentMethod)
        assertEquals(null, none.pendingPaymentName)
    }
}
