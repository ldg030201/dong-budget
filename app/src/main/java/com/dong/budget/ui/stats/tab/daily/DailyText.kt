package com.dong.budget.ui.stats.tab.daily

import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatDateSpoken
import com.dong.budget.ui.format.formatNetExpense
import com.dong.budget.ui.format.formatSignedTotal
import com.dong.budget.ui.format.formatSpentAmount
import com.dong.budget.ui.format.formatWeekday
import com.dong.budget.ui.format.formatWeekdayFull
import com.dong.budget.ui.stats.DayStack
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.StatSeries
import com.dong.budget.ui.stats.WeekdayAverage
import com.dong.budget.ui.stats.chart.WeekdayBar
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────
// 일별 탭에서만 쓰는 문구. 여러 탭이 같이 쓰는 문장은 calc/StatsSentences.kt 에 있고, 여기는 그 밖의 것이다.
// 화면 읽기 문장은 늘 전체 금액으로 쓴다.
// ─────────────────────────────────────────────────────────────────────

/** 읽기 판의 날짜 줄. "9월 3일 수요일", 오늘이면 "9월 3일 수요일 · 오늘" */
internal fun dayTitle(date: LocalDate, today: LocalDate): String {
    val base = formatDateSpoken(date)
    return if (date == today) "$base · 오늘" else base
}

/** 하루 평균·쓴 날 평균 값. 낼 수 없으면(센 날이 없거나 환불이 더 많음) "—" */
internal fun averageValue(amount: Long?): String = amount?.let { formatNetExpense(it) } ?: "—"

/** 하루 평균 줄의 캡션. "27일 기준" */
internal fun countedCaption(counted: IntRange): String = "${counted.count()}일 기준"

/** 쓴 날 평균 줄의 캡션. "돈을 쓴 12일 기준" */
internal fun spentDaysCaption(spentDays: Int): String = "돈을 쓴 ${spentDays}일 기준"

/**
 * 날마다 쓴 돈 차트의 칸 하나를 화면 읽기로 읽을 문장.
 * "9월 3일 수요일, 지출 32,000원, 식비 20,000원, 교통/차량 12,000원"
 * 지출이 없으면 ", 지출 없음", 오늘 뒤의 날이면 ", 아직 오지 않은 날"(미리 적은 지출이 있으면 그 금액도)이다.
 * 오늘 칸에는 날짜 뒤에 ", 오늘" 을 붙여 칸을 훑다가 오늘을 알아채게 한다.
 */
internal fun daySlotDescription(day: DayStack, today: LocalDate): String = buildList {
    add(formatDateSpoken(day.date))
    if (day.date == today) add("오늘")
    when {
        day.isFuture -> {
            add("아직 오지 않은 날")
            if (day.expense > 0) add("미리 적은 지출 ${formatAmount(day.expense)}원")
        }

        day.expense > 0 -> add("지출 ${formatAmount(day.expense)}원")

        else -> add("지출 없음")
    }
    day.details.forEach { add("${it.name} ${formatSpentAmount(it.amount)}") }
}.joinToString(", ")

/** 필터 칩의 화면 읽기 문장. '전체' 칩은 "모든 분류 보기", 계열 칩은 "식비만 보기" */
internal fun filterChipDescription(series: StatSeries?): String = series?.let { "${it.name}만 보기" } ?: "모든 분류 보기"

/** 읽기 판에서 접어 둔 거래를 펼치는 버튼. "2건 더 보기" */
internal fun moreItemsText(hidden: Int): String = "${hidden}건 더 보기"

/**
 * '하나만 보기' 로 고른 계열을 저장해 두는 이름. 번호가 아니라 분류를 가리켜서, 거래를 고쳐 순위가 바뀌어도 같은 분류를 따라간다.
 * 여러 분류를 묶은 '그 외' 는 하나로 본다. 고른 분류가 '그 외' 로 접히면 이름을 못 찾아 '전체' 로 돌아간다.
 */
internal fun StatSeries.focusKey(): String = when (val group = key) {
    is GroupKey.Id -> "id:${group.id}"
    GroupKey.None -> "none"
    null -> "other"
}

/**
 * 요일 가로 막대 한 줄. 값 글자는 "32,000원", 합쳐 읽는 문장은 "토요일, 하루 평균 32,000원, 가장 많음".
 * @param top 가장 많이 쓴 요일인지
 */
internal fun weekdayBar(average: WeekdayAverage, top: Boolean): WeekdayBar {
    // 화면에는 지출 부호를 붙이고, 화면 읽기는 '하루 평균 32,000원' 으로 읽는다(말이 방향을 전한다)
    val spoken = formatSpentAmount(average.average)
    val description = listOfNotNull(formatWeekdayFull(average.day), "하루 평균 $spoken", "가장 많음".takeIf { top }).joinToString(", ")
    return WeekdayBar(
        label = formatWeekday(average.day),
        value = average.average,
        valueText = formatNetExpense(average.average),
        description = description,
    )
}
