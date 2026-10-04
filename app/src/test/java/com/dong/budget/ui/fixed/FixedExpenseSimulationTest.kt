package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.localDate
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 진짜로 낸 결제 한 건. [due] 는 그 결제를 원래 낼 날이다(밀린 몫을 늦게 내거나 다음 몫을 일찍 내면 [date] 와 다르다). */
private data class Pay(val date: LocalDate, val amount: Long, val due: LocalDate = date)

/**
 * 진짜 몫 하나. [month] 몫을 [payments] 로 냈다. [scheduled] 는 달력대로라면 낼 날(휴일 밀림 · 미리 냄 포함)이다.
 * 빠뜨린 몫은 [payments] 가 비었다(밀린 몫은 메운 달의 몫에 함께 넣는다).
 */
private data class Share(val month: YearMonth, val scheduled: LocalDate, val payments: List<Pay>)

/**
 * 한 가게의 진짜 결제 흐름. [monthly] 면 매달 내는 것(I1 을 본다), [oneMonth] 는 진짜 한 달 치(I4 의 기준).
 * [changed] 는 평소 낼 날을 바꾼 첫 달이다(바꾸지 않았으면 null).
 */
private class Pattern(
    val name: String,
    val monthly: Boolean,
    val oneMonth: Long,
    val shares: List<Share>,
    val changed: YearMonth? = null,
) {
    val rows: List<TransactionListItem> =
        shares.flatMap { it.payments }.map { tx(it.date.toString(), it.amount, categoryId = 4, paymentId = 10, merchant = name) }

    fun shareIn(month: YearMonth): Share? = shares.firstOrNull { it.month == month }
}

/** 새 기록으로 보고 받아들이는 달 수(첫 결제 달부터 이만큼 뒤 달까지) */
private const val NEW_RECORD_MONTHS = 2L

/** 시뮬레이션 달력의 첫 달 · 마지막 달 */
private val FIRST = YearMonth.of(2025, 1)
private val LAST = YearMonth.of(2035, 12)

private fun months(from: YearMonth = FIRST, to: YearMonth = LAST, step: Long = 1): List<YearMonth> =
    generateSequence(from) { it.plusMonths(step) }.takeWhile { it <= to }.toList()

