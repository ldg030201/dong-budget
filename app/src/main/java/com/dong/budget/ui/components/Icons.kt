package com.dong.budget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.dong.budget.R
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.CategorySwatch

/**
 * 저장된 아이콘 이름을 그림 리소스로 바꾼다.
 *
 * 리소스를 이름 문자열로 찾지(getIdentifier) 않고 여기서 하나씩 짝지어 둔다.
 * 그래야 오타가 컴파일에서 잡히고, 코드 축소(R8)가 쓰이는 그림을 지우지 않는다.
 * CategoryStyle.ICONS 에 이름을 추가하면 여기에도 반드시 추가할 것. 단위 테스트가 빠진 것을 잡는다.
 */
@DrawableRes
fun categoryIconRes(key: String?): Int = when (key) {
    "restaurant" -> R.drawable.ic_sym_restaurant
    "local_cafe" -> R.drawable.ic_sym_local_cafe
    "local_bar" -> R.drawable.ic_sym_local_bar
    "storefront" -> R.drawable.ic_sym_storefront
    "shopping_bag" -> R.drawable.ic_sym_shopping_bag
    "directions_car" -> R.drawable.ic_sym_directions_car
    "directions_bus" -> R.drawable.ic_sym_directions_bus
    "local_gas_station" -> R.drawable.ic_sym_local_gas_station
    "checkroom" -> R.drawable.ic_sym_checkroom
    "content_cut" -> R.drawable.ic_sym_content_cut
    "event_repeat" -> R.drawable.ic_sym_event_repeat
    "home" -> R.drawable.ic_sym_home
    "smartphone" -> R.drawable.ic_sym_smartphone
    "local_hospital" -> R.drawable.ic_sym_local_hospital
    "school" -> R.drawable.ic_sym_school
    "movie" -> R.drawable.ic_sym_movie
    "sports_esports" -> R.drawable.ic_sym_sports_esports
    "fitness_center" -> R.drawable.ic_sym_fitness_center
    "flight" -> R.drawable.ic_sym_flight
    "pets" -> R.drawable.ic_sym_pets
    "redeem" -> R.drawable.ic_sym_redeem
    "payments" -> R.drawable.ic_sym_payments
    "savings" -> R.drawable.ic_sym_savings
    "credit_card" -> R.drawable.ic_sym_credit_card
    "account_balance" -> R.drawable.ic_sym_account_balance
    "account_balance_wallet" -> R.drawable.ic_sym_account_balance_wallet
    "contactless" -> R.drawable.ic_sym_contactless
    "interests" -> R.drawable.ic_sym_interests
    else -> R.drawable.ic_sym_more_horiz
}

/** 옅은 색 원 안에 진한 색 아이콘 */
@Composable
fun IconBadge(@DrawableRes iconRes: Int, swatch: CategorySwatch, modifier: Modifier = Modifier, size: Dp = BudgetTheme.size.badge) {
    Box(
        modifier = modifier.size(size).background(swatch.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            // 아이콘은 꾸밈이다. 분류 이름이 항상 글자로 옆에 있다.
            contentDescription = null,
            tint = swatch.content,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

@Composable
fun CategoryBadge(icon: String?, color: String?, modifier: Modifier = Modifier, size: Dp = BudgetTheme.size.badge) {
    IconBadge(
        iconRes = categoryIconRes(icon),
        swatch = BudgetTheme.categoryPalette[color],
        modifier = modifier,
        size = size,
    )
}

private const val ICON_RATIO = 0.55f
