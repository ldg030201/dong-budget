package com.dong.budget.ui.salary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dong.budget.R
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.WorkStatus
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetListItem
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.components.RollingText
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.components.transactionAmountColor
import com.dong.budget.ui.components.transactionTitle
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatClock
import com.dong.budget.ui.format.formatDayShort
import com.dong.budget.ui.format.formatNoticeTime
import com.dong.budget.ui.format.formatSignedAmount
import com.dong.budget.ui.stats.SectionNote
import com.dong.budget.ui.stats.StatsSection
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 월급 탭. 일하는 동안 오늘 번 돈이 초마다 오르고, 이번 달·올해 번 돈과 월급날까지 남은 날을 보여 준다.
 *
 * 초마다 바뀌는 값은 지금 시각([rememberNow])을 람다로 받아 작은 묶음 안에서만 읽는다.
 * 화면 전체가 매초 다시 그려지지 않게 하기 위함이다. 탭을 떠나거나 앱이 뒤로 가면 시계도 멈춘다.
 *
 * @param onRegisterSalary 월급날이 막 지났는데 아직 등록하지 않은 달의 월급을 수입으로 등록한다
 */
@Composable
fun SalaryScreen(
    state: SalaryUiState,
    onOpenSettings: () -> Unit,
    onRegisterSalary: (YearMonth) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = remember { Clock.system(BudgetTime.ZONE) },
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        SalaryHeader(onOpenSettings = onOpenSettings)
        // 저장된 설정을 읽기 전에는 비워 둔다. '월급을 정해 주세요' 가 잠깐 비쳤다가 바뀌지 않게 한다.
        if (!state.loaded) return@Column
        val settings = state.settings
        if (!settings.isReady) {
            EmptySalary(onOpenSettings = onOpenSettings)
            return@Column
        }
        val now = rememberNow(clock)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.itemGap),
        ) {
            // 카드는 화면 여백 안에, 최근 내역 줄은 내역 목록처럼 끝까지 눌리게 여백을 줄이 가진다
            val card = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            TodayCard(settings = settings, now = { now.value }, spentToday = state.spentToday, modifier = card)
            TotalsCard(settings = settings, now = { now.value }, modifier = card)
            PaydayCard(
                settings = settings,
                today = state.today,
                registerMonth = state.registerMonth,
                onRegister = onRegisterSalary,
                modifier = card,
            )
            RecentSection(settings = settings, today = state.today, items = state.recent, onOpenTransaction = onOpenTransaction)
            Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
        }
    }
}

/** 매초 정각의 지금 시각(서울). 화면에 보일 때만 돈다. */
@Composable
private fun rememberNow(clock: Clock): State<LocalDateTime> {
    val ticks = remember(clock) { BudgetTime.everySecond(clock).map { it.atZone(BudgetTime.ZONE).toLocalDateTime() } }
    val initial = remember(clock) { clock.instant().atZone(BudgetTime.ZONE).toLocalDateTime() }
    return ticks.collectAsStateWithLifecycle(initialValue = initial)
}

/** '전체' 탭과 같은 머리. 오른쪽 톱니로 월급 설정을 연다. 잠겨 있거나 처음 안내 중이면 톱니를 두지 않는다([onOpenSettings] 가 null). */
@Composable
internal fun SalaryHeader(onOpenSettings: (() -> Unit)?) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(
                start = BudgetTheme.spacing.screenHorizontal,
                end = BudgetTheme.spacing.inlineGap,
                top = BudgetTheme.spacing.inlineGap,
                bottom = BudgetTheme.spacing.inlineGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "월급",
            style = MaterialTheme.typography.headlineSmall,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onOpenSettings != null) {
            BudgetIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_sym_settings),
                contentDescription = "월급 설정",
                onClick = onOpenSettings,
            )
        } else {
            // 톱니가 없어도 머리 높이는 같게 둔다
            Spacer(Modifier.size(BudgetTheme.size.minTouchTarget))
        }
    }
}

@Composable
private fun EmptySalary(onOpenSettings: () -> Unit) {
    // 가로 화면처럼 높이가 모자라면 버튼이 찌그러지지 않게 스크롤한다. 높이가 넉넉하면 가운데에 둔다.
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
            iconRes = R.drawable.ic_sym_payments,
            swatch = BudgetTheme.categoryPalette["teal"],
            size = BudgetTheme.size.badgeLarge,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        Text(text = "월급을 정해 주세요", style = MaterialTheme.typography.titleMedium, color = BudgetTheme.colors.textPrimary)
        Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
        Text(
            text = "연봉이나 월급과 출퇴근 시간을 정하면\n일하는 동안 번 돈이 초마다 쌓여요.",
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
        BudgetPrimaryButton(text = "월급 정하기", onClick = onOpenSettings)
        Spacer(Modifier.weight(1f))
    }
}

