package com.dong.budget.data.salary

import com.dong.budget.data.holiday.KoreanHolidays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────
// 실시간 월급. 연봉이나 월급, 출퇴근 시간, 일하는 요일을 정해 두면 일하는 동안 초마다 번 돈이 쌓인다.
//
// 셈법
//   - 월급은 월급날 기준으로 센다. 앞 달 월급날 다음 날부터 이번 월급날까지가 한 번의 월급 기간이다([payPeriod]).
//   - 한 달 월급을 그 기간의 일하는 날 수로 나눠 하루치를 정하고, 하루치를 하루에 일하는 초로 나눠 초당 버는 돈을 정한다.
//     그래서 기간마다 일하는 날 수가 달라도 월급날 퇴근 때 '월급날부터 번 돈' 이 딱 월급이 되고, 다음 날 0원부터 다시 쌓인다.
//     (초당 버는 돈은 기간마다 조금 다르다. 한국의 통상시급처럼 월 209시간으로 나누면 쉬는 날의 주휴 몫까지 들어 있어,
//     일하는 시간에만 쌓을 때 월급날에 월급의 약 83% 에서 멈춘다. 통상시급은 [ordinaryHourlyWage] 로 따로 보여 준다.)
//   - 출근부터 퇴근 사이에만 쌓이고, 점심시간은 뺀다(끌 수 있다). 퇴근 뒤와 쉬는 요일에는 멈춘다.
//   - 쌓이는 날은 공휴일을 따로 빼지 않는다. 일하는 요일이면 공휴일도 일한 날로 친다.
//     월급날만 주말 · 공휴일([KoreanHolidays])이면 앞 영업일로 당긴다([paydayIn]).
//   - 시작일(입사일)이 있으면 그 전날까지는 벌지 않는다. 시작한 기간은 그날부터 일한 날만큼만(일할 계산) 쌓인다.
//   - '올해 번 돈' 은 1월 1일부터 센다. 월급 기간이 해를 넘으면 올해에 든 날만 센다.
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
 * @property amount 세전 금액(원). [basis] 가 연봉이면 연봉, 월급이면 월급. 통상시급은 이것으로 센다. 0 이면 적지 않은 것이다.
 * @property takeHome 실수령 월급(원). 적었으면 쌓이는 돈과 월급날 등록 금액은 이것으로 센다. 0 이면 세전으로 센다.
 * @property workEnd 퇴근. 출근보다 늦어야 한다(밤을 넘기는 근무는 아직 모른다).
 * @property skipLunch true 면 점심시간([lunchStart]~[lunchEnd])에는 쌓이지 않는다.
 * @property workdays 일하는 요일
 * @property payday 월급날(1~31). 그 달에 그날이 없으면 말일, 주말 · 공휴일이면 앞 영업일로 당긴다([paydayIn]).
 * @property startDate 이날부터 번다(입사일). null 이면 따지지 않는다.
 * @property paydayNotice 월급날 출근 시각에 '월급 들어왔나요?' 알림을 띄울지
 */
