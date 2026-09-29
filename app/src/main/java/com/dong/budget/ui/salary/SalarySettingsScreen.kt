package com.dong.budget.ui.salary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.salary.MAX_PAYDAY
import com.dong.budget.data.salary.PayBasis
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.components.AnimatedInputPanel
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.ErrorText
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.InputPanelBox
import com.dong.budget.ui.components.NumberKeypad
import com.dong.budget.ui.components.SegmentedToggle
import com.dong.budget.ui.components.SwitchRow
import com.dong.budget.ui.editor.DatePanel
import com.dong.budget.ui.editor.TimePanel
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatClock
import com.dong.budget.ui.format.formatKoreanWon
import com.dong.budget.ui.settings.SettingsGroup
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 아래 입력판. 무엇을 고치는 중인지 */
private enum class SalaryPanel { AMOUNT, TAKE_HOME, START_DATE, WORK_START, WORK_END, LUNCH_START, LUNCH_END, PAYDAY }

/**
 * 월급 설정. 줄을 누르면 화면 아래에 입력판(키패드·시계·날짜)이 열린다(등록창과 같은 방식). 바꾸는 대로 바로 저장한다.
 * 말이 안 되는 값(퇴근이 출근보다 이름, 요일 없음)은 그 자리에 안내하고, 고칠 때까지 월급 탭은 '월급을 정해 주세요' 로 보인다.
 *
 * @param settings 지금 설정. 저장소를 다 읽기 전에는 null 이다.
 */
