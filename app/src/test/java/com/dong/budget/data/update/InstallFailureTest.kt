package com.dong.budget.data.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallFailureTest {
    private val aborted = PackageInstaller.STATUS_FAILURE_ABORTED

    @Test
    fun `갤럭시 자동 차단이 막은 자기 업데이트는 자동 차단을 끄라고 안내한다`() {
        // 실제로 받은 메시지(0.1.5 → 0.1.6)
        val failure =
            InstallResultReceiver.failureOf(
                aborted,
                -115,
                "INSTALL_FAILED_ABORTED: Self update is blocked by unknown source package",
                galaxy = true,
            )
        assertTrue(failure.suggestGalaxySecurity)
        assertTrue(failure.reason.contains("보안 위험 자동 차단"))
        assertTrue(failure.detail!!.contains("내부 코드 -115"))
    }

    @Test
    fun `갤럭시에서 이유 없이 중단된 설치에도 자동 차단 버튼을 보여준다`() {
        val failure = InstallResultReceiver.failureOf(aborted, 0, null, galaxy = true)
        assertTrue(failure.suggestGalaxySecurity)
    }

    @Test
    fun `갤럭시가 아니면 자동 차단 버튼을 보여주지 않는다`() {
        val failure = InstallResultReceiver.failureOf(aborted, 0, null, galaxy = false)
        assertFalse(failure.suggestGalaxySecurity)
        assertFalse(failure.reason.contains("자동 차단"))
    }

    @Test
    fun `Play 프로텍트가 막은 경우는 갤럭시여도 자동 차단 대신 Play 프로텍트를 안내한다`() {
        val failure = InstallResultReceiver.failureOf(aborted, -22, "INSTALL_FAILED_VERIFICATION_FAILURE", galaxy = true)
        assertFalse(failure.suggestGalaxySecurity)
        assertTrue(failure.reason.contains("Play 프로텍트"))
    }
}
