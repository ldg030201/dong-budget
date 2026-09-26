package com.dong.budget.data.capture

import com.dong.budget.BuildConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 결제를 등록할지 사용자에게 묻는 창구. 실제로는 우리 앱의 알림이다(CaptureNotifier). */
interface CapturePrompt {
    /** 물어볼 수 있는 상태인지(알림이 허용됐는지) */
    fun canAsk(): Boolean

    /** @param quietly 소리 없이 띄운다. 이미 한 번 알린 결제를 다시 띄울 때 쓴다. */
    fun ask(payment: CapturedPayment, quietly: Boolean = false)

    fun dismiss(dedupKey: String)
}

/**
 * 다른 앱의 알림을 받아 결제면 등록할지 묻는다.
 *
 * 알림 읽기(PaymentNotificationListener)가 모든 알림을 여기로 넘긴다.
 * 토스가 아닌 앱의 알림은 [isSource] 에서 바로 버리고, 어디에도 남기지 않는다.
 */
class PaymentCapture(
    private val store: CaptureStore,
    private val prompt: CapturePrompt,
    /** 이미 가계부에 등록한 결제인지. 보통 TransactionRepository.isRegistered */
    private val isRegistered: suspend (dedupKey: String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * 새 결제 처리와 되살리기([restorePrompts])가 겹치지 않게 한다.
     * 겹치면 되살리기가 방금 기록된 새 결제를 '소리 없이' 먼저 띄우고, 뒤따른 소리 알림은 같은 알림의 갱신이라
     * 울리지 않는다(setOnlyAlertOnce). 새 결제인데 조용히 지나가게 된다.
     */
    private val mutex = Mutex()

    /**
     * 토스 알림 하나를 살펴 결제면 등록할지 묻는다.
     *
     * 제목과 본문은 알림에 따라 담기는 칸이 달라서 후보를 여러 개 받는다. 짧은 본문부터 맞춰 본다.
     * 펼친 본문에는 줄이 더 붙을 수 있어 딱 맞는 모양을 찾기 어렵기 때문이다.
     *
     * @return 물어봤으면 true
     */
    suspend fun onNotification(titles: List<CharSequence?>, texts: List<CharSequence?>, occurredAtMillis: Long): Boolean =
        mutex.withLock { handleNotification(titles, texts, occurredAtMillis) }

    private suspend fun handleNotification(titles: List<CharSequence?>, texts: List<CharSequence?>, occurredAtMillis: Long): Boolean {
        // 너무 오래된 결제는 묻지 않는다. 기록을 지운 뒤 같은 알림이 다시 들어와도 또 묻지 않게 하기 위함이다.
        if (now() - occurredAtMillis > CaptureStore.RETENTION_MS) return false
        val payment =
            titles.firstNotNullOfOrNull { title ->
                texts.firstNotNullOfOrNull { text -> TossPaymentParser.parse(title, text, occurredAtMillis) }
            } ?: return false
        // 이미 물어본 결제는 더 볼 것이 없다. 앱으로 돌아올 때마다 알림창에 남은 토스 알림을 다시 살피므로,
        // 알림 권한·채널을 묻는 일(시스템 호출)보다 먼저 본다.
        if (store.knows(payment.dedupKey)) return false
        // 알림을 보낼 수 없으면 기록하지 않는다. 나중에 알림을 허용한 뒤 다시 연결될 때 물을 수 있게 둔다.
        if (!prompt.canAsk()) return false
        if (!store.remember(payment)) return false
        if (isRegistered(payment.dedupKey)) {
            store.markRegistered(payment.dedupKey)
            return false
        }
        prompt.ask(payment)
        return true
    }

    /**
     * 지워졌거나 띄우지 못한 묻는 알림을 다시 띄운다. 알림 읽기가 연결될 때(앱 업데이트·재시작 뒤)와
     * 앱으로 돌아올 때(다시 살피기) 부른다. 알림이나 채널을 꺼 둔 동안 시스템이 지운 알림도 이때 되살아난다.
     * 답한 것(사용자가 지웠거나 등록한 것), 이미 등록돼 있는 것, 지금 떠 있는 것([showing])은 빼고 소리 없이 띄운다.
     */
    suspend fun restorePrompts(showing: Set<String>) = mutex.withLock {
        if (!prompt.canAsk()) return@withLock
        store.pending().forEach { payment ->
            when {
                // 이미 등록한 결제는 답한 것으로 적는다. 0.1.6 은 등록해도 기록에 남기지 않아서 여기서 정리한다.
                // 적어 두지 않으면 그 거래를 나중에 지웠을 때 묻는 알림이 되살아난다.
                isRegistered(payment.dedupKey) -> store.markRegistered(payment.dedupKey)

                payment.dedupKey !in showing -> prompt.ask(payment, quietly = true)
            }
        }
    }

    /**
     * 사용자가 묻는 알림을 지웠다. 등록하지 않겠다는 뜻이니 다시 띄우지 않는다.
     * 읽은 것으로는 적지 않는다. '모두 지우기' 로 못 보고 지웠을 수 있어 알림 목록에는 새 알림으로 남긴다.
     */
    fun onPromptDismissed(dedupKey: String) = store.markAnswered(dedupKey)

    /** 우리 알림을 눌렀을 때 채울 결제. 기록이 지났으면 null */
    fun find(dedupKey: String): CapturedPayment? = store.find(dedupKey)

    /**
     * 결제 등록 알림을 눌렀다. 알림창의 알림과 알림 화면이 같이 쓴다.
     * 누른 결제는 읽은 것으로 적는다. 등록창을 열었다가 그냥 닫아도 새 알림 표시가 남지 않게 하기 위함이다.
     * 이미 등록한 결제면 묻던 알림을 치운다.
     */
    suspend fun open(dedupKey: String): OpenResult {
        val payment = store.find(dedupKey) ?: return OpenResult.Expired
        store.markRead(dedupKey)
        if (isRegistered(dedupKey)) {
            onRegistered(dedupKey)
            return OpenResult.AlreadyRegistered
        }
        return OpenResult.Editor(payment)
    }

    /** 결제 등록 알림을 눌렀을 때 할 일([open]) */
    sealed interface OpenResult {
        /** 보관 기간이 지나 채울 내용이 없다 */
        data object Expired : OpenResult

        /** 이미 등록한 결제 */
        data object AlreadyRegistered : OpenResult

        /** 이 결제로 채운 등록창을 연다 */
        data class Editor(val payment: CapturedPayment) : OpenResult
    }

    /**
     * 등록을 마쳤다(또는 이미 등록돼 있었다). 묻던 알림을 치우고 답한 것으로 적어 둔다.
     * 적어 두지 않으면 등록한 거래를 나중에 지웠을 때, 다시 연결되는 순간 묻는 알림이 되살아난다.
     */
    fun onRegistered(dedupKey: String) {
        store.markRegistered(dedupKey)
        prompt.dismiss(dedupKey)
    }

    /**
     * 알림 화면의 목록. 물어본 결제들을 최근 것부터 담는다.
     * 기록이 바뀔 때마다 다시 읽고, 가장 먼저 보관 기간이 끝나는 때에도 다시 읽어 목록에서 뺀다.
     * 그 결제의 묻는 알림이 알림창에서 저절로 사라지는 때(CaptureNotifier 의 setTimeoutAfter)와 같다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val records: Flow<List<CaptureRecord>> =
        store.changes.transformLatest {
            while (true) {
                val records = store.records()
                emit(records)
                val nextExpiry = records.minOfOrNull { it.payment.occurredAtMillis + CaptureStore.RETENTION_MS } ?: break
                // 보관 기간을 '넘어야' 지난 것으로 보므로 1ms 뒤에 읽는다
                delay((nextExpiry - now() + 1).coerceAtLeast(1))
            }
        }

    /** 알림 목록이나 알림창에서 이 결제를 눌렀다. 새 알림 표시를 없앤다. */
    fun markRead(dedupKey: String) = store.markRead(dedupKey)

    /**
     * 알림 목록의 '모두 읽음'. 남은 결제는 등록하지 않겠다는 뜻이라 알림창의 묻는 알림도 치우고 다시 띄우지 않는다.
     * 목록에는 보관 기간 동안 남아 있어서, 마음이 바뀌면 거기서 눌러 등록할 수 있다.
     * 되살리기([restorePrompts])와 겹치면 방금 치운 알림을 도로 띄울 수 있어 같은 자물쇠 안에서 한다.
     *
     * @param dedupKeys 목록에 보이던 결제. 누르는 사이 새로 온 결제는 새 알림으로 남기고 묻는 알림도 그대로 둔다.
     */
    suspend fun markAllRead(dedupKeys: Collection<String>) = mutex.withLock {
        store.markAllRead(dedupKeys).forEach(prompt::dismiss)
    }

    /**
     * 알림창에 남아 있는 토스 알림을 다시 살피고, 띄우지 못한 묻는 알림을 되살려 달라고 알림 읽기에 부탁한다.
     * 알림을 보낼 수 없던 사이 들어온 결제는 기록하지 않고 넘겼으므로, 알림을 허용한 뒤 여기서 다시 묻는다.
     * 알림 읽기가 연결돼 있지 않으면 아무 일도 없다(연결될 때 어차피 훑는다).
     */
    fun requestRescan() {
        rescans.tryEmit(Unit)
    }

    private val rescans = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 알림 읽기(PaymentNotificationListener)가 연결돼 있는 동안 듣는다 */
    val rescanRequests: SharedFlow<Unit> = rescans.asSharedFlow()

    companion object {
        /** 토스 앱 */
        const val TOSS_PACKAGE = "viva.republica.toss"

        /** 개발 빌드에서는 adb 로 올린 가짜 알림(`adb shell cmd notification post`)도 받는다 */
        private const val SHELL_PACKAGE = "com.android.shell"

        private const val CLOCK_SKEW_MS = 60 * 1000L
        private const val MAX_DELAY_MS = 24 * 60 * 60 * 1000L

        fun isSource(packageName: String?): Boolean = packageName == TOSS_PACKAGE || (BuildConfig.DEBUG && packageName == SHELL_PACKAGE)

        /**
         * 결제 시각. 토스가 알림에 적은 시각(when)을 쓴다.
         * 알림이 늦게 도착해도 결제한 시각이 들어가고, 같은 알림이 다시 올라와도 시각이 바뀌지 않는다.
         * 그 값이 없거나 알림이 올라온 시각과 너무 어긋나면(하루 넘게 이르거나 미래) 올라온 시각을 쓴다.
         */
        fun paymentTime(whenMillis: Long, postTimeMillis: Long): Long =
            whenMillis.takeIf { it > 0 && it in (postTimeMillis - MAX_DELAY_MS)..(postTimeMillis + CLOCK_SKEW_MS) }
                ?: postTimeMillis
    }
}
