package com.dong.budget.data

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Collections

/** 화면 모델이 저장을 맡기는 [launchWrite]. 차례대로 끝까지 쓰는 것은 SalarySettingsSaveTest 가 본다. */
class LaunchWriteTest {
    /** 화면 모델 범위에서 잡히지 않고 빠져나간 오류. 앱에서는 이것이 앱을 죽인다. */
    private val crashes: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> crashes += e })

    @Test
    fun `파일에 쓰지 못하면 앱을 죽이지 않고 알린다`() = runBlocking {
        val failures = Collections.synchronizedList(mutableListOf<IOException>())
        scope.launchWrite(onFailure = { failures += it }) { throw IOException("저장 공간 없음") }.join()
        assertEquals(1, failures.size)
        assertTrue(crashes.toString(), crashes.isEmpty())
        scope.cancel()
    }

    @Test
    fun `코드 잘못은 삼키지 않는다`() = runBlocking {
        val failures = Collections.synchronizedList(mutableListOf<IOException>())
        scope.launchWrite(onFailure = { failures += it }) { error("잘못") }.join()
        assertTrue(failures.isEmpty())
        assertEquals(1, crashes.size)
        scope.cancel()
    }
}
