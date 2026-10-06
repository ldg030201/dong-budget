package com.dong.budget.data.settings

import kotlinx.serialization.Serializable

/**
 * 아래 메뉴에 둘 수 있는 칸.
 *
 * @property key 설정 저장소에 적는 이름. 저장된 값을 가리키므로 바꾸지 않는다(바꾸면 사용자가 고른 차림이 풀린다).
 *   이름(name)도 셸이 탭마다 화면 상태를 보관하는 열쇠라 바꾸지 않는다.
 */
@Serializable
enum class MenuItem(val key: String) {
    HOME("home"),

    /** 실시간 월급 */
    SALARY("salary"),

    /** 통계. 탭이 아니라 입구다. 누르면 통계 화면이 셸 위로 올라온다. */
    STATISTICS("statistics"),

    /** '고정지출' 분류로 적은 지출을 가게별로 묶어 이번 달 냈는지 본다 */
    FIXED_EXPENSE("fixed_expense"),

    /** 카드마다 실적 구간을 얼마나 채웠는지 본다 */
    CARD_PERFORMANCE("card_performance"),

    MORE("more"),
    ;

    /** 늘 그 자리(홈은 맨 앞, 전체는 맨 뒤)에 있어 빼거나 옮길 수 없는 칸 */
    val fixed: Boolean get() = this == HOME || this == MORE

    /** 아래 메뉴에서 누르면 셸 안의 탭으로 바뀌는 칸. 아니면(통계) 셸 위에 화면을 연다. */
    val isTab: Boolean get() = this != STATISTICS

    companion object {
        /** 넣고 빼고 옮길 수 있는 칸. 처음 보는 칸이 '넣을 수 있는 메뉴' 에 들어가는 순서이기도 하다. */
        val movable: List<MenuItem> = entries.filterNot { it.fixed }

        fun fromKey(key: String): MenuItem? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 아래 메뉴 차림. 홈이 맨 앞, 전체가 맨 뒤에 늘 있고, 그 사이 칸([shown])을 사용자가 고르고 줄 세운다.
 * 가운데 칸이 [VISIBLE_MIDDLE] 개보다 많으면 홈과 전체 사이에서 그만큼만 보이고 옆으로 밀어 바꾼다.
 * 뺀 칸([hidden])도 순서를 기억한다. 설정 화면의 '넣을 수 있는 메뉴' 가 그 순서로 늘어선다.
 *
 * 만들 때는 [of] 를 쓴다. 겹치거나 고정 칸이 섞인 목록을 정리하고, 어느 쪽에도 없는 칸을 뺀 쪽 끝에 붙인다.
 */
data class BottomMenu(val shown: List<MenuItem>, val hidden: List<MenuItem>) {
    /** 아래 메뉴에 놓이는 칸 전부, 왼쪽부터 */
    val items: List<MenuItem> get() = listOf(MenuItem.HOME) + shown + MenuItem.MORE

    /** 아래 메뉴에 있는 칸인지. 홈과 전체는 늘 있다. */
    operator fun contains(item: MenuItem): Boolean = item.fixed || item in shown

    /** 가운데 칸이 넘쳐 옆으로 밀어 봐야 하는지 */
    val scrolls: Boolean get() = shown.size > VISIBLE_MIDDLE

    /** 저장소에 적는 글. "salary,statistics|card_performance" (넣은 칸 | 뺀 칸) */
    fun encode(): String = shown.joinToString(",") { it.key } + SECTION_SEPARATOR + hidden.joinToString(",") { it.key }

    companion object {
        /** 가운데에 한 번에 보이는 칸 수. 홈·전체와 합쳐 여섯 칸이면 좁은 폰(320dp)에서도 네 글자 이름이 한 줄에 든다. */
        const val VISIBLE_MIDDLE = 4

        private const val SECTION_SEPARATOR = "|"

        /** 처음 차림. 홈 · 월급 · 통계 · 고정지출 · 전체(사용자 결정) */
        val DEFAULT: BottomMenu = of(listOf(MenuItem.SALARY, MenuItem.STATISTICS, MenuItem.FIXED_EXPENSE))

        /** [shown] 을 넣고 나머지는 [hidden] 순서로, 거기에도 없으면 기본 순서로 뺀 차림 */
        fun of(shown: List<MenuItem>, hidden: List<MenuItem> = emptyList()): BottomMenu {
            val cleanShown = shown.filterNot { it.fixed }.distinct()
            val cleanHidden = (hidden + MenuItem.movable).filterNot { it.fixed || it in cleanShown }.distinct()
            return BottomMenu(cleanShown, cleanHidden)
        }

        /** 저장된 글([encode])을 읽는다. 적힌 적 없으면 [DEFAULT]. 모르는 이름은 버리고, 새로 생긴 칸은 뺀 쪽 끝에 둔다. */
        fun decode(value: String?): BottomMenu {
            if (value == null) return DEFAULT
            val sections = value.split(SECTION_SEPARATOR)
            fun parse(section: String?): List<MenuItem> = section.orEmpty().split(',').mapNotNull { MenuItem.fromKey(it.trim()) }
            return of(parse(sections.getOrNull(0)), parse(sections.getOrNull(1)))
        }
    }
}
