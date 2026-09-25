package com.dong.budget.ui.patchnotes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.R
import com.dong.budget.data.update.AppVersion
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.CategorySwatch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 패치노트. 버전마다 메뉴별로 바뀐 점을 모아 보여준다.
 * 바뀐 점마다 종류(추가·개선·수정·오류수정)를 색 꼬리표로 붙인다.
 *
 * @param currentVersion 설치된 버전. 그 버전에 '지금 버전' 을 붙이고, 그보다 새 버전(개발 중)에는 '준비 중' 을 붙인다.
 */
@Composable
fun PatchNotesScreen(currentVersion: String, onBack: () -> Unit, modifier: Modifier = Modifier, releases: List<Release> = PATCH_NOTES) {
    val installed = AppVersion.parse(currentVersion)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "패치노트")
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding =
                PaddingValues(
                    start = BudgetTheme.spacing.screenHorizontal,
                    end = BudgetTheme.spacing.screenHorizontal,
                    bottom = BudgetTheme.spacing.sectionGap,
                ),
                verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.itemGap),
            ) {
                items(items = releases, key = { it.version }) { release ->
                    val version = AppVersion.parse(release.version)
                    ReleaseBlock(
                        release = release,
                        status =
                        when {
                            version == null || installed == null -> ReleaseStatus.PAST
                            version == installed -> ReleaseStatus.CURRENT
                            version > installed -> ReleaseStatus.UPCOMING
                            else -> ReleaseStatus.PAST
                        },
                    )
                }
            }
        }
    }
}

private enum class ReleaseStatus { PAST, CURRENT, UPCOMING }

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA)

@Composable
private fun ReleaseBlock(release: Release, status: ReleaseStatus) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(BudgetTheme.colors.sectionBackground, RoundedCornerShape(BudgetTheme.radius.block))
            .padding(BudgetTheme.spacing.sectionPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.version,
                style = MaterialTheme.typography.titleLarge,
                color = BudgetTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            when (status) {
                ReleaseStatus.CURRENT ->
                    StatusBadge("지금 버전", MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)

                ReleaseStatus.UPCOMING ->
                    // 보조 글자색은 이 회색 위에서 대비가 모자라다(4.497:1). 본문색을 쓴다.
                    StatusBadge("준비 중", BudgetTheme.colors.textPrimary, BudgetTheme.colors.divider)

                ReleaseStatus.PAST -> Unit
            }
        }
        if (release.date != null) {
            Text(
                text = dateFormatter.format(release.date),
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
                modifier = Modifier.padding(top = BudgetTheme.spacing.tightGap),
            )
        }
        release.menus.forEach { menu ->
            Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
            MenuSection(menu)
        }
    }
}

/**
 * 한 메뉴의 바뀐 점. 메뉴 아이콘과 이름 아래로 세로줄을 내려 그 메뉴에 속한 항목을 묶어 보여준다.
 * 항목은 종류 순서(추가 → 개선 → 수정 → 오류수정)로 정렬한다. 같은 종류 안에서는 적은 순서를 지킨다.
 */
@Composable
private fun MenuSection(menu: MenuChanges) {
    val badgeSize = BudgetTheme.size.badgeSmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(iconRes = menu.menu.iconRes(), swatch = BudgetTheme.categoryPalette[menu.menu.color()], size = badgeSize)
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(
            text = menu.menu.label,
            style = MaterialTheme.typography.labelLarge,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
    // 세로줄이 항목들 높이만큼만 내려오게 한다
    Row(modifier = Modifier.height(IntrinsicSize.Min).padding(top = BudgetTheme.spacing.tightGap)) {
        Box(modifier = Modifier.width(badgeSize).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .width(BudgetTheme.size.underlineActive)
                    .fillMaxHeight()
                    .background(BudgetTheme.colors.divider),
            )
        }
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Column(
            modifier = Modifier.padding(vertical = BudgetTheme.spacing.tightGap),
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
        ) {
            menu.changes.sortedBy { it.kind.ordinal }.forEach { ChangeRow(it) }
        }
    }
}

@DrawableRes
private fun PatchMenu.iconRes(): Int = when (this) {
    PatchMenu.HOME -> R.drawable.ic_sym_home
    PatchMenu.HISTORY -> R.drawable.ic_sym_receipt_long
    PatchMenu.EDITOR -> R.drawable.ic_sym_edit_note
    PatchMenu.CATEGORIES -> R.drawable.ic_sym_category
    PatchMenu.MORE -> R.drawable.ic_sym_apps
    PatchMenu.SETTINGS -> R.drawable.ic_sym_settings
    PatchMenu.COMMON -> R.drawable.ic_sym_devices
}

/** 전체 메뉴 화면의 색과 맞춘다(분류 관리는 남색, 설정은 회색). 나머지는 서로 겹치지 않게 고른다. */
private fun PatchMenu.color(): String = when (this) {
    PatchMenu.HOME -> "blue"
    PatchMenu.HISTORY -> "teal"
    PatchMenu.EDITOR -> "green"
    PatchMenu.CATEGORIES -> "indigo"
    PatchMenu.MORE -> "purple"
    PatchMenu.SETTINGS -> "gray"
    PatchMenu.COMMON -> "amber"
}

@Composable
private fun StatusBadge(text: String, content: Color, container: Color) {
    Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier =
        Modifier
            .background(container, RoundedCornerShape(BudgetTheme.radius.full))
            .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.tightGap / 2),
    )
}

/** 꼬리표와 설명 한 줄. 화면 읽기는 '추가, 달력에 …' 처럼 한 번에 읽는다. */
@Composable
private fun ChangeRow(change: Change) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        KindTag(change.kind)
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(
            text = change.text,
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 종류 꼬리표. 색만으로 구분하지 않고 글자를 함께 쓴다.
 * 너비를 모두 같게 해서 설명 글의 시작 위치가 줄마다 흔들리지 않게 한다.
 */
@Composable
private fun KindTag(kind: ChangeKind) {
    val swatch = kind.swatch()
    Box(
        modifier =
        Modifier
            // 설명 첫 줄(줄 높이 23)의 가운데에 맞춘다
            .padding(top = BudgetTheme.spacing.tightGap / 2)
            .width(BudgetTheme.size.patchTagWidth)
            .background(swatch.container, RoundedCornerShape(BudgetTheme.radius.chip / 2))
            .padding(vertical = BudgetTheme.spacing.tightGap / 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.label,
            style = MaterialTheme.typography.labelSmall,
            color = swatch.content,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChangeKind.swatch(): CategorySwatch = when (this) {
    ChangeKind.ADDED -> BudgetTheme.colors.tagAdded
    ChangeKind.IMPROVED -> BudgetTheme.colors.tagImproved
    ChangeKind.CHANGED -> BudgetTheme.colors.tagChanged
    ChangeKind.FIXED -> BudgetTheme.colors.tagFixed
}
