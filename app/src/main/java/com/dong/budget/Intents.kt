package com.dong.budget

import android.content.Context
import android.content.Intent

/**
 * 후보 화면 중 처음 열리는 것을 연다. 제조사나 버전마다 없거나 막아 둔 화면이 달라서 여러 개를 차례로 시도한다.
 * @return 어느 화면이든 열었는지
 */
fun Context.startFirst(intents: List<Intent>): Boolean = intents.any { runCatching { startActivity(it) }.isSuccess }
