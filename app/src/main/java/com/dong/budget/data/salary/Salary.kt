package com.dong.budget.data.salary

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 실시간 월급. 연봉이나 월급, 출퇴근 시간, 일하는 요일을 정해 두면 일하는 동안 초마다 번 돈이 쌓인다.
//
// 셈법
//   - 한 달 월급을 그 달의 일하는 날 수로 나눠 하루치를 정하고, 하루치를 하루에 일하는 초로 나눠 초당 버는 돈을 정한다.
//     그래서 달마다 일하는 날 수가 달라도 말일 퇴근 때 '이번 달' 이 딱 월급이 된다(초당 버는 돈은 달마다 조금 다르다).
//   - 출근부터 퇴근 사이에만 쌓이고, 점심시간은 뺀다(끌 수 있다). 퇴근 뒤와 쉬는 요일에는 멈춘다.
//   - 공휴일은 모른다(자료가 없다). 일하는 요일이면 일한 날로 친다.
//   - 시작일(입사일)이 있으면 그 전날까지는 벌지 않는다. 시작한 달은 그날부터 일한 날만큼만(일할 계산) 쌓인다.
//   - 시각은 가계부처럼 서울 기준이다(BudgetTime.ZONE). 부르는 쪽이 서울 시각을 넘긴다.
// ─────────────────────────────────────────────────────────────────────

/** 급여를 무엇으로 적었는지 */
enum class PayBasis {
    /** 한 달 월급 */
    MONTHLY,

    /** 연봉. 12로 나눠 한 달 월급으로 본다. */
    YEARLY,
}

/**
 * 실시간 월급 설정.
 * @property amount 적은 금액(원). [basis] 가 연봉이면 연봉, 월급이면 월급. 0 이면 아직 정하지 않은 것이다.
 * @property workEnd 퇴근. 출근보다 늦어야 한다(밤을 넘기는 근무는 아직 모른다).
 * @property skipLunch true 면 점심시간([lunchStart]~[lunchEnd])에는 쌓이지 않는다.
 * @property workdays 일하는 요일
 * @property payday 월급날(1~31). 그 달에 그날이 없으면 말일, 주말이면 앞 금요일로 당긴다([paydayIn]).
 * @property startDate 이날부터 번다(입사일). null 이면 따지지 않는다.
 * @property paydayNotice 월급날 출근 시각에 '월급 들어왔나요?' 알림을 띄울지
 */
