package com.dong.budget.ui.fixed

import com.dong.budget.data.db.FixedKind
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.monthLabel
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 고정지출 · 고정저축 탭의 글자. 영어·기호(D-3 등) 없이 해요체로 적는다.
// 화면 읽기 문장은 금액을 줄이지 않고 1원까지 읽는다. 사용자가 적은 가게 이름 뒤에는 조사를 붙이지 않는다.
// 두 탭이 다르게 적는 문장(탭 이름 · 분류 이름이 든 것)은 [FixedTexts] 에 종류마다 두고, 나머지는 두 탭이 같이 쓴다.
// ─────────────────────────────────────────────────────────────────────

/** 몇 달마다 내는지. "매달", "2달마다", "매년" */
internal fun cadenceText(cadence: Int): String = when {
    cadence <= 1 -> "매달"
    cadence >= YEARLY -> "매년"
    else -> "${cadence}달마다"
}

/** 평소 내는 날. "25일쯤", 31일이면 달마다 날이 달라 "말일쯤" */
internal fun usualDayText(day: Int): String = if (day >= LAST_DAY) "말일쯤" else "${day}일쯤"

/** 차례마다 평소 내는 날. 한 차례면 [usualDayText], 한 달에 두 차례면 "3일, 28일쯤" · "15일, 말일쯤" */
internal fun usualDaysText(days: List<Int>): String = (days.dropLast(1).map(::dayName) + usualDayText(days.last())).joinToString(", ")

/** 평소 날의 이름. "3일", 31일이면 "말일" */
private fun dayName(day: Int): String = if (day >= LAST_DAY) "말일" else "${day}일"

/**
 * 평소 언제 내는지. "매달 25일쯤", "2달마다 25일쯤", 한 달에 두 차례면 "매달 3일, 28일쯤",
 * 매년이면 마지막으로 낸 몫의 달을 붙여 "매년 3월 25일쯤". 낸 날의 달이 아니라 몇 월 몫인지로 적는다.
 * 덧붙임의 '다음은 …에 내요' 도 몫의 달에서 센다. 자주 내는 가게는 평소 날 없이 "한 달에 13번쯤" 이다.
 */
internal fun scheduleText(item: FixedExpenseItem): String {
    val times = item.timesPerMonth
    return when {
        times != null -> "한 달에 ${times}번쯤"
        item.cadence >= YEARLY -> "매년 ${item.lastShareMonth.monthValue}월 ${usualDaysText(item.usualDays)}"
        else -> "${cadenceText(item.cadence)} ${usualDaysText(item.usualDays)}"
    }
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
    return listOfNotNull(main, item.latest.paymentMethodName).joinToString(" · ")
}

/** 줄 부제 아래에 덧붙이는 한 줄의 뜻. 경고(지났거나 놓침)·오늘·그냥 알림 */
internal enum class NoteTone { WARNING, TODAY, PLAIN }

internal data class RowNote(val text: String, val tone: NoteTone)

/**
 * 줄 부제 아래에 덧붙일 한 줄. 없으면 null
 * - 아직 안 냈어요: 지난 차례를 놓쳤으면 [missedText], 앞 차례의 낼 날이 아직이면 "10월 차례는 11월 2일에 내요",
 *   이번 달 낼 날(평소 날짜가 쉬는 날이면 다음 영업일, [FixedExpenseItem.dueDateIn])이 지났으면 "평소보다 3일 지났어요",
 *   오늘이면 "오늘 낼 차례예요". 지난 달인데 그 달 낼 날이 아직이면(말일이 쉬는 날이라 다음 달 초에 나감) "11월 2일에 낼 차례예요".
 *   한 달 몫을 여러 번에 나눠 내는데 일부만 냈으면 앞에 "2번 중 1번 냈어요" 를 붙인다("2번 중 1번 냈고, 남은 건 평소보다 2일 지났어요").
 *   하나도 안 냈는데 안 나간 차례([FixedExpenseItem.droppedDays])가 있으면 "3일 차례는 안 나갔어요"("3일 차례는 안 나갔고, 남은 건 오늘 낼 차례예요").
 * - 냈어요: 나머지가 안 나갔으면 "2번 중 1번만 냈어요". 지난번과 금액이 다르면 "지난번보다 1,000원 올랐어요" / "내렸어요"
 * - 이번 달엔 안 내요: "다음은 12월에 내요". 다음 차례 몫을 미리 냈으면 "3월 몫을 2월 22일에 미리 냈어요"
 */
