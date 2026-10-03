package com.dong.budget.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.dong.budget.R
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.BudgetSmallButton
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.components.TabHeader
import com.dong.budget.ui.components.animatedItem
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.stats.SectionNote
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.LocalDate

/**
 * 카드실적 탭. 실적 구간을 적은 카드마다 이번 기간에 얼마 썼는지, 다음 구간까지 얼마 남았는지, 지난 기간 실적을 판 하나로 보여 준다.
 * 실적을 안 적은 카드는 아래에 이번 달 쓴 돈과 '실적 추가' 로 늘어놓는다.
 * 상단 인셋은 이 화면이, 하단은 셸의 아래 메뉴가 처리한다(HomeShell).
 *
 * ```
 * (뱃지) 하나카드                                   >
 *        10월 1일 ~ 10월 31일
 * 10월 실적 · 29일 남았어요
 * 123,450원
 * ██████████░░░░░│░░░░░░░░░░░░░░                  ← 카드 색, 구간 자리마다 눈금, 끝이 가장 높은 구간
 * 첫 구간 30만원까지 176,550원 남았어요
 * 9월 실적 523,000원 · 30만원 구간을 채웠어요
 * ```
 *
 * @param onOpenDetail 카드 판을 누르면 그 카드의 실적 상세
 * @param onAddPerformance '실적 추가'(줄을 눌러도 같다). 그 카드의 실적 구간을 적는 화면
 * @param onOpenCategories 카드가 하나도 없을 때 결제수단을 추가하러 분류 관리로
 */
@Composable
fun CardPerformanceScreen(
    state: CardPerformanceUiState,
    onOpenDetail: (Long) -> Unit,
    onAddPerformance: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = CARD_PERFORMANCE_TITLE)
        // 첫 계산 전에는 비워 둔다. '카드가 없어요' 가 잠깐 비쳤다가 바뀌지 않게 한다.
        if (!state.loaded) return@Column
        if (!state.hasCards) {
            EmptyCards(onOpenCategories = onOpenCategories)
            return@Column
        }
        CardList(state = state, onOpenDetail = onOpenDetail, onAddPerformance = onAddPerformance)
    }
}

@Composable
private fun CardList(state: CardPerformanceUiState, onOpenDetail: (Long) -> Unit, onAddPerformance: (Long) -> Unit) {
    val tracked = state.tracked
    val untracked = state.untracked
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BudgetTheme.spacing.sectionGap),
    ) {
        // 실적을 하나도 안 적었으면 무엇을 적으면 되는지 먼저 알린다
        if (tracked.isEmpty()) {
            animatedItem(key = INTRO_KEY) {
                StatsSection(block = true) { SectionNote(INTRO_TEXT) }
            }
        }
        tracked.forEachIndexed { index, item ->
            animatedItem(key = "tracked-${item.card.id}") {
                TrackedCardBlock(
                    item = item,
                    today = state.today,
                    onClick = { onOpenDetail(item.card.id) },
                    modifier =
                    Modifier
                        .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                        // 판 사이는 월급 탭의 카드처럼 띄운다. 첫 판은 탭 머리 바로 아래에 둔다.
                        .then(if (index > 0) Modifier.padding(top = BudgetTheme.spacing.itemGap) else Modifier),
                )
            }
        }
        if (tracked.isNotEmpty()) {
            animatedItem(key = FOOTNOTE_KEY) {
                HintText(
                    text = FOOTNOTE_TEXT,
                    modifier =
                    Modifier.padding(
                        start = BudgetTheme.spacing.screenHorizontal,
                        end = BudgetTheme.spacing.screenHorizontal,
                        top = BudgetTheme.spacing.itemGap,
                    ),
                )
            }
        }
        if (untracked.isNotEmpty()) {
            animatedItem(key = UNTRACKED_KEY) {
                StatsSection(
                    modifier = Modifier.padding(top = BudgetTheme.spacing.sectionGap),
                    title = UNTRACKED_TITLE,
                    // 맨 위 안내가 이미 말했으면 되풀이하지 않는다
                    subtitle = UNTRACKED_SUBTITLE.takeIf { tracked.isNotEmpty() },
                ) {}
            }
            untracked.forEach { item ->
                animatedItem(key = "untracked-${item.card.id}") {
                    UntrackedRow(item = item, onAdd = { onAddPerformance(item.card.id) })
                }
            }
        }
    }
}

/**
 * 실적을 적은 카드 판. 누르면 그 카드의 상세가 열린다.
 * 화면 읽기는 판 전체를 한 문장으로 읽는다(막대는 빼고 문장이 뜻을 전한다).
 */
