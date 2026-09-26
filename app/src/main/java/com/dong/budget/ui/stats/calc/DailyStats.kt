package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.WEEK_ORDER
import com.dong.budget.ui.home.dailyTotals
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.home.totals
import com.dong.budget.ui.stats.DayDetail
import com.dong.budget.ui.stats.DayPeak
import com.dong.budget.ui.stats.DayStack
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.Period
import com.dong.budget.ui.stats.StatSeries
import com.dong.budget.ui.stats.WeekdayAverage
import com.dong.budget.ui.stats.WeekdayStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────────────
// 일별 탭: 날마다 쌓은 막대, 하루 기록(평균·가장 많이 쓴 날·돈 안 쓴 날), 요일별 하루 평균.
// 하루 금액은 홈 달력 칸(dailyTotals)과 같다. 오늘 뒤 날짜로 미리 적은 거래는 막대에는 그리되
// 평균·가장 많이 쓴 날·돈 안 쓴 날·요일에는 넣지 않는다(아직 쓴 돈이 아니다).
// ─────────────────────────────────────────────────────────────────────

/**
 * 1일부터 말일까지 하루씩 쌓는다.
 * @param series 쌓을 계열(지출 분류의 상위 + '그 외'). 조각은 이 순서다.
 */
fun dayStacks(month: YearMonth, today: LocalDate, rows: List<TransactionListItem>, series: List<StatSeries>): List<DayStack> {
    val byDay = rows.filter { it.isRecord }.groupBy { it.localDate() }
    val seriesIndex = series.flatMapIndexed { index, s -> s.members.map { it to index } }.toMap()
    return (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        dayStack(date, today, byDay[date].orEmpty(), series.size, seriesIndex)
    }
}

private fun dayStack(
    date: LocalDate,
    today: LocalDate,
    items: List<TransactionListItem>,
    seriesCount: Int,
    seriesIndex: Map<GroupKey, Int>,
): DayStack {
    val totals = items.totals()
    val details = dayDetails(items, seriesIndex)
    // 계열마다 순지출을 모은 뒤 음수는 0 으로 둔다. 환불이 더 많은 조각은 아래로 그릴 수 없다.
    val segments = LongArray(seriesCount)
    details.forEach { detail -> detail.seriesIndex?.let { segments[it] += detail.amount } }
    return DayStack(
        date = date,
        expense = totals.expense,
        income = totals.income,
        refund = items.filter { it.type == TransactionType.REFUND }.sumOf { it.amount },
        segments = segments.map { it.coerceAtLeast(0) },
        details = details,
        items = items.sortedWith(compareByDescending<TransactionListItem> { it.occurredAt }.thenByDescending { it.id }),
        isFuture = date.isAfter(today),
    )
}

/** 그날 금액이 0 이 아닌 지출 분류 전부. 계열에서 접힌 분류도 제 이름으로 남긴다. 목록 순서는 분류 탭과 같다. */
private fun dayDetails(items: List<TransactionListItem>, seriesIndex: Map<GroupKey, Int>): List<DayDetail> =
    breakdown(items, Measure.EXPENSE, Grouping.CATEGORY, window = null).entries.map { entry ->
        DayDetail(key = entry.key, name = entry.name, color = entry.color, seriesIndex = seriesIndex[entry.key], amount = entry.amount)
    }

/**
 * 하루 기록. [counted] 안의 날만 센다.
 * @property average 하루 평균 지출. 센 날이 없거나 환불이 더 많아 음수면 null
 * @property spentDays 지출(환불 말고)이 한 건이라도 있던 날 수
 * @property spentDayAverage 쓴 날 평균 = 센 날의 지출 ÷ 쓴 날 수. 쓴 날이 없거나 음수면 null
 * @property peak 하루 순지출이 가장 큰 날(0 보다 커야 한다). 같으면 이른 날
 * @property noSpendDays 지출이 없던 날 수. 환불이나 수입만 있던 날도 센다. 센 날이 없으면 null
 * @property longestNoSpend 돈 안 쓴 날의 최장 연속 일수
 */
data class DayRecords(
    val average: Long?,
    val spentDays: Int,
    val spentDayAverage: Long?,
    val peak: DayPeak?,
    val noSpendDays: Int?,
    val longestNoSpend: Int,
)

/** @param days [dayStacks] 의 결과(1일부터 말일까지) */
fun dayRecords(counted: IntRange, days: List<DayStack>): DayRecords {
    val inRange = days.filter { it.date.dayOfMonth in counted }
    if (inRange.isEmpty()) return DayRecords(null, 0, null, null, null, 0)
    val spent = inRange.map { day -> day.items.any { it.type == TransactionType.EXPENSE } }
    val spentDays = spent.count { it }
    val total = inRange.sumOf { it.expense }
    return DayRecords(
        average = divRound(total, inRange.size).takeIf { it >= 0 },
        spentDays = spentDays,
        spentDayAverage = if (spentDays > 0) divRound(total, spentDays).takeIf { it >= 0 } else null,
        // maxByOrNull 은 같은 값이면 앞의 것(이른 날)을 준다
        peak = inRange.filter { it.expense > 0 }.maxByOrNull { it.expense }?.let { DayPeak(it.date, it.expense) },
        noSpendDays = inRange.size - spentDays,
        longestNoSpend = longestRun(spent.map { !it }),
    )
}

