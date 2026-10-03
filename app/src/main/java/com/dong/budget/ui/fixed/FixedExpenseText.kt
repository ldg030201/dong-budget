package com.dong.budget.ui.fixed

import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.monthLabel
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 탭의 글자. 영어·기호(D-3 등) 없이 해요체로 적는다.
// 화면 읽기 문장은 금액을 줄이지 않고 1원까지 읽는다. 사용자가 적은 가게 이름 뒤에는 조사를 붙이지 않는다.
// ─────────────────────────────────────────────────────────────────────

internal const val FIXED_TAB_TITLE = "고정지출"

/** 몇 달마다 내는지. "매달", "2달마다", "매년" */
internal fun cadenceText(cadence: Int): String = when {
    cadence <= 1 -> "매달"
    cadence >= YEARLY -> "매년"
    else -> "${cadence}달마다"
}

/** 평소 내는 날. "25일쯤", 31일이면 달마다 날이 달라 "말일쯤" */
internal fun usualDayText(day: Int): String = if (day >= LAST_DAY) "말일쯤" else "${day}일쯤"

/** 평소 언제 내는지. "매달 25일쯤", "2달마다 25일쯤", 매년이면 마지막으로 낸 달을 붙여 "매년 3월 25일쯤" */
internal fun scheduleText(item: FixedExpenseItem): String = if (item.cadence >= YEARLY) {
    "매년 ${item.lastPaidOn.monthValue}월 ${usualDayText(item.usualDay)}"
} else {
    "${cadenceText(item.cadence)} ${usualDayText(item.usualDay)}"
}

/**
 * 줄의 부제(이름 아래 한 줄). 뒤에 결제수단 이름을 붙인다.
 * - 아직 안 냈어요 · 이번 달엔 안 내요: "매달 25일쯤 · 하나카드"
 * - 냈어요: "10월 3일 · 하나카드", 한 달에 두 번 냈으면 "10월 3일 외 1번 · 하나카드"
 * - 한동안 안 냈어요: "마지막 7월 3일 · 하나카드"
 */
internal fun rowSubtitle(item: FixedExpenseItem, month: YearMonth): String {
    val main =
        when (item.status) {
            FixedStatus.DUE, FixedStatus.NOT_THIS_MONTH -> scheduleText(item)

            FixedStatus.PAID -> {
                val day = dateText(item.lastPaidOn, month)
                if (item.lastPaidCount > 1) "$day 외 ${item.lastPaidCount - 1}번" else day
            }

            FixedStatus.STOPPED -> "마지막 ${dateText(item.lastPaidOn, month)}"
        }
    return listOfNotNull(main, item.paymentMethodName).joinToString(" · ")
}

/** 줄 부제 아래에 덧붙이는 한 줄의 뜻. 경고(지났거나 놓침)·오늘·그냥 알림 */
internal enum class NoteTone { WARNING, TODAY, PLAIN }

internal data class RowNote(val text: String, val tone: NoteTone)

/**
 * 줄 부제 아래에 덧붙일 한 줄. 없으면 null
 * - 아직 안 냈어요: 지난 차례를 놓쳤으면 [missedText], 이번 달 평소 날짜가 지났으면 "평소보다 3일 지났어요",
 *   오늘이면 "오늘 낼 차례예요"
 * - 냈어요: 지난번과 금액이 다르면 "지난번보다 1,000원 올랐어요" / "내렸어요"
 * - 이번 달엔 안 내요: "다음은 12월에 내요"
 */
