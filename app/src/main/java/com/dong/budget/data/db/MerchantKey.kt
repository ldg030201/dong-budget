package com.dong.budget.data.db

import java.util.Locale

/**
 * 가게를 묶는 열쇠. 공백을 모두 빼고 소문자로 만든다(결제수단 이름을 견주는 규칙과 같다).
 * '스타벅스 ' 와 '스타 벅스', 'GS25' 와 'gs25' 가 한 가게가 된다.
 * 통계의 많이 쓴 곳 · 거래 상세의 같은 곳 내역 · 고정지출이 같은 열쇠로 묶는다.
 * @return 이름이 없거나 공백뿐이면 null. 그런 거래는 가게별로 세지 않는다.
 */
fun merchantKey(merchant: String?): String? = merchant
    ?.filterNot { it.isWhitespace() }
    ?.lowercase(Locale.ROOT)
    ?.takeIf { it.isNotEmpty() }