/** 참이 이어진 가장 긴 길이 */
private fun longestRun(flags: List<Boolean>): Int {
    var longest = 0
    var run = 0
    flags.forEach { flag ->
        run = if (flag) run + 1 else 0
        longest = maxOf(longest, run)
    }
    return longest
}

/**
 * 처음 고를 날(일).
 * 이번 달은 오늘, 지나간 달은 가장 많이 쓴 날, 오지 않은 달은 기록이 있는 첫날. 없으면 1일이다.
 */
fun defaultDay(period: Period, today: LocalDate, peak: DayPeak?, days: List<DayStack>): Int = when (period) {
    Period.CURRENT -> today.dayOfMonth
    Period.PAST -> peak?.date?.dayOfMonth ?: 1
    Period.FUTURE -> days.firstOrNull { it.items.isNotEmpty() }?.date?.dayOfMonth ?: 1
}

/**
 * 요일별 하루 평균. 창은 최근 3달(고른 달의 두 달 전 1일부터)을 기록 시작일과 오늘로 자른 것이다.
 * 평균의 분모는 창 안의 그 요일 날 수다. 돈을 안 쓴 날도 넣어야 '그 요일에 보통 얼마 쓰나' 가 된다.
 *
 * @param rows 창을 덮는 거래(고른 달과 앞선 두 달). 창 밖 거래는 무시한다.
 * @return 기록이 없거나, 오지 않은 달이거나, 창이 [MIN_WEEKDAY_WINDOW_DAYS] 일보다 짧으면 null. 짧으면 요일마다 한 번꼴이라 평균이 흔들린다.
 */
fun weekdayStats(month: YearMonth, today: LocalDate, firstRecord: LocalDate?, rows: List<TransactionListItem>): WeekdayStats? {
    firstRecord ?: return null
    // 오지 않은 달은 지나간 날이 없다. 지난 달 요일을 빌려 보이지 않는다(눈에 띄는 점 c 도 같이 빠진다).
    if (periodOf(month, today) == Period.FUTURE) return null
    val from = maxOf(firstRecord, month.minusMonths(WEEKDAY_WINDOW_MONTHS - 1L).atDay(1))
    val to = minOf(today, month.atEndOfMonth())
    if (ChronoUnit.DAYS.between(from, to) + 1 < MIN_WEEKDAY_WINDOW_DAYS) return null

    val daily = dailyTotals(rows)
    val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    val spentByDay = dates.groupBy({ it.dayOfWeek }, { daily[it]?.expense ?: 0L })
    val averages = WEEK_ORDER.map { day ->
        val amounts = spentByDay[day].orEmpty()
        WeekdayAverage(day = day, average = divRound(amounts.sum(), amounts.size), days = amounts.size)
    }
    return WeekdayStats(
        from = from,
        to = to,
        averages = averages,
        // 같으면 앞선 요일(일요일 쪽)
        top = averages.filter { it.average > 0 }.maxByOrNull { it.average }?.day,
        weekendRatio = weekendRatio(spentByDay),
    )
}

/**
 * 주말 하루 평균 ÷ 평일 하루 평균. 요일별로 반올림한 값이 아니라 합과 날 수로 바로 나눈다.
 * 어느 쪽이든 0 이하면 null 이다. 몇 배인지 말할 수 없다.
 */
private fun weekendRatio(spentByDay: Map<DayOfWeek, List<Long>>): Double? {
    val (weekend, weekday) = spentByDay.entries.partition { it.key in WEEKEND }
    val weekendSum = weekend.sumOf { it.value.sum() }
    val weekdaySum = weekday.sumOf { it.value.sum() }
    if (weekendSum <= 0 || weekdaySum <= 0) return null
    val weekendDays = weekend.sumOf { it.value.size }
    val weekdayDays = weekday.sumOf { it.value.size }
    // 곱해서 한 번만 나눈다. 평균끼리 나누면 반올림이 두 번 생겨 1.3 같은 문턱에서 흔들린다.
    return (weekendSum * weekdayDays).toDouble() / (weekdaySum * weekendDays)
}

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** 요일별 평균을 낼 최근 달 수(고른 달 포함) */
const val WEEKDAY_WINDOW_MONTHS = 3

/** 요일별 평균을 보이려면 창이 이만큼은 돼야 한다. 요일마다 두 번은 들어간다. */
const val MIN_WEEKDAY_WINDOW_DAYS = 14
