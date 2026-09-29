package com.dong.budget.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.components.ConfirmDialog
import java.time.LocalDate

/** 설정의 백업·복원 줄이 부르는 일 */
@Immutable
data class BackupActions(
    val onCopy: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onRestoreFile: (Uri) -> Unit = {},
    val onRestorePasted: () -> Unit = {},
    val onConfirmRestore: () -> Unit = {},
    val onDismissRestore: () -> Unit = {},
)

/**
 * 백업(내보내기)과 복원(되살리기) 두 묶음. 백업은 거래·분류·결제수단을 담고, 설정(테마·자동 기능)과 알림 목록은 담지 않는다.
 * 복원은 백업을 읽어 무엇이 담겼는지 보여 주고 한 번 더 물은 뒤에 지금 데이터를 통째로 바꾼다.
 */
@Composable
internal fun BackupSection(state: BackupUiState, actions: BackupActions) {
    // 파일 고르기. 동계부가 저장한 파일은 기기에 따라 종류(MIME)가 json·text·알 수 없음으로 달리 잡혀 모든 파일을 보여 준다.
    // 백업이 아닌 파일을 고르면 읽을 때 걸러진다.
    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) actions.onRestoreFile(uri)
        }
    val enabled = !state.busy

    SettingsGroup("백업") {
        ActionRow(
            title = "JSON으로 복사",
            description = "거래·분류·결제수단을 글로 복사해요. 메모나 메신저에 붙여 두세요",
            onClick = actions.onCopy,
            enabled = enabled,
        )
        ActionRow(
            title = "파일로 내려받기",
            description = "다운로드 폴더에 JSON 파일로 저장해요. 앱을 지워도 남아요",
            onClick = actions.onSave,
            enabled = enabled,
        )
    }

    SettingsGroup("복원") {
        ActionRow(
            title = "파일에서 복원",
            description = "저장해 둔 백업 파일을 골라요",
            onClick = { pickFile.launch(arrayOf("*/*")) },
            enabled = enabled,
        )
        ActionRow(
            title = "복사한 JSON으로 복원",
            description = "복사해 둔 백업 글을 붙여 넣어요",
            onClick = actions.onRestorePasted,
            enabled = enabled,
        )
    }

    state.restore?.let { preview ->
        val today = remember { LocalDate.now(BudgetTime.ZONE) }
        ConfirmDialog(
            title = "백업으로 복원할까요?",
            message = restoreMessage(preview, today),
            confirmLabel = "복원하기",
            onConfirm = actions.onConfirmRestore,
            onDismiss = actions.onDismissRestore,
        )
    }
}
