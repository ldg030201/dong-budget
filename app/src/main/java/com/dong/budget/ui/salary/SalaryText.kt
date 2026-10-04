package com.dong.budget.ui.salary

import com.dong.budget.data.salary.Earnings
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.WorkStatus
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatClock
import com.dong.budget.ui.format.formatDayShort
import com.dong.budget.ui.format.formatDuration
import com.dong.budget.ui.format.formatWeekdayFull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────────────
// 월급 탭의 글자. 영어·기호(D-3 등) 없이 해요체로 적는다.
// ─────────────────────────────────────────────────────────────────────

/** 번 돈. 원 아래는 버린다(초마다 몇 원씩 쌓이므로 소수가 생긴다). "+128,340원" */
internal fun formatEarned(amount: Double): String = "+${formatAmount(floor(amount + ROUNDING_SLACK_WON).toLong())}원"

/** 지금 상태 한 줄. "일하는 중 · 퇴근까지 2시간 14분" */
internal fun statusLine(settings: SalarySettings, now: LocalDateTime): String {
    val time = now.toLocalTime()
    fun until(target: LocalTime) = formatDuration(Duration.between(time, target).seconds.coerceAtLeast(1))
    return when (settings.statusAt(now)) {
        WorkStatus.NOT_SET -> "월급을 정하면 쌓이기 시작해요"

        WorkStatus.NOT_STARTED -> "입사일 ${settings.startDate?.let(::formatDayShort)}부터 쌓여요"

        WorkStatus.DAY_OFF -> "쉬는 날이에요 · ${nextWorkdayText(settings, now.toLocalDate())}"

        WorkStatus.BEFORE_WORK -> "출근 전이에요 · 출근까지 ${until(settings.workStart)}"

        WorkStatus.WORKING -> "일하는 중이에요 · 퇴근까지 ${until(settings.workEnd)}"

        // 점심이 퇴근 때까지 이어지면 다시 쌓이는 때가 없다
        WorkStatus.LUNCH ->
            if (settings.lunchEnd.isBefore(settings.workEnd)) {
                "점심시간이에요 · ${formatClock(settings.lunchEnd)}부터 다시 쌓여요"
            } else {
                "점심시간이에요 · 오늘 몫을 다 벌었어요"
            }

        WorkStatus.AFTER_WORK -> "퇴근했어요 · 오늘 몫을 다 벌었어요"
    }
}

/** 쉬는 날에 다음에 일하는 날. "내일부터 다시 쌓여요", "월요일부터 다시 쌓여요" */
private fun nextWorkdayText(settings: SalarySettings, today: LocalDate): String {
    val next = (1L..DAYS_TO_LOOK).map(today::plusDays).firstOrNull(settings::earnsOn) ?: return "일하는 요일이 오면 쌓여요"
    return if (next == today.plusDays(1)) "내일부터 다시 쌓여요" else "${formatWeekdayFull(next.dayOfWeek)}부터 다시 쌓여요"
}

/** 월급날까지. "오늘은 월급날이에요", "내일은 월급날이에요", "월급날까지 3일 남았어요" */
internal fun paydayLine(payday: LocalDate, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, payday)) {
    0L -> "오늘은 월급날이에요"
    1L -> "내일은 월급날이에요"
    else -> "월급날까지 ${days}일 남았어요"
}

/** 1초에 버는 돈. 사용자가 고른 모양이다. "₩4.73/s" */
internal fun perSecondBadge(perSecond: Double): String = "₩${String.format(Locale.KOREA, "%,.2f", perSecond)}/s"

/** 1시간에 버는 돈. 원 아래는 반올림한다. "₩17,045/h" */
internal fun perHourBadge(perSecond: Double): String = "₩${formatAmount(perHour(perSecond))}/h"

/** 1시간에 버는 돈(원). 원 아래는 반올림한다. */
private fun perHour(perSecond: Double): Long = (perSecond * SECONDS_PER_HOUR).roundToLong()

/** 오늘 번 돈 옆에 나란히 두는 빠르기. "₩4.73/s · ₩17,045/h" */
internal fun rateBadge(perSecond: Double): String = "${perSecondBadge(perSecond)} · ${perHourBadge(perSecond)}"

/** 화면 읽기로 읽을 빠르기. "1초에 4.73원, 1시간에 17,045원" */
internal fun spokenRate(perSecond: Double): String =
    "1초에 ${String.format(Locale.KOREA, "%.2f", perSecond)}원, 1시간에 ${formatAmount(perHour(perSecond))}원"

/** 번 돈 카드 밑 안내. 무엇으로 쌓는지. "실수령 기준으로 쌓여요" */
internal fun basisLine(settings: SalarySettings): String = if (settings.usesTakeHome) "실수령 기준으로 쌓여요" else "세전 기준으로 쌓여요"

/**
 * 1시간에 버는 돈(₩/h)과 통상시급을 두 줄로 견준다.
 * ₩/h 는 월급(주휴수당이 들어 있다)을 이번 월급 기간에 실제로 일하는 시간으로 나눈 값이라 주휴수당이 포함돼 있고,
 * 통상시급은 주휴 시간까지 넣은 한 달 209시간으로 나눈, 주휴수당을 뺀 본래 시급이다.
 * "주휴수당 포함: ₩13,481/h" / "통상시급: ₩10,320/h"
 * 실수령으로 쌓이면 ₩/h 는 실수령, 통상시급은 세전이라 (실수령)·(세전) 을 붙인다. 세전을 적지 않았으면 통상시급 줄은 뺀다.
 * 주휴가 없으면(주 15시간 미만) 위 줄은 '실제 근무 기준' 이라 한다.
 * 화면 읽기에는 ₩·/h·괄호 대신 말로 푼 문장([HourlyLine.spoken])을 읽힌다.
 * @return 이번 월급 기간에 일하는 날이 없으면 null
 */
