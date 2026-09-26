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
            // 먹고 마시기
            "restaurant",
            "local_cafe",
            "local_bar",
            // 가게·쇼핑
            "storefront",
            "shopping_bag",
            "checkroom",
            "content_cut",
            // 이동
            "directions_car",
            "directions_bus",
            "local_gas_station",
            "flight",
            // 생활
            "home",
            "smartphone",
            "event_repeat",
            "local_hospital",
            "school",
            "pets",
            // 여가
            "movie",
            "sports_esports",
            "fitness_center",
            "redeem",
            // 돈·결제수단
            "payments",
            "credit_card",
            "account_balance",
            "account_balance_wallet",
            "contactless",
            "savings",
            // 기타
            "interests",
            "more_horiz",
        )

    val COLORS =
        listOf("red", "orange", "amber", "green", "teal", "blue", "indigo", "purple", "pink", "gray")

    const val FALLBACK_ICON = "more_horiz"
    const val FALLBACK_COLOR = "gray"

    /**
     * 새로 만드는 분류·결제수단의 색. 아직 안 쓴 색 중 첫 번째(회색 제외), 다 쓰였으면 회색.
     * 추가 창의 처음 색과 알림에서 읽은 카드를 새로 만들 때의 색이 같은 규칙을 쓰게 한 곳에 둔다.
     */
    fun firstUnusedColor(used: Set<String>): String = COLORS.firstOrNull { it != FALLBACK_COLOR && it !in used } ?: FALLBACK_COLOR
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
        DefaultCategory(CategoryScope.EXPENSE, ETC_EXPENSE_CODE, "기타", "interests", "gray", ETC_SORT_ORDER),
        DefaultCategory(CategoryScope.INCOME, "SALARY", "급여", "payments", "teal", 0),
        DefaultCategory(CategoryScope.INCOME, "ALLOWANCE", "용돈", "redeem", "amber", 1),
        DefaultCategory(CategoryScope.INCOME, ETC_INCOME_CODE, "기타", "interests", "gray", ETC_SORT_ORDER),
    )

fun etcCodeFor(scope: CategoryScope): String = when (scope) {
    CategoryScope.EXPENSE -> ETC_EXPENSE_CODE
    CategoryScope.INCOME -> ETC_INCOME_CODE
}

/** 기본 결제수단 한 줄 */
data class DefaultPaymentMethod(
    val code: String,
    val name: String,
    val type: PaymentMethodType,
    val icon: String,
    val color: String,
    val sortOrder: Int,
) {
    /** 1.x 부터 쓰던 uuid 를 그대로 쓴다. 결제수단은 이름이 바뀌지 않아서 겹칠 일이 없다. */
    val uuid: String get() = "seed:payment:$code"
}

/**
 * 기본 결제수단. 분류와 마찬가지로 새 설치와 옛 DB 변환이 같은 목록을 쓴다.
 * 결제수단은 비워둘 수 있는 칸이라 지울 수 없는 '기타' 같은 것은 두지 않는다.
 */
val DEFAULT_PAYMENT_METHODS =
    listOf(
        DefaultPaymentMethod("CASH", "현금", PaymentMethodType.CASH, "payments", "green", 0),
        DefaultPaymentMethod("CHECK_CARD", "체크카드", PaymentMethodType.CARD, "credit_card", "blue", 1),
        DefaultPaymentMethod("CREDIT_CARD", "신용카드", PaymentMethodType.CARD, "credit_card", "purple", 2),
        DefaultPaymentMethod("ACCOUNT", "계좌이체", PaymentMethodType.ACCOUNT, "account_balance", "teal", 3),
    )
