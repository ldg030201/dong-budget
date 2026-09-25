package com.dong.budget.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 새 버전 APK 를 내려받아 설치 화면까지 띄운다.
 *
 * 내려받은 APK 는 먼저 앱 전용 캐시 폴더에 파일로 저장한다. 설치 세션에 바로 흘려보내면
 * 설치가 중간에 막혔을 때(제조사 설치기가 세션을 중단하거나, 앱이 꺼지거나) 처음부터 다시 받아야 하고,
 * 다른 방법으로 설치할 파일도 남지 않는다. 파일이 있으면
 *   1. 앱 안 설치(세션)를 다시 시도하거나
 *   2. 시스템 설치기로 파일을 직접 열어(브라우저에서 받은 파일을 여는 것과 같은 길) 설치할 수 있다.
 * 설치가 끝나 새 버전이 켜지면 [deleteStaleDownloads] 가 지난 파일을 지운다.
 *
 * 실제 설치는 어느 길이든 사용자가 시스템 확인창에서 직접 눌러야 진행된다.
 */
class ApkInstaller(private val context: Context) {
    private val directory: File get() = File(context.cacheDir, DIRECTORY)

    private fun fileFor(version: String) = File(directory, "$FILE_PREFIX$version$FILE_SUFFIX")

    /** 받아둔 설치 파일 중 [currentVersion] 보다 새 것 가운데 가장 최신. 없으면 null */
    fun latestDownloaded(currentVersion: String): DownloadedApk? {
        val current = AppVersion.parse(currentVersion) ?: return null
        return directory
            .listFiles()
            .orEmpty()
            .mapNotNull { file ->
                val version = file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
                val parsed = AppVersion.parse(version)
                if (file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX) && parsed != null && parsed > current) {
                    DownloadedApk(version, file, parsed)
                } else {
                    null
                }
            }.maxByOrNull { it.parsedVersion }
    }

    /** 이미 설치된 버전 이하의 파일과, 받다 만 조각을 지운다. 앱이 켜질 때 부른다. */
    fun deleteStaleDownloads(currentVersion: String) {
        val current = AppVersion.parse(currentVersion) ?: return
        directory.listFiles().orEmpty().forEach { file ->
            val version = AppVersion.parse(file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX))
            val stale = file.name.endsWith(PART_SUFFIX) || version == null || version <= current
            if (stale) file.delete()
        }
    }

    /**
     * 설치 파일을 받는다. 이미 받아둔 파일이 있으면 다시 받지 않는다.
     *
     * 받는 동안은 '.part' 임시 파일에 쓰고, 끝까지 받은 뒤에만 제 이름으로 바꾼다.
     * 받다가 끊긴 조각을 완성된 파일로 오해해 설치하려 들면 설치기가 '잘못된 파일' 로 거절한다.
     * 임시 파일은 받을 때마다 새 이름으로 만든다. 멈춘 채 남은 이전 다운로드가 뒤늦게 끝나면서
     * 같은 이름의 파일을 지우거나 덮어쓰지 못하게 하기 위함이다.
     *
     * @param onProgress 0.0 에서 1.0 사이. 크기를 모르면 불리지 않는다.
     */
    suspend fun download(downloadUrl: String, version: String, expectedSize: Long, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = fileFor(version)
                if (target.isFile && (expectedSize <= 0 || target.length() == expectedSize)) return@runCatching target

                directory.mkdirs()
                val part = File.createTempFile(target.name, PART_SUFFIX, directory)
                val connection = openConnection(downloadUrl)
                // 화면을 나가 취소돼도 read() 는 스스로 풀리지 않는다. 연결을 끊어 바로 멈추게 한다.
                val disconnectOnCancel = coroutineContext.job.invokeOnCompletion { connection.disconnect() }
                try {
                    connection.inputStream.use { input ->
                        part.outputStream().use { output -> copyWithProgress(input, output, expectedSize, onProgress) }
                    }
                    check(expectedSize <= 0 || part.length() == expectedSize) { "설치 파일을 끝까지 받지 못했어요" }
                    check(part.renameTo(target)) { "설치 파일을 저장하지 못했어요" }
                    target
                } finally {
                    disconnectOnCancel.dispose()
                    connection.disconnect()
                    part.delete()
                }
            }
        }

    /**
     * 받아둔 파일을 앱 안 설치(세션)로 넘긴다.
     * @return 세션을 넘기는 데 성공했는지. 설치 자체의 성공은 이후 시스템 확인창에서 결정된다.
     */
    suspend fun installWithSession(apk: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(apk.length())
                }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    session.openWrite(WRITE_NAME, 0, apk.length()).use { output ->
                        apk.inputStream().use { input -> input.copyTo(output, BUFFER_SIZE) }
                        session.fsync(output)
                    }
                    session.commit(statusIntentSender(sessionId))
                }
            } catch (throwable: Throwable) {
                // 세션을 남겨두면 기기에 조각이 쌓인다.
                runCatching { installer.abandonSession(sessionId) }
                throw throwable
            }
        }
    }

    /**
     * 받아둔 파일을 시스템 설치기로 직접 연다.
     * 파일 관리자나 브라우저에서 APK 를 눌렀을 때와 같은 길이다. 제조사 설치기가 앱 안 설치(세션)를
     * 중단시키는 기기에서도 이 길은 된다. 결과는 앱으로 돌아오지 않는다(성공하면 앱이 새 버전으로 바뀐다).
     */
    fun openWithSystemInstaller(apk: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", apk)
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "dong-budget")
        }
        if (connection.responseCode !in HTTP_OK_RANGE) {
            val code = connection.responseCode
            connection.disconnect()
            error("내려받기 실패 ($code)")
        }
        return connection
    }

    /**
     * [version] 이 아닌 받아둔 설치 파일을 모두 지운다. null 이면 전부 지운다.
     * 업데이트 확인 결과와 맞춘다. 배포를 내린 버전의 파일이 남아 설치를 권하는 일이 없게 한다.
     */
    fun keepOnly(version: String?) {
        directory.listFiles().orEmpty().forEach { file ->
            if (version == null || file != fileFor(version)) file.delete()
        }
    }

    private suspend fun copyWithProgress(input: InputStream, output: OutputStream, expectedSize: Long, onProgress: (Float) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            // 화면을 닫으면 내려받기도 멈춘다.
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (expectedSize > 0) onProgress(copied.toFloat() / expectedSize)
        }
    }

    /**
     * 설치 진행 상황을 받을 곳.
     *
     * Android 12 부터 PendingIntent 에 변경 가능 여부를 반드시 지정해야 하는데,
     * 시스템이 결과 정보를 채워 넣어야 하므로 MUTABLE 이어야 한다.
     */
    private fun statusIntentSender(sessionId: Int) = PendingIntent
        .getBroadcast(
            context,
            sessionId,
            Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender

    private companion object {
        /** res/xml/update_files.xml 의 cache-path 와 같아야 한다 */
        const val DIRECTORY = "updates"

        /** AndroidManifest 의 FileProvider authorities 뒷부분과 같아야 한다 */
        const val AUTHORITY_SUFFIX = ".updates"
        const val FILE_PREFIX = "dong-budget-"
        const val FILE_SUFFIX = ".apk"
        const val PART_SUFFIX = ".part"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val WRITE_NAME = "dong-budget-update"
        const val TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_SIZE = 64 * 1024
        val HTTP_OK_RANGE = 200..299
    }
}

/** 받아둔 설치 파일 */
data class DownloadedApk(val version: String, val file: File, internal val parsedVersion: AppVersion)
