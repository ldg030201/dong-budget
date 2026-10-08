package com.dong.budget.data.update

import android.app.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * 새 버전 확인과 업데이트. 배포처(빌드 종류 store)마다 하는 일이 다르다.
 *  - github: GitHub Releases 를 확인하고 APK 를 내려받아 앱 안에서 설치한다(src/github)
 *
 * 홈 알림 줄, 설정의 앱 정보 줄, 패치노트처럼 배포처와 상관없이 같은 화면은 이것만 본다.
 * 배포처마다 모양이 다른 곳은 배포처 소스에 같은 이름으로 하나씩 둔다.
 *  - data.update.StoreUpdates: 이 약속을 지키는 배포처의 구현. AppContainer 가 만든다.
 *  - ui.settings.AppInfoRoute: 앱 정보 화면(업데이트 칸)
 *  - ui.permission.STORE_PERMISSIONS: 배포처에만 있는 권한(GitHub 배포의 설치 허용)
 */
interface AppUpdates {
    /** 앱이 켜질 때(Application.onCreate) 한 번 부른다. 오래 걸리는 일은 뒤 스레드에서 한다. */
    fun start(application: Application)

    /** 앱을 열 때 확인한다. 얼마나 자주 확인할지는 배포처마다 다르다. 여러 번 겹쳐 불려도 한 번만 돈다. */
    suspend fun checkIfDue()

    /** 아직 설치하지 않은 새 버전(설정의 앱 정보 줄). 홈 알림 줄을 닫거나 건너뛴 것과 상관없다. 없으면 null */
    val newer: StateFlow<UpdateNotice?>

    /** 홈 알림 줄에 보일 것. 닫았거나 건너뛴 버전이면 null */
    val notice: Flow<UpdateNotice?>

    /** 배포됐지만 아직 설치하지 않은 버전들의 바뀐 점(패치노트 맨 위). 최신순 */
    val newerNotes: StateFlow<List<NewerNotes>>

    /** 홈 알림 줄을 이번 실행에서만 닫는다. 앱을 다시 켜면 다시 보인다. */
    fun dismissNotice()

    /** 홈 알림 줄의 새 버전을 건너뛴다. 앱을 다시 켜도 알리지 않고, 더 새 버전이 나오면 다시 알린다. */
    fun skipNotice()
}

/** 홈 알림 줄과 설정의 앱 정보 줄이 알리는 새 버전 소식 */
sealed interface UpdateNotice {
    /** 새 버전이 나왔다 */
    data class Available(val version: String) : UpdateNotice
}

/**
 * 배포됐지만 아직 설치하지 않은 버전의 바뀐 점. 배포 본문의 글 그대로다.
 * @property date 배포한 날. 알 수 없으면 null
 */
data class NewerNotes(val version: String, val date: LocalDate?, val notes: String)
