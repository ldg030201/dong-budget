package com.dong.budget.ui.format

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val KOREA = Locale.KOREA
private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", KOREA)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", KOREA)

/** 12345 -> "12,345" */
fun formatAmount(amount: Long): String = String.format(KOREA, "%,d", amount)

/**
 * 거래 한 건의 금액에 부호를 붙인다. 들어온 돈(수입·환불)은 +, 쓴 돈(지출)은 - 다.
 * 이체는 내 계좌끼리 옮긴 것이라 부호를 붙이지 않는다.
 */
fun formatSignedAmount(type: TransactionType, amount: Long): String = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> "+${formatAmount(amount)}원"
    TransactionType.EXPENSE -> "-${formatAmount(amount)}원"
    TransactionType.TRANSFER -> "${formatAmount(amount)}원"
}

/**
 * 합계처럼 방향이 값에 따라 정해지는 금액. 들어온 쪽이면 +, 나간 쪽이면 -, 0 이면 부호 없이 적는다.
 * @param unit 뒤에 붙일 단위. 달력 칸처럼 좁은 곳은 빈 문자열로 뺀다.
 */
fun formatSignedTotal(amount: Long, unit: String = "원"): String = when {
    amount > 0 -> "+${formatAmount(amount)}$unit"
    amount < 0 -> "-${formatAmount(-amount)}$unit"
    else -> "0$unit"
}

/**
 * 순지출(쓴 돈 − 돌려받은 돈)을 방향 부호와 함께 적는다. 쓴 돈은 "-12,000원", 돌려받은 돈이 더 많아 음수면 "+12,000원", 0 은 "0원".
 * 통계 화면의 지출 금액이 모두 이 규칙을 따른다(색은 netExpenseColor). 홈 요약·달력과 같은 부호다.
 */
fun formatNetExpense(amount: Long, unit: String = "원"): String = formatSignedTotal(-amount, unit)

/**
 * 지출 금액을 부호 없이. '지출', '썼어요' 처럼 말이 방향을 이미 전하는 화면 읽기 문장에 쓴다("지출 -2만원" 으로 읽히지 않게).
 * 돌려받은 돈이 더 많아 음수면 "+12,000원" 이다.
 */
fun formatSpentAmount(amount: Long, unit: String = "원"): String = if (amount <
    0
) {
    formatSignedTotal(-amount, unit)
} else {
    "${formatAmount(amount)}$unit"
}

/** 수입이면 "+", 지출이면 "-" 로 방향을 붙인다. 분류·결제수단처럼 한 화면이 수입과 지출을 오가는 곳에 쓴다. */
fun formatDirected(amount: Long, income: Boolean, unit: String = "원"): String =
    if (income) formatSignedTotal(amount, unit) else formatNetExpense(amount, unit)

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

/** 시각만. "오전 9:00", "오후 6:30" */
fun formatClock(time: LocalTime): String = timeFormatter.format(time)

/**
 * 남은 시간. 분 아래는 올려서 "1시간 5분", "40분", "3시간" 처럼 적는다. 1분이 안 남았으면 "1분" 이다.
 * @param seconds 0 보다 커야 한다
 */
fun formatDuration(seconds: Long): String {
    val minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "${minutes.coerceAtLeast(1)}분"
        rest == 0L -> "${hours}시간"
        else -> "${hours}시간 ${rest}분"
    }
}

/** 알림이 온 때. "오늘 오후 2:22", "어제 오전 8:40", 그 전은 "9월 23일 오후 3:00" */
fun formatNoticeTime(instant: Instant, today: LocalDate): String {
    val at = instant.atZone(BudgetTime.ZONE)
    val day =
        when (at.toLocalDate()) {
            today -> "오늘"
            today.minusDays(1) -> "어제"
            else -> "${at.monthValue}월 ${at.dayOfMonth}일"
        }
    return "$day ${timeFormatter.format(at)}"
}

