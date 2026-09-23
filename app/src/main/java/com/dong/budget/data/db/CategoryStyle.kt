package com.dong.budget.data.db

/**
 * 분류 아이콘과 색의 저장용 이름.
 *
 * DB 에는 안드로이드 리소스 번호가 아니라 이 문자열을 저장한다.
 * 리소스 번호는 빌드할 때마다 바뀔 수 있어서, 번호를 저장하면
 * 업데이트 후에 아이콘이 엉뚱한 것으로 바뀐다.
 *
 * 이름을 빼거나 바꾸면 이미 저장된 분류가 기본 아이콘으로 떨어진다. 추가만 할 것.
 */
object CategoryStyle {
    /** 아이콘 이름은 Material Symbols 의 이름과 같다. res/drawable/ic_sym_<이름>.xml */
    val ICONS =
        listOf(
            "restaurant",
            "local_cafe",
            "local_bar",
            "storefront",
            "shopping_bag",
            "directions_car",
            "directions_bus",
            "local_gas_station",
            "checkroom",
            "content_cut",
            "event_repeat",
            "home",
            "smartphone",
            "local_hospital",
            "school",
            "movie",
            "sports_esports",
            "fitness_center",
            "flight",
            "pets",
            "redeem",
            "payments",
            "savings",
            "more_horiz",
        )

    val COLORS =
        listOf("red", "orange", "amber", "green", "teal", "blue", "indigo", "purple", "pink", "gray")

    const val FALLBACK_ICON = "more_horiz"
    const val FALLBACK_COLOR = "gray"
}

/** 기본 분류 한 줄 */
data class DefaultCategory(
    val scope: CategoryScope,
    val code: String,
    val name: String,
    val icon: String,
    val color: String,
    val sortOrder: Int,
) {
    /** '기타' 는 지울 수 없다. 다른 분류를 지울 때 거래가 옮겨갈 곳이기 때문이다. */
    val isSystem: Boolean get() = code == ETC_EXPENSE_CODE || code == ETC_INCOME_CODE
}

const val ETC_EXPENSE_CODE = "ETC_EXPENSE"
const val ETC_INCOME_CODE = "ETC_INCOME"

/** '기타' 는 항상 맨 뒤에 오도록 정렬 순서를 크게 둔다. */
const val ETC_SORT_ORDER = 1000

/**
 * 기본 분류.
 *
 * 새로 설치할 때(SeedCallback)와 옛 DB 를 올릴 때(Migration1To2) 모두 이 목록을 쓴다.
 * 두 곳이 서로 다른 목록을 들고 있으면 설치 경로에 따라 분류가 달라진다.
 */
val DEFAULT_CATEGORIES =
    listOf(
        DefaultCategory(CategoryScope.EXPENSE, "FOOD", "식비", "restaurant", "orange", 0),
        DefaultCategory(CategoryScope.EXPENSE, "TRANSPORT", "교통/차량", "directions_car", "blue", 1),
        DefaultCategory(CategoryScope.EXPENSE, "CONVENIENCE", "편의점", "storefront", "green", 2),
        DefaultCategory(CategoryScope.EXPENSE, "FASHION", "패션/미용", "checkroom", "pink", 3),
        DefaultCategory(CategoryScope.EXPENSE, "FIXED", "고정지출", "event_repeat", "purple", 4),
        DefaultCategory(CategoryScope.EXPENSE, ETC_EXPENSE_CODE, "기타", "more_horiz", "gray", ETC_SORT_ORDER),
        DefaultCategory(CategoryScope.INCOME, "SALARY", "급여", "payments", "teal", 0),
        DefaultCategory(CategoryScope.INCOME, "ALLOWANCE", "용돈", "redeem", "amber", 1),
        DefaultCategory(CategoryScope.INCOME, ETC_INCOME_CODE, "기타", "more_horiz", "gray", ETC_SORT_ORDER),
    )

fun etcCodeFor(scope: CategoryScope): String = when (scope) {
    CategoryScope.EXPENSE -> ETC_EXPENSE_CODE
    CategoryScope.INCOME -> ETC_INCOME_CODE
}
