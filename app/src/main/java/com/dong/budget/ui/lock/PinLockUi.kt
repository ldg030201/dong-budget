package com.dong.budget.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dong.budget.R
import com.dong.budget.data.lock.PinLock
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.components.AnimatedErrorText
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.KeypadCorner
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.components.SwitchRow
import com.dong.budget.ui.settings.SettingsGroup
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

// ─────────────────────────────────────────────────────────────────────
// PIN·지문 잠금 화면들. 월급 탭 잠금과 앱 잠금이 글만 바꿔 같이 쓴다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 잠금마다 다른 글
 * @property lockedTitle 잠금 화면 제목. "월급은 잠겨 있어요"
 * @property biometricTitle 지문 창 제목. "월급 열기"
 * @property biometricSubtitle 지문 창 설명
 * @property setupHint PIN 정하기 화면의 안내(두 줄)
 * @property biometricOffer PIN 을 정한 뒤 지문도 쓸지 물을 때의 글
 * @property forgot 'PIN 을 잊었어요' 를 누르면 한 번 더 물을 글. null 이면 묻지 않고 바로 onForgot 을 부른다.
 */
@Immutable
data class PinLockTexts(
    val lockedTitle: String,
    val biometricTitle: String,
    val biometricSubtitle: String,
    val setupHint: String,
    val biometricOffer: String,
    val forgot: ForgotPinTexts?,
)

/** 'PIN 을 잊었어요' 를 눌렀을 때 묻는 창의 글 */
@Immutable
data class ForgotPinTexts(val title: String, val message: String, val confirmLabel: String)

/**
 * 잠긴 화면. PIN 4자리를 누르면 바로 맞춰 본다. 지문을 켜 두었으면 열자마자 지문 창을 띄우고, 취소하면 PIN 으로 연다.
 * @param onUnlock PIN 으로 풀어 본다
 * @param onBiometricSuccess 지문으로 풀었다
 * @param onForgot 'PIN 을 잊었어요'. [PinLockTexts.forgot] 이 있으면 한 번 더 물은 뒤에 부른다.
 */
@Composable
fun PinLockScreen(
    texts: PinLockTexts,
    biometric: Boolean,
    onUnlock: (String) -> PinLock.Attempt,
    onBiometricSuccess: () -> Unit,
    onForgot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // PIN 은 화면을 돌려도 남기지 않는다(저장 상태에 PIN 을 두지 않는다)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askForgot by remember { mutableStateOf(false) }
    val canBiometric = biometric && Biometric.isAvailable(context)
    val promptBiometric =
        rememberAuthPrompt(onSuccess = onBiometricSuccess) { ctx, success ->
            Biometric.prompt(ctx, texts.biometricTitle, texts.biometricSubtitle, success)
        }

    // 지문을 켜 두었으면 열자마자 지문 창을 띄운다
    LaunchedEffect(canBiometric) { if (canBiometric) promptBiometric() }

    val forgot = texts.forgot
    if (askForgot && forgot != null) {
        ConfirmDialog(
            title = forgot.title,
            message = forgot.message,
            confirmLabel = forgot.confirmLabel,
            onConfirm = {
                askForgot = false
                onForgot()
            },
            onDismiss = { askForgot = false },
        )
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = BudgetTheme.colors.textSecondary)
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            Text(
                text = texts.lockedTitle,
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            Text(
                text = if (canBiometric) "PIN 4자리를 누르거나 지문으로 열어 주세요" else "PIN 4자리를 눌러 주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            PinDots(count = pin.length)
            AnimatedErrorText(text = error, modifier = Modifier.padding(top = BudgetTheme.spacing.itemGap))
            BudgetTextButton(
                text = "PIN을 잊었어요",
                onClick = { if (forgot != null) askForgot = true else onForgot() },
                color = BudgetTheme.colors.textSecondary,
            )
        }
        PinPad(
            onDigit = { digit ->
                if (pin.length >= PinLock.PIN_LENGTH) return@PinPad
                pin += digit
                if (pin.length == PinLock.PIN_LENGTH) {
                    error =
                        when (val attempt = onUnlock(pin)) {
                            PinLock.Attempt.Ok -> null
                            is PinLock.Attempt.Wrong -> "PIN이 맞지 않아요 (${attempt.remaining}번 남음)"
                            is PinLock.Attempt.Blocked -> "너무 많이 틀렸어요. ${attempt.seconds}초 뒤에 다시 해 주세요"
                        }
                    pin = ""
                }
            },
            onDelete = { pin = pin.dropLast(1) },
            onClear = { pin = "" },
            corner = if (canBiometric) KeypadCorner.Action(R.drawable.ic_fingerprint, "지문으로 열기", promptBiometric) else KeypadCorner.Empty,
        )
    }
}