data class SalarySettings(
    val basis: PayBasis = PayBasis.MONTHLY,
    val amount: Long = 0,
    val takeHome: Long = 0,
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
    /** 세전 한 달 월급(원). 연봉이면 12로 나눈다. */
    val grossMonthly: Double get() = if (basis == PayBasis.YEARLY) amount / MONTHS_PER_YEAR else amount.toDouble()

    /** 실수령으로 세는지. 실수령을 적었으면 그렇다. */
    val usesTakeHome: Boolean get() = takeHome > 0

    /** 쌓이는 한 달 월급(원). 실수령을 적었으면 실수령, 아니면 세전이다. 월급날 등록 금액도 이것이다. */
    val monthly: Double get() = if (usesTakeHome) takeHome.toDouble() else grossMonthly

    /** 하루에 쌓이는 초. 출근~퇴근에서 그 안에 든 점심시간만큼 뺀다. */
    val workSecondsPerDay: Long get() = workedSeconds(workEnd.toSecondOfDay().toDouble()).toLong()

    /** 계산할 수 있게 다 정했는지. 금액·요일이 있고 하루에 일하는 시간이 있어야 한다. */
    val isReady: Boolean get() = monthly > 0 && workdays.isNotEmpty() && workSecondsPerDay > 0

    /** [date] 가 버는 날인지. 일하는 요일이고 시작일 뒤여야 한다. */
    fun earnsOn(date: LocalDate): Boolean = date.dayOfWeek in workdays && (startDate == null || !date.isBefore(startDate))

    /** [payMonth] 월급을 받기까지의 기간. 앞 달 월급날 다음 날부터 그달 월급날까지(둘 다 넣는다). */
    fun payPeriod(payMonth: YearMonth): ClosedRange<LocalDate> = paydayIn(payMonth.minusMonths(1)).plusDays(1)..paydayIn(payMonth)

    /** [date] 가 어느 달 월급의 기간에 드는지. 월급날 당일은 그달 월급의 마지막 날이다. */
    fun payMonthFor(date: LocalDate): YearMonth {
        // 월급날은 달마다 앞으로만 간다(주말 · 연휴로 당겨도 며칠). 앞 달부터 차례로 보면 곧 찾는다.
        var month = YearMonth.from(date).minusMonths(1)
        while (paydayIn(month).isBefore(date)) month = month.plusMonths(1)
        return month
    }

    /** [payMonth] 월급 기간의 일하는 요일 수. 시작일과 상관없이 센다(하루치를 정하는 데 쓴다). */
    fun workdaysIn(payMonth: YearMonth): Int = workdaysIn(payPeriod(payMonth))

    private fun workdaysIn(period: ClosedRange<LocalDate>): Int = period.dates().count { it.dayOfWeek in workdays }

    /** [payMonth] 월급 기간에 하루 일하면 버는 돈 */
    fun dailyAmount(payMonth: YearMonth): Double = workdaysIn(payMonth).let { days -> if (days == 0) 0.0 else monthly / days }

    /** [payMonth] 월급 기간에 일하는 동안 1초에 버는 돈 */
    fun perSecond(payMonth: YearMonth): Double =
        workSecondsPerDay.let { seconds -> if (seconds == 0L) 0.0 else dailyAmount(payMonth) / seconds }

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

    /** [payMonth] 월급 기간이 다 지나면 번 돈(보통 월급). 시작일이 기간 안이면 그날부터 일한 날만큼(일할 계산), 그 뒤면 0 이다. */
    fun earnedInPeriod(payMonth: YearMonth): Double = shareOf(payMonth, payPeriod(payMonth).dates().count(::earnsOn).toDouble())

    /**
     * [payMonth] 월급 기간에서 [days] 일(하루 몫 소수 포함)을 일하고 번 돈. 월급 × 일한 날 ÷ 기간의 일하는 날로 곱하고 나서 나눈다.
     * 하루치(월급 ÷ 일하는 날)를 먼저 구해 곱하면 소수 오차로 월급날 퇴근 때 2,999,999.9999… 원이 되어 한 원 모자라 보인다.
     */
    private fun shareOf(payMonth: YearMonth, days: Double): Double = share(workdaysIn(payMonth), days)

    /** [shareOf] 를 일하는 날 수([total])를 이미 셌을 때. 매초 도는 [earningsAt] 이 기간을 여러 번 다시 세지 않게 한다. */
    private fun share(total: Int, days: Double): Double = if (total == 0) 0.0 else monthly * days / total

    /**
     * [payMonth] 월급으로 받을 돈(원). 월급날 알림과 등록창이 채우는 금액이다. 입사한 달은 입사일부터 일한 날만큼(일할 계산)이고,
     * 입사 전 달이면 0 이다(월급날 알림도 띄우지 않는다).
     */
    fun payFor(payMonth: YearMonth): Long = floor(earnedInPeriod(payMonth) + ROUNDING_SLACK_WON).toLong()

    /**
     * 한 달 소정근로시간(통상시급을 셀 때 나누는 시간). 주 소정근로시간(40시간까지)에 주휴시간을 더해 한 달 평균 주 수를 곱하고 올린다.
     * 주 5일 하루 8시간이면 (40 + 8) × 365 ÷ 7 ÷ 12 = 208.6 → 209시간이다. 주 15시간이 안 되면 주휴가 없다.
     * 주 40시간을 넘는 몫은 연장근로라 넣지 않는다.
     */
    val standardMonthlyHours: Int
        get() {
            val weekly = weeklyStandardHours
            if (weekly <= 0) return 0
            return ceil((weekly + weeklyRestHours) * DAYS_PER_YEAR / DAYS_PER_WEEK / MONTHS_PER_YEAR - ROUNDING_SLACK).toInt()
        }

    /** 주휴가 있는지. 주 소정근로시간이 15시간 이상이어야 한다. */
    val hasWeeklyRest: Boolean get() = weeklyStandardHours >= MIN_HOURS_FOR_WEEKLY_REST

    /** 주 소정근로시간. 주 40시간을 넘는 몫은 연장근로라 넣지 않는다. */
    private val weeklyStandardHours: Double
        get() = (workdays.size * workSecondsPerDay / SECONDS_PER_HOUR).coerceAtMost(MAX_WEEKLY_HOURS)

    /** 주휴시간. 주 소정근로시간에 비례하고(주 40시간이면 8시간), 주휴가 없으면([hasWeeklyRest]) 0 이다. */
    private val weeklyRestHours: Double
        get() = if (hasWeeklyRest) weeklyStandardHours / MAX_WEEKLY_HOURS * PAID_REST_HOURS else 0.0

    /** 통상시급(원). 세전 한 달 월급을 [standardMonthlyHours] 로 나눈다(통상임금은 세전이다). 세전을 적지 않았거나 계산할 수 없으면 0 */
    val ordinaryHourlyWage: Double
        get() = standardMonthlyHours.let { hours -> if (!isReady || hours == 0 || amount <= 0) 0.0 else grossMonthly / hours }

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
     * 1일 월급이 주말 · 공휴일이라 앞 달 말로 당겨지면 그날은 다음 달 월급날이다.
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
        // 입사일이 아주 먼 뒤라도 끝없이 돌지 않게 앞으로 몇 해까지만 본다
        repeat(MONTHS_TO_LOOK) {
            val at = paydayAlarmAt(month)
            val alreadyNotified = notified != null && !month.isAfter(notified)
            when {
                // 입사 전 달은 받을 월급이 없다
                alreadyNotified || payFor(month) <= 0 -> Unit

                at.isAfter(now) -> return month to at

                at.toLocalDate() == now.toLocalDate() -> return month to now
            }
            month = month.plusMonths(1)
        }
        return null
    }

    /**
     * [month] 의 월급날. 그 달에 [payday] 가 없으면 말일이고, 주말 · 공휴일([KoreanHolidays])이면 앞 영업일로 당긴다
     * (2026년 9월 25일 추석이면 23일). 1일이 쉬는 날이면 앞 달 말일쯤으로 넘어갈 수 있다.
     */
    fun paydayIn(month: YearMonth): LocalDate {
        var date = month.atDay(payday.coerceIn(1, month.lengthOfMonth()))
        while (!KoreanHolidays.isBusinessDay(date)) date = date.minusDays(1)
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

    /**
     * [now](서울 시각)에 본 벌이. 아직 다 정하지 않았으면 모두 0 이다.
     * @param earnedBefore [now] 의 날짜로 구한 [earnedThisYearBefore]. 하루 동안 같아서, 매초 부르는 화면은 날마다 한 번 구해 넘긴다.
     */
    fun earningsAt(now: LocalDateTime, earnedBefore: Double = earnedThisYearBefore(now.toLocalDate())): Earnings {
        val today = now.toLocalDate()
        val status = statusAt(now)
        if (!isReady) return Earnings(status = status)
        val payMonth = payMonthFor(today)
        val period = payPeriod(payMonth)
        // 이번 월급 기간의 일하는 날 수는 한 번만 센다(오늘·월급날부터·기간 합·초당 버는 돈이 함께 쓴다)
        val total = workdaysIn(period)
        val secondOfDay = now.toLocalTime().toSecondOfDay() + now.nano / NANOS_PER_SECOND

        val daySeconds = workSecondsPerDay.toDouble()
        val worked = workedSeconds(secondOfDay)
        val todayShare = if (earnsOn(today)) worked / daySeconds else 0.0
        val earnedToday = share(total, todayShare)
        return Earnings(
            status = status,
            today = earnedToday,
            period = share(total, countEarnDays(period.start, today) + todayShare),
            year = earnedBefore + earnedToday,
            dayProgress = if (earnsOn(today)) (worked / daySeconds).toFloat() else 0f,
            // 하루치 ÷ 하루 일하는 초. perSecond(payMonth) 와 같은 값이다.
            perSecond = share(total, 1.0) / daySeconds,
            payMonth = payMonth,
            periodStart = period.start,
            periodTotal = share(total, period.dates().count(::earnsOn).toDouble()),
        )
    }

    /**
     * 올해 1월 1일부터 [today] 전날까지 번 돈. 지나간 날들을 그날이 든 월급 기간의 하루치로 더한다.
     * 오늘 몫은 넣지 않는다([earningsAt] 이 더한다). 아직 다 정하지 않았으면 0 이다.
     */
    fun earnedThisYearBefore(today: LocalDate): Double {
        if (!isReady) return 0.0
        val newYear = LocalDate.of(today.year, 1, 1)
        val payMonth = payMonthFor(today)
        var earned = 0.0
        var month = payMonthFor(newYear)
        while (month <= payMonth) {
            val range = payPeriod(month)
            val from = maxOf(range.start, newYear)
            val until = minOf(range.endInclusive.plusDays(1), today)
            earned += shareOf(month, countEarnDays(from, until).toDouble())
            month = month.plusMonths(1)
        }
        return earned
    }

    /** [from] 부터 [until] 전날까지 버는 날 수. [until] 이 [from] 과 같거나 앞이면 0 */
    private fun countEarnDays(from: LocalDate, until: LocalDate): Int =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it.isBefore(until) }.count(::earnsOn)

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

        private const val SECONDS_PER_HOUR = 3_600.0
        private const val MAX_WEEKLY_HOURS = 40.0
        private const val PAID_REST_HOURS = 8.0
        private const val MIN_HOURS_FOR_WEEKLY_REST = 15.0
        private const val DAYS_PER_YEAR = 365.0
        private const val DAYS_PER_WEEK = 7.0

        /** 다음 월급날 알림을 찾아볼 달 수(10년) */
        private const val MONTHS_TO_LOOK = 120

        /** 3,000,000 이 2,999,999.9999999 로 계산돼 원 아래를 버릴 때 한 원 모자라지 않게 한다 */
        private const val ROUNDING_SLACK_WON = 1e-6

        /** 208.0000001 처럼 계산 오차로 딱 떨어지는 값이 한 시간 올라가지 않게 한다 */
        private const val ROUNDING_SLACK = 1e-9

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
 * @property period 지난 월급날 다음 날부터 지금까지 번 돈('월급날부터 번 돈')
 * @property dayProgress 오늘 하루치 중 일한 몫(0~1). 쉬는 날은 0
 * @property perSecond 이번 월급 기간에 일하는 동안 1초에 버는 돈
 * @property payMonth 지금 벌고 있는 월급이 몇 월 월급인지
 * @property periodStart 이번 월급 기간의 첫날(지난 월급날 다음 날)
 * @property periodTotal 이번 월급 기간이 다 지나면 벌 돈(보통 월급, 시작한 기간은 일할 계산)
 */
data class Earnings(
    val status: WorkStatus,
    val today: Double = 0.0,
    val period: Double = 0.0,
    val year: Double = 0.0,
    val dayProgress: Float = 0f,
    val perSecond: Double = 0.0,
    val payMonth: YearMonth? = null,
    val periodStart: LocalDate? = null,
    val periodTotal: Double = 0.0,
)

/** 기간 안의 날짜들(처음과 끝 포함) */
private fun ClosedRange<LocalDate>.dates(): Sequence<LocalDate> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }
