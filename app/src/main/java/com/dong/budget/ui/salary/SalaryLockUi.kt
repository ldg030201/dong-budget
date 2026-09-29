package com.dong.budget.ui.salary

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.dong.budget.data.salary.SalaryLock
import com.dong.budget.ui.components.AnimatedErrorText
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.KeypadCorner
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.components.SwitchRow
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/**
 * 월급 탭의 문. 처음이면 안내([SalaryIntro]), 잠겨 있으면 잠금 화면([SalaryLockScreen]), 아니면 [content] 를 그린다.
 * 안내와 잠금 화면에는 톱니 없는 '월급' 머리를 둔다.
 *
 * @param onIntroConfirm 안내를 확인했다. 잠금을 켰으면 PIN 을 정하러 간다.
 */
@Composable
fun SalaryTabGate(
    state: SalaryLock.State,
    unlocked: Boolean,
    onIntroConfirm: (lock: Boolean) -> Unit,
    onUnlock: (String) -> SalaryLock.Attempt,
    onBiometricSuccess: () -> Unit,
    onForgot: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        !state.introDone ->
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                SalaryHeader(onOpenSettings = null)
                SalaryIntro(onConfirm = onIntroConfirm)
            }

        state.enabled && !unlocked ->
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                SalaryHeader(onOpenSettings = null)
                SalaryLockScreen(state.biometric, onUnlock, onBiometricSuccess, onForgot)
            }

        else -> content()
    }
}

// ─────────────────────────────────────────────────────────────────────
// 월급 탭 잠금 화면들. 처음 안내(연봉이 드러날 수 있어요), PIN 으로 풀기, PIN 정하기.
// ─────────────────────────────────────────────────────────────────────

/**
 * 월급 탭을 처음 열 때의 안내. 연봉이 남에게 보일 수 있고, 계약서에 따라 연봉을 알리면 안 될 수 있다는 것을 알리고,
 * 잠금을 켤지 고르게 한다(처음에는 켜짐). '확인' 을 눌러야 월급 탭을 쓸 수 있다.
 * @param onConfirm 확인을 눌렀다. 잠금을 켰으면 PIN 을 정하러 간다.
 */