/**
 * PIN 정하기. 4자리를 두 번 눌러 맞으면 [onDone]. 지문을 쓸 수 있는 폰이면 마지막에 지문으로도 열지 묻는다.
 * @param onDone 정한 PIN 과 지문을 쓸지
 */
@Composable
fun PinSetupScreen(texts: PinLockTexts, onDone: (pin: String, biometric: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // 두 번 맞게 누른 PIN. 지문을 물어보는 동안 들고 있다.
    var confirmed by remember { mutableStateOf<String?>(null) }
    val canBiometric = remember { Biometric.isAvailable(context) }

    val done = confirmed
    if (done != null && canBiometric) {
        ConfirmDialog(
            title = "지문으로도 열까요?",
            message = texts.biometricOffer,
            confirmLabel = "지문도 쓰기",
            dismissLabel = "PIN만 쓰기",
            destructive = false,
            onConfirm = { onDone(done, true) },
            onDismiss = { onDone(done, false) },
        )
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (first == null) "PIN 4자리를 정해 주세요" else "한 번 더 눌러 주세요",
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            Text(
                text = texts.setupHint,
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            PinDots(count = pin.length)
            AnimatedErrorText(text = error, modifier = Modifier.padding(top = BudgetTheme.spacing.itemGap))
        }
        PinPad(
            onDigit = { digit ->
                if (pin.length >= PinLock.PIN_LENGTH || confirmed != null) return@PinPad
                pin += digit
                if (pin.length < PinLock.PIN_LENGTH) return@PinPad
                val entered = pin
                pin = ""
                val previous = first
                when {
                    previous == null -> {
                        first = entered
                        error = null
                    }

                    previous == entered -> if (canBiometric) confirmed = entered else onDone(entered, false)

                    else -> {
                        first = null
                        error = "두 번 누른 PIN이 달라요. 처음부터 다시 정해 주세요"
                    }
                }
            },
            onDelete = { pin = pin.dropLast(1) },
            onClear = { pin = "" },
            corner = KeypadCorner.Empty,
        )
    }
}

/**
 * 설정의 잠금 묶음이 부르는 일
 * @property enabled PIN 으로 잠가 두었는지
 * @property biometricAvailable 이 폰에서 지문을 쓸 수 있는지(지문을 등록해 두었는지)
 * @property onEnable 잠금을 켠다. PIN 을 정하러 간다.
 * @property onDisable 잠금을 끈다(한 번 더 물은 뒤)
 */
@Immutable
data class LockControls(
    val enabled: Boolean = false,
    val biometric: Boolean = false,
    val biometricAvailable: Boolean = false,
    val onEnable: () -> Unit = {},
    val onDisable: () -> Unit = {},
    val onChangePin: () -> Unit = {},
    val onBiometricChange: (Boolean) -> Unit = {},
    /** 2중 잠금 스위치 값. null 이면 줄을 두지 않는다(앱 잠금 묶음). */
    val double: Boolean? = null,
    /** 앱 잠금이 켜져 있는지. 이 잠금과 앱 잠금이 둘 다 켜져 있어야 2중 잠금을 고를 수 있다. */
    val appLockOn: Boolean = false,
    val onDoubleChange: (Boolean) -> Unit = {},
)

/**
 * 설정의 잠금 묶음 글
 * @property switchTitle 잠금 스위치 이름. "월급 잠그기"
 * @property switchDescription 잠금 스위치 아래 설명
 * @property biometricDescription 지문 스위치 아래 설명(지문을 쓸 수 있을 때)
 * @property offMessage 잠금을 끌 때 묻는 글
 */
@Immutable
data class LockGroupTexts(val switchTitle: String, val switchDescription: String, val biometricDescription: String, val offMessage: String)

/** 설정의 잠금 묶음. 잠금 켜기(PIN 정하러 감)·PIN 바꾸기·지문으로 열기. 잠금을 끌 때는 한 번 더 묻는다. */
@Composable
fun LockGroup(controls: LockControls, texts: LockGroupTexts, modifier: Modifier = Modifier) {
    var askOff by rememberSaveable { mutableStateOf(false) }
    if (askOff) {
        ConfirmDialog(
            title = "잠금을 끌까요?",
            message = texts.offMessage,
            confirmLabel = "잠금 끄기",
            onConfirm = {
                askOff = false
                controls.onDisable()
            },
            onDismiss = { askOff = false },
        )
    }
    SettingsGroup("잠금", modifier) {
        SwitchRow(
            title = texts.switchTitle,
            description = texts.switchDescription,
            checked = controls.enabled,
            onCheckedChange = { on -> if (on) controls.onEnable() else askOff = true },
        )
        controls.double?.let { double ->
            val available = controls.enabled && controls.appLockOn
            SwitchRow(
                title = "2중 잠금",
                description =
                if (controls.appLockOn) {
                    "앱 잠금을 풀고 월급 탭을 열 때 월급 PIN을 한 번 더 물어요. 끄면 앱 잠금만으로 열려요"
                } else {
                    "앱 잠금(설정 > 잠금)도 켜면 월급을 한 번 더 잠글지 고를 수 있어요"
                },
                // 앱 잠금이 꺼져 있으면 월급 잠금 하나뿐이라 늘 따로 묻는다
                checked = if (controls.appLockOn) double else true,
                onCheckedChange = controls.onDoubleChange,
                enabled = available,
            )
        }
        ActionRow(title = "PIN 바꾸기", onClick = controls.onChangePin, enabled = controls.enabled, opensScreen = true)
        SwitchRow(
            title = "지문으로 열기",
            description = if (controls.biometricAvailable) texts.biometricDescription else "이 폰에 지문이 등록돼 있지 않아요",
            checked = controls.biometric,
            onCheckedChange = controls.onBiometricChange,
            enabled = controls.enabled && controls.biometricAvailable,
        )
    }
}

/**
 * 앱 잠금과 월급 잠금이 둘 다 켜졌을 때 월급을 한 번 더 잠글지(2중 잠금) 묻는다. 둘 중 하나를 꼭 고른다.
 * 둘 중 하나의 PIN 을 막 정했을 때(다른 하나는 이미 켜져 있을 때) 띄운다.
 * @param onChoose 2중 잠금을 쓸지
 */
@Composable
fun DoubleLockQuestion(onChoose: (double: Boolean) -> Unit) {
    ConfirmDialog(
        title = "월급 탭도 한 번 더 잠글까요?",
        message =
        "앱 잠금과 월급 잠금이 둘 다 켜져 있어요.\n" +
            "2중 잠금을 쓰면 앱을 연 뒤 월급 탭을 열 때 월급 PIN을 한 번 더 물어요. 앱 잠금만 쓰면 앱을 열 때 한 번만 물어요.\n\n" +
            "월급 설정 > 잠금에서 언제든 바꿀 수 있어요.",
        confirmLabel = "2중 잠금 쓰기",
        dismissLabel = "앱 잠금만 쓰기",
        destructive = false,
        cancelable = false,
        onConfirm = { onChoose(true) },
        onDismiss = { onChoose(false) },
    )
}

/** 누른 자리 수만큼 채운 점 네 개. 화면 읽기는 '4자리 중 2자리' 로 읽는다. */
@Composable
private fun PinDots(count: Int) {
    val spoken = "PIN ${PinLock.PIN_LENGTH}자리 중 ${count}자리 누름"
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionPadding),
    ) {
        repeat(PinLock.PIN_LENGTH) { index ->
            val filled = index < count
            val fill by animateColorAsState(
                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                Motion.quick(),
                label = "pinDot",
            )
            Box(
                modifier =
                Modifier
                    .size(PIN_DOT)
                    .background(fill, CircleShape)
                    .border(
                        BudgetTheme.size.underline,
                        if (filled) MaterialTheme.colorScheme.primary else BudgetTheme.colors.divider,
                        CircleShape,
                    ),
            )
        }
    }
}

