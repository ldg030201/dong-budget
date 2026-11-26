package com.dong.budget.ui.salary

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 월급 설정 저장. 화면 모델([SalarySettingsViewModel])은 저장소가 기기의 DataStore 를 써서 기기 없이 만들 수 없다.
 * 그래서 화면 모델이 저장을 맡기는 [launchWrite] 를 가짜 저장소와 끊기는 범위로 본다.
 */
class SalarySettingsSaveTest {
    @Test
    fun `화면을 나가 화면 모델이 끝나도 맡긴 저장은 누른 차례대로 끝까지 한다`() = runBlocking {
        // 화면 모델의 범위(viewModelScope). 메인 스레드가 없어 기본 풀에서 돈다.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = QueuedStore()
        // 1, 2, 3 을 빨리 누르는 동안 저장소는 아직 하나도 쓰지 못했다
        val saves = listOf(1L, 12L, 123L).map { amount -> scope.launchWrite { store.save(amount) } }
        // 바로 화면을 나가 화면 모델이 끝난다(viewModelScope 가 끊긴다)
        scope.cancel()
        store.resume()
        saves.joinAll()
        // 전에는 아직 차례가 안 온 쓰기가 끊겨 버려졌다. 마지막으로 누른 값이 마지막에 쓰인다.
        assertEquals(listOf(1L, 12L, 123L), store.written)
    }
}

/** DataStore 처럼 받은 차례대로 하나씩 쓴다. [resume] 전에는 첫 쓰기에서 멈춰 있어 뒤의 쓰기가 차례를 기다린다. */
private class QueuedStore {
    /** 먼저 온 쪽부터 잠금을 준다 */
    private val turns = Mutex()
    private val gate = CompletableDeferred<Unit>()
    val written = mutableListOf<Long>()

    suspend fun save(amount: Long) = turns.withLock {
        gate.await()
        written += amount
    }

    fun resume() {
        gate.complete(Unit)
    }
}
