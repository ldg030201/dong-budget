package com.dong.budget.ui.patchnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.update.NewerRelease
import com.dong.budget.data.update.UpdateChecker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 패치노트 화면에 보여줄, 아직 설치하지 않은 새 버전들.
 *
 * 앱에 들어 있는 패치노트(PATCH_NOTES)는 설치된 버전까지만 안다. 새 버전의 바뀐 점은 업데이트 확인이
 * GitHub 배포 목록에서 받아 둔 것을 보여준다. 마지막 확인이 오래됐으면 다시 확인한다.
 */
class PatchNotesViewModel(updateChecker: UpdateChecker) : ViewModel() {
    val newer: StateFlow<List<NewerRelease>> = updateChecker.newer

    init {
        viewModelScope.launch { updateChecker.checkIfDue() }
    }
}
