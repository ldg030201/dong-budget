package com.dong.budget.data.capture

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 등록할지 물어본 결제를 기억한다.
 *
 * - 같은 결제로 두 번 묻지 않는다. 알림 읽기가 다시 연결될 때(앱 업데이트 등) 알림창에 남은 결제 알림을
 *   다시 훑는데, 이미 물어본 것은 건너뛴다.
 * - 앱을 업데이트하거나 기기를 다시 켜면 시스템이 우리 알림을 지운다. 그때 아직 답하지 않은 결제([pending])는
 *   다시 띄운다. 답한 결제(사용자가 알림을 지움 [markAnswered], 등록을 마침 [markRegistered])는 다시 띄우지 않는다.
 * - 우리 알림을 누르면 여기서 결제 내용을 찾아 등록창을 채운다. 알림(Intent)에는 열쇠만 담는다.
 *   동계부의 첫 화면은 다른 앱도 열 수 있으므로, Intent 에 담긴 금액이나 가게를 그대로 믿지 않기 위함이다.
 * - 결제 시각에서 [RETENTION_MS] 가 지난 기록은 지운다. 우리 알림도 그때 저절로 사라진다(CaptureNotifier).
 * - 알림 화면(홈 오른쪽 위 종)도 이 기록을 보여준다. 눌러 본 결제는 [markRead] 로 적어 새 알림 표시를 없앤다.
 *   '답함' 과 '읽음' 은 따로 센다. 알림창에서 밀어 지운 결제는 답한 것이지만, 못 보고 지웠을 수 있어 새 알림으로 남긴다.
 * - 데이터 초기화는 알림 목록을 비우되 기록은 숨겨 둔다([hideAll]). 비운 결제를 다시 묻지 않기 위함이다.
 *
 * 저장소는 SharedPreferences 다. 자동 백업에 들어가지 않으므로(data_extraction_rules) 다른 기기로 따라가지 않는다.
 */