/**
 * 오늘. 지금 상태, 초마다 오르는 오늘 번 돈, 출근부터 퇴근까지 얼마나 왔는지, 오늘 쓴 돈.
 * 쓴 돈은 번 돈과 나란히 놓여서 빨갛게 쓴다(한 자리에 수입·지출이 함께 있을 때만 지출에 색을 쓰는 규칙).
 */
@Composable
private fun TodayCard(settings: SalarySettings, now: () -> LocalDateTime, spentToday: Long, modifier: Modifier = Modifier) {
    val time = now()
    val earnings = settings.earningsAt(time)
    Column(modifier = modifier.fillMaxWidth().sectionBlock()) {
        StatusRow(status = earnings.status, text = statusLine(settings, time))
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        // 매초 바뀌는 금액은 화면 읽기에서 빼고, 천 원 단위로 끊은 글을 대신 읽힌다
        val spoken = "오늘 번 돈 ${spokenEarned(earnings.today)}, ${spokenRate(earnings.perSecond)}"
        Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "오늘 번 돈",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                // 1초·1시간에 버는 돈. 일하지 않는 때에도 이번 월급 기간의 빠르기를 보여 준다.
                Text(text = rateBadge(earnings.perSecond), style = BudgetTheme.amount.tableCell, color = BudgetTheme.colors.income)
            }
            HeroAmount(text = formatEarned(earnings.today))
        }
        Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
        EarnBar(fraction = earnings.dayProgress)
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Text(
                text = formatClock(settings.workStart),
                style = MaterialTheme.typography.labelSmall,
                color = BudgetTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatClock(settings.workEnd),
                style = MaterialTheme.typography.labelSmall,
                color = BudgetTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "오늘 쓴 돈", style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
                HintText(spentLine(earnings.today, spentToday))
            }
            Text(
                text = if (spentToday > 0) "-${formatAmount(spentToday)}원" else "${formatAmount(-spentToday)}원",
                style = BudgetTheme.amount.medium,
                color = if (spentToday > 0) BudgetTheme.colors.expense else BudgetTheme.colors.textPrimary,
            )
        }
    }
}

/** 지금 상태 한 줄. 일하는 중이면 초록 점, 점심이면 브랜드색 점, 그 밖에는 흐린 점 */
@Composable
private fun StatusRow(status: WorkStatus, text: String) {
    val dot =
        when (status) {
            WorkStatus.WORKING -> BudgetTheme.colors.income
            WorkStatus.LUNCH -> BudgetTheme.colors.brandText
            else -> BudgetTheme.colors.textTertiary
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(BudgetTheme.size.noticeDot).drawBehind { drawCircle(dot) })
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = BudgetTheme.colors.textSecondary)
    }
}

/** 월급날부터와 올해 번 돈. 초마다 따라 오르지만 글자가 굴러가지는 않는다(움직이는 것은 오늘 번 돈 하나로 둔다). */
@Composable
private fun TotalsCard(settings: SalarySettings, now: () -> LocalDateTime, modifier: Modifier = Modifier) {
    val time = now()
    val earnings = settings.earningsAt(time)
    Column(modifier = modifier.fillMaxWidth().sectionBlock()) {
        TotalRow(label = "월급날부터 번 돈", amount = earnings.period, caption = periodCaption(earnings))
        Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
        EarnBar(fraction = if (earnings.periodTotal > 0) (earnings.period / earnings.periodTotal).toFloat() else 0f)
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        TotalRow(label = "올해 번 돈", amount = earnings.year, caption = yearCaption(settings, time.toLocalDate()))
        Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))
        HintText(basisLine(settings))
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, caption: String) {
    Column(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label ${spokenEarned(amount)}. $caption" }) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
        Text(text = formatEarned(amount), style = BudgetTheme.amount.summary, color = BudgetTheme.colors.income)
        HintText(caption)
    }
}

/** 올해 언제부터 셌는지. 입사일이 올해면 그날부터, 아니면 1월 1일부터 */
private fun yearCaption(settings: SalarySettings, today: LocalDate): String {
    val start = settings.startDate?.takeIf { it.year == today.year && !it.isAfter(today) }
    return if (start == null) "1월 1일부터 셌어요" else "입사일 ${start.monthValue}월 ${start.dayOfMonth}일부터 셌어요"
}