internal fun rowNote(item: FixedExpenseItem, month: YearMonth, today: LocalDate): RowNote? = when (item.status) {
    FixedStatus.DUE -> {
        val due = dueNote(item, month, today)
        when {
            item.paidCount <= 0 && item.droppedDays.isNotEmpty() -> droppedNote(item.droppedDays, due)
            item.paidCount <= 0 -> due
            due == null -> RowNote(partlyText(item) + "어요", NoteTone.PLAIN)
            else -> RowNote("${partlyText(item)}고, 남은 건 ${due.text}", due.tone)
        }
    }

    FixedStatus.PAID -> {
        val change = item.previousAmount?.let { item.amount - it } ?: 0
        when {
            item.paidCount < item.requiredCount -> RowNote("${item.requiredCount}번 중 ${item.paidCount}번만 냈어요", NoteTone.PLAIN)
            change > 0 -> RowNote("지난번보다 ${formatAmount(change)}원 올랐어요", NoteTone.PLAIN)
            change < 0 -> RowNote("지난번보다 ${formatAmount(-change)}원 내렸어요", NoteTone.PLAIN)
            else -> null
        }
    }

    FixedStatus.NOT_THIS_MONTH -> {
        val prepaid = item.prepaidMonth
        val on = item.prepaidOn
        when {
            prepaid != null && on != null -> RowNote("${monthName(prepaid, month)} 몫을 ${dateText(on, month)}에 미리 냈어요", NoteTone.PLAIN)
            else -> item.nextMonth?.let { RowNote("다음은 ${monthName(it, month)}에 내요", NoteTone.PLAIN) }
        }
    }

    FixedStatus.STOPPED -> null
}

/** 아직 안 냈어요 줄의 낼 날 알림(일부만 낸 것은 남은 차례의 낼 날). 자주 내는 가게는 낼 날이 없어 놓친 달만 알린다. 없으면 null */
private fun dueNote(item: FixedExpenseItem, month: YearMonth, today: LocalDate): RowNote? {
    val missed = item.missedMonth
    val waiting = item.waitingMonth
    val past = item.daysPastUsual
    val due = item.dueDateIn(month)
    return when {
        missed != null -> RowNote(missedText(item, missed, month, today), NoteTone.WARNING)
        waiting != null -> waitingNote(waiting, dueDateOf(waiting, item.usualDays.last()), month, today)
        item.timesPerMonth != null -> null
        past != null && past > 0 -> RowNote("평소보다 ${past}일 지났어요", NoteTone.WARNING)
        past == 0 || (past == null && due == today) -> RowNote("오늘 낼 차례예요", NoteTone.TODAY)
        past == null && due.isAfter(today) -> RowNote("${dateText(due, month)}에 낼 차례예요", NoteTone.PLAIN)
        else -> null
    }
}

/** 앞 차례 달([waiting])이 비었는데 그 낼 날([due])이 아직이다. "10월 차례는 11월 2일에 내요", 그날이면 "10월 차례는 오늘 내요" */
private fun waitingNote(waiting: YearMonth, due: LocalDate, month: YearMonth, today: LocalDate): RowNote {
    val name = monthName(waiting, month)
    return if (due == today) {
        RowNote("$name 차례는 오늘 내요", NoteTone.TODAY)
    } else {
        RowNote("$name 차례는 ${dateText(due, month)}에 내요", NoteTone.PLAIN)
    }
}

