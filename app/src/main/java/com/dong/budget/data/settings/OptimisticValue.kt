package com.dong.budget.data.settings

import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 저장소([source]) 값을 따라가되, 이 앱이 [set] 으로 바꾼 값은 저장소를 돌기 전에 바로 보이는 값.
 *
 * 저장소는 쓴 값을 늦게 돌려준다. 연달아 바꾸면 앞서 쓴 값이 뒤늦게 돌아와 화면이 옛 값으로 잠깐 튄다.
 * 그래서 쓰는 중에는 저장소가 내보내는 값(앞서 쓴 값의 메아리)을 건너뛰고, 쓰기가 다 끝나면 저장소의 마지막 값으로 맞춘다.
 * 밖에서 바뀐 값(백업 복원 · 설정 초기화)은 그대로 따라온다. 쓰는 중에 밖에서 바뀌었으면 쓰기가 끝날 때 맞춰진다.
 * 쓰지 못했으면 저장소 값으로 되돌린다.
 *
 * @param write 저장소에 쓰는 일. 부른 차례대로 한다(늦게 부른 쓰기가 먼저 끝나 옛 값이 남지 않게).
 */
class OptimisticValue<T>(scope: CoroutineScope, private val source: Flow<T>, initial: T, private val write: suspend (T) -> Unit) {
    private val state = MutableStateFlow(initial)

    val value: StateFlow<T> = state.asStateFlow()

    private val lock = Any()

    /** 아직 다 쓰지 않은 값의 수. 0 이 아니면 저장소가 내보내는 값은 메아리라 건너뛴다. */
    private var pending = 0

    private val writes = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch { source.collect { stored -> synchronized(lock) { if (pending == 0) state.value = stored } } }
        scope.launch {
            for (next in writes) {
                try {
                    write(next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DevLog.warn(LogTag.SETTINGS, "설정을 저장하지 못해 저장된 값으로 되돌려요", e)
                }
                val done = synchronized(lock) { --pending == 0 }
                if (done) {
                    // 다 썼으면 저장소의 값으로 맞춘다. 읽는 사이 또 바꿨으면 그 쓰기가 끝날 때 맞춘다.
                    val stored = source.first()
                    synchronized(lock) { if (pending == 0) state.value = stored }
                }
            }
        }
    }

    /** [next] 로 바꾼다. 바로 보이고, 저장은 뒤에서 한다. */
    fun set(next: T) {
        synchronized(lock) {
            pending++
            state.value = next
        }
        writes.trySend(next)
    }
}
