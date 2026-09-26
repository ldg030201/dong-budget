package com.dong.budget.ui.settings

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dong.budget.R
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.GalaxyAutoBlocker
import com.dong.budget.ui.components.BudgetChip
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetSmallButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.permission.AppPermission
import com.dong.budget.ui.permission.PermissionDialog
import com.dong.budget.ui.theme.BudgetTheme
import java.util.Locale

private const val BYTES_PER_MB = 1024.0 * 1024.0

/** 업데이트 화면의 '바뀐 점' 줄 수. 설치 버튼 아래에 두지만 너무 길면 화면이 늘어진다. */
private const val NOTES_MAX_LINES = 6

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
                            icon = mode.iconRes(),
                        )
                    }
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))

                SectionTitle("앱 정보")
                // 새 버전 확인은 어느 상태에서든 버전 옆 버튼으로 다시 할 수 있다.
                // 새 버전을 보고 있는 사이 더 새 버전이 나와도 눌러서 바로 최신으로 바꿔 볼 수 있게 하기 위함이다.
                // 확인 중이거나 내려받는 중에는 막는다. 내려받던 화면이 확인 결과로 덮이면 진행 상황이 사라진다.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "현재 버전 $currentVersion",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BudgetTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    BudgetSmallButton(
                        text = "업데이트 확인",
                        onClick = onCheckUpdate,
                        enabled = !updateState.isBusy,
                    )
                }

                Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))

                UpdateSection(
                    state = updateState,
                    downloadedVersion = downloadedVersion,
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
    onDownloadUpdate: () -> Unit,
    onInstallDownloaded: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    when (state) {
        // 확인은 버전 옆 버튼으로 한다
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking ->
            StatusText("새 버전이 있는지 확인하고 있어요")

        UpdateUiState.UpToDate ->
            StatusText("최신 버전을 쓰고 있어요")

        is UpdateUiState.Available -> {
            val release = state.release
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            Text(
                text = "새 버전(${release.version})이 있어요",
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
            )
            // 버튼을 바뀐 점보다 먼저 둔다.
            // 글이 길어져도 버튼이 화면 밖으로 밀려나지 않게 하기 위함이다.
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetPrimaryButton(
                // 이미 받아둔 파일이 있으면 다시 받지 않는다
                text =
                if (downloadedVersion == release.version) {
                    "설치하기"
                } else {
                    "내려받고 설치 (${release.sizeBytes.toMegabytes()}MB)"
                },
                onClick = onDownloadUpdate,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
            // 앱이 자기 자신을 업데이트하면 안드로이드가 실행 중인 앱을 종료한다.
            // 미리 알려주지 않으면 앱이 죽은 줄 안다.
            HintText("설치가 끝나면 앱이 닫혀요. 다시 열어주세요.")
            OtherWays(downloadedVersion, onInstallDownloaded, onOpenReleasePage)
            if (release.notes.isNotEmpty()) {
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
                Text(
                    text = "바뀐 점",
                    style = MaterialTheme.typography.labelMedium,
                    color = BudgetTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
                // 길면 자른다. 전체는 패치노트에서 볼 수 있다.
                Text(
                    text = release.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textPrimary,
                    maxLines = NOTES_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
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
            OtherWays(downloadedVersion, onInstallDownloaded, onOpenReleasePage)
        }

        is UpdateUiState.Failed -> {
            val failure = state.failure
            StatusText(failure.reason)
            if (failure.detail != null) {
                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
                // 기기마다 설치기가 달라 실패 이유가 제각각이다. 시스템 원문을 복사해서 알려줄 수 있게 선택 가능하게 둔다.
                SelectionContainer { HintText("시스템 메시지: ${failure.detail}") }
            }
            // 갤럭시 자동 차단이 켜져 있으면 어느 길로 설치해도 막힌다. 끄는 화면으로 가는 길을 맨 앞에 둔다.
            if (failure.suggestGalaxySecurity) {
                val context = LocalContext.current
                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
                BudgetPrimaryButton(text = "보안 위험 자동 차단 열기", onClick = { GalaxyAutoBlocker.open(context) })
                Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
                HintText(
                    "자동 차단을 끄고 돌아와서 '업데이트 확인'을 누르면 다시 설치할 수 있어요. " +
                        "설치가 끝나면 다시 켜도 되지만, 다음 업데이트 때 다시 꺼야 해요.",
                )
            }
            // 서명이 다르거나 저장 공간이 없으면 다른 길로 설치해도 똑같이 막힌다. 헛걸음을 권하지 않는다.
            if (failure.canTryOtherWays) {
                // 자동 차단이 켜져 있으면 아래 길도 똑같이 막힌다. 끈 뒤에도 막힐 때 쓰는 길이라고 알려준다.
                OtherWays(
                    downloadedVersion,
                    onInstallDownloaded,
                    onOpenReleasePage,
                    title = if (failure.suggestGalaxySecurity) "자동 차단을 끈 뒤에도 막히면" else "설치가 안 되면",
                )
            }
        }
    }
}

/**
 * 앱 안 설치가 안 될 때 쓰는 다른 길. 자주 쓰지 않으므로 글자 버튼 한 줄로 둔다.
 *   1. 받은 파일로 설치: 이미 받은 파일을 시스템 설치 화면으로 연다. 파일 관리자에서 APK 를 누르는 것과 같다.
 *   2. 브라우저에서 받기: 처음 설치할 때와 같은 길이라 어느 기기에서나 된다. 기록은 그대로 남는다.
 *
 * @param title 줄 위에 붙일 설명
 */
@Composable
private fun OtherWays(
    downloadedVersion: String?,
    onInstallDownloaded: () -> Unit,
    onOpenReleasePage: () -> Unit,
    title: String = "설치가 안 되면",
) {
    Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
    HintText(title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (downloadedVersion != null) {
            BudgetTextButton(text = "받은 파일로 설치", onClick = onInstallDownloaded)
            Text(text = "·", style = MaterialTheme.typography.labelLarge, color = BudgetTheme.colors.textSecondary)
        }
        BudgetTextButton(text = "브라우저에서 받기", onClick = onOpenReleasePage)
    }
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

/** 테마 칩 아이콘. 기기 설정은 휴대폰, 밝게는 해, 어둡게는 달 */
@DrawableRes
private fun ThemeMode.iconRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.drawable.ic_sym_smartphone
    ThemeMode.LIGHT -> R.drawable.ic_sym_light_mode
    ThemeMode.DARK -> R.drawable.ic_sym_dark_mode
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "기기 설정"
    ThemeMode.LIGHT -> "밝게"
    ThemeMode.DARK -> "어둡게"
}

// 기기 언어와 상관없이 같은 모양(12.3)으로 보인다. 로케일을 주지 않으면 독일어 기기에서 '12,3' 이 된다.
private fun Long.toMegabytes(): String = String.format(Locale.KOREA, "%.1f", this / BYTES_PER_MB)