/**
 * 하나도 안 냈는데 안 나간 차례([days], 해지했거나 건너뜀)가 있다. "3일 차례는 안 나갔어요", 남은 차례의 낼 날 알림([due])이 있으면
 * "3일 차례는 안 나갔고, 남은 건 평소보다 2일 지났어요"(톤은 낼 날 알림 것). 말일이면 "말일 차례".
 */
private fun droppedNote(days: List<Int>, due: RowNote?): RowNote {
    val dropped = "${days.joinToString(", ", transform = ::dayName)} 차례는 안 나갔"
    return if (due == null) RowNote(dropped + "어요", NoteTone.PLAIN) else RowNote("${dropped}고, 남은 건 ${due.text}", due.tone)
}

/** 한 달 몫을 여러 번에 나눠 내는데 일부만 냈다. 뒤에 '어요' · '고' 를 붙인다: "2번 중 1번 냈" */
private fun partlyText(item: FixedExpenseItem): String = "${item.requiredCount}번 중 ${item.paidCount}번 냈"

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

/** 줄 오른쪽 금액. 냈으면 낸 돈, 일부만 냈으면 남은 돈, 아니면 지난번에 낸 돈. 부호 없이 "17,000원" */
internal fun rowAmount(item: FixedExpenseItem): String = "${formatAmount(item.amount)}원"

/** '등록하기' 버튼. 줄마다 같은 버튼이라 화면 읽기에는 가게 이름을 붙인다: "넷플릭스 등록하기" */
internal const val REGISTER_TEXT = "등록하기"

internal fun registerLabel(name: String): String = "$name $REGISTER_TEXT"

/** 줄을 누르면 무엇을 하는지(화면 읽기). 그 가게의 가장 최근 거래 상세가 열린다. */
internal const val ROW_CLICK_LABEL = "최근 거래 보기"

// ── 묶음 제목 ────────────────────────────────────────────────────────

/**
 * 묶음 제목. 지난 달을 보면 끝난 달에 맞게 적는다. 다만 아직 안 낸 것은 [open] 이면(이번 달, 또는 지난 달에 낼 날이 아직 안 온 줄이 있으면,
 * [isStillOpen]) '아직 안 냈어요' 다. 줄의 상태는 [rowStatus] 다.
 */
internal fun statusTitle(status: FixedStatus, month: YearMonth, today: LocalDate, open: Boolean = month >= YearMonth.from(today)): String {
    val thisMonth = month == YearMonth.from(today)
    return when (status) {
        FixedStatus.DUE -> if (open) "아직 안 냈어요" else "안 냈어요"
        FixedStatus.PAID -> "냈어요"
        FixedStatus.NOT_THIS_MONTH -> if (thisMonth) "이번 달엔 안 내요" else "${monthLabel(month, today)}엔 낼 차례가 아니었어요"
        FixedStatus.STOPPED -> STOPPED_TITLE
    }
}

/**
 * 줄의 상태(화면 읽기). 묶음 제목과 같되 줄마다 본다. 지난 달이어도 그 줄의 낼 날이 아직이면 '아직 안 냈어요' 이고([isStillDue]),
 * 한 달 몫을 여러 번에 나눠 내는데 일부만 냈으면 "아직 덜 냈어요"(끝난 지난 달이면 "덜 냈어요") 다.
 */
internal fun rowStatus(item: FixedExpenseItem, month: YearMonth, today: LocalDate): String {
    val open = isStillDue(item, month, today)
    if (item.status != FixedStatus.DUE || item.paidCount <= 0) return statusTitle(item.status, month, today, open)
    return if (open) "아직 덜 냈어요" else "덜 냈어요"
}

/**
 * 아직 안 낸 줄을 [month] 에 아직 낼 수 있는지. 이번 달이면 늘 그렇고, 지난 달이면 그 달 낼 날([FixedExpenseItem.dueDateIn])이
 * 아직 안 왔을 때다(말일이 쉬는 날이라 다음 달 초에 나가는 차례). 같은 날 본 이번 달 화면도 그 차례를 놓쳤다고 하지 않는다.
 */