internal fun rowNote(item: FixedExpenseItem, month: YearMonth, today: LocalDate): RowNote? = when (item.status) {
    FixedStatus.DUE -> {
        val missed = item.missedMonth
        val past = item.daysPastUsual
        when {
            missed != null -> RowNote(missedText(item, missed, month, today), NoteTone.WARNING)
            past != null && past > 0 -> RowNote("평소보다 ${past}일 지났어요", NoteTone.WARNING)
            past == 0 -> RowNote("오늘 낼 차례예요", NoteTone.TODAY)
            else -> null
        }
    }

    FixedStatus.PAID -> {
        val change = item.previousAmount?.let { item.amount - it } ?: 0
        when {
            change > 0 -> RowNote("지난번보다 ${formatAmount(change)}원 올랐어요", NoteTone.PLAIN)
            change < 0 -> RowNote("지난번보다 ${formatAmount(-change)}원 내렸어요", NoteTone.PLAIN)
            else -> null
        }
    }

    FixedStatus.NOT_THIS_MONTH -> item.nextMonth?.let { RowNote("다음은 ${monthName(it, month)}에 내요", NoteTone.PLAIN) }

    FixedStatus.STOPPED -> null
}

/**
 * 놓친 차례. 매달 내는 것은 보는 달도 낼 차례라 "9월 차례도 안 냈어요".
 * 몇 달마다·매년 내는 것은 보는 달이 낼 차례가 아니라(놓친 차례 다음 달이다) '도' 없이 "10월 차례를 아직 안 냈어요",
 * 지난 달로 보면 그 달까지의 일이라 "10월 차례를 안 냈어요".
 */
private fun missedText(item: FixedExpenseItem, missed: YearMonth, month: YearMonth, today: LocalDate): String {
    val name = monthName(missed, month)
    return when {
        item.cadence <= 1 -> "$name 차례도 안 냈어요"
        month == YearMonth.from(today) -> "$name 차례를 아직 안 냈어요"
        else -> "$name 차례를 안 냈어요"
    }
}

/** 줄 오른쪽 금액. 냈으면 낸 돈, 아니면 지난번에 낸 돈. 부호 없이 "17,000원" */
internal fun rowAmount(item: FixedExpenseItem): String = "${formatAmount(item.amount)}원"

/** '등록하기' 버튼. 줄마다 같은 버튼이라 화면 읽기에는 가게 이름을 붙인다: "넷플릭스 등록하기" */
internal const val REGISTER_TEXT = "등록하기"

internal fun registerLabel(name: String): String = "$name $REGISTER_TEXT"

/** 줄을 누르면 무엇을 하는지(화면 읽기). 그 가게의 가장 최근 거래 상세가 열린다. */
internal const val ROW_CLICK_LABEL = "최근 거래 보기"

// ── 묶음 제목 ────────────────────────────────────────────────────────

/** 묶음 제목이자 줄의 상태(화면 읽기). 지난 달을 보면 끝난 달에 맞게 적는다. */
internal fun statusTitle(status: FixedStatus, month: YearMonth, today: LocalDate): String {
    val thisMonth = month == YearMonth.from(today)
    return when (status) {
        FixedStatus.DUE -> if (thisMonth) "아직 안 냈어요" else "안 냈어요"
        FixedStatus.PAID -> "냈어요"
        FixedStatus.NOT_THIS_MONTH -> if (thisMonth) "이번 달엔 안 내요" else "${monthLabel(month, today)}엔 낼 차례가 아니었어요"
        FixedStatus.STOPPED -> STOPPED_TITLE
    }
}

/** 접어 두는 묶음의 제목. 달과 상관없이 같다. */
internal const val STOPPED_TITLE = "한동안 안 냈어요"

/** 접힌 '한동안 안 냈어요' 옆의 개수. "3개" */
internal fun countText(count: Int): String = "${count}개"

internal const val EXPANDED_STATE = "펼쳐짐"
internal const val COLLAPSED_STATE = "접힘"

// ── 요약 ──────────────────────────────────────────────────────────────

/** 요약 판 제목. "이번 달 고정지출", "9월 고정지출" */
internal fun summaryTitle(month: YearMonth, today: LocalDate): String = "${monthLabel(month, today)} 고정지출"

internal const val PAID_LABEL = "낸 돈"

