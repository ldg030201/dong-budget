package com.dong.budget.data.backup

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 백업 글을 앱 밖(다운로드 폴더·클립보드)에 두고 다시 읽어 온다.
 * 다운로드 폴더는 안드로이드 10 부터 권한 없이 쓸 수 있다. 앱을 지워도 파일은 남는다.
 */
class BackupStorage(private val context: Context) {
    /**
     * 다운로드 폴더에 [fileName] 으로 저장한다. 같은 이름이 있으면 시스템이 뒤에 번호를 붙인다.
     * @return 실제로 저장된 파일 이름
     */
    suspend fun saveToDownloads(fileName: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val pending =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    // 다 쓰기 전에는 다른 앱(파일 관리자)에 반쯤 쓴 파일이 보이지 않게 한다
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending) ?: throw IOException("다운로드 폴더에 파일을 만들지 못했어요")
            try {
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("파일을 열지 못했어요")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                // 반쯤 쓴 파일을 남기지 않는다
                resolver.delete(uri, null, null)
                throw e
            }
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: fileName
        }
    }

    /** 사용자가 고른 파일을 글로 읽는다. 백업이 아닌 큰 파일(동영상 등)을 잘못 골라도 메모리가 넘치지 않게 [MAX_BYTES] 에서 멈춘다. */
    suspend fun read(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("파일을 열지 못했어요")
            input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) throw TooLargeException()
                }
                out.toString(Charsets.UTF_8.name())
            }
        }
    }

    /**
     * 클립보드에 넣는다. 거래 내용이라 클립보드 미리보기에는 가려 달라고 표시한다.
     * 클립보드는 한 번에 담을 수 있는 크기가 작아서(약 1MB) 거래가 아주 많으면 실패한다.
     * @return 넣었는지
     */
    fun copy(text: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        return runCatching { clipboard.setPrimaryClip(clip) }.isSuccess
    }

    /** 클립보드의 글. 비었거나 글이 아니면 null. 앱이 화면에 떠 있을 때만 읽을 수 있다. */
    fun pasted(): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
        return item.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    /** 고른 파일이 백업이라기엔 너무 크다 */
    class TooLargeException : IOException("파일이 너무 커요")

    private companion object {
        const val MIME_TYPE = "application/json"
        const val CLIP_LABEL = "동계부 백업"
        const val BUFFER_BYTES = 64 * 1024

        /** 거래 10만 건쯤. 이보다 큰 파일은 백업이 아니라고 본다. */
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}
