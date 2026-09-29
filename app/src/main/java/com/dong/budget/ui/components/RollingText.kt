package com.dong.budget.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import com.dong.budget.ui.theme.Motion

/**
 * 바뀐 글자만 아래에서 위로 굴러 올라오는 글. 초마다 오르는 금액(월급 탭)에 쓴다.
 *
 * 글자마다 오른쪽 끝에서 센 자리로 칸을 잡는다. 자리수가 늘어도(9,999 → 10,000) 일의 자리는 같은 칸에 남아 그 칸만 움직인다.
 * 금액 글꼴은 고정폭 숫자(tnum)라 칸 폭이 바뀌지 않는다.
 * 화면 읽기에서는 뺀다. 매초 바뀌는 글을 읽게 하면 따라가지 못한다. 뜻은 부르는 쪽이 거칠게 끊은 글로 전한다.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.clearAndSetSemantics {}) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (
                            slideInVertically(Motion.quick()) { it } + fadeIn(Motion.quick()) togetherWith
                                slideOutVertically(Motion.quick()) { -it } + fadeOut(Motion.quick())
                            )
                            .using(SizeTransform(clip = false))
                    },
                    // 위아래로 밀리는 글자가 윗줄·아랫줄에 겹치지 않게 자기 칸 안에서만 보인다
                    modifier = Modifier.clipToBounds(),
                    label = "rollingChar",
                ) { shown -> Text(text = shown.toString(), style = style, color = color) }
            }
        }
    }
}