@Composable
internal fun SalaryIntro(onConfirm: (lock: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var lock by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        Box(
            modifier = Modifier.size(BudgetTheme.size.badgeLarge).background(BudgetTheme.colors.sectionBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = BudgetTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        Text(
            text = "월급은 나만 볼 수 있게 해 두세요",
            style = MaterialTheme.typography.titleLarge,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        Column(modifier = Modifier.fillMaxWidth().sectionBlock(), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.itemGap)) {
            NoticeLine("이 화면에는 연봉·월급과 번 돈이 그대로 나와요. 폰을 다른 사람이 보면 연봉이 드러날 수 있어요.")
            NoticeLine("회사에 따라 연봉을 다른 사람에게 알리지 않기로 계약서에 적혀 있을 수 있어요. 드러나면 곤란해질 수 있으니 조심해 주세요.")
            NoticeLine("잠금을 켜 두면 월급 탭을 열 때마다 PIN 4자리나 지문을 물어요. 앱을 나갔다 오면 다시 잠겨요.")
        }
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        SwitchRow(
            title = "잠금 설정하기",
            description = "켜면 확인을 누른 뒤 PIN 4자리를 정해요. 나중에 월급 설정에서 바꿀 수 있어요",
            checked = lock,
            onCheckedChange = { lock = it },
        )
        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
        BudgetPrimaryButton(text = "확인", onClick = { onConfirm(lock) })
        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
    }
}

@Composable
private fun NoticeLine(text: String) {
    Row {
        Text(text = "·", style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
        Spacer(Modifier.size(BudgetTheme.spacing.inlineGap))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textPrimary)
    }
}

/**
 * 잠긴 월급. PIN 4자리를 누르면 바로 맞춰 본다. 지문을 켜 두었으면 열자마자 지문 창을 띄우고, 취소하면 PIN 으로 연다.
 * @param onUnlock PIN 으로 풀어 본다
 * @param onBiometricSuccess 지문으로 풀었다
 * @param onForgot 'PIN 을 잊었어요'. 월급 설정과 잠금을 지운다(한 번 더 묻는다).
 */
@Composable
fun SalaryLockScreen(
    biometric: Boolean,
    onUnlock: (String) -> SalaryLock.Attempt,
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
    val promptBiometric = rememberBiometricPrompt(onSuccess = onBiometricSuccess)

    // 지문을 켜 두었으면 열자마자 지문 창을 띄운다
    LaunchedEffect(canBiometric) { if (canBiometric) promptBiometric() }

    if (askForgot) {
        ConfirmDialog(
            title = "PIN을 잊으셨나요?",
            message = "PIN 없이 월급을 볼 수 없게, 적어 둔 월급 설정과 잠금을 함께 지우고 처음부터 다시 정해요. 가계부에 등록한 거래는 그대로예요.",
            confirmLabel = "지우고 다시 정하기",
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
                text = "월급은 잠겨 있어요",
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
            BudgetTextButton(text = "PIN을 잊었어요", onClick = { askForgot = true }, color = BudgetTheme.colors.textSecondary)
        }
        PinPad(
            onDigit = { digit ->
                if (pin.length >= SalaryLock.PIN_LENGTH) return@PinPad
                pin += digit
                if (pin.length == SalaryLock.PIN_LENGTH) {
                    error =
                        when (val attempt = onUnlock(pin)) {
                            SalaryLock.Attempt.Ok -> null
                            is SalaryLock.Attempt.Wrong -> "PIN이 맞지 않아요 (${attempt.remaining}번 남음)"
                            is SalaryLock.Attempt.Blocked -> "너무 많이 틀렸어요. ${attempt.seconds}초 뒤에 다시 해 주세요"
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
fun SalaryPinSetup(onDone: (pin: String, biometric: Boolean) -> Unit, modifier: Modifier = Modifier) {
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
            message = "월급 탭을 열 때 지문 창이 바로 떠요. 지문이 안 되면 PIN으로 열 수 있어요.",
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
                text = "월급 탭을 열 때 물어요\n잊으면 월급 설정부터 다시 해야 해요",
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
                if (pin.length >= SalaryLock.PIN_LENGTH || confirmed != null) return@PinPad
                pin += digit
                if (pin.length < SalaryLock.PIN_LENGTH) return@PinPad
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

/** 누른 자리 수만큼 채운 점 네 개. 화면 읽기는 '4자리 중 2자리' 로 읽는다. */
@Composable
private fun PinDots(count: Int) {
    val spoken = "PIN ${SalaryLock.PIN_LENGTH}자리 중 ${count}자리 누름"
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.sectionPadding),
    ) {
        repeat(SalaryLock.PIN_LENGTH) { index ->
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
 * 지문 창을 띄우는 함수. 화면에서 사라지면 떠 있던 지문 창을 닫는다.
 * 지문이 맞으면 [onSuccess]. 취소하거나 'PIN 으로 열기' 를 누르면 아무 일 없이 PIN 화면에 남는다.
 */
@Composable
private fun rememberBiometricPrompt(onSuccess: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnSuccess by rememberUpdatedState(onSuccess)
    val cancel = remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(Unit) { onDispose { cancel.value?.cancel() } }
    return remember(context) {
        {
            cancel.value?.cancel()
            cancel.value = Biometric.prompt(context) { latestOnSuccess() }
        }
    }
}

/** 기기 지문(생체 인증). 앱에 라이브러리를 더하지 않고 안드로이드에 들어 있는 것을 쓴다. */
private object Biometric {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** 지문을 쓸 수 있는지. 지문을 등록해 두지 않았으면 쓸 수 없다. */
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** 지문 창을 띄운다. 닫을 때 쓰는 신호를 돌려준다. 띄울 수 없으면 null */
    fun prompt(context: Context, onSuccess: () -> Unit): CancellationSignal? {
        if (!isAvailable(context)) return null
        val executor = ContextCompat.getMainExecutor(context)
        val prompt =
            BiometricPrompt
                .Builder(context)
                .setTitle("월급 열기")
                .setSubtitle("지문으로 월급 탭을 열어요")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setNegativeButton("PIN으로 열기", executor) { _, _ -> }
                .build()
        val signal = CancellationSignal()
        return runCatching {
            prompt.authenticate(
                signal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                },
            )
            signal
        }.getOrNull()
    }
}

/**
 * 월급이 보이는 동안 최근 앱 화면에 이 앱의 모습을 남기지 않는다([active] 일 때, Android 13 부터).
 * 월급 탭에서 바로 앱을 나가면 최근 앱 목록의 미리보기에 번 돈이 그대로 찍혀, 잠가 두어도 목록만 넘기면 보이기 때문이다.
 * 화면 캡처까지 막는 FLAG_SECURE 는 쓰지 않는다. 월급 화면이 사라지면 되돌린다.
 */
@Composable
fun HideFromRecents(active: Boolean) {
    val activity = LocalActivity.current ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    DisposableEffect(activity, active) {
        if (active) activity.setRecentsScreenshotEnabled(false)
        onDispose { if (active) activity.setRecentsScreenshotEnabled(true) }
    }
}

/** 잠금을 쓸 수 있는지 설정 화면이 본다 */
fun biometricAvailable(context: Context): Boolean = Biometric.isAvailable(context)

/** PIN 점 하나의 지름 */
private val PIN_DOT = 14.dp
