package com.dong.budget.ui.patchnotes

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.R
import com.dong.budget.data.update.AppVersion
import com.dong.budget.data.update.NewerRelease
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.CategorySwatch
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 패치노트. 버전마다 메뉴별로 바뀐 점을 모아 보여준다.
 * 바뀐 점마다 종류(추가·개선·수정·오류수정)를 색 꼬리표로 붙인다.
 * 버전 카드는 제목을 눌러 접고 펼친다. 새 버전·지금 버전·준비 중인 버전만 펼친 채로 시작한다.
 *
 * @param currentVersion 설치된 버전. 그 버전에 '지금 버전' 을 붙이고, 그보다 새 버전(개발 중)에는 '준비 중' 을 붙인다.
 * @param newer 이미 배포됐지만 아직 설치하지 않은 버전들. 맨 위에 '새 버전' 으로 보여준다.
 *   앱에 들어 있는 기록에 같은 버전이 있으면 그쪽을 보여준다(메뉴별로 정리돼 있어서).
 * @param onOpenUpdate 새 버전 칸의 [업데이트하러 가기]
 */
@Composable
fun PatchNotesScreen(
    currentVersion: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    releases: List<Release> = PATCH_NOTES,
    newer: List<NewerRelease> = emptyList(),
    onOpenUpdate: () -> Unit = {},
) {
    val installed = AppVersion.parse(currentVersion)
    val bundled = releases.mapNotNullTo(mutableSetOf()) { AppVersion.parse(it.version) }
    val newerShown = newer.filter { AppVersion.parse(it.version) !in bundled }
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
                // 업데이트 버튼은 가장 새 버전 칸에만 둔다. 업데이트하면 늘 최신 버전이 설치된다.
                itemsIndexed(items = newerShown, key = { _, release -> "newer-${release.version}" }) { index, release ->
                    NewerReleaseBlock(release = release, onOpenUpdate = onOpenUpdate.takeIf { index == 0 })
                }
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

/**
 * 배포됐지만 아직 설치하지 않은 버전. 바뀐 점은 배포 본문의 글 그대로 보여준다.
 * @param onOpenUpdate null 이면 업데이트 버튼을 두지 않는다
 */
@Composable
private fun NewerReleaseBlock(release: NewerRelease, onOpenUpdate: (() -> Unit)?) {
    ReleaseCard(
        version = release.version,
        date = release.date,
        initiallyExpanded = true,
        badge = { StatusBadge("새 버전", MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer) },
    ) {
        Text(
            // 예전 형식의 배포는 앱에 보여줄 구간을 알 수 없어 본문이 비어 온다
            text = release.notes.ifBlank { "바뀐 점은 업데이트한 뒤 여기서 볼 수 있어요." },
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        if (onOpenUpdate != null) {
            BudgetPrimaryButton(text = "업데이트하러 가기", onClick = onOpenUpdate)
        }
    }
}

@Composable
private fun ReleaseBlock(release: Release, status: ReleaseStatus) {
    ReleaseCard(
        version = release.version,
        date = release.date,
        initiallyExpanded = status != ReleaseStatus.PAST,
        badge = {
            when (status) {
                ReleaseStatus.CURRENT ->
                    StatusBadge("지금 버전", MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)

                ReleaseStatus.UPCOMING ->
                    // 보조 글자색은 이 회색 위에서 대비가 모자라다(4.497:1). 본문색을 쓴다.
                    StatusBadge("준비 중", BudgetTheme.colors.textPrimary, BudgetTheme.colors.divider)

                ReleaseStatus.PAST -> Unit
            }
        },
    ) {
        release.menus.forEach { MenuSection(it) }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA)

/**
 * 버전 하나의 카드. 버전 제목과 뱃지, 배포한 날을 누르면 아래의 [content] 를 접고 펼친다.
 * 접힌 카드는 [content] 를 아예 그리지 않으므로 버전이 많아져도 펼친 카드만큼만 그린다.
 *
 * @param initiallyExpanded 처음 열었을 때 펼쳐 둘지. 사용자가 바꾼 뒤로는 화면을 돌리거나 스크롤해도 그대로 둔다.
 */
@Composable
private fun ReleaseCard(
    version: String,
    date: LocalDate?,
    initiallyExpanded: Boolean,
    badge: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "releaseArrow")
    // 모양은 sectionBlock 과 같다. 여백은 누르는 머리와 내용에 따로 준다(카드 끝까지 누를 수 있게).
    val shape = RoundedCornerShape(BudgetTheme.radius.block)
    val padding = BudgetTheme.spacing.sectionPadding
    Column(modifier = Modifier.fillMaxWidth().background(BudgetTheme.colors.sectionBackground, shape)) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                // 접혔을 때는 머리가 카드 전체라 카드와 같은 모양으로 누름·포커스 표시를 그린다
                .pressScaleClickable(shape = shape) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = version,
                        style = MaterialTheme.typography.titleLarge,
                        color = BudgetTheme.colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    badge()
                }
                if (date != null) {
                    Text(
                        text = dateFormatter.format(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = BudgetTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = BudgetTheme.spacing.tightGap),
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = BudgetTheme.colors.textTertiary,
                modifier = Modifier.rotate(arrowRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = padding, end = padding, bottom = padding),
                verticalArrangement = Arrangement.spacedBy(padding),
                content = content,
            )
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
    // 카드가 메뉴 사이를 띄우므로, 메뉴 이름과 항목은 한 덩어리로 묶어 그 간격이 끼지 않게 한다
    Column {
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
}

@DrawableRes
private fun PatchMenu.iconRes(): Int = when (this) {
    PatchMenu.HOME -> R.drawable.ic_sym_home
    PatchMenu.HISTORY -> R.drawable.ic_sym_receipt_long
    PatchMenu.EDITOR -> R.drawable.ic_sym_edit_note
    PatchMenu.CATEGORIES -> R.drawable.ic_sym_category
    PatchMenu.MORE -> R.drawable.ic_sym_apps
    PatchMenu.PATCH_NOTES -> R.drawable.ic_sym_new_releases
    PatchMenu.SETTINGS -> R.drawable.ic_sym_settings
    PatchMenu.COMMON -> R.drawable.ic_sym_devices
}

/** 전체 메뉴 화면의 색과 맞춘다(분류 관리는 남색, 패치노트는 보라, 설정은 회색). 나머지는 서로 겹치지 않게 고른다. */
private fun PatchMenu.color(): String = when (this) {
    PatchMenu.HOME -> "blue"
    PatchMenu.HISTORY -> "teal"
    PatchMenu.EDITOR -> "green"
    PatchMenu.CATEGORIES -> "indigo"
    PatchMenu.MORE -> "pink"
    PatchMenu.PATCH_NOTES -> "purple"
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
