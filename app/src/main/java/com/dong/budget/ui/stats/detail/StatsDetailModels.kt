package com.dong.budget.ui.stats.detail

import androidx.compose.runtime.Immutable
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.ui.home.DayGroup
import com.dong.budget.ui.stats.Breakdown
import com.dong.budget.ui.stats.BreakdownEntry
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.MerchantStat
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 통계 상세(분류·결제수단 하나)가 그리는 값. 숫자는 분류·결제수단 탭과 같은 함수(calc 의 breakdown 등)로 낸다.
// 그래서 탭의 줄과 상세 머리의 금액·비율·건수·증감이 늘 같다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 상세 화면 전체의 상태.
 * @property loaded 첫 계산이 끝났는지. 끝나기 전에는 상단 바와 달 줄만 그린다.
 * @property key 이 상세가 보는 묶음. '분류 없음' / '결제수단 없음' 이면 [GroupKey.None]
 * @property month 이 화면만의 달. 통계 본 화면의 달과 따로 움직인다.
 * @property amount 이 달 이 항목의 금액(지출이면 지출 − 환불, 음수 가능)
 * @property entry 이 달 이 항목(비율·건수·한 번에 평균). 이 달 금액이 0 이면 null. 증감은 여기 말고 [change] 에 있다.
 * @property change 지난달 대비. 이 달 금액이 0 이어도 지난달에 있었으면 있다. 비교할 수 없으면 null
 * @property trend 고른 달까지 최근 6개월. 오래된 달이 앞이고 마지막 칸이 고른 달이다.
 * @property cross 교차 비중(분류 상세는 결제수단별, 결제수단 상세는 분류별). 수입 분류는 null
 * @property merchants 많이 쓴 곳 상위 5곳. 수입 분류는 비어 있다.
 * @property days 이 달 이 항목의 거래를 날짜별로(최근 날이 먼저)
 * @property count 이 달 이 항목의 거래 수(지출이면 지출·환불, 수입이면 수입)
 */
@Immutable
data class StatsDetailUiState(
    val loaded: Boolean,
    val dimension: StatsDimension,
    val key: GroupKey,
    val month: YearMonth,
    val today: LocalDate,
    val entity: DetailEntity,
    val amount: Long,
    val entry: BreakdownEntry?,
    val change: EntryChange?,
    val trend: List<DetailMonth>,
    val cross: Breakdown?,
    val merchants: List<MerchantStat>,
    val days: List<DayGroup>,
    val count: Int,
) {
    companion object {
        /** 첫 계산 전 자리 값 */
        fun loading(dimension: StatsDimension, key: GroupKey, month: YearMonth, today: LocalDate): StatsDetailUiState = StatsDetailUiState(
            loaded = false,
            dimension = dimension,
            key = key,
            month = month,
            today = today,
            entity = DetailEntity(name = "", icon = null, color = null, missing = false),
            amount = 0,
            entry = null,
            change = null,
            trend = emptyList(),
            cross = null,
            merchants = emptyList(),
            days = emptyList(),
            count = 0,
        )
    }
}

/**
 * 상세가 보는 분류나 결제수단의 이름·아이콘·색. 분류 관리에서 이름이나 색을 바꾸면 따라 바뀐다.
 * @property icon 아이콘 이름(CategoryStyle). '분류 없음'·지운 것은 null
 * @property color 색 이름(CategoryPalette). '분류 없음'·지운 것은 null
 * @property missing id 로 찾을 수 없는지(지운 분류·결제수단). 제목 아래에 거래가 어디로 갔는지 알린다.
 */
data class DetailEntity(val name: String, val icon: String?, val color: String?, val missing: Boolean)

/**
 * 최근 6개월 막대의 한 칸.
 * @property amount 그 달 이 항목의 금액(음수면 막대는 0 으로 그린다)
 * @property beforeFirstRecord 기록을 시작하기 전 달인지. 막대 없이 달 이름만 둔다.
 */
data class DetailMonth(val month: YearMonth, val amount: Long, val count: Int, val beforeFirstRecord: Boolean)
