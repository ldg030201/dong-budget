package com.dong.budget.ui.salary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dong.budget.data.lock.PinLock
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.SwitchRow
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.lock.ForgotPinTexts
import com.dong.budget.ui.lock.LockGroupTexts
import com.dong.budget.ui.lock.PinLockScreen
import com.dong.budget.ui.lock.PinLockTexts
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 월급 탭의 문. 처음이면 안내([SalaryIntro]), 잠겨 있으면 잠금 화면([SalaryLockScreen]), 아니면 [content] 를 그린다.
 * 안내와 잠금 화면에는 톱니 없는 '월급' 머리를 둔다.
 *
 * @param onIntroConfirm 안내를 확인했다. 잠금을 켰으면 PIN 을 정하러 간다.
 */
@Composable
fun SalaryTabGate(
    lock: PinLock,
    state: PinLock.State,
    open: Boolean,
    onIntroConfirm: (lock: Boolean) -> Unit,
    onForgot: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        !state.introDone ->
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                SalaryHeader(onOpenSettings = null)
                SalaryIntro(onConfirm = onIntroConfirm)
            }

        !open ->
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                SalaryHeader(onOpenSettings = null)
                SalaryLockScreen(lock = lock, state = state, onForgot = onForgot)
            }

        else -> content()
    }
}

// ─────────────────────────────────────────────────────────────────────
// 월급 탭 잠금 화면들. 처음 안내(연봉이 드러날 수 있어요), PIN 으로 풀기, PIN 정하기.
// PIN·지문 화면 자체는 앱 잠금과 같이 쓴다(ui/lock/PinLockUi.kt).
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

/** 월급 탭 잠금의 글 */
internal val SALARY_LOCK_TEXTS =
    PinLockTexts(
        lockedTitle = "월급은 잠겨 있어요",
        biometricTitle = "월급 열기",
        biometricSubtitle = "지문으로 월급 탭을 열어요",
        setupHint = "월급 탭을 열 때 물어요\n잊으면 월급 설정부터 다시 해야 해요",
        biometricOffer = "월급 탭을 열 때 지문 창이 바로 떠요. 지문이 안 되면 PIN으로 열 수 있어요.",
        forgot =
        ForgotPinTexts(
            title = "PIN을 잊으셨나요?",
            message = "PIN 없이 월급을 볼 수 없게, 적어 둔 월급 설정과 잠금을 함께 지우고 처음부터 다시 정해요. 가계부에 등록한 거래는 그대로예요.",
            confirmLabel = "지우고 다시 정하기",
        ),
    )

/** 월급 설정의 잠금 묶음 글 */
internal val SALARY_LOCK_GROUP_TEXTS =
    LockGroupTexts(
        switchTitle = "월급 잠그기",
        switchDescription = "월급 탭을 열 때 PIN 4자리를 물어요. 앱을 나갔다 오면 다시 잠겨요",
        biometricDescription = "월급 탭을 열 때 지문 창이 바로 떠요",
        offMessage = "월급 탭을 열 때 PIN이나 지문을 묻지 않아요. 폰을 다른 사람이 보면 연봉이 드러날 수 있어요.",
    )

/**
 * 잠긴 월급. [lock] 의 PIN 4자리나 지문으로 연다.
 * @param onForgot 'PIN 을 잊었어요'. 한 번 더 물은 뒤 월급 설정과 잠금을 지운다.
 */
@Composable
fun SalaryLockScreen(lock: PinLock, state: PinLock.State, onForgot: () -> Unit, modifier: Modifier = Modifier) =
    PinLockScreen(SALARY_LOCK_TEXTS, state.biometric, lock::tryUnlock, lock::unlockWithBiometric, onForgot, modifier)
