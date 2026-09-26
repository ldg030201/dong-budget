package com.dong.budget.ui.patchnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.update.NewerRelease
import com.dong.budget.data.update.UpdateChecker
import com.dong.budget.data.update.UpdateRepository
import com.dong.budget.data.update.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 패치노트 화면에 보여줄, 아직 설치하지 않은 새 버전들.
 *
 * 앱에 들어 있는 패치노트(PATCH_NOTES)는 설치된 버전까지만 안다. 새 버전의 바뀐 점은 GitHub 배포 본문에서 받아온다.
 * 받아오기 전이나 받지 못했을 때(인터넷 없음 등)는 마지막 업데이트 확인에서 찾아둔 새 버전을 먼저 보여준다.
 */
class PatchNotesViewModel(updateRepository: UpdateRepository, updateChecker: UpdateChecker) : ViewModel() {
    private val _newer =
        MutableStateFlow(
            updateChecker.available.value
                ?.let { listOf(NewerRelease(version = it.version, date = null, notes = it.notes)) }
                .orEmpty(),
        )
    val newer: StateFlow<List<NewerRelease>> = _newer.asStateFlow()

    init {
        // 방금 확인해서 새 버전이 없다는 걸 알고 있으면 목록을 받지 않는다.
        // GitHub 는 로그인 없이 부를 수 있는 횟수가 시간당 60번이라 같은 정보를 거듭 받지 않는다.
        val knownUpToDate = updateChecker.checkedRecently() && updateChecker.available.value == null
        if (!knownUpToDate) {
            viewModelScope.launch {
                updateRepository.newerReleases().onSuccess { list ->
                    _newer.value = list
                    // 받은 목록을 새 버전 확인 결과로도 기록한다. 홈 배너와 설정 화면이 같은 최신 버전을 보게 된다.
                    val newest = list.firstOrNull()
                    updateChecker.apply(
                        if (newest == null) {
                            UpdateStatus.UpToDate
                        } else {
                            UpdateStatus.Available(newest.version, newest.notes, newest.downloadUrl, newest.sizeBytes)
                        },
                    )
                }
            }
        }
    }
}