internal fun hourlyLine(settings: SalarySettings, payMonth: YearMonth?): HourlyLine? {
    payMonth ?: return null
    if (settings.workdaysIn(payMonth) == 0 || settings.workSecondsPerDay == 0L) return null
    val takeHome = settings.usesTakeHome
    val label = if (settings.hasWeeklyRest) "주휴수당 포함" else "실제 근무 기준"
    val perSecond = settings.perSecond(payMonth)
    val rate = "$label${if (takeHome) "(실수령)" else ""}: ${perHourBadge(perSecond)}"
    val spokenRate = "$label${if (takeHome) " 실수령" else ""} 1시간에 ${formatAmount(perHour(perSecond))}원"
    val ordinary = settings.ordinaryHourlyWage
    if (ordinary <= 0) return HourlyLine(text = rate, spoken = spokenRate)
    val wage = formatAmount(floor(ordinary).toLong())
    return HourlyLine(
        text = "$rate\n통상시급${if (takeHome) "(세전)" else ""}: ₩$wage/h",
        spoken = "$spokenRate, ${if (takeHome) "세전 " else ""}통상시급 ${wage}원",
    )
}

/**
 * 번 돈 카드 밑 시급 줄.
 * @property text 화면 글. "주휴수당 포함: ₩17,045/h\n통상시급: ₩14,354/h"
 * @property spoken 화면 읽기 문장. "주휴수당 포함 1시간에 17,045원, 통상시급 14,354원"
 */
internal data class HourlyLine(val text: String, val spoken: String)

/**
 * '월급날부터 번 돈' 밑 안내. 언제부터 셌고, 이번 월급의 몇 %를 벌었는지.
 * "9월 24일부터 · 10월 월급 3,000,000원 중 14%"
 */
internal fun periodCaption(earnings: Earnings): String {
    val start = earnings.periodStart ?: return ""
    val month = earnings.payMonth ?: return ""
    val from = "${start.monthValue}월 ${start.dayOfMonth}일부터"
    if (earnings.periodTotal <= 0) return "$from · 이번 월급 기간에는 일하는 날이 없어요"
    val percent = floor(earnings.period * PERCENT / earnings.periodTotal + ROUNDING_SLACK_WON).toInt().coerceIn(0, PERCENT.toInt())
    return "$from · ${month.monthValue}월 월급 ${formatAmount(floor(earnings.periodTotal + ROUNDING_SLACK_WON).toLong())}원 중 $percent%"
}

/**
 * 오늘 번 돈과 쓴 돈을 견준 한 줄.
 * @param spent 오늘 쓴 돈(지출 − 환불). 환불이 더 많으면 음수다.
 */
internal fun spentLine(earned: Double, spent: Long): String = when {
    spent < 0 -> "오늘은 쓴 돈보다 환불받은 돈이 많아요"
    spent == 0L -> "오늘은 아직 쓴 돈이 없어요"
    earned < 1 -> "아직 번 돈이 없어요"
    spent > earned -> "번 돈보다 ${formatAmount((spent - floor(earned)).toLong())}원 더 썼어요"
    else -> "번 돈의 ${(spent * PERCENT / earned).roundToLong().coerceAtLeast(1)}%를 썼어요"
}

/**
 * 돈을 버는 데 드는 일한 시간. 하루치를 넘으면 일하는 날 수로 센다.
 * "약 47분", "약 2시간 5분", "약 3일 2시간", 1분이 안 되면 "1분 미만"
 * @param secondsPerDay 하루에 일하는 초. 일하는 날 수를 셀 때 쓴다.
 */
internal fun workValue(seconds: Double, secondsPerDay: Long): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToLong()
    if (minutes < 1) return "1분 미만"
    val minutesPerDay = secondsPerDay / SECONDS_PER_MINUTE
    if (minutesPerDay <= 0 || minutes < minutesPerDay) return "약 ${hoursAndMinutes(minutes)}"
    var days = minutes / minutesPerDay
    var hours = ((minutes - days * minutesPerDay) / MINUTES_PER_HOUR.toDouble()).roundToLong()
    // 남은 시간을 올리다 하루치가 되면 하루로 넘긴다('3일 8시간' 이 아니라 '4일')
    if (hours * MINUTES_PER_HOUR >= minutesPerDay) {
        days += 1
        hours = 0
    }
    return if (hours == 0L) "약 ${days}일" else "약 ${days}일 ${hours}시간"
}

private fun hoursAndMinutes(minutes: Long): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "${rest}분"
        rest == 0L -> "${hours}시간"
        else -> "${hours}시간 ${rest}분"
    }
}

/** 화면 읽기로 읽을 번 돈. 매초 바뀌면 읽기가 따라가지 못하므로 천 원 단위로 끊는다. "약 128,000원" */
internal fun spokenEarned(amount: Double): String = "약 ${formatAmount((floor(amount / SPOKEN_STEP) * SPOKEN_STEP).toLong())}원"

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
private const val PERCENT = 100.0
private const val SPOKEN_STEP = 1_000.0

/** 3,000,000 이 2,999,999.9999999 로 계산돼 원 아래를 버릴 때 한 원 모자라지 않게 한다 */
private const val ROUNDING_SLACK_WON = 1e-6

/** 쉬는 날에 다음 일하는 날을 찾아볼 날 수. 일하는 요일이 하나라도 있으면 일주일 안에 온다. */
private const val DAYS_TO_LOOK = 14L