data class SalarySettings(
    val basis: PayBasis = PayBasis.MONTHLY,
    val amount: Long = 0,
    val workStart: LocalTime = LocalTime.of(9, 0),
    val workEnd: LocalTime = LocalTime.of(18, 0),
    val skipLunch: Boolean = true,
    val lunchStart: LocalTime = LocalTime.of(12, 0),
    val lunchEnd: LocalTime = LocalTime.of(13, 0),
    val workdays: Set<DayOfWeek> = WEEKDAYS,
    val payday: Int = 25,
    val startDate: LocalDate? = null,
    val paydayNotice: Boolean = true,
) {
    /** 한 달 월급(원). 연봉이면 12로 나눈다. */
    val monthly: Double get() = if (basis == PayBasis.YEARLY) amount / MONTHS_PER_YEAR else amount.toDouble()

    /** 하루에 쌓이는 초. 출근~퇴근에서 그 안에 든 점심시간만큼 뺀다. */
    val workSecondsPerDay: Long get() = workedSeconds(workEnd.toSecondOfDay().toDouble()).toLong()

    /** 계산할 수 있게 다 정했는지. 금액·요일이 있고 하루에 일하는 시간이 있어야 한다. */
    val isReady: Boolean get() = amount > 0 && workdays.isNotEmpty() && workSecondsPerDay > 0

    /** [date] 가 버는 날인지. 일하는 요일이고 시작일 뒤여야 한다. */
    fun earnsOn(date: LocalDate): Boolean = date.dayOfWeek in workdays && (startDate == null || !date.isBefore(startDate))

    /** [month] 의 일하는 요일 수. 시작일과 상관없이 센다(하루치를 정하는 데 쓴다). */
    fun workdaysIn(month: YearMonth): Int = (1..month.lengthOfMonth()).count { month.atDay(it).dayOfWeek in workdays }

    /** [month] 에 하루 일하면 버는 돈 */
    fun dailyAmount(month: YearMonth): Double = workdaysIn(month).let { days -> if (days == 0) 0.0 else monthly / days }

    /** [month] 에 일하는 동안 1초에 버는 돈 */
    fun perSecond(month: YearMonth): Double = workSecondsPerDay.let { seconds -> if (seconds == 0L) 0.0 else dailyAmount(month) / seconds }

    /** 하루 중 [secondOfDay] 까지 일한 초. 출근 전 0, 퇴근 뒤 하루치. 점심시간을 빼면 그동안은 늘지 않는다. */
    fun workedSeconds(secondOfDay: Double): Double {
        val start = workStart.toSecondOfDay().toDouble()
        val end = workEnd.toSecondOfDay().toDouble()
        if (end <= start) return 0.0
        val until = secondOfDay.coerceIn(start, end)
        var worked = until - start
        if (skipLunch) {
            // 점심시간 중 근무 시간 안에 든 부분만 뺀다(점심을 근무 밖에 잡아도 깨지지 않게)
            val lunchFrom = lunchStart.toSecondOfDay().toDouble().coerceIn(start, end)
            val lunchTo = lunchEnd.toSecondOfDay().toDouble().coerceIn(start, end)
            if (lunchTo > lunchFrom) worked -= (until.coerceIn(lunchFrom, lunchTo) - lunchFrom)
        }
        return worked
    }

    /** [month] 가 다 지나면 번 돈. 시작일이 그 달 안이면 그날부터 일한 날만큼(일할 계산), 그 뒤면 0 이다. */
    fun earnedInWholeMonth(month: YearMonth): Double {
        val counted = (1..month.lengthOfMonth()).count { earnsOn(month.atDay(it)) }
        return dailyAmount(month) * counted
    }

    /**
     * 달마다 다르지 않은, 평균으로 1초에 버는 돈. 한 달을 평균 [WEEKS_PER_MONTH] 주로 보고 일하는 요일 수로 센다.
     * '이 결제는 47분 일한 값' 처럼 금액을 시간으로 바꿀 때 쓴다. 그달 기준([perSecond])으로 바꾸면 같은 금액이 달마다 다르게 보인다.
     */
    val averagePerSecond: Double
        get() {
            val secondsPerMonth = workdays.size * WEEKS_PER_MONTH * workSecondsPerDay
            return if (!isReady || secondsPerMonth <= 0) 0.0 else monthly / secondsPerMonth
        }

    /** [amount] 원을 벌려면 일해야 하는 초([averagePerSecond] 기준). 계산할 수 없으면 null */
    fun secondsToEarn(amount: Long): Double? = averagePerSecond.takeIf { it > 0 }?.let { amount / it }

    /** [month] 월급날 알림을 띄울 때. 그 달 월급날의 출근 시각이다. */
    fun paydayAlarmAt(month: YearMonth): LocalDateTime = paydayIn(month).atTime(workStart)

    /**
     * [today] 가 어느 달 월급을 받는 날인지. 월급날이 아니면 null.
     * 1일 월급이 주말이라 앞 달 말로 당겨지면 그날은 다음 달 월급날이다.
     */
    fun payMonthOn(today: LocalDate): YearMonth? {
        val month = YearMonth.from(today)
        return listOf(month, month.plusMonths(1)).firstOrNull { paydayIn(it) == today }
    }

    /**
     * [month] 월급을 받는 기간. 월급날 일주일 전부터 다음 달 월급날 일주일 전 전날까지다.
     * 이 사이에 급여 분류 수입을 직접 적었으면 그달 월급을 등록한 것으로 본다(조금 일찍 들어오는 월급도 잡는다).
     */
    fun salaryPeriod(month: YearMonth): Pair<LocalDate, LocalDate> =
        paydayIn(month).minusDays(SALARY_EARLY_DAYS) to paydayIn(month.plusMonths(1)).minusDays(SALARY_EARLY_DAYS)

    /** [today] 까지 월급날이 지난 가장 최근 달(오늘이 월급날이면 그달) */
    fun latestPayMonth(today: LocalDate): YearMonth {
        var month = YearMonth.from(today).plusMonths(1)
        while (paydayIn(month).isAfter(today)) month = month.minusMonths(1)
        return month
    }

    /**
     * 다음 월급날 알림을 언제 어느 달 월급으로 띄울지. 아직 정하지 않았거나 알림을 껐으면 null.
     * 오늘이 월급날인데 출근 시각이 이미 지났고 이번 달을 아직 알리지 않았으면 [now] 에 바로 띄운다
     * (그 시각에 폰이 꺼져 있었거나 앱을 강제로 멈췄던 경우).
     * @param notified 이미 알린 가장 최근 달
     */
    fun nextPaydayAlarm(now: LocalDateTime, notified: YearMonth?): Pair<YearMonth, LocalDateTime>? {
        if (!isReady || !paydayNotice) return null
        var month = YearMonth.from(now).minusMonths(1)
        while (true) {
            val at = paydayAlarmAt(month)
            val alreadyNotified = notified != null && !month.isAfter(notified)
            when {
                alreadyNotified -> Unit
                at.isAfter(now) -> return month to at
                at.toLocalDate() == now.toLocalDate() -> return month to now
            }
            month = month.plusMonths(1)
        }
    }

    /**
     * [month] 의 월급날. 그 달에 [payday] 가 없으면 말일이고, 토·일이면 앞 금요일로 당긴다(공휴일은 모른다).
     * 1일이 주말이면 앞 달 말일쯤으로 넘어갈 수 있다.
     */
    fun paydayIn(month: YearMonth): LocalDate {
        var date = month.atDay(payday.coerceIn(1, month.lengthOfMonth()))
        while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) date = date.minusDays(1)
        return date
    }

    /** [today] 나 그 뒤의 가장 가까운 월급날. 오늘이 월급날이면 오늘이다. */
    fun nextPayday(today: LocalDate): LocalDate {
        var month = YearMonth.from(today).minusMonths(1)
        // 월급날을 앞으로 당기면 앞 달로 넘어갈 수 있어서, 앞 달부터 차례로 본다
        while (true) {
            val date = paydayIn(month)
            if (!date.isBefore(today)) return date
            month = month.plusMonths(1)
        }
    }

    /** [now](서울 시각)에 본 벌이. 아직 다 정하지 않았으면 모두 0 이다. */
    fun earningsAt(now: LocalDateTime): Earnings {
        val today = now.toLocalDate()
        val month = YearMonth.from(today)
        val secondOfDay = now.toLocalTime().toSecondOfDay() + now.nano / NANOS_PER_SECOND
        val status = statusAt(now)
        if (!isReady) return Earnings(status = status)

        val daySeconds = workSecondsPerDay.toDouble()
        val worked = workedSeconds(secondOfDay)
        val todayShare = if (earnsOn(today)) worked / daySeconds else 0.0
        val daily = dailyAmount(month)
        val earnedToday = daily * todayShare
        val daysBefore = (1 until today.dayOfMonth).count { earnsOn(month.atDay(it)) }
        val earnedMonth = daily * daysBefore + earnedToday
        val earnedYear = (1 until month.monthValue).sumOf { earnedInWholeMonth(YearMonth.of(month.year, it)) } + earnedMonth
        return Earnings(
            status = status,
            today = earnedToday,
            month = earnedMonth,
            year = earnedYear,
            dayProgress = if (earnsOn(today)) (worked / daySeconds).toFloat() else 0f,
            perSecond = perSecond(month),
            monthTotal = earnedInWholeMonth(month),
        )
    }

    /** [now] 에 일하는 중인지, 점심인지, 쉬는 날인지 */
    fun statusAt(now: LocalDateTime): WorkStatus {
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        return when {
            !isReady -> WorkStatus.NOT_SET
            startDate != null && today.isBefore(startDate) -> WorkStatus.NOT_STARTED
            today.dayOfWeek !in workdays -> WorkStatus.DAY_OFF
            time.isBefore(workStart) -> WorkStatus.BEFORE_WORK
            !time.isBefore(workEnd) -> WorkStatus.AFTER_WORK
            skipLunch && lunchEnd.isAfter(lunchStart) && !time.isBefore(lunchStart) && time.isBefore(lunchEnd) -> WorkStatus.LUNCH
            else -> WorkStatus.WORKING
        }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> =
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        private const val MONTHS_PER_YEAR = 12.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        /** 월급이 월급날보다 이만큼 일찍 들어와도 그달 월급으로 본다 */
        private const val SALARY_EARLY_DAYS = 7L

        /** 한 달의 평균 주 수(365.2425일 / 7 / 12) */
        private const val WEEKS_PER_MONTH = 365.2425 / 7 / 12
    }
}

