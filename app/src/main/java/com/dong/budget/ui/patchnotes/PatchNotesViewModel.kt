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
 * '앱을 열 때 새 버전 확인하기' 를 껐으면 여기서도 스스로 확인하지 않고, 받아 둔 것만 보여준다(앱 정보에서 직접 확인한 결과 포함).
 *
 * @param autoCheck 스스로 확인해도 되는지. 저장소를 다 읽은 스위치 값을 돌려준다.
 */
class PatchNotesViewModel(updateChecker: UpdateChecker, autoCheck: suspend () -> Boolean = { true }) : ViewModel() {
    val newer: StateFlow<List<NewerRelease>> = updateChecker.newer

    init {
        viewModelScope.launch { if (autoCheck()) updateChecker.checkIfDue() }
    }
}
