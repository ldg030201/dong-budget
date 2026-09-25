package com.dong.budget.data.capture

import com.dong.budget.testing.FakePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCaptureTest {
    private var clock = 1_758_783_600_000L
    private val prefs = FakePreferences()
    private val prompt = FakePrompt()
    private val registered = mutableSetOf<String>()

    private fun capture(store: CaptureStore = CaptureStore(prefs, now = { clock })) = PaymentCapture(
        store = store,
        prompt = prompt,
        isRegistered = { it in registered },
        now = { clock },
    )

    private suspend fun PaymentCapture.post(title: String = "133,500원 결제", text: String = "하나카드 | 비비큐 강동밀레니얼점(일시불)", at: Long = clock) =
        onNotification(listOf(title, null), listOf(text, null), at)

    @Test
    fun `결제 알림이면 묻고, 누르면 같은 결제를 찾는다`() = runBlocking {
        val capture = capture()
        assertTrue(capture.post())
        val asked = prompt.asked.single()
        assertEquals(133_500L, asked.amount)
        assertEquals(asked, capture.find(asked.dedupKey))
    }

    @Test
    fun `같은 결제는 두 번 묻지 않는다`() = runBlocking {
        val capture = capture()
        assertTrue(capture.post())
        assertFalse(capture.post())
        // 앱이 다시 켜져 새로 만들어져도 기억한다
        assertFalse(capture().post())
        assertEquals(1, prompt.asked.size)
    }

    @Test
    fun `이미 등록한 결제는 묻지 않는다`() = runBlocking {
        val payment = TossPaymentParser.parse("133,500원 결제", "하나카드 | 비비큐 강동밀레니얼점(일시불)", clock)!!
        registered += payment.dedupKey
        assertFalse(capture().post())
        assertTrue(prompt.asked.isEmpty())
    }

    @Test
    fun `모르는 알림이나 오래된 결제는 묻지 않는다`() = runBlocking {
        val capture = capture()
        assertFalse(capture.post(title = "133,500원 결제 취소"))
        assertFalse(capture.post(at = clock - CaptureStore.RETENTION_MS - 1))
        assertTrue(prompt.asked.isEmpty())
    }

    @Test
    fun `알림을 보낼 수 없으면 기록하지 않고, 허용한 뒤에 다시 들어오면 묻는다`() = runBlocking {
        val capture = capture()
        prompt.allowed = false
        assertFalse(capture.post())
        prompt.allowed = true
        assertTrue(capture.post())
    }

    @Test
    fun `기록은 보관 기간이 지나면 지워진다`() = runBlocking {
        val store = CaptureStore(prefs, now = { clock })
        val capture = capture(store)
        capture.post()
        val key = prompt.asked.single().dedupKey
        clock += CaptureStore.RETENTION_MS + 1
        assertNull(capture.find(key))
        assertTrue(store.pending().isEmpty())
        // 다른 결제를 기록할 때 지난 기록을 치운다
        capture.post(text = "하나카드 | 스타벅스(일시불)")
        assertEquals(1, prefs.all.size)
    }

    @Test
    fun `읽을 수 없는 기록은 없는 것으로 보고 치운다`() = runBlocking {
        prefs.edit().putString("prompted:toss:1:1:가게", "{깨진 값").apply()
        val capture = capture()
        assertNull(capture.find("toss:1:1:가게"))
        capture.post()
        assertEquals(1, prefs.all.size)
    }

    @Test
    fun `지워진 묻는 알림은 다시 띄우되, 사용자가 지운 것과 등록한 것과 떠 있는 것은 빼고 소리 없이 띄운다`() = runBlocking {
        val capture = capture()
        capture.post(text = "하나카드 | 가게1(일시불)")
        capture.post(text = "하나카드 | 가게2(일시불)")
        capture.post(text = "하나카드 | 가게3(일시불)")
        capture.post(text = "하나카드 | 가게4(일시불)")
        val (first, second, third, fourth) = prompt.asked.map { it.dedupKey }
        capture.onPromptDismissed(first)
        registered += second

        capture.restorePrompts(showing = setOf(third))

        assertEquals(listOf(fourth), prompt.restored.map { it.dedupKey })
    }

    @Test
    fun `등록을 마친 결제는 거래를 지워도 다시 띄우지 않는다`() = runBlocking {
        val capture = capture()
        capture.post()
        val key = prompt.asked.single().dedupKey
        capture.onRegistered(key)
        assertEquals(listOf(key), prompt.dismissed)
        // 등록한 거래를 지웠다(가계부에는 없음). 그래도 답한 결제라 되살리지 않는다.
        capture.restorePrompts(showing = emptySet())
        assertTrue(prompt.restored.isEmpty())
    }

    @Test
    fun `0_1_6 에서 등록만 하고 답함으로 안 남은 결제는 다시 연결될 때 정리한다`() = runBlocking {
        val capture = capture()
        capture.post()
        val key = prompt.asked.single().dedupKey
        // 0.1.6 처럼 알림만 치우고 기록은 그대로 둔 채 등록했다
        registered += key
        capture.restorePrompts(showing = emptySet())
        // 그 뒤 거래를 지워도 되살리지 않는다
        registered -= key
        capture.restorePrompts(showing = emptySet())
        assertTrue(prompt.restored.isEmpty())
    }

    @Test
    fun `결제 시각은 알림에 적힌 시각을 쓰고, 어긋나면 올라온 시각을 쓴다`() {
        val posted = clock
        assertEquals(posted - 5_000, PaymentCapture.paymentTime(posted - 5_000, posted))
        assertEquals(posted, PaymentCapture.paymentTime(0, posted))
        // 하루 넘게 이르거나 미래면 믿지 않는다
        assertEquals(posted, PaymentCapture.paymentTime(posted - 2 * 24 * 60 * 60 * 1000L, posted))
        assertEquals(posted, PaymentCapture.paymentTime(posted + 10 * 60 * 1000L, posted))
    }

    @Test
    fun `토스 알림만 받는다`() {
        assertTrue(PaymentCapture.isSource("viva.republica.toss"))
        assertFalse(PaymentCapture.isSource("com.kakao.talk"))
        assertFalse(PaymentCapture.isSource(null))
    }

    private class FakePrompt : CapturePrompt {
        var allowed = true
        val asked = mutableListOf<CapturedPayment>()
        val restored = mutableListOf<CapturedPayment>()
        val dismissed = mutableListOf<String>()

        override fun canAsk() = allowed

        override fun ask(payment: CapturedPayment, quietly: Boolean) {
            asked += payment
            if (quietly) restored += payment
        }

        override fun dismiss(dedupKey: String) {
            dismissed += dedupKey
        }
    }
}