@Composable
private fun TrackedCardBlock(item: TrackedCard, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = trackedCardDescription(item, today)
    val shape = RoundedCornerShape(BudgetTheme.radius.block)
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .pressScaleClickable(shape = shape, onClickLabel = DETAIL_CLICK_LABEL, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .sectionBlock(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(icon = item.card.icon, color = item.card.color)
            Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
                Text(
                    text = item.card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = BudgetTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = periodRange(item.period), style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                // 누를 수 있다는 표시일 뿐이다. 화면 읽기는 '버튼' 으로 알려준다.
                contentDescription = null,
                tint = BudgetTheme.colors.textSecondary,
                modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap).size(BudgetTheme.size.iconSmall),
            )
        }
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        // 기간 이름과 남은 날은 판 폭을 다 쓰는 이 줄에 둔다. 다른 해의 기간("2025년 12월 실적 · 오늘이 마지막 날이에요")처럼 길어도
        // 좁은 화면(320dp)에서 꺾이지 않고 한 줄을 지키며 글자를 줄인다.
        val headlineStyle = MaterialTheme.typography.bodyMedium
        BasicText(
            text = trackedHeadline(item, today),
            style = headlineStyle.copy(color = BudgetTheme.colors.textSecondary),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_HEADLINE_SIZE, maxFontSize = headlineStyle.fontSize),
        )
        Text(text = spentText(item.progress.spent), style = BudgetTheme.amount.summary, color = BudgetTheme.colors.textPrimary)
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        TierBar(progress = item.progress, color = item.card.color)
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        Text(
            text = tierSentence(item.progress, past = false),
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        // 카드 혜택은 보통 전월 실적으로 정해진다. 그래서 지난 기간도 한 줄로 같이 둔다.
        HintText(previousLine(item.previousMonth, today, item.previous))
    }
}

/** 실적을 안 적은 카드 한 줄. 줄을 눌러도, 오른쪽 '실적 추가' 를 눌러도 실적을 적는 화면이 열린다. */
@Composable
private fun UntrackedRow(item: UntrackedCard, onAdd: () -> Unit) {
    val label = addPerformanceLabel(item.card.name)
    BudgetListItem(
        title = item.card.name,
        subtitle = untrackedSubtitle(item),
        leading = { CategoryBadge(icon = item.card.icon, color = item.card.color) },
        trailing = {
            // 줄마다 같은 버튼이라 화면 읽기에는 카드 이름을 붙여 읽힌다
            BudgetSmallButton(text = ADD_PERFORMANCE, onClick = onAdd, modifier = Modifier.semantics { contentDescription = label })
        },
        onClick = onAdd,
    )
}

/** 실적을 볼 카드가 하나도 없다(현금·계좌이체만 남음). 결제수단을 추가하러 분류 관리로 보낸다. */
@Composable
private fun EmptyCards(onOpenCategories: () -> Unit) {
    // 가로 화면처럼 높이가 모자라면 버튼이 찌그러지지 않게 스크롤한다. 높이가 넉넉하면 가운데에 둔다(월급 탭과 같다).
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BudgetTheme.spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        IconBadge(
            iconRes = R.drawable.ic_sym_credit_card,
            swatch = BudgetTheme.categoryPalette[EMPTY_BADGE_COLOR],
            size = BudgetTheme.size.badgeLarge,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        Text(text = EMPTY_CARDS_TITLE, style = MaterialTheme.typography.titleMedium, color = BudgetTheme.colors.textPrimary)
        Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
        Text(
            text = EMPTY_CARDS_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
        BudgetPrimaryButton(text = OPEN_CATEGORIES, onClick = onOpenCategories)
        Spacer(Modifier.weight(1f))
    }
}

/** 카드 판을 누르면 무엇을 하는지 화면 읽기가 알려 줄 말 */
internal const val DETAIL_CLICK_LABEL = "자세히 보기"

private const val OPEN_CATEGORIES = "분류 관리 열기"

/** 카드 판의 '10월 실적 · 29일 남았어요' 가 좁은 화면에서 줄어드는 가장 작은 글자 크기(기간 날짜 글자와 같다) */
private val MIN_HEADLINE_SIZE = 12.sp

/** 빈 상태 뱃지 색. 전체 화면의 카드실적 줄과 같은 색이다. */
private const val EMPTY_BADGE_COLOR = "blue"

private const val INTRO_KEY = "intro"
private const val FOOTNOTE_KEY = "footnote"
private const val UNTRACKED_KEY = "untracked"
