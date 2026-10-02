package com.dong.budget.ui.editor

import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.navigation.PrefillSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiStateTest {
    private val hana = PaymentMethodEntity(id = 1, uuid = "u1", name = "하나카드", type = PaymentMethodType.OTHER)

    /** 알림에서 채워 연 등록창. '토스뱅크' 는 아직 결제수단에 없다. */
    private val prefilled = EditorUiState(pendingPaymentName = "토스뱅크", paymentMethods = listOf(hana))

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
        val fixed = EditorUiState(prefillSource = PrefillSource.FIXED_EXPENSE, paymentMethodId = 99, paymentMethods = listOf(hana))
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
}