/** 월급날. 막 지났는데 아직 등록하지 않았으면 등록 버튼을 둔다. */
@Composable
private fun PaydayCard(
    settings: SalarySettings,
    today: LocalDate,
    registerMonth: YearMonth?,
    onRegister: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val payday = settings.nextPayday(today)
    Column(modifier = modifier.fillMaxWidth().sectionBlock()) {
        Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "월급날", style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary)
                Text(
                    text = paydayLine(payday, today),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (payday == today) BudgetTheme.colors.brandText else BudgetTheme.colors.textPrimary,
                )
            }
            Text(text = formatDayShort(payday), style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textPrimary)
        }
        if (registerMonth != null) {
            Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
            BudgetPrimaryButton(text = "${registerMonth.monthValue}월 월급 수입으로 등록하기", onClick = { onRegister(registerMonth) })
        }
    }
}

/**
 * 최근 내역. 오늘까지 쓴 돈을 최근 것부터, 그 돈을 벌려면 일해야 하는 시간과 함께 보여 준다.
 * 일한 시간은 달마다 다르지 않은 평균 시급으로 센다(같은 금액이 달마다 다르게 보이지 않게). 누르면 그 거래의 상세가 열린다.
 */
@Composable
private fun RecentSection(settings: SalarySettings, today: LocalDate, items: List<TransactionListItem>, onOpenTransaction: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = BudgetTheme.spacing.itemGap)) {
        StatsSection(title = "최근 내역", subtitle = "쓴 돈을 벌려면 얼마나 일해야 하는지 평균 시급으로 셌어요") {
            if (items.isEmpty()) SectionNote("아직 쓴 돈이 없어요")
        }
        if (items.isNotEmpty()) Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
        items.forEach { item ->
            val workTime = settings.secondsToEarn(item.amount)?.let { workValue(it, settings.workSecondsPerDay) }
            RecentRow(item = item, today = today, workTime = workTime, onClick = { onOpenTransaction(item.id) })
        }
    }
}

/** 최근 내역 한 줄. 오른쪽에 금액과 그 아래 일한 시간 */
@Composable
private fun RecentRow(item: TransactionListItem, today: LocalDate, workTime: String?, onClick: () -> Unit) {
    BudgetListItem(
        title = transactionTitle(item),
        subtitle = listOfNotNull(formatNoticeTime(item.occurredAt, today), item.categoryName).joinToString(" · "),
        leading = { CategoryBadge(icon = item.categoryIcon, color = item.categoryColor) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSignedAmount(item.type, item.amount),
                    style = BudgetTheme.amount.medium,
                    color = transactionAmountColor(item.type),
                )
                if (workTime != null) {
                    Text(
                        text = workTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = BudgetTheme.colors.textSecondary,
                        modifier = Modifier.semantics { contentDescription = "일한 시간 $workTime" },
                    )
                }
            }
        },
        onClick = onClick,
    )
}

/** 얼마나 벌었는지 채워 보여 주는 막대. 번 돈이라 초록으로 채운다. 뜻은 옆 글이 전하므로 화면 읽기에서 뺀다. */
@Composable
private fun EarnBar(fraction: Float) {
    val track = BudgetTheme.colors.chartTrack
    val fill = BudgetTheme.colors.chartIncome
    val corner = BudgetTheme.radius.full
    val shown = fraction.coerceIn(0f, 1f)
    Spacer(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(BudgetTheme.chart.meterHeight)
            .clearAndSetSemantics {}
            .drawBehind {
                val radius = CornerRadius(minOf(corner.toPx(), size.height / 2f))
                drawRoundRect(track, cornerRadius = radius)
                if (shown > 0f) drawRoundRect(fill, size = Size(size.width * shown, size.height), cornerRadius = radius)
            },
    )
}

/**
 * 오늘 번 돈. 가장 큰 글씨로 굴러 오르다가, 월급이 커 자리가 늘어 폭에 안 들어가면(좁은 화면) 한 단계 작은 글씨로 바꾼다.
 * 굴러 오르는 글은 글자마다 칸이라 넘치면 끝 글자가 잘린다.
 */
@Composable
private fun HeroAmount(text: String) {
    val measurer = rememberTextMeasurer()
    val hero = BudgetTheme.amount.hero
    val fallback = BudgetTheme.amount.large
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val fits = measurer.measure(text, hero, maxLines = 1).size.width <= maxPx
        RollingText(text = text, style = if (fits) hero else fallback, color = BudgetTheme.colors.income)
    }
}