class CaptureStore(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }

    private val revision = MutableStateFlow(0)

    /**
     * 기록을 바꾸거나 지난 기록을 치울 때마다 오르는 번호. 알림 목록이 이것을 보고 [records] 를 다시 읽는다.
     * 켜 둔 알림 목록에 남아 있던 지난 기록도 치울 때 함께 빠진다.
     */
    val changes: StateFlow<Int> = revision.asStateFlow()

    /** 이미 물어본 결제인지. 기록을 풀지 않고 열쇠만 본다. */
    fun knows(dedupKey: String): Boolean = prefs.contains(KEY_PREFIX + dedupKey)

    /** 처음 보는 결제면 기록하고 true, 이미 물어본 결제면 false */
    @Synchronized
    fun remember(payment: CapturedPayment): Boolean {
        // 이미 물어본 결제는 여기서 바로 끝낸다. 정리(모든 기록을 읽어 푸는 일)는 새 기록을 넣을 때만 한다.
        if (knows(payment.dedupKey)) return false
        liveEntries()
        write(payment.dedupKey, Entry(payment))
        return true
    }

    /** 다른 알림으로 이미 물어본 같은 결제([CapturedPayment.isSamePaymentAs]). 없으면 null */
    @Synchronized
    fun findSamePayment(payment: CapturedPayment): CapturedPayment? =
        liveEntries().firstOrNull { it.payment.isSamePaymentAs(payment) }?.payment

    /** 물어본 결제를 찾는다. 기록이 없거나 지났으면 null */
    @Synchronized
    fun find(dedupKey: String): CapturedPayment? = entry(dedupKey)?.takeUnless { it.hidden }?.payment

    /** 답한 결제로 적는다(사용자가 알림을 지움). 다시 띄우지 않는다. */
    @Synchronized
    fun markAnswered(dedupKey: String) = change(dedupKey) { it.copy(answered = true) }

    /** 눌러 본 결제로 적는다(알림 목록이나 알림창에서 눌렀음). 새 알림 표시가 사라진다. */
    @Synchronized
    fun markRead(dedupKey: String) = change(dedupKey) { it.copy(read = true) }

    /** 등록을 마친 결제. 답했고, 등록하며 봤으니 읽은 것이기도 하다. */
    @Synchronized
    fun markRegistered(dedupKey: String) = change(dedupKey) { it.copy(answered = true, read = true) }

    /**
     * 모두 읽음. 등록하지 않겠다는 뜻이라 모두 답한 것으로도 적는다(다시 띄우지 않는다).
     * @param dedupKeys 알림 목록에 보이던 결제. 누르는 사이 새로 들어와 아직 못 본 결제는 건드리지 않는다.
     * @return 이번에 답한 것으로 바뀐 결제의 열쇠. 그 묻는 알림은 아직 알림창에 떠 있을 수 있어 부르는 쪽이 치운다.
     */
    @Synchronized
    fun markAllRead(dedupKeys: Collection<String>): List<String> {
        val keys = dedupKeys.toSet()
        val changed = liveEntries().filter { it.payment.dedupKey in keys && !(it.answered && it.read) }
        if (changed.isEmpty()) return emptyList()
        writeAll(changed.map { it.copy(answered = true, read = true) })
        return changed.filterNot { it.answered }.map { it.payment.dedupKey }
    }

    /**
     * 알림 목록을 비운다(데이터 초기화). 기록은 지우지 않고 숨긴다. 지워 버리면 처음 보는 결제가 되어,
     * 알림창에 남은 결제 알림을 다시 훑을 때(앱을 열 때) 비운 결제를 모두 다시 묻는다. 숨긴 기록도 보관 기간이 지나면 지워진다.
     * @return 숨긴 결제의 열쇠. 그 묻는 알림이 알림창에 떠 있을 수 있어 부르는 쪽이 치운다.
     */
    @Synchronized
    fun hideAll(): List<String> = hideEntries(liveEntries().filterNot { it.hidden }).map { it.payment.dedupKey }

    /**
     * 알림 목록에서 [dedupKeys] 를 지운다(읽은 알림 지우기). [hideAll] 처럼 기록은 숨기기만 해서 다시 묻지 않는다.
     * @return 이번에 답한 것으로 바뀐 결제의 열쇠. 그 묻는 알림이 알림창에 떠 있을 수 있어 부르는 쪽이 치운다.
     */
    @Synchronized
    fun hide(dedupKeys: Collection<String>): List<String> {
        val keys = dedupKeys.toSet()
        return hideEntries(liveEntries().filter { it.payment.dedupKey in keys && !it.hidden })
            .filterNot { it.answered }
            .map { it.payment.dedupKey }
    }

    /**
     * 기록을 숨긴다. 답했고 읽은 것으로도 적어 다시 묻거나 새 알림으로 보이지 않게 한다. [hideAll]·[hide] 가 같이 쓴다.
     * @return 숨긴 기록(숨기기 전 모습). 부르는 쪽이 알림창에서 치울 것을 고른다.
     */
    private fun hideEntries(entries: List<Entry>): List<Entry> {
        if (entries.isEmpty()) return emptyList()
        writeAll(entries.map { it.copy(answered = true, read = true, hidden = true) })
        return entries
    }

    /** 알림 목록에 보여줄 기록. 최근 결제부터. */
    @Synchronized
    fun records(): List<CaptureRecord> = liveEntries().filterNot {
        it.hidden
    }.sortedByDescending { it.payment.occurredAtMillis }.map { CaptureRecord(it.payment, read = it.read) }

    /** 물어봤지만 아직 답하지 않은 결제. 결제 시각 순. */
    @Synchronized
    fun pending(): List<CapturedPayment> = liveEntries().filterNot { it.answered }.map { it.payment }.sortedBy { it.occurredAtMillis }

    private fun write(dedupKey: String, entry: Entry) {
        prefs.edit { putString(KEY_PREFIX + dedupKey, json.encodeToString(entry)) }
        revision.update { it + 1 }
    }

    /** 여러 기록을 한 번에 쓴다. 번호는 한 번만 올린다. 열쇠는 기록의 결제에서 꺼낸다. */
    private fun writeAll(entries: List<Entry>) {
        prefs.edit { entries.forEach { putString(KEY_PREFIX + it.payment.dedupKey, json.encodeToString(it)) } }
        revision.update { it + 1 }
    }

    /** 기록 하나를 [transform] 으로 바꿔 쓴다. 기록이 없거나 지났거나, 이미 그 모습이면 쓰지 않는다(번호도 그대로). */
    private inline fun change(dedupKey: String, transform: (Entry) -> Entry) {
        val entry = entry(dedupKey) ?: return
        val changed = transform(entry)
        if (changed != entry) write(dedupKey, changed)
    }

    /** 기록 하나를 찾는다. 지났으면 그 자리에서 치운다. 켜 둔 알림 목록에서도 빠지게 하기 위함이다. */
    private fun entry(dedupKey: String): Entry? {
        val entry = prefs.getString(KEY_PREFIX + dedupKey, null)?.let(::decode) ?: return null
        if (!isExpired(entry)) return entry
        prefs.edit { remove(KEY_PREFIX + dedupKey) }
        revision.update { it + 1 }
        return null
    }

    /** 기록을 모두 한 번에 읽는다. 지난 기록과 읽을 수 없는 기록(형식이 바뀐 옛 기록 등)은 이때 지운다. */
    private fun liveEntries(): List<Entry> {
        val live = mutableListOf<Entry>()
        val dead = mutableListOf<String>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(KEY_PREFIX)) return@forEach
            val entry = (value as? String)?.let(::decode)
            if (entry == null || isExpired(entry)) dead += key else live += entry
        }
        if (dead.isNotEmpty()) {
            prefs.edit { dead.forEach(::remove) }
            revision.update { it + 1 }
        }
        return live
    }

    private fun isExpired(entry: Entry): Boolean = now() - entry.payment.occurredAtMillis > RETENTION_MS

    /** 기록을 꺼내는 곳은 여기 하나다. 옛 버전이 잘못 읽어 남긴 결제는 여기서 고쳐 읽는다([withoutTossBalance]). */
    private fun decode(value: String): Entry? =
        runCatching { json.decodeFromString<Entry>(value) }.getOrNull()?.let { it.copy(payment = it.payment.withoutTossBalance()) }

    /**
     * 1.10.0 에서 받은 기록용. 1.10.0 은 토스가 가게 끝에 붙여 보낸 잔액을 떼지 못해, 가게가 '(주)아성다이소 잔액 848,010원(토스뱅크 체크카드)'
     * 처럼 남고 카드는 비었다. 그대로 두면 알림 목록·되살린 묻는 알림·누르면 채우는 등록창에 잔액이 붙어 보인다.
     * 읽을 때 파서와 같은 규칙으로 잔액을 떼고([TossPaymentParser.cutTrailingBalance]), 카드가 비었으면 잔액 괄호의 카드 이름으로 채운다.
     * - 열쇠는 그대로 둔다. 기록·거래·알림이 이 열쇠로 같은 결제를 찾는다.
     * - 고치려고 기록을 다시 쓰지는 않는다. 보관 기간([RETENTION_DAYS])이 지나면 저절로 사라진다(읽음 표시 등으로 쓸 때는 고친 모양으로 남는다).
     * - 토스 결제 기록만 본다. 입금·카카오페이 기록은 다른 파서가 읽은 것이라 건드리지 않는다.
     * - 할부 표시는 보지 않는다. 잔액이 붙어 오는 건 할부가 없는 체크카드 알림이다.
     */
    private fun CapturedPayment.withoutTossBalance(): CapturedPayment {
        if (kind != CaptureKind.PAYMENT || !dedupKey.startsWith(TossPaymentParser.DEDUP_KEY_PREFIX)) return this
        val cut = TossPaymentParser.cutTrailingBalance(merchant) ?: return this
        return copy(merchant = cut.merchant, paymentName = paymentName ?: cut.card)
    }

    /**
     * @property answered 답했는지. 0.1.6 에서 'dismissed' 라는 이름으로 저장했으므로 저장 이름은 그대로 둔다.
     * @property read 눌러 봤는지. 1.1.0 에서 생겼다. 그전 기록은 읽지 않은 것으로 읽힌다.
     * @property hidden 알림 목록에서 숨겼는지(데이터 초기화 [hideAll], 읽은 알림 지우기 [hide]). 목록에도 안 보이고 눌러도 열리지 않지만, 다시 묻지는 않는다.
     */
    @Serializable
    private data class Entry(
        val payment: CapturedPayment,
        @SerialName("dismissed") val answered: Boolean = false,
        val read: Boolean = false,
        val hidden: Boolean = false,
    )

    companion object {
        /** SharedPreferences 파일 이름 */
        const val PREFS_NAME = "payment_capture"

        /** 결제 시각부터 기록을 남겨두는 날 수. 이보다 오래된 결제는 묻지 않고, 알림 목록에서도 빠진다. */
        const val RETENTION_DAYS = 7

        /** [RETENTION_DAYS] 를 밀리초로 */
        const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000L

        private const val KEY_PREFIX = "prompted:"
    }
}

/**
 * 알림 목록의 한 건. 우리가 등록할지 물어본 결제다.
 * @property read 눌러 봤는지. 등록을 마친 결제도 읽은 것이다.
 */
data class CaptureRecord(val payment: CapturedPayment, val read: Boolean)
