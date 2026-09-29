package com.dong.budget.ui.salary

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
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────────────
// 월급 탭의 글자. 영어·기호(D-3 등) 없이 해요체로 적는다.
// ─────────────────────────────────────────────────────────────────────

/** 번 돈. 원 아래는 버린다(초마다 몇 원씩 쌓이므로 소수가 생긴다). "+128,340원" */
internal fun formatEarned(amount: Double): String = "+${formatAmount(floor(amount).toLong())}원"

/** 지금 상태 한 줄. "일하는 중 · 퇴근까지 2시간 14분" */
internal fun statusLine(settings: SalarySettings, now: LocalDateTime): String {
    val time = now.toLocalTime()
    fun until(target: LocalTime) = formatDuration(Duration.between(time, target).seconds.coerceAtLeast(1))
    return when (settings.statusAt(now)) {
        WorkStatus.NOT_SET -> "월급을 정하면 쌓이기 시작해요"
        WorkStatus.NOT_STARTED -> "${settings.startDate?.let(::formatDayShort)}부터 쌓여요"
        WorkStatus.DAY_OFF -> "쉬는 날이에요 · ${nextWorkdayText(settings, now.toLocalDate())}"
        WorkStatus.BEFORE_WORK -> "출근 전이에요 · 출근까지 ${until(settings.workStart)}"
        WorkStatus.WORKING -> "일하는 중이에요 · 퇴근까지 ${until(settings.workEnd)}"
        WorkStatus.LUNCH -> "점심시간이에요 · ${formatClock(settings.lunchEnd)}부터 다시 쌓여요"
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

/** 버는 빠르기. "1초에 4.73원 · 1분에 284원 · 1시간에 17,045원" */
internal fun rateLine(perSecond: Double): String {
    val second = String.format(Locale.KOREA, "%,.2f", perSecond)
    val minute = formatAmount(floor(perSecond * SECONDS_PER_MINUTE).toLong())
    val hour = formatAmount(floor(perSecond * SECONDS_PER_HOUR).toLong())
    return "1초에 ${second}원 · 1분에 ${minute}원 · 1시간에 ${hour}원"
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
 * "약 47분", "약 2시간 5분", "약 3일 2시간", 1분이 안 되면 "1분도 안 돼요"
 * @param secondsPerDay 하루에 일하는 초. 일하는 날 수를 셀 때 쓴다.
 */
internal fun workValue(seconds: Double, secondsPerDay: Long): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToLong()
    if (minutes < 1) return "1분도 안 돼요"
    val minutesPerDay = secondsPerDay / SECONDS_PER_MINUTE
    if (minutesPerDay <= 0 || minutes < minutesPerDay) return "약 ${hoursAndMinutes(minutes)}"
    val days = minutes / minutesPerDay
    val hours = ((minutes - days * minutesPerDay) / MINUTES_PER_HOUR.toDouble()).roundToLong()
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
private const val SECONDS_PER_HOUR = 3_600L
private const val MINUTES_PER_HOUR = 60L
private const val PERCENT = 100.0
private const val SPOKEN_STEP = 1_000.0

/** 쉬는 날에 다음 일하는 날을 찾아볼 날 수. 일하는 요일이 하나라도 있으면 일주일 안에 온다. */
private const val DAYS_TO_LOOK = 14L