/** [month] 의 [day] 일. 그 달에 없는 날이면 말일 */
private fun dayIn(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

/** 쉬는 날이면 다음 영업일로 밀린 날 */
private fun pushed(date: LocalDate): LocalDate = KoreanCalendar.nextBusinessDay(date)

/** 매 [step] 달 [day] 일에 [amount] 를 내고, 쉬는 날이면 다음 영업일로 밀리는 것 */
private fun every(name: String, amount: Long, day: Int, step: Long = 1, from: YearMonth = FIRST) = Pattern(
    name = name,
    monthly = step == 1L,
    oneMonth = amount,
    shares = months(from, step = step).map { month -> pushed(dayIn(month, day)).let { Share(month, it, listOf(Pay(it, amount))) } },
)

/** 1일 월세. 평소엔 1일(쉬는 날이면 다음 영업일), 3번에 1번([early] 번째)은 전달 마지막 영업일에 미리 낸다. */
private fun rent(name: String = "월세", early: Int = 2): Pattern = Pattern(
    name = name,
    monthly = true,
    oneMonth = 500_000,
    shares =
    months().mapIndexed { i, month ->
        val date = if (i % 3 == early) KoreanCalendar.previousBusinessDay(month.atDay(1).minusDays(1)) else pushed(month.atDay(1))
        Share(month, date, listOf(Pay(date, 500_000)))
    },
)

/** 한 가게에 매달 3일 50,000원 · 28일 30,000원 두 구독 */
private fun twoDays(): Pattern = Pattern(
    name = "보험",
    monthly = true,
    oneMonth = 80_000,
    shares =
    months().map { month ->
        val third = pushed(month.atDay(3))
        Share(month, third, listOf(Pay(third, 50_000), Pay(pushed(month.atDay(28)), 30_000)))
    },
)

/** 매달 10일 60,000원인데 2025년 8월을 빠뜨리고 9월 10일에 두 번(밀린 8월 몫 + 9월 몫) 냈다 */
private fun skipped(): Pattern = Pattern(
    name = "헬스",
    monthly = true,
    oneMonth = 60_000,
    shares =
    months().map { month ->
        val date = pushed(month.atDay(10))
        val pays =
            when (month) {
                YearMonth.of(2025, 8) -> emptyList()
                YearMonth.of(2025, 9) -> listOf(Pay(date, 60_000), Pay(date, 60_000))
                else -> listOf(Pay(date, 60_000))
            }
        Share(month, date, pays)
    },
)

/** 급여일 뒤 25일 카드 대금. 달마다 250,000~350,000원으로 바뀐다. */
private fun cardBill(): Pattern = Pattern(
    name = "카드대금",
    monthly = true,
    oneMonth = 350_000,
    shares =
    months().mapIndexed { i, month ->
        val date = pushed(month.atDay(25))
        Share(month, date, listOf(Pay(date, 250_000 + i % 5 * 25_000L)))
    },
)

/** 같은 가게 두 회선(45,000원 · 48,000원)을 매달 20일에 함께 낸다 */
private fun twoLines(): Pattern = Pattern(
    name = "통신사",
    monthly = true,
    oneMonth = 93_000,
    shares = months().map { month -> pushed(month.atDay(20)).let { Share(month, it, listOf(Pay(it, 45_000), Pay(it, 48_000))) } },
)

/** 매년 10월 말일 도메인. 2024년부터 냈다. */
private fun domain(): Pattern = every("도메인", 22_000, 31, step = 12, from = YearMonth.of(2024, 10))

/**
 * [pays] 달의 몫은 [on] 에 내고(밀린 몫과 다음 몫을 한날 냄), 나머지는 매달 [day] 일(쉬는 날이면 다음 영업일)에 내는 것.
 * rereview.json rr-fixed-5 의 두 경우다.
 */
private fun paidTogether(name: String, amount: Long, day: Int, on: LocalDate, pays: Set<YearMonth>) = Pattern(
    name = name,
    monthly = true,
    oneMonth = amount,
    shares =
    months().map { month ->
        val due = pushed(dayIn(month, day))
        Share(month, due, listOf(Pay(if (month in pays) on else due, amount, due)))
    },
)

/** 말일 관리비를 손으로 내는데 3번에 1번은 이틀 늦게(그 뒤 첫 영업일) 낸다 */
private fun lateHand(): Pattern = Pattern(
    name = "관리비C",
    monthly = true,
    oneMonth = 130_000,
    shares =
    months().mapIndexed { i, month ->
        val due = pushed(month.atEndOfMonth())
        val date = if (i % 3 == 1) pushed(due.plusDays(2)) else due
        Share(month, due, listOf(Pay(date, 130_000, due)))
    },
)

/** 한 가게에 매달 3일 33,000원 · 28일 30,000원(금액이 비슷한 두 차례) */
private fun twoDaysSimilar(): Pattern = Pattern(
    name = "보험2",
    monthly = true,
    oneMonth = 63_000,
    shares =
    months().map { month ->
        val third = pushed(month.atDay(3))
        Share(month, third, listOf(Pay(third, 33_000), Pay(pushed(month.atDay(28)), 30_000)))
    },
)

/** 한 가게에 매달 3일 · 28일 같은 금액(30,000원)을 낸다 */
private fun twoDaysSame(): Pattern = Pattern(
    name = "보험3",
    monthly = true,
    oneMonth = 60_000,
    shares =
    months().map { month ->
        val third = pushed(month.atDay(3))
        Share(month, third, listOf(Pay(third, 30_000), Pay(pushed(month.atDay(28)), 30_000)))
    },
)

/** 말일 관리비를 쉬는 날이면 앞 영업일에 미리 낸다 */
private fun endEarly(): Pattern = Pattern(
    name = "관리비E",
    monthly = true,
    oneMonth = 140_000,
    shares = months().map { month ->
        KoreanCalendar.previousBusinessDay(month.atEndOfMonth()).let { Share(month, it, listOf(Pay(it, 140_000))) }
    },
)

/** 매달 [from] 일 구독을 [at] 부터 [to] 일로 바꿨다 */
private fun switched(name: String, from: Int, to: Int, at: YearMonth): Pattern = Pattern(
    name = name,
    monthly = true,
    oneMonth = 15_000,
    shares = months().map { month -> pushed(dayIn(month, if (month < at) from else to)).let { Share(month, it, listOf(Pay(it, 15_000))) } },
    changed = at,
)

/** 시뮬레이션에 쓰는 가게들 */
private fun patterns(): List<Pattern> = listOf(
    every("관리비", 150_000, 31),
    rent(),
    every("넷플릭스", 17_000, 25),
    every("통신비", 55_000, 15),
    every("수도", 40_000, 31, step = 2),
    domain(),
    twoDays(),
    skipped(),
    cardBill(),
    twoLines(),
    // 매달 30일, 2월은 말일
    every("적금", 50_000, 30),
    // 기록 1~2건뿐인 새 항목: 2027년 10월 말일(일요일 → 11월 1일)부터 낸 관리비, 11월 25일부터 낸 구독
    every("새관리비", 120_000, 31, from = YearMonth.of(2027, 10)),
    every("새구독", 9_900, 25, from = YearMonth.of(2027, 11)),
    // 말일 관리비가 2025년 9월 것을 놓쳐 10월 2일에 9월 몫 · 10월 몫을 함께 냄
    paidTogether("관리비B", 120_000, 31, LocalDate.of(2025, 10, 2), setOf(YearMonth.of(2025, 9), YearMonth.of(2025, 10))),
    // 1일 월세가 2025년 10월 것을 놓쳐 10월 28일에 10월 몫 · 11월 몫을 함께 냄
    paidTogether("월세B", 300_000, 1, LocalDate.of(2025, 10, 28), setOf(YearMonth.of(2025, 10), YearMonth.of(2025, 11))),
    lateHand(),
    twoDaysSimilar(),
    every("구독28", 12_000, 28),
    every("회비", 20_000, 5),
    twoDaysSame(),
    endEarly(),
    // 월세와 같되 미리 내는 달이 다른 것
    rent("월세P0", early = 0),
    rent("월세P1", early = 1),
    // 쉬는 날이면 다음 영업일로 밀리는 1일 자동이체(미리 내지 않음)
    every("월세2", 450_000, 1),
    // 2025년 5월 말일(토요일 → 6월 2일)부터 낸 말일 관리비 · 2026년 5월 말일(일요일 → 6월 1일)부터 낸 말일 관리비
    every("관리비D", 110_000, 31, from = YearMonth.of(2025, 5)),
    every("관리비F", 100_000, 31, from = YearMonth.of(2026, 5)),
    // 평소 날을 바꿈: 28일 → 3일, 3일 → 28일, 15일 → 말일
    switched("구독전환", 28, 3, YearMonth.of(2026, 4)),
    switched("구독전환2", 3, 28, YearMonth.of(2026, 4)),
    switched("구독전환3", 15, 31, YearMonth.of(2026, 7)),
)

/** 불변식 위반 하나. [accepted] 는 fixes3.md '받아들이는 모호함' 에 해당하면 그 까닭(테스트를 실패시키지 않는다) */
private data class Violation(
    val pattern: String,
    val day: LocalDate,
    val invariant: String,
    val view: YearMonth,
    val expected: String,
    val actual: String,
    val accepted: String? = null,
)

/** 한 날 한 달 화면에서 한 가게를 본 것. [seen] 은 그 화면이 쓴 이 가게의 결제(오늘까지) */
private class View(val month: YearMonth, val item: FixedExpenseItem?, val seen: List<TransactionListItem>) {
    /**
     * 냈어요의 근거가 될 수 있는 결제 id 묶음들. 첫 결제일(lastPaidOn)에 낸 것을 하나 넣고 그날부터 낸 결제 가운데 낸 횟수만큼이며,
     * 합이 그 몫 금액이다. 한날 두 몫의 결제가 함께 나가면(28일 것이 휴일로 밀려 다음 달 3일 것과 같은 날) 날짜 차례만으로는
     * 어느 몫인지 모르므로 금액까지 맞춘다.
     */
    fun paidBases(): List<Set<Long>> {
        val item = item?.takeIf { it.status == FixedStatus.PAID } ?: return emptyList()
        val pool = seen.filter { !it.localDate().isBefore(item.lastPaidOn) }
        return combinations(pool, item.lastPaidCount)
            .filter { picked -> picked.any { it.localDate() == item.lastPaidOn } && picked.sumOf { it.amount } == item.amount }
            .map { picked -> picked.map { it.id }.toSet() }
    }
}

/** [items] 에서 [size] 개를 고르는 모든 묶음 */
private fun <T> combinations(items: List<T>, size: Int): List<List<T>> = when {
    size == 0 -> listOf(emptyList())
    items.size < size -> emptyList()
    else -> combinations(items.drop(1), size - 1).map { listOf(items.first()) + it } + combinations(items.drop(1), size)
}

/** I4: 낼 돈 · 등록하기 금액이 진짜 한 달 치의 몇 % 까지인지 */
private const val I4_LIMIT_PERCENT = 120L

/**
 * 2025-01-01 ~ 2035-12-31(공휴일 표가 있는 해까지)의 실제 한국 달력([KoreanCalendar])으로 진짜 결제를 만들고, 오늘을 날마다 옮기며
 * 그날까지의 기록만으로 이번 달 · 지난 달 화면을 계산해 불변식(fixes3.md '검증 방법' I1~I6, 일부 냄 I7)을 본다. 읽는 범위는 화면 모델과 같다
 * ([fixedHistoryStart] ~ [fixedHistoryEnd]). 위반은 모아서 한 번에 보여 준다.
 */
class FixedExpenseSimulationTest {
    private val patterns = patterns()
    private val rows = patterns.flatMap { it.rows }

    @Test
    fun `실제 달력으로 날마다 보면 이번 달과 지난 달 화면이 불변식을 지킨다`() {
        val found = mutableListOf<Violation>()
        val checked = sortedMapOf<String, Int>()
        var day = FIRST.atDay(1)
        while (!day.isAfter(LAST.atEndOfMonth())) {
            val known = rows.filter { !it.localDate().isAfter(day) }
            val thisMonth = YearMonth.from(day)
            val now = boardOf(known, thisMonth, day)
            val before = boardOf(known, thisMonth.minusMonths(1), day)
            patterns.forEach { pattern ->
                val thisView = viewOf(pattern, now, thisMonth, known, day)
                found += check(pattern, day, thisView, viewOf(pattern, before, thisMonth.minusMonths(1), known, day), checked)
            }
            day = day.plusDays(1)
        }
        val report = report(found, checked)
        println(report)
        if (found.any { it.accepted == null }) fail(report)
    }

    /** 화면 모델처럼 [month] 의 읽는 범위만, 조회처럼 최신순으로 넘겨 계산한 판 */
    private fun boardOf(known: List<TransactionListItem>, month: YearMonth, today: LocalDate): FixedExpenseBoard = buildFixedExpenses(
        month,
        today,
        known
            .filter { YearMonth.from(it.localDate()) in fixedHistoryStart(month)..fixedHistoryEnd(month, today) }
            .sortedWith(compareByDescending<TransactionListItem> { it.occurredAt }.thenByDescending { it.id }),
    )

    private fun viewOf(
        pattern: Pattern,
        board: FixedExpenseBoard,
        month: YearMonth,
        known: List<TransactionListItem>,
        today: LocalDate,
    ): View {
        val range = fixedHistoryStart(month)..fixedHistoryEnd(month, today)
        return View(
            month = month,
            item = (board.due + board.paid + board.notThisMonth + board.stopped).firstOrNull { it.name == pattern.name },
            seen = known.filter { it.merchant == pattern.name && YearMonth.from(it.localDate()) in range },
        )
    }
}

/** [day] 에 [pattern] 을 이번 달([now]) · 지난 달([before]) 화면에서 본 것의 위반. [checked] 에 불변식마다 따져 본 수를 더한다. */
private fun check(pattern: Pattern, day: LocalDate, now: View, before: View, checked: MutableMap<String, Int>): List<Violation> {
    val found = mutableListOf<Violation>()
    fun judge(invariant: String, view: View, applies: Boolean, holds: Boolean, expected: () -> String) {
        if (!applies) return
        checked.merge(invariant, 1, Int::plus)
        if (holds) return
        val violation = Violation(pattern.name, day, invariant, view.month, expected(), describe(view.item))
        found += violation.copy(accepted = acceptedReason(pattern, violation, day))
    }
    for (view in listOf(now, before)) {
        val item = view.item
        // I1: 매달 내는 것은 3번째 결제 뒤로 '이번 달엔 안 내요' · '한동안 안 냈어요' 가 아니다
        val active = item != null && item.status !in setOf(FixedStatus.NOT_THIS_MONTH, FixedStatus.STOPPED)
        judge("I1", view, pattern.monthly && view.seen.size >= 3, active) { "아직 안 냈어요 또는 냈어요" }
        // I4: 낼 돈(등록하기 금액)은 진짜 한 달 치의 1.2배까지
        judge("I4", view, item?.status == FixedStatus.DUE, (item?.amount ?: 0) * 100 <= pattern.oneMonth * I4_LIMIT_PERCENT) {
            "낼 돈 ${pattern.oneMonth * I4_LIMIT_PERCENT / 100}원 이하"
        }
    }
    // I2: 지난 달 몫을 진짜로 다 냈으면 지난 달 화면은 냈어요
    val share = pattern.shareIn(before.month)
    val sharePaid = share != null && share.payments.isNotEmpty() && share.payments.all { !it.date.isAfter(day) }
    judge("I2", before, sharePaid, before.item?.status == FixedStatus.PAID) {
        "냈어요(${share?.payments.orEmpty().joinToString { it.date.toString() }})"
    }
    // I2b(fixes3.md 에 없음 · 검증에서 더함): I2 를 이번 달 화면으로 넓힌 것. 이번 달 몫을 진짜로 다 냈으면(미리 낸 것 포함) 이번 달 화면도 냈어요
    val nowShare = pattern.shareIn(now.month)
    val nowPaid = nowShare != null && nowShare.payments.isNotEmpty() && nowShare.payments.all { !it.date.isAfter(day) }
    judge("I2b", now, nowPaid, now.item?.status == FixedStatus.PAID) {
        "냈어요(${nowShare?.payments.orEmpty().joinToString { it.date.toString() }})"
    }
    // I7(일부 냄 · 리뷰 s1): 한 달 몫을 여러 번에 나눠 내는데 일부만 냈으면 그 달 화면은 냈어요가 아니다(남은 차례를 알린다)
    for (view in listOf(now, before)) {
        val pays = pattern.shareIn(view.month)?.payments.orEmpty()
        val partly = pays.any { !it.date.isAfter(day) } && pays.any { it.date.isAfter(day) }
        judge("I7", view, partly, view.item?.status != FixedStatus.PAID) {
            "냈어요 아님(${pays.joinToString { it.date.toString() }} 가운데 일부만 냄)"
        }
    }
    // I3: 한 결제가 두 달 화면에서 함께 냈어요의 근거가 되지 않는다
    val nowBases = now.paidBases()
    val apart = before.paidBases().any { basis -> nowBases.any { (it intersect basis).isEmpty() } }
    val bothPaid = now.item?.status == FixedStatus.PAID && before.item?.status == FixedStatus.PAID
    judge("I3", now, bothPaid, apart) { "${before.month} 과 ${now.month} 이 결제를 나눠 갖지 않음" }
    // I5: 이번 달 몫을 내기 전(미리 냄 제외)에는 냈어요가 아니다
    val due = pattern.shareIn(now.month)
    judge("I5", now, due?.payments.orEmpty().none { !it.date.isAfter(day) }, now.item?.status != FixedStatus.PAID) {
        "냈어요 아님(진짜 낼 날 ${due?.scheduled ?: "없음"})"
    }
    // I6: '평소보다 N일 지났어요' 는 진짜 낼 날(주말 · 공휴일 밀림 포함) 다음 날부터만 뜬다. 앱이 공휴일 달력으로 밀린 낼 날을 알므로
    // 봐주는 날이 없다(공휴일 달력을 넣기 전에는 주말 밀림 이틀과 긴 연휴를 받아들였다).
    val past = now.item?.daysPastUsual
    val scheduled = due?.scheduled
    val early = scheduled == null || !day.isAfter(scheduled)
    judge("I6", now, now.item?.status == FixedStatus.DUE && past != null && past > 0, !early) {
        // 긴 연휴(설 · 추석 등 평일 공휴일)로 밀린 것인지 보기 쉽게 그 수를 적는다(받아들이지 않음, [acceptedReason]).
        val holidays = scheduled?.let { end ->
            generateSequence(day) { it.plusDays(1) }.takeWhile { it < end }.count(KoreanCalendar::isWeekdayHoliday)
        }
        "지났어요 없음(진짜 낼 날 ${scheduled ?: "없음"}${holidays?.takeIf { it > 0 }?.let { " · 사이 평일 공휴일 ${it}일" }.orEmpty()})"
    }
    return found
}

/** 화면에 보이는 대로 한 줄 */
private fun describe(item: FixedExpenseItem?): String {
    if (item == null) return "줄 없음"
    val past = item.daysPastUsual?.let { " · 평소보다 ${it}일" }.orEmpty()
    val missed = item.missedMonth?.let { " · ${it}차례 놓침" }.orEmpty()
    val next = item.nextMonth?.let { " · 다음 $it" }.orEmpty()
    val last = "마지막 ${item.lastPaidOn}(${item.lastShareMonth}몫 ${item.lastPaidCount}건)"
    return "${item.status} ${item.cadence}달마다 ${item.usualDay}일 ${item.amount}원 · $last$past$missed$next"
}

/** fixes3.md '받아들이는 모호함' 에 해당하면 그 까닭 */
private fun acceptedReason(pattern: Pattern, violation: Violation, day: LocalDate): String? {
    // 새로 생긴 기록의 첫 몇 달은 어긋날 수 있다. 결제가 두세 번뿐이면 1일 것을 미리 낸 것과 말일 것이 밀린 것을 가를 증거가 모자라고
    // (월세P0 는 첫 결제가 미리 낸 12월 31일이다), 두 차례 가게는 두 달을 다 내야 두 차례인 줄 안다.
    // 새 일정 모델(리뷰 G1)로 다섯 달에서 석 달로 좁혔다.
    val first = pattern.shares.flatMap { it.payments }.minOf { it.date }
    if (YearMonth.from(first) >= YearMonth.from(day).minusMonths(NEW_RECORD_MONTHS)) return "새 기록의 첫 석 달(평소 쪽이 아직 자리 잡지 않음)"
    // 평소 날을 바꾼 첫 몇 달도 어긋날 수 있다. 평소 날은 최근 여섯 번 가운데 새 날에 딱 맞는 결제가 넷이 되어야 새 날로 넘어간다
    // ([estimateSchedule]). 바꾼 달부터 넉 달 몫의 화면과 넷째를 내기 전까지는 받아들인다.
    val changed = pattern.changed
    if (changed != null) {
        val fourth = pattern.shares.filter { it.month >= changed }.flatMap { it.payments }.map { it.date }.sorted().getOrNull(3)
        val settling = YearMonth.from(day) >= changed && (fourth == null || day < fourth)
        if (violation.view in changed..changed.plusMonths(3) || settling) return "평소 날을 바꾼 첫 몇 달(새 날 결제 넷째까지)"
    }
    // 새 일정 모델(리뷰 G1)에서는 매년 · 몇 달마다 결제의 달 경계 밀림과 평소 날과 반 달 넘게 떨어진 결제를 따로 받아들이지 않는다.
    // 긴 연휴(설 · 추석 등)로 밀린 결제는 받아들이지 않는다. 앱이 공휴일 달력(KoreanHolidays)으로 낼 날을 다음 영업일로 본다.
    return null
}

/** 위반을 가게 · 불변식 · 화면 달별로 묶어 첫날 ~ 마지막 날과 날 수로 보여 준다 */
private fun report(found: List<Violation>, checked: Map<String, Int>): String {
    val groups = found.groupBy { listOf(it.accepted != null, it.invariant, it.pattern, it.view) }
    val lines =
        groups.entries
            .sortedWith(
                compareBy<Map.Entry<List<Any>, List<Violation>>>({
                    it.key[0] as Boolean
                }, { it.key[1] as String }, { it.value.first().day }),
            )
            .map { (_, list) ->
                val first = list.first()
                val tag = first.accepted?.let { "[받아들임: $it] " }.orEmpty()
                "$tag${first.invariant} ${first.pattern} · ${first.view} 화면 · ${first.day}~${list.last().day}(${list.size}일)" +
                    " · 기대: ${first.expected} · 실제: ${first.actual}"
            }
    val counts =
        checked.map { (key, total) ->
            val failed = found.filter { it.invariant == key }
            val accepted = failed.count { it.accepted != null }
            "$key ${total - failed.size}/$total 통과 · 위반 ${failed.size - accepted} · 받아들임 $accepted"
        }
    return counts.joinToString("\n") + "\n" + lines.joinToString("\n")
}