/** 한 달 치 월급을 등록한 거래에 붙이는 열쇠. 같은 달 월급을 두 번 등록하지 않게 한다. "salary:2026-09" */
fun salaryKey(month: YearMonth): String = "$SALARY_KEY_PREFIX$month"

/** [salaryKey] 에서 달을 읽는다. 월급 열쇠가 아니면 null */
fun salaryMonthOf(key: String): YearMonth? = key.removePrefix(SALARY_KEY_PREFIX).takeIf { key.startsWith(SALARY_KEY_PREFIX) }?.let {
    runCatching { YearMonth.parse(it) }.getOrNull()
}

private const val SALARY_KEY_PREFIX = "salary:"

/** 지금 일하는 중인지 */
enum class WorkStatus {
    /** 급여·요일·근무 시간을 아직 다 정하지 않았다 */
    NOT_SET,

    /** 시작일(입사일) 전이다 */
    NOT_STARTED,

    /** 오늘은 일하지 않는 요일이다 */
    DAY_OFF,

    BEFORE_WORK,
    WORKING,

    /** 점심시간. 쌓이지 않는다. */
    LUNCH,
    AFTER_WORK,
}

/**
 * 어느 순간에 본 벌이. 금액은 원 단위 소수다(초마다 몇 원씩 쌓이므로). 화면에 보일 때 원 아래는 버린다.
 * @property dayProgress 오늘 하루치 중 일한 몫(0~1). 쉬는 날은 0
 * @property perSecond 이번 달에 일하는 동안 1초에 버는 돈
 * @property monthTotal 이번 달이 다 지나면 벌 돈(보통 월급, 시작한 달은 일할 계산)
 */
data class Earnings(
    val status: WorkStatus,
    val today: Double = 0.0,
    val month: Double = 0.0,
    val year: Double = 0.0,
    val dayProgress: Float = 0f,
    val perSecond: Double = 0.0,
    val monthTotal: Double = 0.0,
)
