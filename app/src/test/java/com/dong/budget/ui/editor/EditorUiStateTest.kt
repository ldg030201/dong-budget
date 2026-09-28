package com.dong.budget.ui.editor

import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
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
    fun `알림에서 읽은 카드가 없으면 결제수단을 골라야 한다`() {
        assertFalse(EditorUiState().isPendingPaymentSelected)
        assertTrue(RequiredField.PAYMENT in EditorUiState().missingFields)
    }
}
