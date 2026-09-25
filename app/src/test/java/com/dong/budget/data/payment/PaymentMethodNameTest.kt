package com.dong.budget.data.payment

import com.dong.budget.data.MAX_NAME_LENGTH
import com.dong.budget.data.PaymentMethodRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodNameTest {
    @Test
    fun `알림의 카드 이름을 결제수단 이름 길이에 맞춰 다듬는다`() {
        assertEquals("하나카드", PaymentMethodRepository.normalizeName("  하나카드 "))
        // 잘린 끝에 공백이 남지 않는다
        val cut = PaymentMethodRepository.normalizeName("MG새마을금고 체크카드")!!
        assertTrue(cut.length <= MAX_NAME_LENGTH)
        assertEquals(cut.trim(), cut)
        assertNull(PaymentMethodRepository.normalizeName("   "))
    }

    @Test
    fun `다듬은 이름끼리는 같은 이름으로 본다`() {
        val stored = PaymentMethodRepository.normalizeName("MG새마을금고 체크카드")!!
        val incoming = PaymentMethodRepository.normalizeName("MG새마을금고 체크카드")!!
        assertTrue(PaymentMethodRepository.sameName(stored, incoming))
    }
}
