package com.dong.budget.data.devlog

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.dong.budget.BuildConfig
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 개발자 모드 로그. 켜 둔 동안만 앱에서 일어난 일(오류, 결제 알림 처리, 거래 저장, 업데이트, 화면 이동)을
 * 이 폰의 앱 전용 폴더에 쌓는다. 문제가 생기면 개발자 모드 화면에서 복사해 보낸다.
 * 기본은 꺼져 있고, 끄면 쌓인 로그도 지운다.
 *
 * 알림 읽기, 앱이 죽을 때의 처리기, 저장소, 설치 결과 수신기처럼 화면 밖에서도 적어야 해서
 * android.util.Log 처럼 어디서나 부르는 하나로 둔다. [init] 전(단위 테스트 등)이나 꺼져 있으면 아무것도 하지 않는다.
 *
 * 파일 읽기·쓰기는 전용 스레드 하나에서 차례로 한다. 부르는 쪽(메인 스레드 포함)은 기다리지 않는다.
 * 앱이 죽을 때만 그 자리에서 바로 쓴다. 다음 기회가 없기 때문이다.
 */
object DevLog {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "dev-log").apply { isDaemon = true } }

    @Volatile private var initialized = false

    /** 저장된 켜짐 값을 읽었는지. 읽기 전에 들어온 로그는 일단 줄에 세우고, 읽은 뒤 켜짐을 보고 적거나 버린다. */
    @Volatile private var loaded = false

    @Volatile private var on = false

    // 아래는 전용 스레드에서만 만진다
    private var prefs: SharedPreferences? = null
    private var file: File? = null
    private var fileLines = 0
    private var nextId = 1L
    private val buffer = ArrayDeque<LogEntry>()

    private val _enabled = MutableStateFlow(false)

    /** 개발자 모드가 켜져 있는지 */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** 쌓인 로그. 오래된 것부터, 최대 [MAX_ENTRIES] 건 */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /** 앱이 뜰 때(Application.onCreate) 한 번 부른다. 저장된 켜짐 값과 로그 파일은 전용 스레드에서 읽는다. */
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        installCrashHandler()
        initialized = true
        worker.execute {
            val saved = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = saved
            file = File(app.filesDir, FILE_NAME)
            on = saved.getBoolean(KEY_ENABLED, false)
            _enabled.value = on
            // 꺼져 있는데 파일이 남았다면 끄는 도중 앱이 닫힌 것이다. 끄면 지운다는 약속대로 지운다.
            if (on) load() else file?.delete()
            loaded = true
            info(LogTag.APP, "앱이 시작됐어요 (${versionText()})")
        }
    }

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)

    fun warn(tag: String, message: String, error: Throwable? = null) = log(LogLevel.WARN, tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) = log(LogLevel.ERROR, tag, message, error)

    private fun log(level: LogLevel, tag: String, message: String, error: Throwable?) {
        if (!initialized || (loaded && !on)) return
        val text = (if (error == null) message else "$message\n${error.stackTraceToString()}").take(MAX_MESSAGE_CHARS)
        val entry = LogEntry(System.currentTimeMillis(), level, tag, text)
        // 개발판은 adb 로도 보이게 한다
        if (BuildConfig.DEBUG) Log.println(level.priority, LOGCAT_TAG, "[$tag] $text")
        worker.execute { if (on) append(entry) }
    }

    /**
     * 켜고 끈다. 켜면 앱·기기 정보를 첫 줄로 적고, 끄면 쌓인 로그를 모두 지운다.
     * 화면의 스위치가 바로 움직이게 켜짐 값은 곧바로 바꾸고, 저장과 파일 일은 전용 스레드에서 한다.
     */
    fun setEnabled(value: Boolean) {
        if (!initialized) return
        on = value
        _enabled.value = value
        worker.execute {
            // 전용 스레드라 기다려도 된다. 켜자마자 앱이 닫혀도 켜짐이 남게 바로 쓴다.
            prefs?.edit(commit = true) { putBoolean(KEY_ENABLED, value) }
            if (value) {
                append(LogEntry(System.currentTimeMillis(), LogLevel.INFO, LogTag.DEV_MODE, "켰어요 · ${versionText()} · ${deviceText()}"))
            } else {
                clearNow()
            }
        }
    }

    /** 쌓인 로그를 지운다. 켜져 있으면 이후 일은 계속 쌓인다. */
    fun clear() {
        if (!initialized) return
        worker.execute(::clearNow)
    }

    /** 복사할 글. 머리에 앱 버전과 기기를 적는다. */
    fun export(): String = exportLog(
        header =
        listOf(
            "동계부 ${versionText()}",
            deviceText(),
            "복사한 때 ${formatLogStamp(System.currentTimeMillis(), BudgetTime.ZONE)}",
        ),
        entries = entries.value,
        zone = BudgetTime.ZONE,
    )

    private fun load() {
        val saved = file ?: return
        val lines = runCatching { saved.readLines() }.getOrDefault(emptyList())
        fileLines = lines.size
        buffer.clear()
        buffer.addAll(lines.mapNotNull(::decodeLogEntry).takeLast(MAX_ENTRIES).map { it.copy(id = nextId++) })
        _entries.value = buffer.toList()
    }

    private fun append(entry: LogEntry) {
        buffer.addLast(entry.copy(id = nextId++))
        while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        _entries.value = buffer.toList()
        val target = file ?: return
        // 로그를 쓰다 실패한 것은 적을 곳이 없다. 앱 동작에는 영향이 없게 조용히 넘긴다.
        runCatching {
            // 파일은 덧붙이기만 하다가 너무 길어지면 남길 만큼만 다시 쓴다. 한 줄마다 파일 전체를 다시 쓰지 않기 위함이다.
            if (fileLines >= MAX_ENTRIES + TRIM_SLACK) {
                target.writeText(buffer.joinToString(separator = "") { encodeLogEntry(it) + "\n" })
                fileLines = buffer.size
            } else {
                target.appendText(encodeLogEntry(entry) + "\n")
                fileLines++
            }
        }
    }

    private fun clearNow() {
        buffer.clear()
        _entries.value = emptyList()
        file?.delete()
        fileLines = 0
    }

    /** 앱이 죽을 때 오류를 적은 뒤, 원래 처리기(안드로이드의 '앱이 멈춤')에 넘긴다. */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 줄에 남은 로그(저장된 켜짐 값 읽기 포함)를 잠깐 기다려 먼저 쓰고, 오류는 이 자리에서 바로 파일에 덧붙인다. */
    private fun recordCrash(thread: Thread, error: Throwable) {
        worker.shutdown()
        worker.awaitTermination(CRASH_FLUSH_MS, TimeUnit.MILLISECONDS)
        if (!on) return
        val target = file ?: return
        val entry =
            LogEntry(
                at = System.currentTimeMillis(),
                level = LogLevel.ERROR,
                tag = LogTag.APP,
                message = "앱이 멈췄어요 (${thread.name} 스레드)\n${error.stackTraceToString()}".take(MAX_MESSAGE_CHARS),
            )
        target.appendText(encodeLogEntry(entry) + "\n")
    }

    private fun versionText(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" + if (BuildConfig.DEBUG) " 개발판" else ""

    private fun deviceText(): String =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

    private val LogLevel.priority: Int
        get() = when (this) {
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }

    private const val PREFS_NAME = "dev_log"
    private const val KEY_ENABLED = "enabled"
    private const val FILE_NAME = "dev_log.txt"
    private const val LOGCAT_TAG = "DongBudget"

    /** 남겨 두는 로그 수. 넘으면 오래된 것부터 버린다. */
    const val MAX_ENTRIES = 1000

    /** 파일은 이만큼 더 쌓인 뒤에 한 번 줄인다 */
    private const val TRIM_SLACK = 500

    /** 한 건의 글자 수 상한. 오류의 호출 기록이 아주 길 때 자른다. */
    private const val MAX_MESSAGE_CHARS = 8_000

    /** 앱이 죽을 때 줄에 남은 로그를 기다리는 시간. 길면 '앱이 멈춤' 창이 늦게 뜬다. */
    private const val CRASH_FLUSH_MS = 500L
}
