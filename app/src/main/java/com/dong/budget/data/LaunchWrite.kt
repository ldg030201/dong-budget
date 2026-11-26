package com.dong.budget.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 저장 [write] 를 맡긴다. 화면을 떠나 화면 모델이 끝나도(이 범위가 끊겨도) 끝까지 한다.
 * 끊기면 DataStore 가 아직 차례가 안 온 쓰기를 버려서, 키패드를 빨리 치고 바로 나가면 마지막 숫자가 저장되지 않았다.
 * 부른 자리에서 바로 시작해(UNDISPATCHED) 부른 차례대로 DataStore 에 넘긴다. DataStore 는 받은 차례대로 써서 마지막 값이 마지막에 쓰인다.
 * 저장 공간이 모자라는 등 파일에 쓰지 못하면(IOException) 앱을 죽이지 않고 [onFailure] 로 넘긴다. 취소와 그 밖의 오류(코드 잘못)는 잡지 않는다.
 * 월급 설정과 설정 화면(테마·한 주 시작·자동 백업·자동 기능 스위치)이 같이 쓴다.
 */
internal fun CoroutineScope.launchWrite(onFailure: (IOException) -> Unit = {}, write: suspend () -> Unit): Job =
    launch(start = CoroutineStart.UNDISPATCHED) {
        withContext(NonCancellable) {
            try {
                write()
            } catch (e: IOException) {
                onFailure(e)
            }
        }
    }
