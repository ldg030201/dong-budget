package com.dong.budget.ui.format

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val KOREA = Locale.KOREA
private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", KOREA)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", KOREA)

/** 12345 -> "12,345" */
fun formatAmount(amount: Long): String = String.format(KOREA, "%,d", amount)

/**
 * 금액에 부호를 붙인다.
 *
 * 지출에는 부호를 붙이지 않는다. 가계부는 지출 항목이 압도적으로 많아서
 * 전부 마이너스를 달면 화면만 지저분해지고 구분에 도움이 안 된다.
 */
fun formatSignedAmount(type: TransactionType, amount: Long): String = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> "+${formatAmount(amount)}원"
    TransactionType.EXPENSE, TransactionType.TRANSFER -> "${formatAmount(amount)}원"
}

fun formatDay(instant: Instant): String = dayFormatter.format(instant.atZone(BudgetTime.ZONE))

/**
 * 날짜 하나만 따로 보여줄 때 (등록 화면의 날짜 칸 등).
 * 올해가 아니면 연도를 붙인다. 지난해 거래를 고칠 때 몇 년도인지 헷갈리지 않게.
 */
fun formatDate(instant: Instant, today: LocalDate = LocalDate.now(BudgetTime.ZONE)): String {
    val date = instant.atZone(BudgetTime.ZONE)
    val day = dayFormatter.format(date)
    return if (date.year == today.year) day else "${date.year}년 $day"
}

fun formatTime(instant: Instant): String = timeFormatter.format(instant.atZone(BudgetTime.ZONE))

fun formatMonth(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"

/**
 * 금액을 만 단위로 줄인다. 문장 안에서 대략의 크기만 전할 때 쓴다.
 * 만 원 아래는 그대로 쓰고, 그 위는 만 단위에서 반올림한다.
 *   8,500 → "8,500원" / 93,000 → "9만원" / 123,456,789 → "1억 2,346만원"
 * 부호는 붙이지 않는다. 덜/더 는 문장이 말한다.
 */
fun formatCompactWon(amount: Long): String {
    val abs = abs(amount)
    if (abs < MAN) return "${formatAmount(abs)}원"
    var eok = abs / EOK
    var man = (abs % EOK + MAN / 2) / MAN
    // 반올림으로 만 단위가 1억이 되면 억으로 올린다 (99,995,000 → 1억원)
    if (man == EOK / MAN) {
        eok += 1
        man = 0
    }
    return buildString {
        if (eok > 0) append("${formatAmount(eok)}억")
        if (eok > 0 && man > 0) append(' ')
        if (man > 0) append("${formatAmount(man)}만")
        append('원')
    }
}

private const val MAN = 10_000L
private const val EOK = 100_000_000L

/** 목록의 날짜 구분선. "25일 금요일", 오늘과 어제는 뒤에 붙여 알려준다. */
fun formatDayHeader(date: LocalDate, today: LocalDate): String {
    val base = dayHeaderFormatter.format(date)
    return when (date) {
        today -> "$base · 오늘"
        today.minusDays(1) -> "$base · 어제"
        else -> base
    }
}

private val dayHeaderFormatter = DateTimeFormatter.ofPattern("d일 EEEE", KOREA)

/** 화면 읽기용 전체 날짜. "9월 25일 금요일" */
fun formatDateSpoken(date: LocalDate): String = spokenDateFormatter.format(date)

private val spokenDateFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", KOREA)

/** 달력 머리줄의 요일 한 글자 */
fun formatWeekday(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, KOREA)
