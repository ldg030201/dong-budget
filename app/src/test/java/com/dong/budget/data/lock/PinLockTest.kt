package com.dong.budget.data.lock

import com.dong.budget.testing.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockTest {
    private var nowMillis = 1_000_000L
    private val prefs = FakePreferences()
    private val lock = PinLock(prefs, now = { nowMillis })

    @Test
    fun `처음에는 안내 전이고 잠겨 있지 않다`() {
        assertEquals(PinLock.State(introDone = false, enabled = false, biometric = false), lock.state.value)
        assertTrue(lock.isOpen())
    }

    @Test
    fun `안내를 확인하면 남는다`() {
        lock.markIntroDone()
        assertTrue(lock.state.value.introDone)
        assertTrue(PinLock(prefs).state.value.introDone)
    }

    @Test
    fun `PIN 을 정하면 잠금이 켜지고 방금 정했으니 풀려 있다`() {
        lock.setPin("1234")
        assertTrue(lock.state.value.enabled)
        assertTrue(lock.isOpen())
        // 앱을 다시 띄우면 잠겨 있다(풀린 상태는 저장하지 않는다)
        val reopened = PinLock(prefs)
        assertTrue(reopened.state.value.enabled)
        assertFalse(reopened.isOpen())
    }

    @Test
    fun `PIN 은 그대로 저장하지 않는다`() {
        lock.setPin("1234")
        assertFalse(prefs.all.values.any { it == "1234" })
    }

    @Test
    fun `앱을 나가면 다시 잠기고 맞는 PIN 으로 풀린다`() {
        lock.setPin("1234")
        lock.lock()
        assertFalse(lock.isOpen())
        assertEquals(PinLock.Attempt.Ok, lock.tryUnlock("1234"))
        assertTrue(lock.isOpen())
    }

    @Test
    fun `틀리면 남은 횟수를 알려 주고 다섯 번 틀리면 30초 막는다`() {
        lock.setPin("1234")
        lock.lock()
        assertEquals(
            listOf(4, 3, 2, 1).map { PinLock.Attempt.Wrong(remaining = it) },
            List(4) { lock.tryUnlock("0000") },
        )
        assertEquals(PinLock.Attempt.Blocked(seconds = 30), lock.tryUnlock("0000"))
        // 막힌 동안에는 맞는 PIN 도 받지 않는다
        nowMillis += 10_500
        assertEquals(PinLock.Attempt.Blocked(seconds = 20), lock.tryUnlock("1234"))
        assertFalse(lock.isOpen())
        nowMillis += 20_000
        assertEquals(PinLock.Attempt.Ok, lock.tryUnlock("1234"))
        assertTrue(lock.isOpen())
    }

    @Test
    fun `맞게 풀면 틀린 횟수를 처음부터 센다`() {
        lock.setPin("1234")
        lock.lock()
        repeat(4) { lock.tryUnlock("0000") }
        assertEquals(PinLock.Attempt.Ok, lock.tryUnlock("1234"))
        lock.lock()
        assertEquals(PinLock.Attempt.Wrong(remaining = 4), lock.tryUnlock("0000"))
    }

    @Test
    fun `PIN 을 바꾸면 전 PIN 으로는 풀리지 않는다`() {
        lock.setPin("1234")
        lock.setPin("5678")
        lock.lock()
        assertEquals(PinLock.Attempt.Wrong(remaining = 4), lock.tryUnlock("1234"))
        assertEquals(PinLock.Attempt.Ok, lock.tryUnlock("5678"))
    }

    @Test
    fun `같은 PIN 이어도 기기마다 소금이 달라 저장 값이 다르다`() {
        val other = FakePreferences()
        lock.setPin("1234")
        PinLock(other).setPin("1234")
        assertNotEquals(prefs.getString("pin_hash", null), other.getString("pin_hash", null))
    }

    @Test
    fun `지문은 잠금이 켜져 있을 때만 켜진다`() {
        lock.setBiometric(true)
        assertFalse(lock.state.value.biometric)
        lock.setPin("1234")
        lock.setBiometric(true)
        assertTrue(lock.state.value.biometric)
        lock.lock()
        lock.unlockWithBiometric()
        assertTrue(lock.isOpen())
    }

    @Test
    fun `잠금을 끄면 지문도 같이 꺼지고 늘 열려 있다`() {
        lock.setPin("1234")
        lock.setBiometric(true)
        lock.lock()
        lock.disable()
        assertEquals(PinLock.State(introDone = false, enabled = false, biometric = false), lock.state.value)
        assertTrue(lock.isOpen())
    }

    @Test
    fun `PIN 을 잊으면 잠금만 지우고 안내는 남긴다`() {
        lock.markIntroDone()
        lock.setPin("1234")
        lock.setBiometric(true)
        lock.reset(keepIntro = true)
        assertEquals(PinLock.State(introDone = true, enabled = false, biometric = false), lock.state.value)
        assertTrue(lock.isOpen())
    }

    @Test
    fun `데이터 초기화는 안내까지 지운다`() {
        lock.markIntroDone()
        lock.setPin("1234")
        lock.reset(keepIntro = false)
        assertEquals(PinLock.State(), lock.state.value)
    }

    @Test
    fun `PIN 은 숫자 네 자리만 된다`() {
        assertTrue(PinLock.isValidPin("0000"))
        assertFalse(PinLock.isValidPin("123"))
        assertFalse(PinLock.isValidPin("12345"))
        assertFalse(PinLock.isValidPin("12a4"))
        assertFalse(PinLock.isValidPin("١٢٣٤"))
    }

    @Test
    fun `2중 잠금을 끄면 앱 잠금이 켜져 있는 동안만 따로 묻지 않고, PIN 은 남는다`() {
        lock.setPin("1234")
        lock.lock()
        // 처음에는 2중 잠금이라 앱 잠금이 켜져 있어도 따로 묻는다
        assertTrue(lock.state.value.double)
        assertFalse(lock.isOpen(appLockOn = true))

        lock.setDouble(false)
        assertTrue(lock.isOpen(appLockOn = true))
        // 앱 잠금을 끄면 다시 이 잠금이 지킨다
        assertFalse(lock.isOpen(appLockOn = false))
        assertFalse(PinLock(prefs).state.value.double)

        // 2중 잠금을 켠다고 풀지 않은 잠금이 열리지는 않는다
        lock.setDouble(true)
        assertFalse(lock.isOpen(appLockOn = true))

        // 월급 설정 안에서(열린 채로) 켜면 그 자리에서 잠기지 않고, 나갔다 오면 다시 묻는다
        lock.setDouble(false)
        lock.setDouble(true, keepOpen = true)
        assertTrue(lock.isOpen(appLockOn = true))
        lock.lock()
        assertFalse(lock.isOpen(appLockOn = true))

        // 잠금을 끄면 2중 잠금 선택도 처음대로
        lock.setDouble(false)
        lock.disable()
        assertTrue(lock.state.value.double)
    }
}
