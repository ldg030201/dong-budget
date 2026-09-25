package com.dong.budget.data.update

import com.dong.budget.testing.FakePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private val prefs = FakePreferences()
    private var clock = 1_000_000L
    private var calls = 0
    private var next: UpdateStatus = available("0.1.5")

    private fun available(version: String) = UpdateStatus.Available(version, "바뀐 점", "https://example.com/$version.apk", 100)

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
    fun `찾은 새 버전은 앱을 다시 켜도 남아 있다`() = runBlocking {
        checker().checkIfDue()
        // 앱을 다시 켠 것처럼 새로 만든다
        val reopened = checker()
        assertEquals("0.1.5", reopened.available.value?.version)
        assertEquals("0.1.5", reopened.bannerVersion.first())
    }

    @Test
    fun `이미 그 버전으로 업데이트했으면 저장된 결과를 버린다`() = runBlocking {
        checker(current = "0.1.4").checkIfDue()
        assertNull(checker(current = "0.1.5").available.value)
    }

    @Test
    fun `최신이라는 결과가 오면 배너를 치운다`() = runBlocking {
        val checker = checker()
        checker.checkIfDue()
        checker.record(UpdateStatus.UpToDate)
        assertNull(checker.available.value)
        assertNull(checker().available.value)
    }

    @Test
    fun `확인에 실패하면 기록하지 않고 다음에 다시 확인한다`() = runBlocking {
        next = UpdateStatus.Failed("네트워크 없음")
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
        assertEquals("0.1.5", checker().bannerVersion.first())
    }
}