/** PIN 키패드. 금액 키패드와 같은 모양이고 왼쪽 아래 칸만 다르다(지문이나 빈칸). */
@Composable
private fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, corner: KeypadCorner) {
    NumberKeypad(
        onDigit = onDigit,
        onDelete = onDelete,
        onClear = onClear,
        corner = corner,
        modifier =
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(bottom = BudgetTheme.spacing.sectionPadding),
    )
}

/**
 * 지문(또는 기기 화면 잠금) 창을 띄우는 함수. 화면에서 사라지면 떠 있던 창을 닫는다.
 * 맞으면 [onSuccess]. 취소하면 아무 일 없이 그 화면에 남는다.
 * @param launch 창을 띄우고 닫을 때 쓰는 신호를 준다. 띄울 수 없으면 null
 */
@Composable
internal fun rememberAuthPrompt(onSuccess: () -> Unit, launch: (Context, () -> Unit) -> CancellationSignal?): () -> Unit {
    val context = LocalContext.current
    val latestOnSuccess by rememberUpdatedState(onSuccess)
    val latestLaunch by rememberUpdatedState(launch)
    val cancel = remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(Unit) { onDispose { cancel.value?.cancel() } }
    return remember(context) {
        {
            cancel.value?.cancel()
            cancel.value = latestLaunch(context) { latestOnSuccess() }
        }
    }
}

