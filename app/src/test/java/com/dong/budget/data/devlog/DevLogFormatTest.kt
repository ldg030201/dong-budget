package com.dong.budget.data.devlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DevLogFormatTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val at = LocalDateTime.of(2026, 9, 27, 14, 55, 3, 120_000_000).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `여러 줄과 탭과 역슬래시가 든 내용도 한 줄로 적었다가 그대로 되살린다`() {
        val entry = LogEntry(at, LogLevel.ERROR, "앱", "앱이 멈췄어요\njava.lang.IllegalStateException: 탭\t끝 \\n 글자\r\n\tat a.b(C.kt:1)")
        val line = encodeLogEntry(entry)
        assertTrue('\n' !in line)
        assertEquals(entry, decodeLogEntry(line))
    }

    @Test
    fun `차례 번호는 파일에 적지 않는다`() {
        val decoded = decodeLogEntry(encodeLogEntry(LogEntry(at, LogLevel.INFO, "알림", "물었어요", id = 7)))
        assertEquals(0L, decoded?.id)
    }

    @Test
    fun `쓰다 만 줄이나 모르는 무게는 버린다`() {
        assertNull(decodeLogEntry(""))
        assertNull(decodeLogEntry("123\tINFO\t알림"))
        assertNull(decodeLogEntry("abc\tINFO\t알림\t내용"))
        assertNull(decodeLogEntry("123\tDEBUG\t알림\t내용"))
    }

    @Test
    fun `복사한 글의 한 줄은 시각 무게 곳 내용 순서다`() {
        assertEquals("09-27 14:55:03.120 오류 [앱] 멈췄어요", formatLogEntry(LogEntry(at, LogLevel.ERROR, "앱", "멈췄어요"), zone))
        assertEquals("09-27 14:55:03.120 [알림] 물었어요", formatLogEntry(LogEntry(at, LogLevel.INFO, "알림", "물었어요"), zone))
        assertEquals("14:55:03", formatLogTime(at, zone))
    }

    @Test
    fun `복사한 글은 머리 뒤에 오래된 것부터 적는다`() {
        val entries = listOf(LogEntry(at, LogLevel.INFO, "화면", "하나"), LogEntry(at + 1_000, LogLevel.WARN, "업데이트", "둘"))
        val text = exportLog(listOf("동계부 1.2.0 (11)", "Android 16"), entries, zone)
        assertEquals(
            listOf(
                "동계부 1.2.0 (11)",
                "Android 16",
                "로그 2건",
                "",
                "09-27 14:55:03.120 [화면] 하나",
                "09-27 14:55:04.120 주의 [업데이트] 둘",
            ),
            text.lines(),
        )
    }

    @Test
    fun `너무 길면 오래된 것부터 빼고 뺐다고 적는다`() {
        val entries = (1..100).map { LogEntry(at + it, LogLevel.INFO, "화면", "로그 $it " + "가".repeat(50)) }
        val text = exportLog(listOf("머리"), entries, zone, maxChars = 1_000)
        assertTrue(text.length <= 1_000)
        val kept = text.lines().drop(3)
        assertTrue(kept.last().contains("로그 100 "))
        assertTrue(text.lines()[1].startsWith("로그 100건 중 최근 ${kept.size}건 (앞의 ${100 - kept.size}건은 너무 길어 뺐어요)"))
    }
}
