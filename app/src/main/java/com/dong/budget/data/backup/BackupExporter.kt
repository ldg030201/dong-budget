package com.dong.budget.data.backup

import com.dong.budget.BuildConfig
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.toRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 지금 데이터를 백업 글로 만들어 클립보드나 다운로드 폴더에 둔다. 설정의 백업 줄과 복원·데이터 초기화 전 자동 백업이 함께 쓴다.
 * 백업을 마치면 [history] 에 적는다.
 */
class BackupExporter(
    private val backupRepository: BackupRepository,
    private val salaryRepository: SalaryRepository,
    private val storage: BackupStorage,
    private val history: BackupHistory,
    private val appVersion: String = BuildConfig.VERSION_NAME,
) {
    /** 거래·분류·결제수단과 월급 설정(정해 둔 때만)을 담은 백업 글 */
    suspend fun encode(pretty: Boolean): String {
        val salary = salaryRepository.settings.first().takeIf { it != SalarySettings() }?.toRecord()
        val backup = backupRepository.export(appVersion).copy(salary = salary)
        // 거래가 많으면 글로 바꾸는 데 시간이 걸린다. 화면이 멈추지 않게 뒤에서 한다.
        return withContext(Dispatchers.Default) { BackupCodec.encode(backup, pretty) }
    }

    /** 한 줄 JSON 으로 클립보드에 넣는다. @return 넣었는지(거래가 아주 많으면 클립보드에 안 들어간다) */
    suspend fun copy(): Boolean = storage.copy(encode(pretty = false)).also { if (it) history.record(BackupKind.COPY) }

    /**
     * 다운로드 폴더에 [fileName] 으로 저장한다. 사람이 열어 볼 수 있게 줄을 나눈다.
     * @return 실제로 저장된 파일 이름. 같은 이름이 있으면 시스템이 뒤에 번호를 붙인다.
     */
    suspend fun saveFile(fileName: String, kind: BackupKind = BackupKind.FILE): Result<String> = try {
        val name = storage.saveToDownloads(fileName, encode(pretty = true)).getOrThrow()
        history.record(kind)
        Result.success(name)
    } catch (e: CancellationException) {
        // 취소는 실패가 아니다. 부른 쪽까지 그대로 올려 보낸다.
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 지울 것이 있는지. 거래가 있거나 월급을 정해 뒀으면 true. 없으면 자동 백업을 건너뛴다(빈 백업만 쌓이므로).
     * @param transactionCount 부르는 쪽이 이미 센 거래 수. 없으면 여기서 센다.
     */
    suspend fun hasData(transactionCount: Int? = null): Boolean =
        (transactionCount ?: backupRepository.transactionCount()) > 0 || salaryRepository.settings.first() != SalarySettings()
}
