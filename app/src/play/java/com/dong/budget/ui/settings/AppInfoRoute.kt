package com.dong.budget.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dong.budget.BuildConfig
import com.dong.budget.data.update.PlayUpdateRules
import com.dong.budget.data.update.PlayUpdateState
import com.dong.budget.data.update.StoreUpdates
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/**
 * 앱 정보(Play 배포). Google Play 에 새 버전이 있는지 묻고, 있으면 Play 가 받게 한다.
 * 받는 동안 앱을 계속 쓸 수 있고, 다 받으면 '다시 시작하기' 로 새 버전이 된다.
 * Play 는 설치하기 전에 새 버전의 이름과 바뀐 점을 알려 주지 않는다. 바뀐 점은 업데이트한 뒤 '바뀐 점'(패치노트)에서 본다.
 */
@Composable
fun AppInfoRoute(updates: StoreUpdates, onBack: () -> Unit, onOpenPatchNotes: () -> Unit) {
    val state by updates.checker.state.collectAsStateWithLifecycle()
    val checking by updates.checker.checking.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            updates.onUpdateFlowResult(result.resultCode)
        }
    AppInfoScreen(
        currentVersion = BuildConfig.VERSION_NAME,
        // 받는 중이나 설치하는 중에는 다시 확인할 것이 없다. 진행 상황은 Play 가 알려 준다.
        canCheckUpdate = !checking && state !is PlayUpdateState.Downloading && state !is PlayUpdateState.Installing,
        onCheckUpdate = updates::check,
        onBack = onBack,
        onOpenPatchNotes = onOpenPatchNotes,
    ) {
        PlayUpdateSection(
            // 묻는 동안은 지난 상태 대신 '확인하고 있어요' 를 보여 준다
            state = if (checking) null else state,
            onUpdate = { updates.startUpdate(launcher) },
            onRestart = updates::completeUpdate,
        )
    }
}

/** @param state 보여 줄 상태. Play 에 묻는 중이면 null */
@Composable
private fun PlayUpdateSection(state: PlayUpdateState?, onUpdate: () -> Unit, onRestart: () -> Unit) {
    // 단계가 바뀌면 겹쳐 바뀌고 칸 높이도 한 번에 튀지 않는다. 같은 단계 안의 변화(받은 양)는 그 자리에서 바뀐다.
    AnimatedContent(
        targetState = state,
        contentKey = { it?.let { shown -> shown::class } },
        transitionSpec = {
            (fadeIn(Motion.standard()) togetherWith fadeOut(Motion.quick())).using(SizeTransform { _, _ -> Motion.standard() })
        },
        label = "playUpdateSection",
    ) { shown ->
        Column { PlayUpdateStep(shown, onUpdate, onRestart) }
    }
}

@Composable
private fun PlayUpdateStep(state: PlayUpdateState?, onUpdate: () -> Unit, onRestart: () -> Unit) {
    when (state) {
        null -> UpdateStatusText("새 버전이 있는지 확인하고 있어요")

        // 확인은 버전 옆 버튼으로 한다
        PlayUpdateState.Unknown -> Unit

        PlayUpdateState.UpToDate -> UpdateStatusText("최신 버전을 쓰고 있어요")

        is PlayUpdateState.Available -> {
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            UpdateTitle("새 버전이 있어요")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            // 앱 안에서 받을 수 없는 업데이트는 Play 스토어의 동계부 화면을 연다
            BudgetPrimaryButton(text = if (state.flexibleAllowed) "업데이트하기" else "Play 스토어에서 업데이트하기", onClick = onUpdate)
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            HintText(
                if (state.flexibleAllowed) {
                    "Play 스토어가 받아요. 받는 동안에도 앱을 쓸 수 있고, 다 받으면 알려 드려요."
                } else {
                    "지금은 앱 안에서 받을 수 없어요. Play 스토어에서 업데이트해 주세요."
                },
            )
        }

        is PlayUpdateState.Downloading -> {
            val percent = state.progress?.let { " (${(it * PERCENT).toInt()}%)" }.orEmpty()
            UpdateStatusText("새 버전을 받고 있어요$percent")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            val progress = state.progress
            if (progress == null) {
                // 받기 전이라 크기를 아직 모른다
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.background,
                )
            } else {
                // 받은 양이 띄엄띄엄 알려져도 막대는 이어서 차오른다
                val shown by animateFloatAsState(progress, ProgressIndicatorDefaults.ProgressAnimationSpec, label = "playDownloadProgress")
                LinearProgressIndicator(
                    progress = { shown },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    // 회색 판 위라 빈 트랙은 화면 바탕색으로 둔다
                    trackColor = MaterialTheme.colorScheme.background,
                )
            }
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            HintText("받는 동안에도 앱을 쓸 수 있어요. 다 받으면 알려 드려요.")
        }

        is PlayUpdateState.Downloaded -> {
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            UpdateTitle("새 버전을 다 받았어요")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetPrimaryButton(text = "다시 시작하기", onClick = onRestart)
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            // 다시 시작하면 Play 가 앱을 닫고 새 버전으로 연다. 미리 알려 주지 않으면 앱이 죽은 줄 안다.
            HintText("앱이 잠깐 닫혔다가 새 버전으로 다시 열려요. 가계부 기록은 그대로예요.")
        }

        PlayUpdateState.Installing -> UpdateStatusText("새 버전을 설치하고 있어요. 곧 앱이 다시 열려요.")

        PlayUpdateState.NotFromPlay -> UpdateStatusText(PlayUpdateRules.NOT_FROM_PLAY_MESSAGE)

        is PlayUpdateState.Failed -> UpdateStatusText(state.reason)
    }
}

@Composable
private fun UpdateTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = BudgetTheme.colors.textPrimary)
}

private const val PERCENT = 100
