package com.dong.budget.data.capture

import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.testing.FakePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private var auto = AutoSettings()

    private fun capture(store: CaptureStore = CaptureStore(prefs, now = { clock })) = PaymentCapture(
        store = store,
        prompt = prompt,
        isRegistered = { it in registered },
        settings = { auto },
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
    fun `같은 결제가 모양이 다른 알림 두 개로 오면 먼저 온 것만 묻는다`() = runBlocking {
        val capture = capture()
        assertTrue(capture.post("46,500원 결제 완료", "원더카드2.0 Life ・ 주식회사 우아한형제들(일시불)"))
        assertFalse(capture.post("46,500원 결제", "하나카드 | 주식회사 우아한형제들(일시불)", at = clock + 1_500))
        // 앱으로 돌아와 알림창을 다시 살펴도 뒤의 알림은 묻지 않는다
        assertFalse(capture().post("46,500원 결제", "하나카드 | 주식회사 우아한형제들(일시불)", at = clock + 1_500))
        assertEquals(listOf("원더카드2.0 Life"), prompt.asked.map { it.paymentName })
        assertEquals(1, capture.records.first().size)
    }

    @Test
    fun `먼저 물은 결제에 답한 뒤에 온 같은 결제의 알림도 묻지 않는다`() = runBlocking {
        val capture = capture()
        capture.post()
        capture.onPromptDismissed(prompt.asked.single().dedupKey)
        assertFalse(capture.post(text = "하나카드 | 비비큐 강동밀레니얼점", at = clock - 2_000))
        assertEquals(1, prompt.asked.size)
    }

    @Test
    fun `금액이나 가게가 다르거나 3초 넘게 떨어진 알림은 다른 결제다`() = runBlocking {
        val capture = capture()
        assertTrue(capture.post())
        assertFalse(capture.post(at = clock + CapturedPayment.SAME_PAYMENT_WINDOW_MS))
        assertTrue(capture.post(at = clock + CapturedPayment.SAME_PAYMENT_WINDOW_MS + 1))
        assertTrue(capture.post(title = "133,000원 결제", at = clock + 1))
        assertTrue(capture.post(text = "하나카드 | 스타벅스(일시불)", at = clock + 1))
        assertEquals(4, prompt.asked.size)
    }

    @Test
    fun `묻기를 끄면 묻지도 기록하지도 않고, 다시 켜면 그 결제를 묻는다`() = runBlocking {
        val capture = capture()
        auto = auto.with(AutoOption.CAPTURE_PROMPT, false)
        assertFalse(capture.post())
        assertTrue(prompt.asked.isEmpty())
        assertTrue(capture.records.first().isEmpty())
        // 알림창에 남은 알림을 다시 살피면 그때 묻는다
        auto = auto.with(AutoOption.CAPTURE_PROMPT, true)
        assertTrue(capture.post())
        assertEquals(1, prompt.asked.size)
    }

    @Test
    fun `묻기를 끄면 지운 묻는 알림도 되살리지 않는다`() = runBlocking {
        val capture = capture()
        capture.post()
        prompt.asked.clear()
        auto = auto.with(AutoOption.CAPTURE_PROMPT, false)
        capture.restorePrompts(showing = emptySet())
        assertTrue(prompt.restored.isEmpty())
    }

    @Test
    fun `같은 결제 한 번만 묻기를 끄면 알림 두 개를 모두 묻는다`() = runBlocking {
        val capture = capture()
        auto = auto.with(AutoOption.CAPTURE_DEDUPE, false)
        assertTrue(capture.post("46,500원 결제 완료", "원더카드2.0 Life ・ 주식회사 우아한형제들(일시불)"))
        assertTrue(capture.post("46,500원 결제", "하나카드 | 주식회사 우아한형제들(일시불)", at = clock + 1_500))
        assertEquals(2, prompt.asked.size)
    }

    @Test
    fun `다시 살피기를 끄면 앱을 열 때의 부탁을 흘려보낸다`() = runBlocking {
        val capture = capture()
        val received = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { capture.rescanRequests.collect { received.send(Unit) } }

        // 켜진 동안 부탁 하나가 닿을 때까지 보낸다. 닿으면 듣기가 시작된 것이 확실하다.
        suspend fun awaitDelivered() = withTimeout(2_000) {
            while (received.tryReceive().isFailure) {
                capture.requestRescan()
                kotlinx.coroutines.delay(5)
            }
        }
        awaitDelivered()

        // 듣고 있는 채로 끄면 부탁이 닿지 않는다
        auto = auto.with(AutoOption.CAPTURE_RESCAN, false)
        repeat(20) {
            capture.requestRescan()
            kotlinx.coroutines.delay(5)
        }
        assertTrue(received.tryReceive().isFailure)

        // 다시 켜면 또 닿는다
        auto = auto.with(AutoOption.CAPTURE_RESCAN, true)
        awaitDelivered()
        job.cancel()
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
    fun `알림 목록은 최근 결제부터 보여주고, 누른 결제는 읽은 것으로 남는다`() = runBlocking {
        val capture = capture()
        capture.post(text = "하나카드 | 가게1(일시불)", at = clock - 2_000)
        capture.post(text = "하나카드 | 가게2(일시불)", at = clock - 1_000)
        val (older, newer) = prompt.asked.map { it.dedupKey }

        assertEquals(listOf(newer, older), capture.records.first().map { it.payment.dedupKey })
        assertTrue(capture.records.first().none { it.read })

        capture.markRead(older)
        assertEquals(listOf(false, true), capture.records.first().map { it.read })
        // 앱이 다시 켜져도 기억한다
        assertEquals(listOf(false, true), capture().records.first().map { it.read })
    }

    @Test
    fun `알림창에서 밀어 지운 결제는 목록에 새 알림으로 남는다`() = runBlocking {
        val capture = capture()
        capture.post()
        capture.onPromptDismissed(prompt.asked.single().dedupKey)
        assertFalse(capture.records.first().single().read)
    }

    @Test
    fun `등록을 마친 결제는 읽은 것이 된다`() = runBlocking {
        val capture = capture()
        capture.post()
        capture.onRegistered(prompt.asked.single().dedupKey)
        assertTrue(capture.records.first().single().read)
    }

    @Test
    fun `모두 읽음은 알림창에 남은 묻는 알림을 치우고 다시 띄우지 않는다`() = runBlocking {
        val capture = capture()
        capture.post(text = "하나카드 | 가게1(일시불)")
        capture.post(text = "하나카드 | 가게2(일시불)")
        capture.post(text = "하나카드 | 가게3(일시불)")
        val (swiped, opened, untouched) = prompt.asked.map { it.dedupKey }
        capture.onPromptDismissed(swiped)
        capture.markRead(opened)
        val shown = listOf(swiped, opened, untouched)

        capture.markAllRead(shown)

        // 목록에서 지우지 않는다. 보관 기간 동안 남아 나중에 눌러 등록할 수 있다.
        assertEquals(listOf(true, true, true), capture.records.first().map { it.read })
        assertEquals(prompt.asked.last(), capture.find(untouched))
        // 밀어 지운 알림은 이미 알림창에 없다. 나머지 둘만 치운다.
        assertEquals(setOf(opened, untouched), prompt.dismissed.toSet())
        capture.restorePrompts(showing = emptySet())
        assertTrue(prompt.restored.isEmpty())
        // 다시 눌러도 치울 것이 없다
        prompt.dismissed.clear()
        capture.markAllRead(shown)
        assertTrue(prompt.dismissed.isEmpty())
    }

    @Test
    fun `알림 목록을 비우면 목록과 알림창에서 사라지고, 같은 결제를 다시 묻지 않는다`() = runBlocking {
        val capture = capture()
        capture.post(text = "하나카드 | 가게1(일시불)")
        capture.post(text = "하나카드 | 가게2(일시불)")
        val keys = prompt.asked.map { it.dedupKey }

        capture.clearInbox()

        assertTrue(capture.records.first().isEmpty())
        assertEquals(keys.toSet(), prompt.dismissed.toSet())
        // 알림창에 남은 토스 알림을 다시 훑어도(앱을 다시 열 때) 묻지 않는다
        assertFalse(capture().post(text = "하나카드 | 가게1(일시불)"))
        capture.restorePrompts(showing = emptySet())
        assertTrue(prompt.restored.isEmpty())
        // 비운 결제는 눌러도 열리지 않는다
        assertEquals(PaymentCapture.OpenResult.Expired, capture.open(keys.first()))
        // 비운 뒤에 온 새 결제는 그대로 묻고 목록에 보인다
        assertTrue(capture.post(text = "하나카드 | 가게3(일시불)"))
        assertEquals(listOf("가게3"), capture.records.first().map { it.payment.merchant })
    }

    @Test
    fun `모두 읽음은 목록에 보이던 결제만 처리한다`() = runBlocking {
        val capture = capture()
        capture.post(text = "하나카드 | 가게1(일시불)", at = clock - 1_000)
        val shown = prompt.asked.single().dedupKey
        // 모두 읽음을 누르는 사이 새 결제가 들어왔다
        capture.post(text = "하나카드 | 가게2(일시불)")
        val arrived = prompt.asked.last().dedupKey

        capture.markAllRead(listOf(shown))

        assertEquals(listOf(arrived to false, shown to true), capture.records.first().map { it.payment.dedupKey to it.read })
        assertEquals(listOf(shown), prompt.dismissed)
    }

    @Test
    fun `알림을 누르면 읽은 것으로 적고 등록창에 채울 결제를 준다`() = runBlocking {
        val capture = capture()
        capture.post()
        val payment = prompt.asked.single()

        assertEquals(PaymentCapture.OpenResult.Editor(payment), capture.open(payment.dedupKey))

        assertTrue(capture.records.first().single().read)
        // 등록창을 열었다가 그냥 닫을 수 있으니 묻는 알림은 남긴다
        assertTrue(prompt.dismissed.isEmpty())
    }

    @Test
    fun `이미 등록한 결제를 누르면 등록창 대신 묻던 알림을 치운다`() = runBlocking {
        val capture = capture()
        capture.post()
        val key = prompt.asked.single().dedupKey
        registered += key

        assertEquals(PaymentCapture.OpenResult.AlreadyRegistered, capture.open(key))

        assertEquals(listOf(key), prompt.dismissed)
        assertTrue(capture.records.first().single().read)
    }

    @Test
    fun `보관 기간이 지난 결제를 누르면 열 수 없고 켜 둔 목록에서도 빠진다`() = runBlocking {
        val capture = capture()
        val sizes = Channel<Int>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) { capture.records.collect { sizes.send(it.size) } }
        assertEquals(0, sizes.receive())
        capture.post()
        assertEquals(1, sizes.receive())
        val key = prompt.asked.single().dedupKey

        clock += CaptureStore.RETENTION_MS + 1
        assertEquals(PaymentCapture.OpenResult.Expired, capture.open(key))

        assertEquals(0, withTimeout(5_000) { sizes.receive() })
        job.cancel()
    }

    @Test
    fun `보관 기간이 끝나면 아무것도 누르지 않아도 알림 목록에서 빠진다`() = runBlocking {
        val capture = capture()
        // 50ms 뒤에 보관 기간이 끝나는 결제
        capture.post(at = clock - CaptureStore.RETENTION_MS + 50)
        val sizes = Channel<Int>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Unconfined) { capture.records.collect { sizes.send(it.size) } }
        assertEquals(1, sizes.receive())

        // 기록은 그대로인 채 시간만 흐른다. 목록은 보관 기간이 끝나는 때에 맞춰 다시 읽는다.
        clock += 100
        assertEquals(0, withTimeout(5_000) { sizes.receive() })
        job.cancel()
    }

    @Test
    fun `읽음 표시가 없던 옛 기록은 새 알림으로 읽힌다`() = runBlocking {
        val payment = TossPaymentParser.parse("133,500원 결제", "하나카드 | 비비큐 강동밀레니얼점(일시불)", clock)!!
        // 1.0.0 이 남긴 기록 모양
        prefs.edit().putString("prompted:${payment.dedupKey}", Json.encodeToString(OldEntry(payment, dismissed = true))).apply()
        val record = capture().records.first().single()
        assertEquals(payment, record.payment)
        assertFalse(record.read)
    }

    @Test
    fun `기록이 바뀌면 알림 목록을 다시 읽는다`() = runBlocking {
        val capture = capture()
        val seen = mutableListOf<Int>()
        val job = launch(Dispatchers.Unconfined) { capture.records.collect { seen += it.size } }
        capture.post()
        capture.markRead(prompt.asked.single().dedupKey)
        job.cancel()
        assertEquals(listOf(0, 1, 1), seen)
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

    /** 1.0.0 의 기록. 읽음 표시(read)가 없다. */
    @Serializable
    private data class OldEntry(val payment: CapturedPayment, val dismissed: Boolean)

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
