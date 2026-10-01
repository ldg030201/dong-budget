package com.dong.budget.ui.lock

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.dong.budget.data.lock.PinLock
import com.dong.budget.startFirst
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.permission.appInfoIntent

/** 앱 잠금의 글 */
internal val APP_LOCK_TEXTS =
    PinLockTexts(
        lockedTitle = "동계부가 잠겨 있어요",
        biometricTitle = "동계부 열기",
        biometricSubtitle = "지문으로 동계부를 열어요",
        setupHint = "앱을 열 때 물어요\n잊으면 기기 화면 잠금으로 확인한 뒤 끌 수 있어요",
        biometricOffer = "앱을 열 때 지문 창이 바로 떠요. 지문이 안 되면 PIN으로 열 수 있어요.",
        // 잊었으면 묻지 않고 바로 기기 화면 잠금으로 확인한다(AppLockScreen)
        forgot = null,
    )

/** 설정의 잠금 묶음 글(앱 잠금) */
val APP_LOCK_GROUP_TEXTS =
    LockGroupTexts(
        switchTitle = "앱 잠금",
        switchDescription = "앱을 열 때 PIN 4자리를 물어요. 앱을 나갔다 오면 다시 잠겨요",
        biometricDescription = "앱을 열 때 지문 창이 바로 떠요",
        offMessage = "앱을 열 때 PIN이나 지문을 묻지 않아요. 폰을 다른 사람이 보면 가계부가 그대로 보여요.",
    )

/** PIN 을 잊어 잠금을 껐을 때 */
private const val FORGOT_DONE_MESSAGE = "앱 잠금을 껐어요. 설정 > 잠금에서 다시 켤 수 있어요"

/**
 * 앱 위를 통째로 덮는 잠금 화면. 아래 화면은 그대로 두고(보던 화면·쓰던 등록창이 풀린 뒤 그대로 남는다) 누름을 모두 받는다.
 * 뒤로 가기는 잠긴 채로 앱을 뒤로 보낸다.
 *
 * 'PIN 을 잊었어요' 는 기기 화면 잠금(PIN·패턴·비밀번호)으로 본인인지 확인한 뒤 앱 잠금을 끈다.
 * 기기에 화면 잠금이 없으면 확인할 길이 없어, 앱 데이터를 지우는 길(거래도 지워진다)만 알려 준다.
 *
 * @param onForgotVerified 기기 화면 잠금으로 확인했다. 앱 잠금을 끈다.
 */
@Composable
fun AppLockScreen(lock: PinLock, state: PinLock.State, onForgotVerified: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    BackHandler { activity?.moveTaskToBack(true) }
    var noScreenLock by remember { mutableStateOf(false) }
    val verifyOwner =
        rememberAuthPrompt(
            onSuccess = {
                onForgotVerified()
                Toast.makeText(context, FORGOT_DONE_MESSAGE, Toast.LENGTH_LONG).show()
            },
        ) { ctx, success -> Biometric.promptDeviceCredential(ctx, "앱 잠금 끄기", "기기 화면 잠금으로 본인인지 확인해요", success) }

    if (noScreenLock) {
        ConfirmDialog(
            title = "PIN을 잊으셨나요?",
            message =
            "이 폰에는 화면 잠금이 없어서 본인인지 확인할 수 없어요.\n" +
                "앱 정보 > 저장공간에서 데이터를 지우면 잠금이 풀려요. 거래도 같이 지워지니 다운로드 폴더의 백업 파일로 되살려 주세요.",
            confirmLabel = "앱 정보 열기",
            dismissLabel = "닫기",
            destructive = false,
            onConfirm = {
                noScreenLock = false
                context.startFirst(listOf(appInfoIntent(context)))
            },
            onDismiss = { noScreenLock = false },
        )
    }

    // 아래 화면으로 누름이 새지 않게 판 전체가 누름을 받는다
    Surface(modifier = modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            PinLockScreen(
                texts = APP_LOCK_TEXTS,
                biometric = state.biometric,
                onUnlock = lock::tryUnlock,
                onBiometricSuccess = lock::unlockWithBiometric,
                onForgot = { if (Biometric.isDeviceSecure(context)) verifyOwner() else noScreenLock = true },
            )
        }
    }
}
