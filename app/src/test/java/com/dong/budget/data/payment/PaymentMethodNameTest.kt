package com.dong.budget.data.payment

import com.dong.budget.data.MAX_NAME_LENGTH
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodNameTest {
    @Test
    fun `알림의 카드 이름을 결제수단 이름 길이에 맞춰 다듬는다`() {
        assertEquals("하나카드", PaymentMethodRepository.normalizeName("  하나카드 "))
        // 카드 상품명은 자르지 않는다(1.2.0 까지는 10자로 잘렸다)
        assertEquals("원더카드2.0 Life", PaymentMethodRepository.normalizeName("원더카드2.0 Life"))
        assertEquals("MG새마을금고 체크카드", PaymentMethodRepository.normalizeName("MG새마을금고 체크카드"))
        // 제한보다 길면 자르고, 잘린 끝에 공백이 남지 않는다
        val tooLong = "가".repeat(MAX_NAME_LENGTH - 1) + " 체크카드"
        assertEquals("가".repeat(MAX_NAME_LENGTH - 1), PaymentMethodRepository.normalizeName(tooLong))
        assertNull(PaymentMethodRepository.normalizeName("   "))
    }

    @Test
    fun `다듬은 이름끼리는 같은 이름으로 본다`() {
        val stored = PaymentMethodRepository.normalizeName("MG새마을금고 체크카드")!!
        val incoming = PaymentMethodRepository.normalizeName("MG새마을금고 체크카드")!!
        assertTrue(PaymentMethodRepository.sameName(stored, incoming))
    }

    @Test
    fun `예전에 10자로 잘려 만든 결제수단도 같은 카드로 찾는다`() {
        val cut = method(1, "원더카드2.0 Li")
        assertEquals(cut, PaymentMethodRepository.findSameCard(listOf(cut), "원더카드2.0 Life"))
        // 띄어쓰기·대소문자는 여기서도 무시한다
        assertEquals(cut, PaymentMethodRepository.findSameCard(listOf(cut), "원더카드2.0 LIFE"))
        // 이름이 딱 맞는 결제수단이 있으면 그것이 먼저다
        val full = method(2, "원더카드2.0 Life")
        assertEquals(full, PaymentMethodRepository.findSameCard(listOf(cut, full), "원더카드2.0 Life"))
    }

    @Test
    fun `앞부분만 같은 다른 이름은 같은 카드가 아니다`() {
        // 10자 이하 이름은 잘린 적이 없다
        assertNull(PaymentMethodRepository.findSameCard(listOf(method(1, "하나카드")), "하나카드플러스"))
        // 10자에서 잘린 모양과 다르면 아니다
        assertNull(PaymentMethodRepository.findSameCard(listOf(method(1, "원더카드")), "원더카드2.0 Life"))
    }

    private fun method(id: Long, name: String) = PaymentMethodEntity(id = id, uuid = "u$id", name = name, type = PaymentMethodType.OTHER)
}
