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
 * - 같은 결제로 두 번 묻지 않는다. 알림 읽기가 다시 연결될 때(앱 업데이트 등) 알림창에 남은 토스 알림을
 *   다시 훑는데, 이미 물어본 것은 건너뛴다.
 * - 앱을 업데이트하거나 기기를 다시 켜면 시스템이 우리 알림을 지운다. 그때 아직 답하지 않은 결제([pending])는
 *   다시 띄운다. 답한 결제(사용자가 알림을 지움 [markAnswered], 등록을 마침 [markRegistered])는 다시 띄우지 않는다.
 * - 우리 알림을 누르면 여기서 결제 내용을 찾아 등록창을 채운다. 알림(Intent)에는 열쇠만 담는다.
 *   동계부의 첫 화면은 다른 앱도 열 수 있으므로, Intent 에 담긴 금액이나 가게를 그대로 믿지 않기 위함이다.
 * - 결제 시각에서 [RETENTION_MS] 가 지난 기록은 지운다. 우리 알림도 그때 저절로 사라진다(CaptureNotifier).
 * - 홈의 알림 목록도 이 기록을 보여준다. 눌러 본 결제는 [markRead] 로 적어 새 알림 표시를 없앤다.
 *   '답함' 과 '읽음' 은 따로 센다. 알림창에서 밀어 지운 결제는 답한 것이지만, 못 보고 지웠을 수 있어 새 알림으로 남긴다.
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

    /** 물어본 결제를 찾는다. 기록이 없거나 지났으면 null */
    @Synchronized
    fun find(dedupKey: String): CapturedPayment? = entry(dedupKey)?.payment

    /** 답한 결제로 적는다(사용자가 알림을 지움). 다시 띄우지 않는다. */
    @Synchronized
    fun markAnswered(dedupKey: String) {
        val entry = entry(dedupKey) ?: return
        if (entry.answered) return
        write(dedupKey, entry.copy(answered = true))
    }

    /** 눌러 본 결제로 적는다(알림 목록이나 알림창에서 눌렀음). 새 알림 표시가 사라진다. */
    @Synchronized
    fun markRead(dedupKey: String) {
        val entry = entry(dedupKey) ?: return
        if (entry.read) return
        write(dedupKey, entry.copy(read = true))
    }

    /** 등록을 마친 결제. 답했고, 등록하며 봤으니 읽은 것이기도 하다. */
    @Synchronized
    fun markRegistered(dedupKey: String) {
        val entry = entry(dedupKey) ?: return
        if (entry.answered && entry.read) return
        write(dedupKey, entry.copy(answered = true, read = true))
    }

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
        prefs.edit {
            changed.forEach { putString(KEY_PREFIX + it.payment.dedupKey, json.encodeToString(it.copy(answered = true, read = true))) }
        }
        revision.update { it + 1 }
        return changed.filterNot { it.answered }.map { it.payment.dedupKey }
    }

    /** 알림 목록에 보여줄 기록. 최근 결제부터. */
    @Synchronized
    fun records(): List<CaptureRecord> =
        liveEntries().sortedByDescending { it.payment.occurredAtMillis }.map { CaptureRecord(it.payment, read = it.read) }

    /** 물어봤지만 아직 답하지 않은 결제. 결제 시각 순. */
    @Synchronized
    fun pending(): List<CapturedPayment> = liveEntries().filterNot { it.answered }.map { it.payment }.sortedBy { it.occurredAtMillis }

    private fun write(dedupKey: String, entry: Entry) {
        prefs.edit { putString(KEY_PREFIX + dedupKey, json.encodeToString(entry)) }
        revision.update { it + 1 }
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

    private fun decode(value: String): Entry? = runCatching { json.decodeFromString<Entry>(value) }.getOrNull()

    /**
     * @property answered 답했는지. 0.1.6 에서 'dismissed' 라는 이름으로 저장했으므로 저장 이름은 그대로 둔다.
     * @property read 눌러 봤는지. 1.1.0 에서 생겼다. 그전 기록은 읽지 않은 것으로 읽힌다.
     */
    @Serializable
    private data class Entry(
        val payment: CapturedPayment,
        @SerialName("dismissed") val answered: Boolean = false,
        val read: Boolean = false,
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
