package com.dong.budget.data

import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class SuspendRunCatchingTest {
    @Test
    fun `값은 성공으로, 다른 오류는 실패로 준다`() {
        assertEquals(1, suspendRunCatching { 1 }.getOrNull())
        val error = IllegalStateException("읽지 못했어요")
        assertSame(error, suspendRunCatching { throw error }.exceptionOrNull())
    }

    @Test
    fun `취소는 실패로 바꾸지 않고 다시 던진다`() {
        assertThrows(CancellationException::class.java) { suspendRunCatching { throw CancellationException("닫힘") } }
    }

    @Test
    fun `취소된 코루틴은 감싼 일시 중단 호출에서 멈추고 오류로 남기지 않는다`() = runBlocking {
        // 알림 읽기 서비스가 닫혀 범위가 취소된 뒤 남은 알림을 처리하던 모양. runCatching 이면 취소를 실패로 받아 오류를 적고 계속 간다.
        val failures = mutableListOf<Throwable>()
        var after = false
        val job = launch {
            cancel()
            suspendRunCatching { yield() }.onFailure { failures += it }
            after = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyList<Throwable>(), failures)
        assertEquals(false, after)
    }
}
