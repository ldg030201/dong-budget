package com.dong.budget.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 안쪽 내용을 기기 언어와 상관없이 한국어로 그린다.
 *
 * 머티리얼 달력과 시계는 월 이름, 요일, 오전/오후 같은 글자를 기기 언어 설정에서 읽는다.
 * 기기가 영어면 'September', 'Mon', 'AM' 이 나와서 나머지 한국어 화면과 섞인다.
 * 이 앱은 기기마다 같은 화면을 보여주는 것이 원칙이라 한국어로 고정한다.
 *
 * 앱 전체가 아니라 달력·시계 주변만 감싼다. 바꿔 끼운 Context 는 액티비티가 아니라서,
 * 앱 전체를 감싸면 Context 에서 액티비티를 찾는 코드가 깨질 수 있다.
 */
@Composable
fun KoreanLocale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val korean = remember(configuration) { Configuration(configuration).apply { setLocale(Locale.KOREA) } }
    val koreanContext = remember(context, korean) { context.createConfigurationContext(korean) }
    CompositionLocalProvider(
        LocalConfiguration provides korean,
        LocalContext provides koreanContext,
        content = content,
    )
}
