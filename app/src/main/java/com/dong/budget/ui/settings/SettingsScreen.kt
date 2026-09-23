package com.dong.budget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.ui.components.BudgetChip
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetSecondaryButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.theme.BudgetTheme

private const val BYTES_PER_MB = 1024.0 * 1024.0

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    currentVersion: String,
    updateState: UpdateUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "설정")

            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                SectionTitle("화면 테마")
                Row(horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap)) {
                    ThemeMode.entries.forEach { mode ->
                        BudgetChip(
                            label = mode.label(),
                            selected = mode == themeMode,
                            onClick = { onThemeModeChange(mode) },
                        )
                    }
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

                SectionTitle("앱 정보")
                Text(
                    text = "현재 버전 $currentVersion",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BudgetTheme.colors.textPrimary,
                )

                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))

                UpdateSection(
                    state = updateState,
                    onCheckUpdate = onCheckUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                )

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }
        }
    }
}

@Composable
private fun UpdateSection(state: UpdateUiState, onCheckUpdate: () -> Unit, onDownloadUpdate: () -> Unit) {
    when (state) {
        UpdateUiState.Idle ->
            BudgetSecondaryButton(text = "업데이트 확인", onClick = onCheckUpdate)

        UpdateUiState.Checking ->
            StatusText("새 버전이 있는지 확인하고 있어요")

        UpdateUiState.UpToDate -> {
            StatusText("최신 버전을 쓰고 있어요")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetSecondaryButton(text = "다시 확인", onClick = onCheckUpdate)
        }

        is UpdateUiState.Available -> {
            Text(
                text = "새 버전 ${state.version} 이 있어요",
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
            )
            // 버튼을 바뀐 점보다 먼저 둔다.
            // 글이 길어져도 버튼이 화면 밖으로 밀려나지 않게 하기 위함이다.
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetPrimaryButton(
                text = "내려받고 설치 (${state.sizeBytes.toMegabytes()}MB)",
                onClick = onDownloadUpdate,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            // 앱이 자기 자신을 업데이트하면 안드로이드가 실행 중인 앱을 종료한다.
            // 미리 알려주지 않으면 앱이 죽은 줄 안다.
            Text(
                text = "설치가 끝나면 앱이 닫혀요. 다시 열어주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
            )
            if (state.notes.isNotEmpty()) {
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
                Text(
                    text = "바뀐 점",
                    style = MaterialTheme.typography.labelMedium,
                    color = BudgetTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
                Text(
                    text = state.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textPrimary,
                )
            }
        }

        is UpdateUiState.Downloading -> {
            StatusText("${state.version} 을 내려받고 있어요")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = BudgetTheme.colors.sectionBackground,
            )
        }

        UpdateUiState.AwaitingInstall ->
            StatusText("설치 화면에서 설치를 눌러주세요. 끝나면 앱이 닫히니 다시 열어주세요.")

        is UpdateUiState.Failed -> {
            StatusText(state.reason)
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetSecondaryButton(text = "다시 확인", onClick = onCheckUpdate)
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = BudgetTheme.colors.textSecondary,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BudgetTheme.colors.textSecondary,
        modifier =
        Modifier.padding(
            top = BudgetTheme.spacing.sectionPadding,
            bottom = BudgetTheme.spacing.inlineGap,
        ),
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "기기 설정"
    ThemeMode.LIGHT -> "밝게"
    ThemeMode.DARK -> "어둡게"
}

private fun Long.toMegabytes(): String = String.format("%.1f", this / BYTES_PER_MB)
