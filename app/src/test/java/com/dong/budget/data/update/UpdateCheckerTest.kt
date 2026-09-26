package com.dong.budget.data.update

import com.dong.budget.testing.FakePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class UpdateCheckerTest {
    private val prefs = FakePreferences()
    private var clock = 1_000_000L
    private var calls = 0
    private var next: Result<List<NewerRelease>> = Result.success(listOf(release("0.1.6"), release("0.1.5")))

    private fun release(version: String) = NewerRelease(version, LocalDate.of(2026, 9, 25), "바뀐 점", "https://example.com/$version.apk", 100)

    private fun checker(current: String = "0.1.4") = UpdateChecker(
        fetch = {
            calls++
            next
        },
        prefs = prefs,
        currentVersion = current,
        now = { clock },
    )

    @Test
    fun `확인 간격 안에서는 다시 확인하지 않는다`() = runBlocking {
        val checker = checker()
        checker.checkIfDue()
        clock += UpdateChecker.INTERVAL_MS - 1
        checker.checkIfDue()
        assertEquals(1, calls)
        clock += 1
        checker.checkIfDue()
        assertEquals(2, calls)
    }

    @Test
    fun `지금 확인은 간격과 상관없이 확인한다`() = runBlocking {
        val checker = checker()
        checker.checkIfDue()
        checker.checkNow()
        assertEquals(2, calls)
    }

    @Test
    fun `찾은 새 버전들은 앱을 다시 켜도 날짜까지 그대로 남아 있다`() = runBlocking {
        checker().checkIfDue()
        // 앱을 다시 켠 것처럼 새로 만든다
        val reopened = checker()
        assertEquals(listOf(release("0.1.6"), release("0.1.5")), reopened.newer.value)
        assertEquals("0.1.6", reopened.bannerVersion.first())
    }

    @Test
    fun `이미 업데이트한 버전까지는 저장된 결과에서 뺀다`() = runBlocking {
        checker(current = "0.1.4").checkIfDue()
        assertEquals(listOf("0.1.6"), checker(current = "0.1.5").newer.value.map { it.version })
        assertTrue(checker(current = "0.1.6").newer.value.isEmpty())
    }

    @Test
    fun `새 버전이 없다는 결과가 오면 배너를 치운다`() = runBlocking {
        val checker = checker()
        checker.checkIfDue()
        next = Result.success(emptyList())
        checker.checkNow()
        assertNull(checker.bannerVersion.first())
        assertTrue(checker().newer.value.isEmpty())
    }

    @Test
    fun `확인에 실패하면 기록하지 않고 다음에 다시 확인한다`() = runBlocking {
        next = Result.failure(IOException("네트워크 없음"))
        val checker = checker()
        checker.checkIfDue()
        checker.checkIfDue()
        assertEquals(2, calls)
    }

    @Test
    fun `배너를 닫으면 이번 실행에서만 숨긴다`() = runBlocking {
        val checker = checker()
        checker.checkIfDue()
        checker.dismissBanner()
        assertNull(checker.bannerVersion.first())
        assertEquals("0.1.6", checker().bannerVersion.first())
    }

    @Test
    fun `0_1_7 이 칸마다 따로 적던 옛 기록은 읽지 않고 치운다`() = runBlocking {
        prefs.edit().putString("version", "0.1.8").putString("url", "https://example.com/old.apk").apply()
        val checker = checker()
        assertTrue(checker.newer.value.isEmpty())
        checker.checkIfDue()
        assertNull(prefs.getString("version", null))
        assertNull(prefs.getString("url", null))
    }
}
