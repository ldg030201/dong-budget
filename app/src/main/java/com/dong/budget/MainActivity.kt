package com.dong.budget

import android.content.Intent
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
import com.dong.budget.data.capture.CaptureNotifier
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.settings.ThemeMode
import com.dong.budget.ui.DongBudgetApp
import com.dong.budget.ui.theme.BudgetTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** 결제 등록 알림을 눌러 들어왔을 때 그 결제의 열쇠. 등록창을 열면 비운다. */
    private val capturedToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 회전 등으로 다시 만들어질 때는 같은 Intent 가 또 들어온다. 등록창은 백스택에 이미 복원돼 있다.
        if (savedInstanceState == null) receiveCaptured(intent)

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
                    val captured by capturedToOpen.collectAsStateWithLifecycle()
                    DongBudgetApp(
                        container = (application as BudgetApplication).container,
                        capturedToOpen = captured,
                        onCapturedOpened = { capturedToOpen.value = null },
                    )
                }
            }
        }
    }

    /** 앱이 열린 채로 결제 등록 알림을 누르면 여기로 온다(singleTop) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveCaptured(intent)
    }

    private fun receiveCaptured(intent: Intent?) {
        if (intent?.action != CaptureNotifier.ACTION_OPEN_CAPTURED) return
        // 최근 앱 목록에서 다시 열면 처음 열었던 Intent 가 그대로 다시 온다. 그때 등록창을 또 띄우지 않는다.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        // 열쇠는 extras 가 아니라 identifier 에 담겨 온다. 동계부 첫 화면은 다른 앱도 열 수 있는데,
        // extras 를 읽으면 다른 앱이 넣은 망가진 값을 풀다가 앱이 죽을 수 있다. identifier 는 그냥 문자열이다.
        capturedToOpen.value = intent.identifier
    }
}