internal fun isStillDue(item: FixedExpenseItem, month: YearMonth, today: LocalDate): Boolean =
    month >= YearMonth.from(today) || !today.isAfter(item.dueDateIn(month))

/** [month] 에 아직 낼 수 있는 것이 남았는지. 이번 달이거나, 지난 달인데 낼 날이 아직 안 온 줄이 있을 때다([isStillDue]). */
internal fun isStillOpen(board: FixedExpenseBoard, month: YearMonth, today: LocalDate): Boolean =
    month >= YearMonth.from(today) || board.due.any { isStillDue(it, month, today) }

/** 접어 두는 묶음의 제목. 달과 상관없이 같다. */
internal const val STOPPED_TITLE = "한동안 안 냈어요"

/** 접힌 '한동안 안 냈어요' 옆의 개수. "3개" */
internal fun countText(count: Int): String = "${count}개"

internal const val EXPANDED_STATE = "펼쳐짐"
internal const val COLLAPSED_STATE = "접힘"

// ── 요약 ──────────────────────────────────────────────────────────────

internal const val PAID_LABEL = "낸 돈"

/**
 * 아직 안 낸 것들의 합. 이번 달이면 앞으로 낼 돈이고, 지난 달이면 끝내 안 낸 돈이다. 지난 달이어도 낼 날이 아직 안 온 줄이 있으면
 * 앞으로 낼 돈이다([isStillOpen], 묶음 제목 '아직 안 냈어요' 와 맞춘다).
 */
internal fun dueLabel(board: FixedExpenseBoard, month: YearMonth, today: LocalDate): String =
    if (isStillOpen(board, month, today)) "낼 돈" else "안 낸 돈"

/**
 * 요약 문장. "6개 중 4개 냈어요", "4개 모두 냈어요", "이번 달에 낼 고정지출이 없어요"
 * @param hasEnded 해지한 곳이 있는지([FixedExpenseUiState.hasEnded]). 있으면 판이 비어도 목록에 넣은 적은 있다.
 */
internal fun FixedTexts.countSentence(board: FixedExpenseBoard, month: YearMonth, today: LocalDate, hasEnded: Boolean = false): String {
    val total = board.dueCount
    return when {
        // 목록에 넣은 곳이 없는데 묻는 곳 · 등록 안 한 곳만 있을 때. '낼 고정지출이 없어요' 로 적으면 고정지출이 정말 없는 것처럼 읽힌다.
        // 해지한 곳이 있으면 목록에 넣은 적은 있고 끝 달 뒤라 판에 없을 뿐이라(끝 달까지의 판에는 있다) 그 달에 낼 것이 없다고 적는다.
        board.isEmpty && !hasEnded -> noRegisteredSentence(month, today)

        total == 0 -> nothingDueSentence(month, today)

        board.due.isNotEmpty() -> "${total}개 중 ${board.paid.size}개 냈어요"

        total == 1 -> "모두 냈어요"

        else -> "${total}개 모두 냈어요"
    }
}

/** 요약 판을 한 번에 읽는 문장. "이번 달 고정지출. 6개 중 4개 냈어요. 낸 돈 85,000원, 낼 돈 1,317,000원". [hasEnded] 는 [countSentence] 와 같다. */
internal fun FixedTexts.summarySpoken(board: FixedExpenseBoard, month: YearMonth, today: LocalDate, hasEnded: Boolean = false): String {
    val parts = mutableListOf(summaryTitle(month, today), countSentence(board, month, today, hasEnded))
    if (board.dueCount > 0) {
        parts += "$PAID_LABEL ${formatAmount(board.paidTotal)}원, ${dueLabel(board, month, today)} ${formatAmount(board.dueTotal)}원"
    }
    return parts.joinToString(". ")
}

// ── 빈 상태 · 안내 ────────────────────────────────────────────────────
// 분류를 지웠을 때 · 비었을 때의 제목과 글, 목록 맨 아래 안내는 [FixedTexts] 에 있다.

