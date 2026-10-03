package com.dong.budget.ui.card

import com.dong.budget.data.card.MAX_PERFORMANCE_START_DAY
import com.dong.budget.data.card.MAX_PERFORMANCE_TIERS
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatKoreanWon
import com.dong.budget.ui.format.formatMonth
import java.time.LocalDate
import java.time.YearMonth

// ─── 카드실적 문구 ───
// 영어·기호 없이 해요체로 적는다. 구간 금액은 만 단위로 끊되 1원까지 적고(30만원, 30만 5,000원), 남은 돈은 숫자로 1원까지 적는다.
// 쓴 돈이 구간 금액과 같아도 '채웠어요' 다(카드사의 '이상'). 지난 기간은 '남았어요' 대신 '모자랐어요' 로 끝난다.
// 사용자가 지은 카드 이름 뒤에는 조사를 붙이지 않는다. 화면 읽기 문장은 보이는 글의 ' · ' 를 쉼표로 바꿔 읽는다.

internal const val CARD_PERFORMANCE_TITLE = "카드실적"

/** 실적을 하나도 안 적었을 때 탭 맨 위 안내 */
internal const val INTRO_TEXT = "카드사 앱에서 전월 실적 기준을 확인해 적어 두면, 이번 달에 얼마 더 써야 하는지 알려 줘요"

/** 탭 맨 아래 작은 안내 */
internal const val FOOTNOTE_TEXT = "카드사마다 실적에서 빼는 결제(공과금·상품권 등)가 달라서, 실제 실적과 다를 수 있어요."

internal const val UNTRACKED_TITLE = "실적을 적지 않은 카드"

internal const val UNTRACKED_SUBTITLE = "실적 구간을 적으면 얼마 더 쓰면 되는지 알려 줘요"

internal const val ADD_PERFORMANCE = "실적 추가"

internal const val EMPTY_CARDS_TITLE = "카드가 없어요"

internal const val EMPTY_CARDS_BODY = "분류 관리의 결제수단에서 카드를 추가하면\n카드마다 실적을 볼 수 있어요."

/** 상세에서 실적을 안 적었을 때 머리 안내 */
internal const val NO_TIERS_TEXT = "실적 구간을 적으면 얼마 더 쓰면 되는지 알려 줘요"

internal const val HISTORY_TITLE = "최근 6개월"

internal const val TIERS_TITLE = "구간"

internal const val NO_TRANSACTIONS_TEXT = "이 기간에 이 카드로 쓴 돈이 없어요"

/** 편집의 구간 묶음 아래 안내 */
internal const val TIERS_HINT = "카드사 혜택 안내의 '전월 실적 30만원 이상' 같은 금액을 적어 주세요"

internal val TIERS_FULL_HINT = "구간은 ${MAX_PERFORMANCE_TIERS}개까지 적을 수 있어요"

/** 기간 이름. 올해면 "10월 실적", 다른 해면 "2025년 12월 실적" */
internal fun periodName(month: YearMonth, today: LocalDate): String =
    if (month.year == today.year) "${month.monthValue}월 실적" else "${formatMonth(month)} 실적"

/** 기간 날짜. "10월 1일 ~ 10월 31일" */
internal fun periodRange(period: PerformancePeriod): String = "${monthDay(period.start)} ~ ${monthDay(period.lastDay)}"

/** 화면 읽기용 기간 날짜. "10월 1일부터 10월 31일까지" */
internal fun spokenPeriodRange(period: PerformancePeriod): String = "${monthDay(period.start)}부터 ${monthDay(period.lastDay)}까지"