@Composable
fun SalarySettingsScreen(
    settings: SalarySettings?,
    onChange: (SalarySettings) -> Unit,
    onDigit: (takeHome: Boolean, digit: String) -> Unit,
    onDeleteDigit: (takeHome: Boolean) -> Unit,
    onClearAmount: (takeHome: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var panel by rememberSaveable { mutableStateOf<SalaryPanel?>(null) }
    fun toggle(target: SalaryPanel) {
        panel = if (panel == target) null else target
    }
    // 입력판이 열려 있으면 뒤로가기는 화면이 아니라 입력판을 닫는다
    BackHandler(enabled = panel != null) { panel = null }

    // 아래쪽 줄을 누르면 열린 입력판에 가려질 수 있다. 입력판 높이가 멈춘 뒤 누른 줄이 보이게 올린다(등록창과 같다).
    val scrollState = rememberScrollState()
    val requesters = remember { SalaryPanel.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(panel) {
        val requester = requesters[panel] ?: return@LaunchedEffect
        snapshotFlow { scrollState.viewportSize }.collectLatest {
            delay(PANEL_SETTLE_MS)
            requester.bringIntoView()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "월급 설정")
            if (settings == null) return@Column

            fun row(target: SalaryPanel) = Modifier.bringIntoViewRequester(requesters.getValue(target))

            // 입력판이 열린 줄은 값을 브랜드색으로 칠해 무엇을 고치는 중인지 보인다
            val activeColor = BudgetTheme.colors.brandText
            val idleColor = BudgetTheme.colors.textSecondary

            fun valueColor(target: SalaryPanel) = if (panel == target) activeColor else idleColor

            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                SettingsGroup("급여") {
                    SegmentedToggle(
                        options = BASIS_ORDER.map { it.label() },
                        selectedIndex = BASIS_ORDER.indexOf(settings.basis),
                        onSelect = { index -> onChange(settings.copy(basis = BASIS_ORDER[index])) },
                        modifier = Modifier.padding(vertical = BudgetTheme.spacing.inlineGap),
                    )
                    ActionRow(
                        title = "세전 ${settings.basis.label()}",
                        value = if (settings.amount > 0) "${formatAmount(settings.amount)}원" else "정해 주세요",
                        valueColor = valueColor(SalaryPanel.AMOUNT),
                        onClick = { toggle(SalaryPanel.AMOUNT) },
                        modifier = row(SalaryPanel.AMOUNT),
                    )
                    amountHint(settings)?.let { HintText(it, modifier = Modifier.padding(bottom = BudgetTheme.spacing.tightGap)) }
                    ActionRow(
                        title = "실수령 월급 (선택)",
                        value = if (settings.takeHome > 0) "${formatAmount(settings.takeHome)}원" else "적지 않음",
                        valueColor = valueColor(SalaryPanel.TAKE_HOME),
                        onClick = { toggle(SalaryPanel.TAKE_HOME) },
                        modifier = row(SalaryPanel.TAKE_HOME),
                    )
                    HintText(
                        text = takeHomeHint(settings),
                        modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap),
                    )
                }

                SettingsGroup("입사일") {
                    ActionRow(
                        title = "입사일",
                        value = settings.startDate?.let { "${it.year}년 ${it.monthValue}월 ${it.dayOfMonth}일" } ?: "정하지 않음",
                        valueColor = valueColor(SalaryPanel.START_DATE),
                        onClick = { toggle(SalaryPanel.START_DATE) },
                        modifier = row(SalaryPanel.START_DATE),
                    )
                    HintText(
                        "입사일 전은 번 돈으로 치지 않아요. 올해 입사했다면 올해 번 돈도 입사일부터 세요",
                        modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap),
                    )
                }

                SettingsGroup("근무 시간") {
                    TimeRow("출근", settings.workStart, SalaryPanel.WORK_START, ::valueColor, ::toggle, row(SalaryPanel.WORK_START))
                    TimeRow("퇴근", settings.workEnd, SalaryPanel.WORK_END, ::valueColor, ::toggle, row(SalaryPanel.WORK_END))
                    if (!settings.workEnd.isAfter(settings.workStart)) ErrorText("퇴근은 출근보다 늦어야 해요")
                    SwitchRow(
                        title = "점심시간 빼기",
                        description = "점심시간에는 번 돈이 쌓이지 않아요",
                        checked = settings.skipLunch,
                        onCheckedChange = { on -> onChange(settings.copy(skipLunch = on)) },
                    )
                    TimeRow(
                        "점심 시작",
                        settings.lunchStart,
                        SalaryPanel.LUNCH_START,
                        ::valueColor,
                        ::toggle,
                        row(SalaryPanel.LUNCH_START),
                        enabled = settings.skipLunch,
                    )
                    TimeRow(
                        "점심 끝",
                        settings.lunchEnd,
                        SalaryPanel.LUNCH_END,
                        ::valueColor,
                        ::toggle,
                        row(SalaryPanel.LUNCH_END),
                        enabled = settings.skipLunch,
                    )
                    if (settings.skipLunch && !settings.lunchEnd.isAfter(settings.lunchStart)) ErrorText("점심 끝은 시작보다 늦어야 해요")
                }

                SettingsGroup("일하는 요일") {
                    WeekdayToggleRow(
                        selected = settings.workdays,
                        onToggle = { day ->
                            val days = if (day in settings.workdays) settings.workdays - day else settings.workdays + day
                            onChange(settings.copy(workdays = days))
                        },
                    )
                    if (settings.workdays.isEmpty()) ErrorText("일하는 요일을 하나 이상 골라 주세요")
                    HintText("공휴일은 따로 빼지 않아요", modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap))
                }

                SettingsGroup("월급날") {
                    ActionRow(
                        title = "월급날",
                        value = if (settings.payday == MAX_PAYDAY) "매달 말일" else "매달 ${settings.payday}일",
                        valueColor = valueColor(SalaryPanel.PAYDAY),
                        onClick = { toggle(SalaryPanel.PAYDAY) },
                        modifier = row(SalaryPanel.PAYDAY),
                    )
                    HintText(
                        "그 달에 없는 날이면 말일, 주말이면 앞 금요일에 받는 것으로 쳐요. 월급날 다음 날부터 다시 0원부터 쌓여요",
                        modifier = Modifier.padding(bottom = BudgetTheme.spacing.inlineGap),
                    )
                    SwitchRow(
                        title = "월급날 알림",
                        description = "월급날 출근 시각에 '월급 들어왔나요?' 알림을 띄워요. 누르면 월급을 수입으로 등록해요",
                        checked = settings.paydayNotice,
                        onCheckedChange = { on -> onChange(settings.copy(paydayNotice = on)) },
                    )
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }

            AnimatedInputPanel(panel = panel, modifier = Modifier.navigationBarsPadding()) { current ->
                when (current) {
                    SalaryPanel.AMOUNT, SalaryPanel.TAKE_HOME -> {
                        val takeHome = current == SalaryPanel.TAKE_HOME
                        InputPanelBox {
                            NumberKeypad(
                                onDigit = { onDigit(takeHome, it) },
                                onDelete = { onDeleteDigit(takeHome) },
                                onClear = { onClearAmount(takeHome) },
                            )
                        }
                    }

                    SalaryPanel.WORK_START -> TimeInput(settings.workStart) { onChange(settings.copy(workStart = it)) }

                    SalaryPanel.WORK_END -> TimeInput(settings.workEnd) { onChange(settings.copy(workEnd = it)) }

                    SalaryPanel.LUNCH_START -> TimeInput(settings.lunchStart) { onChange(settings.copy(lunchStart = it)) }

                    SalaryPanel.LUNCH_END -> TimeInput(settings.lunchEnd) { onChange(settings.copy(lunchEnd = it)) }

                    SalaryPanel.PAYDAY ->
                        InputPanelBox {
                            PaydayGrid(
                                selected = settings.payday,
                                onPick = { day ->
                                    onChange(settings.copy(payday = day))
                                    panel = null
                                },
                            )
                        }

                    SalaryPanel.START_DATE ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            if (settings.startDate != null) {
                                BudgetTextButton(
                                    text = "시작일 지우기",
                                    onClick = {
                                        onChange(settings.copy(startDate = null))
                                        panel = null
                                    },
                                )
                            }
                            DatePanel(
                                date = settings.startDate ?: BudgetTime.toLocalDate(Instant.now()),
                                onPick = { date: LocalDate ->
                                    onChange(settings.copy(startDate = date))
                                    panel = null
                                },
                            )
                        }
                }
            }
        }
    }
}

