package com.dong.budget.data.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** 저장소를 흉내 낸 [MutableStateFlow] 위에서 [OptimisticValue] 가 메아리를 거르고 밖의 변경을 따라가는지 본다. */
class OptimisticValueTest {
    // 일을 부른 자리에서 바로 돌게 해 순서를 정해 둔다
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** 저장소. [gates] 를 열어야 쓰기가 끝난다(저장소가 늦게 돌려주는 것 흉내). */
    private val store = MutableStateFlow(0)
    private val gates = ArrayDeque<CompletableDeferred<Unit>>()

    private fun optimistic(fail: Boolean = false) = OptimisticValue(scope, store, initial = -1) { next ->
        val gate = CompletableDeferred<Unit>().also(gates::addLast)
        gate.await()
        if (fail) throw IOException("저장 실패")
        store.value = next
    }

    @Test
    fun `바꾼 값은 저장소를 돌기 전에 바로 보이고 늦게 돌아온 앞의 값으로 튀지 않는다`() {
        val value = optimistic()
        assertEquals(0, value.value.value)
        value.set(1)
        value.set(2)
        assertEquals(2, value.value.value)
        // 첫 쓰기가 끝나 저장소가 1 을 내보내도 2 를 그대로 보인다
        gates.removeFirst().complete(Unit)
        assertEquals(1, store.value)
        assertEquals(2, value.value.value)
        // 다 쓰면 저장소의 값과 같다
        gates.removeFirst().complete(Unit)
        assertEquals(2, value.value.value)
    }

    @Test
    fun `밖에서 바뀐 값은 따라가고 쓰는 중에 바뀌었으면 쓰기가 끝날 때 맞춘다`() {
        val value = optimistic()
        // 백업 복원처럼 저장소가 밖에서 바뀌었다
        store.value = 7
        assertEquals(7, value.value.value)
        value.set(8)
        store.value = 9
        assertEquals(8, value.value.value)
        gates.removeFirst().complete(Unit)
        assertEquals(8, value.value.value)
    }

    @Test
    fun `쓰지 못했으면 저장소 값으로 되돌린다`() {
        val value = optimistic(fail = true)
        value.set(5)
        assertEquals(5, value.value.value)
        gates.removeFirst().complete(Unit)
        assertEquals(0, value.value.value)
    }
}
