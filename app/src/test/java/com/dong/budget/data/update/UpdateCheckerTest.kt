package com.dong.budget.data.update

import android.content.SharedPreferences
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

/** 메모리에만 두는 SharedPreferences. UpdateChecker 가 쓰는 부분만 동작한다. */
private class FakePreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()

        override fun putString(key: String, value: String?) = apply { pending[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value }

        override fun putLong(key: String, value: Long) = apply { pending[key] = value }

        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply { removed += key }

        override fun clear() = apply { removed += values.keys }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            removed.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}
