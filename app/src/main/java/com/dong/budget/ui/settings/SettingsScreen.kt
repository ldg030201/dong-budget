package com.dong.budget.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.R
import com.dong.budget.data.backup.BackupSchedule
import com.dong.budget.data.backup.LastBackup
import com.dong.budget.data.settings.BottomMenu
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.data.update.UpdateNotice
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.SectionLabel
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.format.formatWeekdayFull
import com.dong.budget.ui.lock.APP_LOCK_GROUP_TEXTS
import com.dong.budget.ui.lock.LockControls
import com.dong.budget.ui.lock.LockGroup
import com.dong.budget.ui.theme.BudgetTheme
import java.time.DayOfWeek

/**
 * 설정. 위에서부터 화면 테마, 한 주 시작 요일, 하단 메뉴, 백업·복원, 앱 잠금, 권한(켜짐·꺼짐),
 * 고급 설정(자동 기능 스위치·초기화)과 앱 정보(버전·업데이트)로 가는 줄이다.
 * 묶음마다 회색 둥근 판에 담는다([SettingsGroup]).
 *
 * @param newer 아직 설치하지 않은 새 버전. 있으면 앱 정보 줄에 알린다.
 * @param bottomMenu 지금 아래 메뉴 차림. 하단 메뉴 편집기([BottomMenuEditor])가 아이콘으로 늘어놓고, 끌어서 고치면 [onBottomMenuChange] 로 저장한다.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    currentVersion: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    onWeekStartChange: (DayOfWeek) -> Unit = {},
    newer: UpdateNotice? = null,
    onOpenAdvanced: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    bottomMenu: BottomMenu = BottomMenu.DEFAULT,
    onBottomMenuChange: (BottomMenu) -> Unit = {},
    backup: BackupUiState = BackupUiState(),
    lastBackup: LastBackup? = null,
    backupSchedule: BackupSchedule = BackupSchedule.DEFAULT,
    backupActions: BackupActions = BackupActions(),
    appLock: LockControls = LockControls(),
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
                SectionLabel("화면 테마")
                // 왼쪽부터 밝게 · 기기 설정 · 어둡게. 기기 설정이 밝음과 어두움의 가운데에 온다.
                SegmentedToggle(
                    options = THEME_ORDER.map { it.label() },
                    selectedIndex = THEME_ORDER.indexOf(themeMode),
                    onSelect = { index -> onThemeModeChange(THEME_ORDER[index]) },
                    icons = THEME_ORDER.map { it.iconRes() },
                )

                // 달력과 통계 요일별 줄이 이 요일부터 시작한다
                SectionLabel("한 주 시작")
                SegmentedToggle(
                    options = WEEK_START_ORDER.map { formatWeekdayFull(it) },
                    selectedIndex = WEEK_START_ORDER.indexOf(weekStart),
                    onSelect = { index -> onWeekStartChange(WEEK_START_ORDER[index]) },
                )

                // 따로 들어가지 않고 여기서 아이콘을 끌어 바로 고친다
                BottomMenuEditor(menu = bottomMenu, onChange = onBottomMenuChange)

                BackupSection(state = backup, lastBackup = lastBackup, schedule = backupSchedule, actions = backupActions)

                LockGroup(controls = appLock, texts = APP_LOCK_GROUP_TEXTS)

                PermissionSection()

                SettingsGroup(title = null) {
                    ActionRow(title = "고급 설정", onClick = onOpenAdvanced, opensScreen = true)
                    // 새 버전이 있으면 버전 대신 알린다. 업데이트는 앱 정보 화면에서 한다.
                    ActionRow(
                        title = "앱 정보",
                        onClick = onOpenAppInfo,
                        opensScreen = true,
                        value = newer?.let(::newerLabel) ?: currentVersion,
                        valueColor = if (newer != null) BudgetTheme.colors.brandText else BudgetTheme.colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }
        }
    }
}

/** 앱 정보 줄에 알리는 새 버전. Play 배포는 설치하기 전에 새 버전의 이름을 알 수 없어 '새 버전' 만 적는다. */
private fun newerLabel(notice: UpdateNotice): String = (notice as? UpdateNotice.Available)?.version?.let { "새 버전 $it" } ?: "새 버전"

/** 한 주 시작 칸 순서 */
private val WEEK_START_ORDER = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)

/** 테마 칸 순서. 왼쪽 밝게, 가운데 기기 설정, 오른쪽 어둡게 */
private val THEME_ORDER = listOf(ThemeMode.LIGHT, ThemeMode.SYSTEM, ThemeMode.DARK)

/** 테마 칸 아이콘. 기기 설정은 휴대폰, 밝게는 해, 어둡게는 달 */
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