internal const val OPEN_CATEGORIES_TEXT = "분류 관리 열기"

// ── 고정지출 목록(등록할지 묻기 · 빼기) ─────────────────────────────────
// '고정지출' 분류와 탭의 목록은 따로다(고정저축도 '저축' 분류와 목록이 따로다). 분류로 적은 곳 가운데 목록에 등록한 곳만 판에 보이고,
// 처음 보는 곳은 등록할지 묻는다. 묻는 묶음 제목 · 안내와 빼기 문장은 [FixedTexts] 에 있다.

internal const val ASK_REGISTER_TEXT = "등록"

internal const val ASK_DECLINE_TEXT = "안 함"

/** '안 함' 버튼(화면 읽기): "넷플릭스 등록 안 함" */
internal fun askDeclineLabel(name: String): String = "$name 등록 안 함"

/** 묻는 줄 · 등록 안 한 줄 · 해지한 줄의 부제. 가장 최근에(해지한 줄은 해지 전에) 낸 날과 결제수단: "마지막 10월 3일 · 하나카드" */
internal fun candidateSubtitle(candidate: FixedCandidate, today: LocalDate): String =
    listOfNotNull("마지막 ${dateText(candidate.lastPaidOn, YearMonth.from(today))}", candidate.latest.paymentMethodName).joinToString(" · ")

/** 묻는 줄 · 등록 안 한 줄 · 해지한 줄 오른쪽 금액. 부제의 그때 낸 돈 */
internal fun candidateAmount(candidate: FixedCandidate): String = "${formatAmount(candidate.latest.amount)}원"

/** 접어 두는 등록 안 한 곳 묶음의 제목. '안 함' 을 눌렀거나 목록에서 뺀 곳이다. */
internal const val DECLINED_TITLE = "등록 안 한 곳"

/** 등록 안 한 줄의 상태(화면 읽기) */
internal const val DECLINED_STATE = "등록 안 함"

internal const val REMOVE_CONFIRM = "빼기"

// ── 해지 ──────────────────────────────────────────────────────────────
// 해지한 곳은 해지 전에 낸 마지막 몫의 달까지는 판에 그대로 보이고, 그다음 달부터는 접어 둔 '해지한 곳' 에만 있다.

/** 판 줄의 '해지' 버튼. 해지 확인 창의 확인, 빼기 확인 창의 보조 동작도 같은 말이다. */
internal const val END_TEXT = "해지"

/** '해지' 버튼(화면 읽기). 줄마다 같은 버튼이라 가게 이름을 붙인다: "넷플릭스 해지" */
internal fun endLabel(name: String): String = "$name $END_TEXT"

/** 해지 확인 창 제목. 사용자가 적은 가게 이름 뒤에 조사를 붙이지 않게 따옴표로 묶는다: "'넷플릭스' 해지했나요?" */
internal fun endTitle(name: String): String = "'$name' 해지했나요?"

/** 해지 확인 창 글. 언제까지 보이는지와 다시 묻는 때를 알린다. */
internal const val END_MESSAGE = "마지막으로 낸 달까지는 그대로 보이고, 다음 달부터 목록에서 안 보여요. 다시 내면 등록할지 물어볼게요."

/** 접어 두는 해지한 곳 묶음의 제목. 달과 상관없이 같다. */
internal const val ENDED_TITLE = "해지한 곳"

/** 해지한 줄의 상태(화면 읽기) */
internal const val ENDED_STATE = "해지함"

/** 해지한 줄의 '되돌리기'. 다시 등록한 곳이 된다. */
internal const val RESTORE_TEXT = "되돌리기"

/** '되돌리기' 버튼(화면 읽기). 줄마다 같은 버튼이라 가게 이름을 붙인다: "넷플릭스 해지 되돌리기" */
internal fun restoreLabel(name: String): String = "$name 해지 $RESTORE_TEXT"

// ── 종류마다 다른 문장 ──────────────────────────────────────────────