/** 기기 지문(생체 인증)과 화면 잠금 확인. 앱에 라이브러리를 더하지 않고 안드로이드에 들어 있는 것을 쓴다. */
internal object Biometric {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** 지문을 쓸 수 있는지. 지문을 등록해 두지 않았으면 쓸 수 없다. */
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** 기기에 화면 잠금(PIN·패턴·비밀번호)이 걸려 있는지. 없으면 기기 화면 잠금으로 본인을 확인할 수 없다. */
    fun isDeviceSecure(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** 지문 창을 띄운다. 'PIN으로 열기' 를 누르면 그냥 닫힌다. 닫을 때 쓰는 신호를 돌려준다. 띄울 수 없으면 null */
    fun prompt(context: Context, title: String, subtitle: String, onSuccess: () -> Unit): CancellationSignal? {
        if (!isAvailable(context)) return null
        val executor = ContextCompat.getMainExecutor(context)
        val prompt =
            BiometricPrompt
                .Builder(context)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setNegativeButton("PIN으로 열기", executor) { _, _ -> }
                .build()
        return authenticate(context, prompt, onSuccess)
    }

    /** 기기 화면 잠금(PIN·패턴·비밀번호)으로 본인인지 확인한다. 앱 PIN 을 잊었을 때 쓴다. 띄울 수 없으면 null */
    fun promptDeviceCredential(context: Context, title: String, subtitle: String, onSuccess: () -> Unit): CancellationSignal? {
        if (!isDeviceSecure(context)) return null
        val prompt =
            BiometricPrompt
                .Builder(context)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        return authenticate(context, prompt, onSuccess)
    }

    private fun authenticate(context: Context, prompt: BiometricPrompt, onSuccess: () -> Unit): CancellationSignal? {
        val signal = CancellationSignal()
        return runCatching {
            prompt.authenticate(
                signal,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                },
            )
            signal
        }.getOrNull()
    }
}

/** 지문을 쓸 수 있는지 설정 화면이 본다 */
fun biometricAvailable(context: Context): Boolean = Biometric.isAvailable(context)

/** 최근 앱 미리보기를 가리고 있는 곳의 수. 월급 화면과 앱 잠금이 겹쳐 가릴 수 있어, 모두 그만둘 때만 되돌린다. */
private var recentsHiders = 0

/**
 * 이 화면이 보이는 동안 최근 앱 화면에 이 앱의 모습을 남기지 않는다([active] 일 때, Android 13 부터).
 * 잠가 둔 화면에서 바로 앱을 나가면 최근 앱 목록의 미리보기에 내용이 그대로 찍혀, 잠가 두어도 목록만 넘기면 보이기 때문이다.
 * 화면 캡처까지 막는 FLAG_SECURE 는 쓰지 않는다. 가리던 곳이 모두 사라지면 되돌린다.
 */
@Composable
fun HideFromRecents(active: Boolean) {
    val activity = LocalActivity.current ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    DisposableEffect(activity, active) {
        if (active) {
            recentsHiders++
            activity.setRecentsScreenshotEnabled(false)
        }
        onDispose {
            if (active) {
                recentsHiders--
                if (recentsHiders == 0) activity.setRecentsScreenshotEnabled(true)
            }
        }
    }
}

/** PIN 점 하나의 지름 */
private val PIN_DOT = 14.dp
