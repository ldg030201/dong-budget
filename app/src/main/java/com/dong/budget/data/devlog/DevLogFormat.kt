package com.dong.budget.data.devlog

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────
// 개발자 모드 로그의 모양. 파일에 적는 한 줄, 화면과 복사한 글에 보이는 한 줄.
// 안드로이드에 기대지 않아서 단위 테스트로 바로 확인한다.
// ─────────────────────────────────────────────────────────────────────

/** 로그의 무게. [mark] 는 복사한 글에서 시각 뒤에 붙는 말이다. */
enum class LogLevel(val mark: String) {
    INFO(""),
    WARN("주의 "),
    ERROR("오류 "),
}

/**
 * 로그 한 건.
 * @property at 적은 시각(epoch ms)
 * @property tag 어디서 난 일인지([LogTag])
 * @property message 여러 줄일 수 있다(오류의 호출 기록)
 * @property id 화면에서 한 건을 가리키는 번호(펼침 상태의 키). 이번 실행에서 매기는 차례 번호라 파일에는 적지 않는다.
 */
data class LogEntry(val at: Long, val level: LogLevel, val tag: String, val message: String, val id: Long = 0)

/** 로그가 난 곳. 복사한 글에서 '[알림]' 처럼 보인다. */
object LogTag {
    const val APP = "앱"
    const val SCREEN = "화면"
    const val CAPTURE = "알림"
    const val TRANSACTION = "거래"
    const val UPDATE = "업데이트"
    const val DEV_MODE = "개발자 모드"
    const val SETTINGS = "설정"
}

/** 파일 한 줄. 칸은 탭으로 가르고, 글 안의 줄바꿈·탭·역슬래시는 되돌릴 수 있게 바꿔 적는다. */
internal fun encodeLogEntry(entry: LogEntry): String =
    listOf(entry.at.toString(), entry.level.name, escape(entry.tag), escape(entry.message)).joinToString(FIELD_SEPARATOR)

/** [encodeLogEntry] 의 반대. 깨진 줄(쓰다 만 줄, 모르는 무게)은 null */
internal fun decodeLogEntry(line: String): LogEntry? {
    val fields = line.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
    if (fields.size != FIELD_COUNT) return null
    val at = fields[0].toLongOrNull() ?: return null
    val level = LogLevel.entries.firstOrNull { it.name == fields[1] } ?: return null
    return LogEntry(at, level, unescape(fields[2]), unescape(fields[3]))
}

private fun escape(text: String): String = buildString(text.length) {
    text.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

private fun unescape(text: String): String = buildString(text.length) {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '\\' && index + 1 < text.length) {
            when (val next = text[index + 1]) {
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> append(next)
            }
            index += 2
        } else {
            append(char)
            index++
        }
    }
}

/** 복사한 글의 한 줄. "09-27 14:55:03.120 오류 [앱] 앱이 멈췄어요" */
internal fun formatLogEntry(entry: LogEntry, zone: ZoneId): String =
    "${formatLogStamp(entry.at, zone)} ${entry.level.mark}[${entry.tag}] ${entry.message}"

/** 복사한 글의 시각. "09-27 14:55:03.120" */
internal fun formatLogStamp(at: Long, zone: ZoneId): String = FULL_TIME.format(Instant.ofEpochMilli(at).atZone(zone))

/** 화면의 짧은 시각. "14:55:03" */
internal fun formatLogTime(at: Long, zone: ZoneId): String = SHORT_TIME.format(Instant.ofEpochMilli(at).atZone(zone))

/**
 * 복사할 글. 머리(앱·기기 정보, 건수) 뒤에 오래된 것부터 한 건씩 적는다.
 * [maxChars] 를 넘으면 오래된 쪽부터 빼고 몇 건을 뺐는지 머리에 적는다. 클립보드가 한 번에 옮길 수 있는 크기에 한도가 있다.
 */
internal fun exportLog(header: List<String>, entries: List<LogEntry>, zone: ZoneId, maxChars: Int = EXPORT_MAX_CHARS): String {
    val lines = entries.map { formatLogEntry(it, zone) }
    var room = maxChars - header.sumOf { it.length + 1 } - COUNT_LINE_ROOM
    var kept = 0
    for (line in lines.asReversed()) {
        room -= line.length + 1
        if (room < 0) break
        kept++
    }
    val dropped = lines.size - kept
    return buildString {
        header.forEach(::appendLine)
        appendLine(if (dropped > 0) "로그 ${lines.size}건 중 최근 ${kept}건 (앞의 ${dropped}건은 너무 길어 뺐어요)" else "로그 ${lines.size}건")
        appendLine()
        lines.takeLast(kept).forEach(::appendLine)
    }.trimEnd()
}

private const val FIELD_SEPARATOR = "\t"
private const val FIELD_COUNT = 4

/** 건수 줄('로그 1000건 중 최근 …')과 빈 줄에 남겨 둘 자리 */
private const val COUNT_LINE_ROOM = 64

/** 복사한 글의 상한(글자 수). 한글은 한 글자가 3바이트라 클립보드로 넘어가는 크기가 1MB 를 넘지 않게 잡는다. */
internal const val EXPORT_MAX_CHARS = 300_000

private val FULL_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
private val SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