/** [kind] 탭의 문장 묶음 */
internal val FixedKind.texts: FixedTexts
    get() =
        when (this) {
            FixedKind.EXPENSE -> FixedTexts.Expense
            FixedKind.SAVINGS -> FixedTexts.Savings
        }

/**
 * 고정지출 · 고정저축 탭이 서로 다르게 적는 문장. 종류마다 하나다([FixedKind.texts]).
 * '고정지출로' · '고정저축으로' 처럼 받침에 따라 조사가 달라 이름만 끼우지 않고 문장째 둔다. 고정저축이 보는 분류는 '저축' 이다.
 * 상태 말('냈어요' · '아직 안 냈어요' · '낸 돈' · '해지')은 두 탭이 같이 쓴다. 적금 · 청약도 '냈다' · '해지했다' 고 한다.
 */
internal sealed interface FixedTexts {
    /** 탭 머리 이름. "고정지출". 머리를 그릴 때만 쓴다(FixedExpenseScreen 의 showHeader). */
    val tabTitle: String

    /** 요약 판 제목. "이번 달 고정지출", "9월 고정지출" */
    fun summaryTitle(month: YearMonth, today: LocalDate): String

    /**
     * 등록한 곳은 있는데 고른 달에 낼 것이 하나도 없을 때의 요약 문장. 해지한 곳만 있어 판이 빈 달(끝 달 뒤)도 이것이다.
     * "이번 달에 낼 고정지출이 없어요", "9월엔 낼 고정지출이 없었어요"
     */
    fun nothingDueSentence(month: YearMonth, today: LocalDate): String

    /**
     * 목록에 등록한 곳이 고른 달까지 하나도 없는데 물을 곳이나 등록 안 한 곳만 있을 때 요약 자리의 문장. 해지한 곳이 있으면
     * 목록에 넣은 적은 있어 쓰지 않는다([countSentence]). "아직 목록에 넣은 고정지출이 없어요", 지난 달이면 "9월까지는 목록에 넣은 고정지출이 없어요"
     */
    fun noRegisteredSentence(month: YearMonth, today: LocalDate): String

    /** 분류를 지웠을 때의 제목. "고정지출 분류가 없어요" */
    val noCategoryTitle: String

    /**
     * 분류를 지웠을 때. 같은 이름으로 다시 만들면 저장소가 기본 분류로 알아본다(지울 때 '기타' 로 옮겨 간 지출은 돌아오지 않는다).
     * 코드 없이 같은 이름(띄어쓰기 무시)으로 만들어 둔 분류는 DB 를 열 때 · 백업을 되살린 뒤 · 분류를 지운 뒤에 알아보므로,
     * 이 안내는 그런 분류가 정말 없을 때만 뜬다(분류 관리에서 만들면 '이미 있는 이름이에요' 로 막히지 않는다).
     */
    val noCategoryBody: String

    /** 고른 달까지 그 분류 지출이 하나도 없을 때. 지난 달이면 그 달까지는 없었다는 뜻이다. */
    fun emptyTitle(month: YearMonth, today: LocalDate): String

    /** 빈 화면 글. 어느 분류로 적으면 여기서 무엇을 해 주는지 알린다. */
    val emptyBody: String

    /**
     * 목록 맨 아래 안내. 어떻게 묶고 짐작하는지(가게 이름을 바꿔 적으면 따로 보이는 까닭)와 목록에서 빼는 법 · 해지하면 어디까지 보이는지를
     * 알려 둔다. 빼기는 줄을 길게 눌러야 해서 눈에 안 띄고, '해지' 버튼은 안 냈거나 한동안 안 낸 줄에만 있다.
     */
    val footnote: String

    /** 묻는 묶음의 제목. "고정지출로 등록할까요?" */
    val askTitle: String

    /** 묻는 묶음 제목 아래 안내. 분류와 목록이 따로라는 것을 알린다. */
    val askHint: String

    /** '등록' 버튼(화면 읽기). 줄마다 같은 버튼이라 가게 이름을 붙인다: "넷플릭스 고정지출로 등록" */
    fun askRegisterLabel(name: String): String

