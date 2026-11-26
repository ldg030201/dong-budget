package com.dong.budget.data.settings

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.dong.budget.data.devlog.DevLog

/**
 * 깨진 설정 파일(Preferences DataStore)을 빈 파일로 바꾸는 처리기. 바꾼 뒤에는 기본값으로 읽고 쓸 수 있다.
 *
 * 깨진 파일은 읽을 수도 없어서 저장소는 이미 기본값으로 보여 주고 있다(읽기의 catch). 하지만 쓰기(edit)는 지금 값을 먼저 읽느라
 * CorruptionException 을 던져서, 처리기가 없으면 설정을 바꿀 때마다 앱이 죽고 파일은 영영 깨진 채로 남는다.
 * @param tag 로그 묶음
 * @param what 로그에 적을 파일 이름("설정")
 */
internal fun resetWhenCorrupted(tag: String, what: String): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { cause ->
        DevLog.warn(tag, "$what 파일이 깨져서 기본값으로 바꿨어요", cause)
        emptyPreferences()
    }
