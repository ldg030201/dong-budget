package com.dong.budget.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * 쓰지 못하는 설정 파일(DataStore). 저장 공간이 모자란 폰 흉내라 쓰기는 늘 IOException 이다.
 * @param failRead 읽기도 IOException 으로 실패하게 할지. 아니면 빈 설정(기본값)으로 읽힌다.
 */
class FailingPreferencesStore(private val failRead: Boolean = false) : DataStore<Preferences> {
    override val data: Flow<Preferences> =
        flow {
            if (failRead) throw IOException("설정 파일을 읽을 수 없어요")
            emit(emptyPreferences())
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("저장 공간이 모자라요")
}
