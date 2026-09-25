package com.dong.budget.data.capture

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 등록할지 물어본 결제를 기억한다.
 *
 * - 같은 결제로 두 번 묻지 않는다. 알림 읽기가 다시 연결될 때(앱 업데이트 등) 알림창에 남은 토스 알림을
 *   다시 훑는데, 이미 물어본 것은 건너뛴다.
 * - 앱을 업데이트하거나 기기를 다시 켜면 시스템이 우리 알림을 지운다. 그때 아직 답하지 않은 결제([pending])는
 *   다시 띄운다. 답한 결제([markAnswered] — 사용자가 알림을 지웠거나 등록을 마침)는 다시 띄우지 않는다.
 * - 우리 알림을 누르면 여기서 결제 내용을 찾아 등록창을 채운다. 알림(Intent)에는 열쇠만 담는다.
 *   동계부의 첫 화면은 다른 앱도 열 수 있으므로, Intent 에 담긴 금액이나 가게를 그대로 믿지 않기 위함이다.
 * - 결제 시각에서 [RETENTION_MS] 가 지난 기록은 지운다. 우리 알림도 그때 저절로 사라진다(CaptureNotifier).
 *
 * 저장소는 SharedPreferences 다. 자동 백업에 들어가지 않으므로(data_extraction_rules) 다른 기기로 따라가지 않는다.
 */
class CaptureStore(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 처음 보는 결제면 기록하고 true, 이미 물어본 결제면 false */
    @Synchronized
    fun remember(payment: CapturedPayment): Boolean {
        prune()
        val key = KEY_PREFIX + payment.dedupKey
        if (prefs.contains(key)) return false
        prefs.edit { putString(key, json.encodeToString(Entry(payment))) }
        return true
    }

    /** 물어본 결제를 찾는다. 기록이 없거나 지났으면 null */
    @Synchronized
    fun find(dedupKey: String): CapturedPayment? = entry(dedupKey)?.payment

    /** 답한 결제로 적는다(사용자가 알림을 지웠거나 등록을 마침). 다시 띄우지 않는다. */
    @Synchronized
    fun markAnswered(dedupKey: String) {
        val entry = entry(dedupKey) ?: return
        if (entry.answered) return
        prefs.edit { putString(KEY_PREFIX + dedupKey, json.encodeToString(entry.copy(answered = true))) }
    }

    /** 물어봤지만 아직 답하지 않은 결제. 결제 시각 순. */
    @Synchronized
    fun pending(): List<CapturedPayment> {
        prune()
        return prefs.all
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .values
            .mapNotNull { (it as? String)?.let(::decode) }
            .filterNot { it.answered }
            .map { it.payment }
            .sortedBy { it.occurredAtMillis }
    }

    private fun entry(dedupKey: String): Entry? = prefs.getString(KEY_PREFIX + dedupKey, null)?.let(::decode)?.takeUnless(::isExpired)

    private fun prune() {
        val expired =
            prefs.all
                .filter { (key, value) ->
                    // 읽을 수 없는 기록(형식이 바뀐 옛 기록 등)도 지운다
                    key.startsWith(KEY_PREFIX) && ((value as? String)?.let(::decode)?.let(::isExpired) ?: true)
                }.keys
        if (expired.isNotEmpty()) prefs.edit { expired.forEach(::remove) }
    }

    private fun isExpired(entry: Entry): Boolean = now() - entry.payment.occurredAtMillis > RETENTION_MS

    private fun decode(value: String): Entry? = runCatching { json.decodeFromString<Entry>(value) }.getOrNull()

    /** @property answered 답했는지. 0.1.6 에서 'dismissed' 라는 이름으로 저장했으므로 저장 이름은 그대로 둔다. */
    @Serializable
    private data class Entry(val payment: CapturedPayment, @SerialName("dismissed") val answered: Boolean = false)

    companion object {
        /** SharedPreferences 파일 이름 */
        const val PREFS_NAME = "payment_capture"

        /** 결제 시각부터 기록을 남겨두는 기간. 이보다 오래된 결제는 묻지 않는다. 7일 */
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L

        private const val KEY_PREFIX = "prompted:"
    }
}
