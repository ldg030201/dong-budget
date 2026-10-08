package com.dong.budget.data.update

import android.app.Activity
import com.dong.budget.testing.FakePreferences
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlayUpdateCheckerTest {
    private val prefs = FakePreferences()
    private var calls = 0
    private var fromPlay = true
    private var next: Result<PlayUpdateInfo> = Result.success(available(21))

    private fun available(code: Int) =
        PlayUpdateInfo(UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.UNKNOWN, versionCode = code, flexibleAllowed = true)

    private class PlayError(val code: Int) : Exception()

    private fun checker() = PlayUpdateChecker(
        fetch = {
            calls++
            next
        },
        fromPlay = { fromPlay },
        prefs = prefs,
        errorCodeOf = { (it as? PlayError)?.code },
    )

    @Test
    fun `앱을 열 때마다 Play 에 묻는다`() = runBlocking {
        val checker = checker()
        checker.check()
        checker.check()
        assertEquals(2, calls)
        assertEquals(PlayUpdateState.Available(21), checker.state.value)
        assertFalse(checker.checking.value)
    }

    @Test
    fun `새 버전이 있으면 홈에 버전 이름 없이 알린다`() = runBlocking {
        val checker = checker()
        assertNull(checker.notice.first())
        checker.check()
        assertEquals(UpdateNotice.Available(version = null), checker.notice.first())
    }

    @Test
    fun `알림 줄을 닫으면 이번 실행에서만 숨긴다`() = runBlocking {
        val checker = checker()
        checker.check()
        checker.dismiss()
        assertNull(checker.notice.first())
        // 앱을 다시 켠 것처럼 새로 만든다
        val reopened = checker()
        reopened.check()
        assertEquals(UpdateNotice.Available(version = null), reopened.notice.first())
    }

    @Test
    fun `건너뛴 버전은 앱을 다시 켜도 알리지 않고, 더 새 버전이 나오면 다시 알린다`() = runBlocking {
        val checker = checker()
        checker.check()
        checker.skip()
        assertNull(checker.notice.first())

        val reopened = checker()
        reopened.check()
        assertNull(reopened.notice.first())
        // 건너뛴 버전도 앱 정보에는 그대로 보인다
        assertEquals(PlayUpdateState.Available(21), reopened.state.value)

        next = Result.success(available(22))
        reopened.check()
        assertEquals(UpdateNotice.Available(version = null), reopened.notice.first())
    }

    @Test
    fun `새 버전이 없으면 건너뛸 것도 없다`() = runBlocking {
        next = Result.success(PlayUpdateInfo(UpdateAvailability.UPDATE_NOT_AVAILABLE, InstallStatus.UNKNOWN, 0, flexibleAllowed = false))
        val checker = checker()
        checker.check()
        checker.skip()
        next = Result.success(available(21))
        checker.check()
        assertEquals(UpdateNotice.Available(version = null), checker.notice.first())
    }

    @Test
    fun `받기를 시작하면 받는 중, 다 받으면 다시 시작하라고 알린다`() = runBlocking {
        val checker = checker()
        checker.check()
        checker.onFlowResult(Activity.RESULT_OK)
        assertEquals(UpdateNotice.Downloading(null), checker.notice.first())

        assertTrue(checker.onInstallState(InstallStatus.DOWNLOADING, 30, 100, 0))
        assertEquals(UpdateNotice.Downloading(0.3f), checker.notice.first())

        assertTrue(checker.onInstallState(InstallStatus.DOWNLOADED, 100, 100, 0))
        assertEquals(UpdateNotice.Downloaded, checker.notice.first())
    }

    @Test
    fun `업데이트 확인 창에서 나중에를 누르면 새 버전 알림이 그대로다`() = runBlocking {
        val checker = checker()
        checker.check()
        checker.onFlowResult(Activity.RESULT_CANCELED)
        assertEquals(PlayUpdateState.Available(21), checker.state.value)
    }

    @Test
    fun `새 버전을 모르는 채로 진행 상황이 오면 Play 에 다시 물으라고 알린다`() {
        // 앱을 다시 켰는데 '앱을 열 때 새 버전 확인하기' 를 꺼 둬서 아직 묻지 않은 경우
        assertFalse(checker().onInstallState(InstallStatus.DOWNLOADING, 30, 100, 0))
    }

    @Test
    fun `Play 에서 설치하지 않은 앱이면 확인할 수 없다고 알린다`() = runBlocking {
        next = Result.failure(PlayError(InstallErrorCode.ERROR_APP_NOT_OWNED))
        val checker = checker()
        checker.check()
        assertEquals(PlayUpdateState.NotFromPlay, checker.state.value)

        // adb 로 설치한 앱에 Play 가 '새 버전 없음' 으로 답한 경우
        fromPlay = false
        next = Result.success(PlayUpdateInfo(UpdateAvailability.UPDATE_NOT_AVAILABLE, InstallStatus.UNKNOWN, 0, flexibleAllowed = false))
        checker.check()
        assertEquals(PlayUpdateState.NotFromPlay, checker.state.value)
    }

    @Test
    fun `앱을 열 때 잠깐 실패해도 알림 줄은 남는다`() = runBlocking {
        val checker = checker()
        checker.check()
        next = Result.failure(IOException("연결 끊김"))
        checker.check()
        assertEquals(UpdateNotice.Available(version = null), checker.notice.first())
    }

    @Test
    fun `처음부터 확인에 실패하면 다시 해 보라고 알린다`() = runBlocking {
        next = Result.failure(IOException("연결 끊김"))
        val checker = checker()
        checker.check()
        assertEquals(PlayUpdateState.Failed(PlayUpdateRules.CHECK_FAILED_MESSAGE), checker.state.value)
        assertNull(checker.notice.first())
    }
}