/** 시각 한 줄. 누르면 시계 입력판을 연다. */
@Composable
private fun TimeRow(
    title: String,
    time: LocalTime,
    target: SalaryPanel,
    valueColor: (SalaryPanel) -> Color,
    onToggle: (SalaryPanel) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    ActionRow(
        title = title,
        value = formatClock(time),
        valueColor = valueColor(target),
        onClick = { onToggle(target) },
        modifier = modifier,
        enabled = enabled,
    )
}

/** 시계 입력판. 입력판마다 따로 그려져 앞 입력판의 시계가 남지 않는다([AnimatedInputPanel]). */
@Composable
private fun TimeInput(time: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePanel(hour = time.hour, minute = time.minute, onChange = { h, m -> onPick(LocalTime.of(h, m)) })
}

/** 급여 칸 순서. 월급을 먼저 둔다(대부분 월급으로 받는다). */
private val BASIS_ORDER = listOf(PayBasis.MONTHLY, PayBasis.YEARLY)

private fun PayBasis.label(): String = when (this) {
    PayBasis.MONTHLY -> "월급"
    PayBasis.YEARLY -> "연봉"
}

/** 세전 금액 밑 안내. 연봉이면 한 달에 얼마로 치는지, 월급이면 한국어 단위로 끊어 읽기 */
private fun amountHint(settings: SalarySettings): String? = when {
    settings.amount <= 0 -> null
    settings.basis == PayBasis.YEARLY -> "${formatKoreanWon(settings.amount)} · 한 달에 ${formatKoreanWon(settings.grossMonthly.toLong())}씩 쳐요"
    else -> formatKoreanWon(settings.amount)
}

/** 실수령 밑 안내. 무엇을 무엇으로 세는지 알린다. */
private fun takeHomeHint(settings: SalarySettings): String = if (settings.takeHome > 0) {
    "${formatKoreanWon(settings.takeHome)} · 번 돈과 월급날 등록 금액은 실수령으로, 통상시급은 세전으로 세요"
} else {
    "적으면 통장에 들어오는 돈으로 쌓여요. 비워 두면 세전으로 쌓여요"
}

/** 입력판이 열린 뒤 줄을 끌어올리기까지 기다리는 시간(등록창과 같다) */
private const val PANEL_SETTLE_MS = 100L
