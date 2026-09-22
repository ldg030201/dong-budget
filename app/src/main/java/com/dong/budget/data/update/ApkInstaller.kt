package com.dong.budget.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 새 버전 APK 를 내려받아 시스템 설치 화면까지 띄운다.
 *
 * 내려받은 내용을 임시 파일로 저장하지 않고 설치 세션에 바로 흘려보낸다.
 * 덕분에 (1) 기기에 찌꺼기 파일이 남지 않고 (2) 지우는 것을 잊어 용량이 늘어날 일이 없고
 * (3) FileProvider 설정도 필요 없다.
 *
 * 실제 설치는 사용자가 시스템 확인창에서 직접 눌러야 진행된다.
 * 일반 앱은 사용자 확인 없이 설치할 수 없다.
 */
class ApkInstaller(private val context: Context) {
    /**
     * @param onProgress 0.0 에서 1.0 사이. 서버가 크기를 알려주지 않으면 호출되지 않는다.
     * @return 세션을 넘기는 데 성공했는지. 설치 자체의 성공은 이후 시스템 확인창에서 결정된다.
     */
    suspend fun downloadAndInstall(downloadUrl: String, expectedSize: Long, onProgress: (Float) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params =
                    PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                    ).apply {
                        setAppPackageName(context.packageName)
                        if (expectedSize > 0) setSize(expectedSize)
                    }

                val sessionId = installer.createSession(params)
                try {
                    installer.openSession(sessionId).use { session ->
                        session.openWrite(WRITE_NAME, 0, expectedSize.takeIf { it > 0 } ?: -1L)
                            .use { output ->
                                openDownloadStream(downloadUrl).use { input ->
                                    copyWithProgress(input, output, expectedSize, onProgress)
                                }
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

    private fun openDownloadStream(url: String): InputStream {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "dong-budget")
        }
        check(connection.responseCode in HTTP_OK_RANGE) {
            "내려받기 실패 (${connection.responseCode})"
        }
        return connection.inputStream
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
        const val WRITE_NAME = "dong-budget-update"
        const val TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_SIZE = 64 * 1024
        val HTTP_OK_RANGE = 200..299
    }
}
