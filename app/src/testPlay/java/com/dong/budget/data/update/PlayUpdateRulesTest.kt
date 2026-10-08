package com.dong.budget.data.update

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayUpdateRulesTest {
    private fun info(
        availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
        installStatus: Int = InstallStatus.UNKNOWN,
        flexibleAllowed: Boolean = true,
        bytes: Long = 0,
        total: Long = 0,
    ) = PlayUpdateInfo(
        availability,
        installStatus,
        versionCode = 21,
        flexibleAllowed = flexibleAllowed,
        bytesDownloaded = bytes,
        totalBytes = total,
    )

    @Test
    fun `새 버전이 있으면 받을 수 있는 상태다`() {
        assertEquals(PlayUpdateState.Available(21), PlayUpdateRules.stateOf(info(), fromPlay = true))
        // 앱 안에서 받을 수 없는 업데이트는 Play 스토어로 보낸다
        assertEquals(
            PlayUpdateState.Available(21, flexibleAllowed = false),
            PlayUpdateRules.stateOf(info(flexibleAllowed = false), fromPlay = true),
        )
    }

    @Test
    fun `받는 중이거나 다 받은 업데이트가 있으면 그것을 먼저 본다`() {
        assertEquals(
            PlayUpdateState.Downloading(21, 0.25f),
            PlayUpdateRules.stateOf(info(installStatus = InstallStatus.DOWNLOADING, bytes = 25, total = 100), fromPlay = true),
        )
        // 받기 전이라 크기를 아직 모른다
        assertEquals(
            PlayUpdateState.Downloading(21, null),
            PlayUpdateRules.stateOf(info(installStatus = InstallStatus.PENDING), fromPlay = true),
        )
        // 앱을 다시 켰을 때 이미 다 받아 둔 업데이트
        assertEquals(
            PlayUpdateState.Downloaded(21),
            PlayUpdateRules.stateOf(info(installStatus = InstallStatus.DOWNLOADED), fromPlay = true),
        )
        assertEquals(PlayUpdateState.Installing, PlayUpdateRules.stateOf(info(installStatus = InstallStatus.INSTALLING), fromPlay = true))
    }

    @Test
    fun `받다가 실패했거나 취소한 업데이트는 다시 받을 수 있다`() {
        assertEquals(PlayUpdateState.Available(21), PlayUpdateRules.stateOf(info(installStatus = InstallStatus.FAILED), fromPlay = true))
        assertEquals(PlayUpdateState.Available(21), PlayUpdateRules.stateOf(info(installStatus = InstallStatus.CANCELED), fromPlay = true))
    }

    @Test
    fun `새 버전이 없다는 답은 Play 에서 설치한 앱일 때만 최신으로 본다`() {
        val none = info(availability = UpdateAvailability.UPDATE_NOT_AVAILABLE)
        assertEquals(PlayUpdateState.UpToDate, PlayUpdateRules.stateOf(none, fromPlay = true))
        // adb·APK 로 설치한 앱에 Play 는 '새 버전 없음' 으로 답한다
        assertEquals(PlayUpdateState.NotFromPlay, PlayUpdateRules.stateOf(none, fromPlay = false))
        // 스토어 밖에서 설치했어도 Play 가 새 버전을 알려 주면 받을 수 있다(같은 서명이면 Play 가 덮어 설치한다)
        assertEquals(PlayUpdateState.Available(21), PlayUpdateRules.stateOf(info(), fromPlay = false))
    }

    @Test
    fun `Play 가 새 버전을 모른다고 하면 확인하지 못한 것으로 본다`() {
        assertEquals(
            PlayUpdateState.Failed(PlayUpdateRules.CHECK_FAILED_MESSAGE),
            PlayUpdateRules.stateOf(info(availability = UpdateAvailability.UNKNOWN), fromPlay = true),
        )
    }

    @Test
    fun `Play 에서 받은 앱이 아니거나 Play 를 쓸 수 없으면 차분히 알린다`() {
        listOf(
            InstallErrorCode.ERROR_APP_NOT_OWNED,
            InstallErrorCode.ERROR_API_NOT_AVAILABLE,
            InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
            InstallErrorCode.ERROR_INSTALL_UNAVAILABLE,
        ).forEach { code -> assertEquals(PlayUpdateState.NotFromPlay, PlayUpdateRules.failureOf(code, fromPlay = true)) }
    }

    @Test
    fun `잠깐의 실패는 다시 해 보라고 알린다`() {
        assertEquals(
            PlayUpdateState.Failed(PlayUpdateRules.CHECK_FAILED_MESSAGE),
            PlayUpdateRules.failureOf(InstallErrorCode.ERROR_INTERNAL_ERROR, fromPlay = true),
        )
        assertEquals(PlayUpdateState.Failed(PlayUpdateRules.CHECK_FAILED_MESSAGE), PlayUpdateRules.failureOf(null, fromPlay = true))
        assertEquals(
            PlayUpdateState.Failed(PlayUpdateRules.NOT_ALLOWED_MESSAGE),
            PlayUpdateRules.failureOf(InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED, fromPlay = true),
        )
        // 스토어 밖에서 설치한 앱의 실패는 Play 에서 설치하라는 안내로 충분하다
        assertEquals(PlayUpdateState.NotFromPlay, PlayUpdateRules.failureOf(null, fromPlay = false))
        // 에뮬레이터에 adb 로 설치한 앱에 Play 가 실제로 준 답(-6). 저장 공간·배터리 탓으로 안내하지 않는다.
        assertEquals(PlayUpdateState.NotFromPlay, PlayUpdateRules.failureOf(InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED, fromPlay = false))
    }

    @Test
    fun `확인에 잠깐 실패해도 이미 알던 새 버전은 지우지 않는다`() {
        val failed = PlayUpdateState.Failed(PlayUpdateRules.CHECK_FAILED_MESSAGE)
        assertEquals(PlayUpdateState.Available(21), PlayUpdateRules.afterFailure(PlayUpdateState.Available(21), failed))
        assertEquals(PlayUpdateState.Downloaded(21), PlayUpdateRules.afterFailure(PlayUpdateState.Downloaded(21), failed))
        assertEquals(failed, PlayUpdateRules.afterFailure(PlayUpdateState.UpToDate, failed))
        assertEquals(PlayUpdateState.NotFromPlay, PlayUpdateRules.afterFailure(PlayUpdateState.Available(21), PlayUpdateState.NotFromPlay))
    }

    @Test
    fun `받는 동안의 진행 상황을 따라간다`() {
        val available = PlayUpdateState.Available(21)
        assertEquals(PlayUpdateState.Downloading(21, 0.5f), PlayUpdateRules.stateOf(available, InstallStatus.DOWNLOADING, 50, 100, 0))
        assertEquals(PlayUpdateState.Downloaded(21), PlayUpdateRules.stateOf(available, InstallStatus.DOWNLOADED, 100, 100, 0))
        assertEquals(
            PlayUpdateState.Available(21),
            PlayUpdateRules.stateOf(PlayUpdateState.Downloading(21, 0.5f), InstallStatus.CANCELED, 50, 100, 0),
        )
        assertEquals(
            PlayUpdateState.Failed(PlayUpdateRules.DOWNLOAD_FAILED_MESSAGE),
            PlayUpdateRules.stateOf(available, InstallStatus.FAILED, 0, 100, InstallErrorCode.ERROR_INTERNAL_ERROR),
        )
        assertEquals(
            PlayUpdateState.Failed(PlayUpdateRules.NOT_ALLOWED_MESSAGE),
            PlayUpdateRules.stateOf(available, InstallStatus.FAILED, 0, 100, InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED),
        )
        assertEquals(PlayUpdateState.Installing, PlayUpdateRules.stateOf(PlayUpdateState.Downloaded(21), InstallStatus.INSTALLING, 0, 0, 0))
        // 새 버전을 모르는 상태에서 온 진행 상황은 따르지 않는다(Play 에 다시 묻는다)
        assertNull(PlayUpdateRules.stateOf(PlayUpdateState.Unknown, InstallStatus.DOWNLOADING, 50, 100, 0))
    }

    @Test
    fun `업데이트 확인 창에서 받기를 고르면 바로 받는 중으로 보인다`() {
        val available = PlayUpdateState.Available(21)
        assertEquals(PlayUpdateState.Downloading(21, null), PlayUpdateRules.afterFlow(available, Activity.RESULT_OK))
        // 진행 상황이 먼저 왔으면 그대로 둔다
        assertNull(PlayUpdateRules.afterFlow(PlayUpdateState.Downloading(21, 0.1f), Activity.RESULT_OK))
        // '나중에' 를 누르면 새 버전은 그대로 있다
        assertNull(PlayUpdateRules.afterFlow(available, Activity.RESULT_CANCELED))
        assertEquals(
            PlayUpdateState.Failed(PlayUpdateRules.START_FAILED_MESSAGE),
            PlayUpdateRules.afterFlow(available, ActivityResult.RESULT_IN_APP_UPDATE_FAILED),
        )
    }

    @Test
    fun `홈과 설정에는 새 버전 이름 없이 알린다`() {
        assertEquals(UpdateNotice.Available(version = null), PlayUpdateState.Available(21).notice)
        assertEquals(UpdateNotice.Downloading(0.5f), PlayUpdateState.Downloading(21, 0.5f).notice)
        assertEquals(UpdateNotice.Downloaded, PlayUpdateState.Downloaded(21).notice)
        listOf(PlayUpdateState.Unknown, PlayUpdateState.UpToDate, PlayUpdateState.Installing, PlayUpdateState.NotFromPlay)
            .forEach { assertNull(it.notice) }
    }
}