private fun monthDay(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

/**
 * 상세 기간 줄에서 오늘이 든 기간으로 돌아가는 버튼. 시작일이 1일이면 기간이 달력 달과 같아 통계처럼 "이번 달" 이다.
 * 아니면 "이번 기간" 이다. 10월 3일에 시작일이 15일인 카드의 이번 기간은 '9월 실적' 이라, '이번 달' 을 눌러 9월이 나오지 않게 한다.
 * 같은 줄의 화살표도 '이전 기간'·'다음 기간' 이라고 읽힌다.
 */
internal fun thisPeriodLabel(period: PerformancePeriod): String = if (period.start.dayOfMonth == 1) "이번 달" else "이번 기간"

/** 이번 기간에 남은 날. 마지막 날이면 "오늘이 마지막 날이에요" */
internal fun daysLeftText(days: Int): String = if (days <= 1) "오늘이 마지막 날이에요" else "${days}일 남았어요"

/** 쓴 돈. "123,450원", 환불받은 돈이 더 많으면 "-1,000원" */
internal fun spentText(spent: Long): String = if (spent < 0) "-${formatAmount(-spent)}원" else "${formatAmount(spent)}원"

/** 화면 읽기용 쓴 돈. "123,450원", 환불받은 돈이 더 많으면 "환불받은 돈이 1,000원 더 많아요" */
internal fun spokenSpent(spent: Long): String = if (spent < 0) "환불받은 돈이 ${formatAmount(-spent)}원 더 많아요" else "${formatAmount(spent)}원"

/** 구간 금액. 만 단위로 끊고 1원까지 적는다. "30만원", "30만 5,000원" */
internal fun tierName(amount: Long): String = formatKoreanWon(amount)

/**
 * 구간을 얼마나 채웠는지 한 문장. 구간이 없으면 빈 글이다.
 * - 하나뿐: "실적 30만원까지 176,550원 남았어요" / "실적 30만원을 채웠어요"
 * - 여럿: "첫 구간 30만원까지 …" / "30만원 구간을 채웠어요 · 70만원까지 176,550원 남았어요" / "가장 높은 구간 70만원을 채웠어요"
 * @param past 지난 기간이면 '남았어요' 대신 '모자랐어요'
 */
internal fun tierSentence(progress: TierProgress, past: Boolean): String {
    val next = progress.next
    val reached = progress.reached
    val gap = "${formatAmount(progress.remaining)}원 ${if (past) "모자랐어요" else "남았어요"}"
    return when {
        progress.tiers.isEmpty() -> ""
        progress.tiers.size == 1 && next == null -> "실적 ${tierName(progress.tiers.first())}을 채웠어요"
        progress.tiers.size == 1 -> "실적 ${tierName(progress.tiers.first())}까지 $gap"
        next == null -> "가장 높은 구간 ${tierName(progress.tiers.last())}을 채웠어요"
        reached == null -> "첫 구간 ${tierName(next)}까지 $gap"
        else -> "${tierName(reached)} 구간을 채웠어요 · ${tierName(next)}까지 $gap"
    }
}

/** 지난 기간 한 줄. "9월 실적 523,000원 · 30만원 구간을 채웠어요" / "9월 실적 120,000원 · 30만원까지 180,000원 모자랐어요" */
internal fun previousLine(month: YearMonth, today: LocalDate, progress: TierProgress): String {
    val head = "${periodName(month, today)} ${spentText(progress.spent)}"
    val first = progress.tiers.firstOrNull() ?: return head
    val reached = progress.reached
    val status =
        if (reached != null) "${tierName(reached)} 구간을 채웠어요" else "${tierName(first)}까지 ${formatAmount(progress.remaining)}원 모자랐어요"
    return "$head · $status"
}

/**
 * 상세 머리의 남은 날 한 줄. "29일 남았어요 · 하루에 6,088원씩 쓰면 돼요". 다 채웠으면 남은 날만.
 * 마지막 날은 나눌 날이 없어 "오늘이 마지막 날이에요 · 오늘 176,550원 더 쓰면 돼요" 다.
 */
internal fun daysLeftLine(daysLeft: Int, need: Long?): String = when {
    need == null -> daysLeftText(daysLeft)
    daysLeft <= 1 -> "${daysLeftText(daysLeft)} · 오늘 ${formatAmount(need)}원 더 쓰면 돼요"
    else -> "${daysLeftText(daysLeft)} · 하루에 ${formatAmount(need)}원씩 쓰면 돼요"
}

/** 화면 읽기는 보이는 글의 ' · ' 를 쉼표로 읽는다 */
internal fun spoken(text: String): String = text.replace(" · ", ", ")

/**
 * 탭의 카드 판을 화면 읽기가 한 번에 읽을 문장. 금액은 줄이지 않는다.
 * "하나카드, 10월 실적 123,450원, 10월 1일부터 10월 31일까지, 29일 남았어요. 첫 구간 30만원까지 176,550원 남았어요. 9월 실적 …"
 */
internal fun trackedCardDescription(item: TrackedCard, today: LocalDate): String = listOf(
    "${item.card.name}, ${periodName(item.period.month, today)} ${spokenSpent(item.progress.spent)}, " +
        "${spokenPeriodRange(item.period)}, ${daysLeftText(item.daysLeft)}",
    spoken(tierSentence(item.progress, past = false)),
    spoken(previousLine(item.previousMonth, today, item.previous)),
).joinToString(". ")

/** 실적을 안 적은 카드 줄의 부제. "이번 달 123,000원 썼어요", 시작일을 바꿨으면 "9월 15일부터 …" */
internal fun untrackedSubtitle(item: UntrackedCard): String {
    val since = if (item.period.start.dayOfMonth == 1) "이번 달" else "${monthDay(item.period.start)}부터"
    return when {
        item.spent > 0 -> "$since ${formatAmount(item.spent)}원 썼어요"
        item.spent < 0 -> "$since 환불받은 돈이 더 많아요"
        else -> "$since 쓴 돈이 없어요"
    }
}

/** 실적 추가 버튼을 화면 읽기가 읽을 말. 줄마다 같은 버튼이라 카드 이름을 붙인다. "하나카드 실적 추가" */
internal fun addPerformanceLabel(name: String): String = "$name $ADD_PERFORMANCE"

/**
 * 최근 6개월 막대 아래 한 줄. 가장 높은 구간을 몇 번 채웠는지. 구간이 없으면 null
 * "최근 6개월 중 4번 가장 높은 구간을 채웠어요" / 구간이 하나면 "… 실적을 채웠어요"
 */
internal fun historySummary(history: List<PeriodSpent>, tiers: List<Long>): String? {
    if (tiers.isEmpty()) return null
    val goal = if (tiers.size == 1) "실적을" else "가장 높은 구간을"
    val count = history.count { it.progress.allReached }
    return when (count) {
        0 -> "최근 6개월에는 $goal 채운 적이 없어요"
        history.size -> "최근 6개월 모두 $goal 채웠어요"
        else -> "최근 6개월 중 ${count}번 $goal 채웠어요"
    }
}

/** 막대 한 칸을 읽는 문장. "2026년 10월 실적, 523,000원, 30만원 구간을 채웠어요" */
internal fun historySlotDescription(entry: PeriodSpent, past: Boolean): String = listOfNotNull(
    "${formatMonth(entry.period.month)} 실적",
    spokenSpent(entry.progress.spent),
    tierSentence(entry.progress, past).takeIf { it.isNotEmpty() }?.let(::spoken),
).joinToString(", ")

/** 구간 목록 한 줄의 상태. "채웠어요" / "176,550원 남았어요" / 지난 기간은 "176,550원 모자랐어요" */
internal fun tierStatus(tier: Long, spent: Long, past: Boolean): String =
    if (spent >= tier) "채웠어요" else "${formatAmount(tier - spent)}원 ${if (past) "모자랐어요" else "남았어요"}"

/** 구간 차례. "1구간" */
internal fun tierOrder(index: Int): String = "${index + 1}구간"

/**
 * 상세 머리를 화면 읽기가 한 번에 읽을 문장.
 * "10월 실적, 123,450원, 첫 구간 30만원까지 176,550원 남았어요, 29일 남았어요, 하루에 6,088원씩 쓰면 돼요"
 */
internal fun detailHeaderDescription(state: CardPerformanceDetailUiState): String {
    val period = state.period ?: return ""
    return buildList {
        add(periodName(period.month, state.today))
        add(spokenSpent(state.progress.spent))
        if (state.tiers.isEmpty()) {
            add(NO_TIERS_TEXT)
        } else {
            add(spoken(tierSentence(state.progress, past = !state.isCurrent)))
            if (state.isCurrent) add(spoken(daysLeftLine(state.daysLeft, dailyNeed(state.progress, state.daysLeft))))
        }
    }.joinToString(", ")
}

/** 시작일 줄의 값. "매달 1일부터", 31일은 "매달 말일부터" */
internal fun startDayValue(day: Int): String = if (day == MAX_PERFORMANCE_START_DAY) "매달 말일부터" else "매달 ${day}일부터"

/** 날짜판 칸을 화면 읽기가 읽을 말. 31일은 없는 달이 있어 말일이라고 알린다. */
internal fun startDayCellDescription(day: Int): String = if (day == MAX_PERFORMANCE_START_DAY) "매달 31일부터, 없는 달은 말일부터" else "매달 ${day}일부터"

/**
 * 시작일 줄 아래 예시. 오늘이 든 기간으로 어느 날부터 어느 날까지를 몇 월 실적으로 세는지 보여 준다.
 * "10월 1일 ~ 10월 31일을 10월 실적으로 세요". 29일부터는 없는 달이 있어 말일부터 센다는 말을 붙인다.
 */
internal fun startDayHint(today: LocalDate, day: Int): String {
    val period = currentPeriod(today, day)
    val example = "${periodRange(period)}을 ${periodName(period.month, today)}으로 세요"
    return if (day >= SHORT_MONTH_DAY) "$example. 그 달에 없는 날이면 말일부터 세요" else example
}

/** 이날부터는 그날이 없는 달이 있다(2월 29일) */
private const val SHORT_MONTH_DAY = 29

/** 편집의 구간 줄 값. "300,000원", 아직 안 적었으면 "정해 주세요" */
internal fun tierRowValue(amount: Long): String = if (amount > 0) "${formatAmount(amount)}원" else "정해 주세요"

/**
 * 편집의 구간 줄 아래 안내. 앞 줄과 같은 금액이면 하나로 친다고 알리고, 아니면 1만 원부터 한국어 단위로 끊어 읽어 준다.
 * 없으면 null
 */
internal fun tierRowHint(rows: List<Long>, index: Int): String? {
    val amount = rows[index]
    if (amount <= 0) return null
    val same = rows.subList(0, index).indexOf(amount)
    return when {
        same >= 0 -> "${tierOrder(same)}과 같은 금액이라 하나로 쳐요"
        amount >= KOREAN_WON_FROM -> formatKoreanWon(amount)
        else -> null
    }
}

/** 이 금액부터 한국어 단위로 끊어 적는다(월급 설정과 같다) */
private const val KOREAN_WON_FROM = 10_000L

/** 구간 줄 지우기 버튼을 화면 읽기가 읽을 말. "1구간 지우기" */
internal fun deleteTierLabel(index: Int): String = "${tierOrder(index)} 지우기"