    /** 해지한 곳의 판 줄을 길게 누르면 하는 일(화면 읽기도 이 이름으로 알린다). "고정지출 목록에서 빼기" */
    val removeActionLabel: String

    /** 그 밖의 판 줄을 길게 누르면 하는 일(화면 읽기). 확인 창에서 빼기와 해지 가운데 고른다. "고정지출 목록에서 빼거나 해지하기" */
    val removeOrEndActionLabel: String

    /** 빼기 확인 창 제목. 사용자가 적은 가게 이름 뒤에 조사를 붙이지 않게 따옴표로 묶는다: "'넷플릭스' 고정지출 목록에서 뺄까요?" */
    fun removeTitle(name: String): String

    /** 빼기 확인 창 글. 분류는 그대로라는 것과 다시 묻는 때를 알린다. */
    val removeMessage: String

    /** 고정지출 탭. '고정지출' 분류 지출을 본다. */
    data object Expense : FixedTexts {
        override val tabTitle = "고정지출"

        override fun summaryTitle(month: YearMonth, today: LocalDate) = "${monthLabel(month, today)} 고정지출"

        override fun nothingDueSentence(month: YearMonth, today: LocalDate) =
            if (month == YearMonth.from(today)) "이번 달에 낼 고정지출이 없어요" else "${monthLabel(month, today)}엔 낼 고정지출이 없었어요"

        override fun noRegisteredSentence(month: YearMonth, today: LocalDate) = if (month == YearMonth.from(today)) {
            "아직 목록에 넣은 고정지출이 없어요"
        } else {
            "${monthLabel(month, today)}까지는 목록에 넣은 고정지출이 없어요"
        }

        override val noCategoryTitle = "고정지출 분류가 없어요"

        override val noCategoryBody = "분류 관리에서 '고정지출' 분류를 다시 만들면, 그 분류로 등록한 지출을 여기서 볼 수 있어요."

        override fun emptyTitle(month: YearMonth, today: LocalDate) = if (month == YearMonth.from(today)) {
            "고정지출로 등록한 지출이 없어요"
        } else {
            "${monthLabel(month, today)}까지는 고정지출로 등록한 지출이 없어요"
        }

        override val emptyBody = "월세·구독료처럼 매달 나가는 돈을 '고정지출' 분류로 등록하면, 여기서 목록에 넣을지 물어보고 이번 달에 냈는지 보여 줘요."

        override val footnote =
            "'고정지출' 분류로 등록한 지출 가운데 목록에 넣은 곳만 가게 이름으로 묶었어요. 언제 얼마를 내는지는 지난 기록으로 짐작해요. " +
                "줄을 길게 누르면 목록에서 빼거나 해지할 수 있고, 해지한 곳은 마지막으로 낸 달까지만 보여요."

        override val askTitle = "고정지출로 등록할까요?"

        override val askHint = "'고정지출' 분류로 적은 곳이에요. 등록하면 매달 냈는지 여기서 볼 수 있어요."

        override fun askRegisterLabel(name: String) = "$name 고정지출로 등록"

        override val removeActionLabel = "고정지출 목록에서 빼기"

        override val removeOrEndActionLabel = "고정지출 목록에서 빼거나 해지하기"

        override fun removeTitle(name: String) = "'$name' 고정지출 목록에서 뺄까요?"

        override val removeMessage = "지난 거래는 '고정지출' 분류 그대로 남아요. 나중에 '고정지출' 분류로 새로 내면 등록할지 다시 물어볼게요."
    }

    /**
     * 고정저축 탭. '저축' 분류 지출을 본다. 저축 화면 안의 칸이라 탭 머리를 그리지 않아 [tabTitle] 은 화면에 쓰이지 않는다.
     * 칸 이름과 칸을 바꿀 때 화면 읽기가 알리는 이름은 저축 화면의 칸(SavingsTab.FIXED)이, 요약 제목은 [summaryTitle] 이 따로 적는다.
     * 가게 칸에 적는 것은 적금 · 청약 같은 상품 이름이라(저축 통계의 '상품별') 안내도 상품 이름이라고 적는다.
     */
    data object Savings : FixedTexts {
        override val tabTitle = "고정저축"

