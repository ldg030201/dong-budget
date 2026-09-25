package com.dong.budget

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.ui.DongBudgetApp
import com.dong.budget.ui.theme.BudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            // 저장값이 아직 도착하지 않은 동안에는 시스템 설정을 따른다.
            // XML 창 배경도 시스템 다크 모드를 따르므로 그동안 색이 어긋나지 않는다.
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val dark =
                when (themeMode) {
                    null, ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

            // 시스템 바 아이콘 밝기를 앱 테마에 맞춘다.
            //
            // enableEdgeToEdge 는 화면 회전 같은 구성 변경 때 자신이 심어둔 숨은 View 를 통해
            // 설정을 다시 적용하는데, 기본값인 SystemBarStyle.auto 는 그때 앱 테마가 아니라
            // 기기의 다크 모드를 기준으로 판단한다. 그래서 스타일을 명시적으로 넘겨야
            // 사용자가 라이트/다크를 직접 고른 경우에도 회전 후 아이콘 색이 뒤집히지 않는다.
            //
            // 명시적으로 넘기면 3버튼 내비게이션의 반투명 스크림도 함께 꺼지므로
            // window.isNavigationBarContrastEnforced 를 따로 건드릴 필요가 없다.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            BudgetTheme(darkTheme = dark) {
                val background = MaterialTheme.colorScheme.background
                // 창 배경(XML 테마)은 기기의 다크 모드를 따른다. 앱에서만 '어둡게' 를 고르면 창 배경은 흰색으로 남아,
                // 화면이 바뀌며 두 화면이 반투명하게 겹치는 순간 흰빛이 비친다. 창 배경도 앱 테마 색으로 맞춘다.
                SideEffect { window.setBackgroundDrawable(ColorDrawable(background.toArgb())) }
                // 화면 전환 중에 비치는 바닥도 앱 테마의 배경색으로 칠해 둔다
                Box(modifier = Modifier.fillMaxSize().background(background)) {
                    DongBudgetApp(container = (application as BudgetApplication).container)
                }
            }
        }
    }
}
