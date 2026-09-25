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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.ui.components.BudgetChip
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetSecondaryButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.permission.AppPermission
import com.dong.budget.ui.permission.PermissionDialog
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
    downloadedVersion: String?,
    onInstallDownloaded: () -> Unit,
    releasePageUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 설치에는 '출처를 알 수 없는 앱 설치' 권한이 필요하다. 꺼진 채로 설치를 시작하면 시스템이 막고
    // 설치가 '중단' 으로 끝나므로, 누르는 순간 먼저 확인하고 꺼져 있으면 설정으로 안내한다.
    var askPermission by remember { mutableStateOf(false) }
    // 창을 띄운 채 홈 화면을 거쳐 설정에서 직접 켜고 돌아와도 창이 닫히게 돌아올 때마다 다시 본다
    LifecycleResumeEffect(askPermission) {
        if (askPermission && AppPermission.INSTALL_UPDATES.isGranted(context)) askPermission = false
        onPauseOrDispose {}
    }
    fun withInstallPermission(action: () -> Unit) {
        if (AppPermission.INSTALL_UPDATES.isGranted(context)) action() else askPermission = true
    }
    if (askPermission) {
        PermissionDialog(
            permission = AppPermission.INSTALL_UPDATES,
            onGoToSettings = { askPermission = false },
            onLater = { askPermission = false },
        )
    }

    val uriHandler = LocalUriHandler.current
    val openReleasePage = {
        // 브라우저가 하나도 없는 기기에서는 열 곳이 없다. 앱이 죽지 않게 조용히 넘어간다.
        runCatching { uriHandler.openUri(releasePageUrl) }
        Unit
    }
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
                    downloadedVersion = downloadedVersion,
                    onCheckUpdate = onCheckUpdate,
                    onDownloadUpdate = { withInstallPermission(onDownloadUpdate) },
                    onInstallDownloaded = { withInstallPermission(onInstallDownloaded) },
                    onOpenReleasePage = openReleasePage,
                )

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }
        }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUiState,
    downloadedVersion: String?,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallDownloaded: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> {
            BudgetSecondaryButton(text = "업데이트 확인", onClick = onCheckUpdate)
            // 받아만 두고 설치하지 못한 채 앱이 꺼졌으면, 다시 받지 않고 바로 설치할 수 있게 보여준다
            if (downloadedVersion != null) {
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
                StatusText("$downloadedVersion 설치 파일을 받아뒀어요")
                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
                OtherWays(downloadedVersion, onInstallDownloaded, onOpenReleasePage)
            }
        }

        UpdateUiState.Checking ->
            StatusText("새 버전이 있는지 확인하고 있어요")

        UpdateUiState.UpToDate -> {
            StatusText("최신 버전을 쓰고 있어요")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetSecondaryButton(text = "다시 확인", onClick = onCheckUpdate)
        }

        is UpdateUiState.Available -> {
            Text(
                text = "새 버전(${state.version})이 있어요",
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
            )
            // 버튼을 바뀐 점보다 먼저 둔다.
            // 글이 길어져도 버튼이 화면 밖으로 밀려나지 않게 하기 위함이다.
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetPrimaryButton(
                // 이미 받아둔 파일이 있으면 다시 받지 않는다
                text =
                if (downloadedVersion == state.version) {
                    "설치하기"
                } else {
                    "내려받고 설치 (${state.sizeBytes.toMegabytes()}MB)"
                },
                onClick = onDownloadUpdate,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            // 앱이 자기 자신을 업데이트하면 안드로이드가 실행 중인 앱을 종료한다.
            // 미리 알려주지 않으면 앱이 죽은 줄 안다.
            HintText("설치가 끝나면 앱이 닫혀요. 다시 열어주세요.")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            OtherWays(downloadedVersion, onInstallDownloaded, onOpenReleasePage)
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
            StatusText(if (state.progress >= 1f) "설치를 준비하고 있어요" else "${state.version} 버전을 내려받고 있어요")
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = BudgetTheme.colors.sectionBackground,
            )
        }

        UpdateUiState.AwaitingInstall -> {
            // 확인창 없이 바로 설치될 수도 있고(자기 업데이트), 확인창이 뜰 수도 있다. 둘 다 안내한다.
            StatusText("설치하고 있어요. 확인 창이 뜨면 '업데이트'를 눌러주세요. 끝나면 앱이 닫히니 다시 열어주세요.")
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            // 스토어 밖에서 받은 앱은 Play 프로텍트가 한 번 더 묻는다. 여기서 '설치 안 함' 을 누르면 설치가 중단된다.
            HintText("'앱 검사 권장됨'(Play 프로텍트) 창이 뜨면 '앱 검사'를 눌러주세요. '앱 설치 안함'을 누르면 설치가 멈춰요.")
            // 설치 화면이 뜨지 않거나 거기서 막히면 여기서 바로 다른 길로 갈 수 있게 한다
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            OtherWays(downloadedVersion, onInstallDownloaded, onOpenReleasePage)
        }

        is UpdateUiState.Failed -> {
            StatusText(state.reason)
            if (state.detail != null) {
                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
                // 기기마다 설치기가 달라 실패 이유가 제각각이다. 시스템 원문을 복사해서 알려줄 수 있게 선택 가능하게 둔다.
                SelectionContainer { HintText("시스템 메시지: ${state.detail}") }
            }
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetSecondaryButton(text = "다시 확인", onClick = onCheckUpdate)
            // 서명이 다르거나 저장 공간이 없으면 다른 길로 설치해도 똑같이 막힌다. 헛걸음을 권하지 않는다.
            if (state.canTryOtherWays) {
                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
                OtherWays(downloadedVersion, onInstallDownloaded, onOpenReleasePage)
            }
        }
    }
}

/**
 * 앱 안 설치가 안 될 때 쓰는 다른 길. 위에서부터 손이 덜 가는 순서다.
 *   1. 받아둔 파일로 직접 설치: 이미 받은 파일을 시스템 설치 화면으로 연다. 파일 관리자에서 APK 를 누르는 것과 같다.
 *   2. 브라우저에서 직접 받기: 처음 설치할 때와 같은 길이라 어느 기기에서나 된다.
 */
@Composable
private fun OtherWays(downloadedVersion: String?, onInstallDownloaded: () -> Unit, onOpenReleasePage: () -> Unit) {
    if (downloadedVersion != null) {
        BudgetSecondaryButton(text = "받은 파일로 직접 설치 ($downloadedVersion)", onClick = onInstallDownloaded)
        Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
        HintText("앱 안에서 설치가 안 되면 받아둔 파일을 설치 화면으로 바로 열어요.")
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
    }
    BudgetSecondaryButton(text = "브라우저에서 직접 받기", onClick = onOpenReleasePage)
    Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
    HintText("그래도 안 되면 여기서 설치 파일(APK)을 받아 열어주세요. 기록은 그대로 남아요.")
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BudgetTheme.colors.textSecondary,
    )
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