        override fun summaryTitle(month: YearMonth, today: LocalDate) = "${monthLabel(month, today)} 고정저축"

        override fun nothingDueSentence(month: YearMonth, today: LocalDate) =
            if (month == YearMonth.from(today)) "이번 달에 낼 고정저축이 없어요" else "${monthLabel(month, today)}엔 낼 고정저축이 없었어요"

        override fun noRegisteredSentence(month: YearMonth, today: LocalDate) = if (month == YearMonth.from(today)) {
            "아직 목록에 넣은 고정저축이 없어요"
        } else {
            "${monthLabel(month, today)}까지는 목록에 넣은 고정저축이 없어요"
        }

        override val noCategoryTitle = "저축 분류가 없어요"

        // 저축 화면의 내역 · 통계 탭과 같은 말이다
        override val noCategoryBody = "분류 관리에서 '저축' 분류를 다시 만들면, 그 분류로 적은 돈을 여기서 볼 수 있어요."

        // 저축 화면의 내역 탭은 모든 저축(꺼낸 돈 포함)을 읽고 이 탭은 고른 달 앞 몇 해([fixedHistoryStart])의 저축 지출만 읽는다.
        // 내역 탭처럼 '저축이 없어요' 라고 하면 옛 저축만 있을 때 바로 옆 탭에 보이는 저축을 없다고 하게 되어, 고정지출 탭처럼 이 탭이 보는 것으로 좁혀 말한다.
        override fun emptyTitle(month: YearMonth, today: LocalDate) = if (month == YearMonth.from(today)) {
            "고정저축으로 등록한 저축이 없어요"
        } else {
            "${monthLabel(month, today)}까지는 고정저축으로 등록한 저축이 없어요"
        }

        override val emptyBody = "적금·청약처럼 매달 넣는 돈을 '저축' 분류로 적으면, 여기서 목록에 넣을지 물어보고 이번 달에 냈는지 보여 줘요."

        override val footnote =
            "'저축' 분류로 적은 돈 가운데 목록에 넣은 곳만 상품 이름으로 묶었어요. 언제 얼마를 내는지는 지난 기록으로 짐작해요. " +
                "줄을 길게 누르면 목록에서 빼거나 해지할 수 있고, 해지한 곳은 마지막으로 낸 달까지만 보여요."

        override val askTitle = "고정저축으로 등록할까요?"

        override val askHint = "'저축' 분류로 적은 곳이에요. 등록하면 매달 냈는지 여기서 볼 수 있어요."

        override fun askRegisterLabel(name: String) = "$name 고정저축으로 등록"

        override val removeActionLabel = "고정저축 목록에서 빼기"

        override val removeOrEndActionLabel = "고정저축 목록에서 빼거나 해지하기"

        override fun removeTitle(name: String) = "'$name' 고정저축 목록에서 뺄까요?"

        override val removeMessage = "지난 거래는 '저축' 분류 그대로 남아요. 나중에 '저축' 분류로 새로 내면 등록할지 다시 물어볼게요."
    }
}

// ── 날짜 ──────────────────────────────────────────────────────────────

/** 보고 있는 달과 같은 해면 "7월 3일", 다른 해면 "2025년 7월 3일" */
private fun dateText(date: LocalDate, month: YearMonth): String {
    val day = "${date.monthValue}월 ${date.dayOfMonth}일"
    return if (date.year == month.year) day else "${date.year}년 $day"
}

/** 보고 있는 달과 같은 해면 "12월", 다른 해면 "2027년 3월" */
private fun monthName(target: YearMonth, month: YearMonth): String =
    if (target.year == month.year) "${target.monthValue}월" else "${target.year}년 ${target.monthValue}월"
