package com.dong.budget.ui.patchnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.update.NewerRelease
import com.dong.budget.data.update.UpdateChecker
import com.dong.budget.data.update.UpdateRepository
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
        viewModelScope.launch {
            updateRepository.newerReleases().onSuccess { _newer.value = it }
        }
    }
}