fun formatMonth(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"

/**
 * 금액을 만 단위로 줄인다. 문장 안에서 대략의 크기만 전할 때 쓴다.
 * 만 원 아래는 그대로 쓰고, 그 위는 만 단위 아래를 버린다(내림). 반올림하면 옆에 적힌 전체 금액보다
 * 커 보일 수 있어서(2,076,048원 → 208만원) 두 금액이 어긋나 보인다.
 *   8,500 → "8,500원" / 93,000 → "9만원" / 99,000 → "9만원" / 123,456,789 → "1억 2,345만원"
 * 부호는 붙이지 않는다. 덜/더 는 문장이 말한다.
 */
fun formatCompactWon(amount: Long): String {
    val abs = abs(amount)
    if (abs < MAN) return "${formatAmount(abs)}원"
    val eok = abs / EOK
    val man = abs % EOK / MAN
    return buildString {
        if (eok > 0) append("${formatAmount(eok)}억")
        if (eok > 0 && man > 0) append(' ')
        if (man > 0) append("${formatAmount(man)}만")
        append('원')
    }
}

/**
 * 금액을 조·억·만 단위로 끊어 줄이지 않고 적는다. 등록창 금액 밑의 작은 글씨에 쓴다.
 *   12,345 → "1만 2,345원" / 120,000,000 → "1억 2,000만원" / 100,005,000 → "1억 5,000원"
 *   1,234,500,000,000 → "1조 2,345억원" / 8,500 → "8,500원"
 * 빈 단위는 건너뛴다. 부호는 붙이지 않는다.
 */
fun formatKoreanWon(amount: Long): String {
    var rest = abs(amount)
    if (rest == 0L) return "0원"
    val groups =
        buildList {
            KOREAN_UNITS.forEach { (unit, name) ->
                val value = rest / unit
                rest %= unit
                if (value > 0) add("${formatAmount(value)}$name")
            }
        }
    return groups.joinToString(" ") + "원"
}

private const val MAN = 10_000L
private const val EOK = 100_000_000L
private const val JO = 1_000_000_000_000L

/** 큰 단위부터. 마지막 1 은 단위 이름 없이 남은 자리다. */
private val KOREAN_UNITS = listOf(JO to "조", EOK to "억", MAN to "만", 1L to "")

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

/** 요일 이름. "토요일" */
fun formatWeekdayFull(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, KOREA)

/** 짧은 날짜. "9월 3일 (목)". 연도는 붙이지 않는다(한 달 안의 날을 가리킬 때 쓴다). */
fun formatDayShort(date: LocalDate): String = dayFormatter.format(date)

/**
 * 비율(0~1)을 정수 % 로 반올림한다. "42%"
 * 0 보다 크지만 반올림하면 0% 가 되는 값은 "1% 미만" 이라고 쓴다. 0% 라고 적으면 없는 것처럼 보인다.
 * 합을 100% 에 억지로 맞추지 않는다.
 */
fun formatShare(share: Double): String {
    // 비율은 나눗셈 결과(소수)라 딱 28.5% 가 28.4999… 로 나올 수 있다. 정수 반올림(divRound)과 같은 값이 나오게 문턱에서만 봐준다.
    val percent = (share * 100 + SHARE_TOLERANCE).roundToLong()
    return if (share > 0 && percent == 0L) "1% 미만" else "$percent%"
}

private const val SHARE_TOLERANCE = 1e-9

/** 몇 배인지. 소수 첫째 자리까지 반올림한다. 1.8 → "1.8배" */
fun formatRatio(ratio: Double): String = String.format(KOREA, "%.1f배", ratio)

/**
 * 문장 안에서 달을 가리키는 이름. 이번 달이면 "이번 달", 올해면 "9월", 다른 해면 "2025년 12월".
 * 통계 문구의 {달} 자리에 모두 이것을 쓴다.
 */
fun monthLabel(month: YearMonth, today: LocalDate): String = when {
    month == YearMonth.from(today) -> "이번 달"
    month.year == today.year -> "${month.monthValue}월"
    else -> formatMonth(month)
}

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
