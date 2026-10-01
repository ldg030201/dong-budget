package com.dong.budget.data.lock

import android.content.SharedPreferences
import androidx.core.content.edit
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PIN 4자리와, 원하면 지문으로 여는 잠금. 월급 탭 잠금과 앱 잠금이 저장 파일을 따로 두고 하나씩 쓴다(AppContainer).
 * 월급은 남에게 보이면 곤란할 수 있어(계약서에 비밀 유지가 있기도 하다) 앱 잠금과 따로 월급 탭만 잠글 수 있다.
 *
 * - PIN 은 그대로 두지 않고 기기마다 다른 소금(salt)을 섞은 SHA-256 으로만 둔다. 4자리라 마음먹고 풀면 풀리지만,
 *   저장 파일을 열어 봐도 바로 보이지는 않는다.
 * - 풀린 상태는 저장하지 않는다. 앱이 다시 뜨면 잠겨 있고, 앱을 나가면 다시 잠근다(MainActivity.onStop 이 [lock]).
 * - 다섯 번 연달아 틀리면 30초 동안 받지 않는다.
 * - 저장소는 SharedPreferences 다. 자동 백업(data_extraction_rules)에 들어가지 않아 다른 기기로 따라가지 않는다. 백업 파일에도 넣지 않는다.
 */
class PinLock(
    private val prefs: SharedPreferences,
    /** 개발자 모드 기록에 적는 이름. "월급 잠금", "앱 잠금" */
    private val label: String = "잠금",
    private val logTag: String = LogTag.SETTINGS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * @property introDone 처음 안내를 확인했는지. 월급 탭만 쓴다(연봉 공개 주의).
     * @property enabled PIN 을 정해 잠가 두었는지
     * @property biometric 지문으로도 열지
     */
    data class State(val introDone: Boolean = false, val enabled: Boolean = false, val biometric: Boolean = false)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _unlocked = MutableStateFlow(false)

    /** 이번에 앱을 쓰는 동안 풀었는지. 잠금을 쓰지 않으면 늘 열려 있는 것으로 본다([isOpen]). */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var failures = 0
    private var blockedUntil = 0L

    /** 지금 잠긴 것(월급 탭, 앱)을 보여 줘도 되는지 */
    fun isOpen(state: State = _state.value, unlocked: Boolean = _unlocked.value): Boolean = !state.enabled || unlocked

    fun markIntroDone() {
        prefs.edit { putBoolean(KEY_INTRO, true) }
        _state.value = read()
    }

    /** PIN 을 정한다(처음 켤 때, 바꿀 때). 방금 정한 사람이니 풀린 상태로 둔다. */
    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN 은 숫자 $PIN_LENGTH 자리다." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        prefs.edit {
            putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
            putString(KEY_HASH, hashPin(pin, salt))
        }
        _state.value = read()
        _unlocked.value = true
        failures = 0
        blockedUntil = 0
        DevLog.info(logTag, "$label PIN 을 정했어요")
    }

    /** 잠금을 끈다. 지문도 같이 끈다. */
    fun disable() {
        prefs.edit {
            remove(KEY_HASH)
            remove(KEY_SALT)
            remove(KEY_BIOMETRIC)
        }
        _state.value = read()
        DevLog.info(logTag, "${label}을 껐어요")
    }

    fun setBiometric(on: Boolean) {
        if (on && !_state.value.enabled) return
        prefs.edit { putBoolean(KEY_BIOMETRIC, on) }
        _state.value = read()
    }

    /** PIN 으로 풀어 본다 */
    fun tryUnlock(pin: String): Attempt {
        val waitMillis = blockedUntil - now()
        if (waitMillis > 0) return Attempt.Blocked(seconds = (waitMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
        val salt = prefs.getString(KEY_SALT, null)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val hash = prefs.getString(KEY_HASH, null)
        if (salt == null || hash == null) {
            // 잠금이 없다(지웠다). 열어 준다.
            _unlocked.value = true
            return Attempt.Ok
        }
        if (MessageDigest.isEqual(hashPin(pin, salt).toByteArray(), hash.toByteArray())) {
            failures = 0
            _unlocked.value = true
            return Attempt.Ok
        }
        failures++
        if (failures >= MAX_FAILURES) {
            failures = 0
            blockedUntil = now() + BLOCK_MILLIS
            DevLog.info(logTag, "$label PIN 을 $MAX_FAILURES 번 틀려 잠깐 막았어요")
            return Attempt.Blocked(seconds = BLOCK_MILLIS / MILLIS_PER_SECOND)
        }
        return Attempt.Wrong(remaining = MAX_FAILURES - failures)
    }

    /** 지문으로 풀었다 */
    fun unlockWithBiometric() {
        failures = 0
        _unlocked.value = true
    }

    /** 다시 잠근다(앱을 나갔을 때) */
    fun lock() {
        _unlocked.value = false
    }

    /**
     * 잠금을 지운다. 월급 잠금은 PIN 을 잊었을 때(월급 설정과 함께)와 데이터 초기화 때 부른다.
     * @param keepIntro 처음 안내를 확인한 것은 남길지. 데이터 초기화는 처음 설치한 상태라 안내부터 다시 본다.
     */
    fun reset(keepIntro: Boolean) {
        prefs.edit {
            remove(KEY_HASH)
            remove(KEY_SALT)
            remove(KEY_BIOMETRIC)
            if (!keepIntro) remove(KEY_INTRO)
        }
        _state.value = read()
        _unlocked.value = false
        failures = 0
        blockedUntil = 0
    }

    private fun read(): State = State(
        introDone = prefs.getBoolean(KEY_INTRO, false),
        enabled = prefs.contains(KEY_HASH),
        biometric = prefs.contains(KEY_HASH) && prefs.getBoolean(KEY_BIOMETRIC, false),
    )

    /** PIN 으로 풀어 본 결과 */
    sealed interface Attempt {
        data object Ok : Attempt

        /** 틀렸다. [remaining] 번 더 틀리면 잠깐 막힌다. */
        data class Wrong(val remaining: Int) : Attempt

        /** 너무 많이 틀려 [seconds] 초 동안 받지 않는다 */
        data class Blocked(val seconds: Long) : Attempt
    }

    companion object {
        /** 월급 탭 잠금의 SharedPreferences 파일 이름 */
        const val SALARY_PREFS_NAME = "salary_lock"

        /** 앱 잠금의 SharedPreferences 파일 이름 */
        const val APP_PREFS_NAME = "app_lock"

        const val PIN_LENGTH = 4
        private const val KEY_INTRO = "intro_done"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_BIOMETRIC = "biometric"
        private const val SALT_BYTES = 16
        private const val MAX_FAILURES = 5
        private const val BLOCK_MILLIS = 30_000L
        private const val MILLIS_PER_SECOND = 1_000L

        fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

        /** 소금을 섞은 PIN 의 SHA-256(Base64) */
        internal fun hashPin(pin: String, salt: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.update(pin.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(digest.digest())
        }
    }
}