/** 아직 안 낸 것들의 합. 이번 달이면 앞으로 낼 돈이고, 지난 달이면 끝내 안 낸 돈이다. */
internal fun dueLabel(month: YearMonth, today: LocalDate): String = if (month == YearMonth.from(today)) "낼 돈" else "안 낸 돈"

/** 요약 문장. "6개 중 4개 냈어요", "4개 모두 냈어요", "이번 달에 낼 고정지출이 없어요" */
internal fun countSentence(board: FixedExpenseBoard, month: YearMonth, today: LocalDate): String {
    val total = board.dueCount
    return when {
        total == 0 -> if (month == YearMonth.from(today)) "이번 달에 낼 고정지출이 없어요" else "${monthLabel(month, today)}엔 낼 고정지출이 없었어요"
        board.due.isNotEmpty() -> "${total}개 중 ${board.paid.size}개 냈어요"
        total == 1 -> "모두 냈어요"
        else -> "${total}개 모두 냈어요"
    }
}

/** 요약 판을 한 번에 읽는 문장. "이번 달 고정지출. 6개 중 4개 냈어요. 낸 돈 85,000원, 낼 돈 1,317,000원" */
internal fun summarySpoken(board: FixedExpenseBoard, month: YearMonth, today: LocalDate): String {
    val parts = mutableListOf(summaryTitle(month, today), countSentence(board, month, today))
    if (board.dueCount > 0) {
        parts += "$PAID_LABEL ${formatAmount(board.paidTotal)}원, ${dueLabel(month, today)} ${formatAmount(board.dueTotal)}원"
    }
    return parts.joinToString(". ")
}

// ── 빈 상태 · 안내 ────────────────────────────────────────────────────

internal const val NO_CATEGORY_TITLE = "고정지출 분류가 없어요"

/** 분류를 지웠을 때. 같은 이름으로 다시 만들면 저장소가 기본 분류로 알아본다(지울 때 '기타' 로 옮겨 간 지출은 돌아오지 않는다). */
internal const val NO_CATEGORY_BODY = "분류 관리에서 '고정지출' 분류를 다시 만들면, 그 분류로 등록한 지출을 여기서 볼 수 있어요."

internal const val OPEN_CATEGORIES_TEXT = "분류 관리 열기"

/** 고른 달까지 고정지출이 하나도 없을 때. 지난 달이면 그 달까지는 없었다는 뜻이다. */
internal fun emptyTitle(month: YearMonth, today: LocalDate): String = if (month == YearMonth.from(today)) {
    "고정지출로 등록한 지출이 없어요"
} else {
    "${monthLabel(month, today)}까지는 고정지출로 등록한 지출이 없어요"
}

internal const val EMPTY_BODY = "월세·구독료처럼 매달 나가는 돈을 '고정지출' 분류로 등록하면, 이번 달에 냈는지 여기서 볼 수 있어요."

/** 목록 맨 아래 안내. 어떻게 묶고 짐작하는지 알려 둔다(가게 이름을 바꿔 적으면 따로 보이는 까닭). */
internal const val FOOTNOTE = "'고정지출' 분류로 등록한 지출을 가게 이름으로 묶었어요. 언제 얼마를 내는지는 지난 기록으로 짐작해요."

// ── 날짜 ──────────────────────────────────────────────────────────────

/** 보고 있는 달과 같은 해면 "7월 3일", 다른 해면 "2025년 7월 3일" */
private fun dateText(date: LocalDate, month: YearMonth): String {
    val day = "${date.monthValue}월 ${date.dayOfMonth}일"
    return if (date.year == month.year) day else "${date.year}년 $day"
}

/** 보고 있는 달과 같은 해면 "12월", 다른 해면 "2027년 3월" */
private fun monthName(target: YearMonth, month: YearMonth): String =
    if (target.year == month.year) "${target.monthValue}월" else "${target.year}년 ${target.monthValue}월"
