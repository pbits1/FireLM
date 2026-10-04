package com.lfmlocal.app.download

import android.content.Context
import android.net.Uri
import com.lfmlocal.app.data.LfmModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class DownloadState(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val done: Boolean = false,
    val error: String? = null
) {
    val fraction: Float get() =
        if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Resumable HF downloader with storage safety, progress, and import support. */
object ModelDownloader {
    fun modelsDir(ctx: Context): File = File(ctx.filesDir, "models").apply { mkdirs() }
    fun destFile(ctx: Context, model: LfmModel): File = File(modelsDir(ctx), model.localName)

    /**
     * Checks if a file has the valid 4-byte GGUF magic header (0x47, 0x47, 0x55, 0x46 / "GGUF").
     * Prevents false positives from partial HTML error pages or corrupted zero-byte files.
     */
    fun isValidGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 &&
                    header[0] == 0x47.toByte() && // 'G'
                    header[1] == 0x47.toByte() && // 'G'
                    header[2] == 0x55.toByte() && // 'U'
                    header[3] == 0x46.toByte()    // 'F'
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isDownloaded(ctx: Context, model: LfmModel): Boolean {
        val f = destFile(ctx, model)
        if (!f.exists() || !isValidGguf(f)) return false

        return if (model.exactBytes > 0L) {
            val len = f.length()
            len == model.exactBytes || abs(len - model.exactBytes) <= 4096L || (len >= model.exactBytes * 0.99 && len <= model.exactBytes * 1.01)
        } else {
            f.length() > (model.sizeMb * 1024L * 1024L * 0.9)
        }
    }

    fun getAvailableStorageMb(ctx: Context): Long {
        return modelsDir(ctx).usableSpace / (1024 * 1024)
    }

    suspend fun download(
        ctx: Context,
        model: LfmModel,
        onProgress: (DownloadState) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val dest = destFile(ctx, model)
        val tmp = File(dest.absolutePath + ".part")

        // If dest already exists and is fully valid, return success immediately
        if (dest.exists() && isDownloaded(ctx, model)) {
            onProgress(DownloadState(dest.length(), dest.length(), done = true))
            return@withContext Result.success(dest)
        }

        // Check if tmp is already 100% complete and valid
        if (tmp.exists() && isValidGguf(tmp)) {
            val isTmpComplete = if (model.exactBytes > 0L) {
                tmp.length() == model.exactBytes || abs(tmp.length() - model.exactBytes) <= 4096L
            } else {
                tmp.length() > (model.sizeMb * 1024L * 1024L * 0.9)
            }
            if (isTmpComplete) {
                if (dest.exists()) dest.delete()
                if (tmp.renameTo(dest)) {
                    onProgress(DownloadState(dest.length(), dest.length(), done = true))
                    return@withContext Result.success(dest)
                }
            }
        }

        // Pre-download storage safety verification (model size + 200MB safety buffer)
        val requiredBytes = if (model.exactBytes > 0L) model.exactBytes else model.sizeMb * 1024L * 1024L
        val freeBytes = modelsDir(ctx).usableSpace
        val alreadyDownloaded = if (tmp.exists()) tmp.length() else 0L
        val netNeeded = (requiredBytes - alreadyDownloaded).coerceAtLeast(0L)

        if (freeBytes < netNeeded + (200L * 1024L * 1024L)) {
            val freeMb = freeBytes / (1024 * 1024)
            val err = "Insufficient storage: need ~${model.sizeMb}MB, but only ${freeMb}MB free."
            onProgress(DownloadState(alreadyDownloaded, requiredBytes, error = err))
            return@withContext Result.failure(RuntimeException(err))
        }

        var downloaded = alreadyDownloaded
        var attempt = 0
        val maxAttempts = 5

        while (attempt < maxAttempts) {
            if (!currentCoroutineContext().isActive) {
                return@withContext Result.failure(CancellationException("Download cancelled"))
            }

            try {
                // Follow redirects manually so custom Range & identity headers are preserved across CDN hops
                var currentUrl = model.url
                var conn: HttpURLConnection? = null
                var redirects = 0
                val maxRedirects = 8

                while (redirects < maxRedirects) {
                    val url = URL(currentUrl)
                    val c = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 45_000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) LfmLocal/1.0")
                        setRequestProperty("Accept-Encoding", "identity")
                        if (downloaded > 0) {
                            setRequestProperty("Range", "bytes=$downloaded-")
                        }
                        instanceFollowRedirects = false
                    }
                    c.connect()

                    val code = c.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val location = c.getHeaderField("Location")
                        c.disconnect()
                        if (!location.isNullOrBlank()) {
                            currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                            redirects++
                            continue
                        }
                    }
                    conn = c
                    break
                }

                if (conn == null) {
                    throw IOException("Exceeded maximum redirects for ${model.url}")
                }

                val code = conn.responseCode

                // HTTP 416: Range Not Satisfiable
                if (code == 416) {
                    conn.disconnect()
                    // Check if file is already completely downloaded
                    val isFull = if (model.exactBytes > 0L) {
                        tmp.length() == model.exactBytes || abs(tmp.length() - model.exactBytes) <= 4096L
                    } else {
                        tmp.length() >= (model.sizeMb * 1024L * 1024L * 0.95)
                    }

                    if (isFull && isValidGguf(tmp)) {
                        if (dest.exists()) dest.delete()
                        if (tmp.renameTo(dest)) {
                            onProgress(DownloadState(dest.length(), dest.length(), done = true))
                            return@withContext Result.success(dest)
                        }
                    }

                    // Otherwise partial file is bad or server offset changed; reset and retry from byte 0
                    tmp.delete()
                    downloaded = 0L
                    attempt++
                    delay(1500L * attempt)
                    continue
                }

                if (code !in 200..299 && code != HttpURLConnection.HTTP_PARTIAL) {
                    conn.disconnect()
                    throw IOException("HTTP $code downloading ${model.file}")
                }

                val isPartial = (code == HttpURLConnection.HTTP_PARTIAL)
                if (!isPartial) {
                    // Server sent full file from byte 0 (ignored Range or new download)
                    downloaded = 0L
                }

                val total = if (model.exactBytes > 0L) {
                    model.exactBytes
                } else {
                    val cl = conn.contentLengthLong
                    if (cl > 0 && isPartial) downloaded + cl
                    else if (cl > 0) cl
                    else model.sizeMb * 1024L * 1024L
                }

                conn.inputStream.use { input ->
                    FileOutputStream(tmp, isPartial).use { out ->
                        val buf = ByteArray(256 * 1024)
                        var lastEmit = System.currentTimeMillis()
                        while (currentCoroutineContext().isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 150) {
                                lastEmit = now
                                onProgress(DownloadState(downloaded, total))
                            }
                        }
                    }
                }

                if (!currentCoroutineContext().isActive) {
                    return@withContext Result.failure(CancellationException("Download cancelled"))
                }

                onProgress(DownloadState(downloaded, total))

                // Verify downloaded file integrity before moving to final destination
                if (!isValidGguf(tmp)) {
                    tmp.delete()
                    throw IOException("Downloaded file failed GGUF integrity check (corrupted or invalid magic header).")
                }

                // Atomic rename to final destination
                if (dest.exists()) dest.delete()
                if (tmp.renameTo(dest)) {
                    onProgress(DownloadState(downloaded, total, done = true))
                    return@withContext Result.success(dest)
                } else {
                    if (dest.exists() && isValidGguf(dest)) {
                        onProgress(DownloadState(downloaded, total, done = true))
                        return@withContext Result.success(dest)
                    }
                    return@withContext Result.failure(IOException("Could not save model file to ${dest.name}"))
                }
            } catch (e: CancellationException) {
                // Do not delete tmp file so user can resume next time!
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts || !currentCoroutineContext().isActive) {
                    onProgress(DownloadState(downloaded, requiredBytes, error = e.localizedMessage ?: e.message))
                    return@withContext Result.failure(e)
                }
                delay(2000L * attempt)
                downloaded = if (tmp.exists()) tmp.length() else 0L
            }
        }

        Result.failure(IOException("Download failed after $maxAttempts attempts."))
    }

    /** Import an arbitrary .gguf picked via Storage Access Framework. */
    suspend fun importUri(ctx: Context, uri: Uri, customFileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val sanitized = customFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val finalName = if (sanitized.endsWith(".gguf", ignoreCase = true)) sanitized else "$sanitized.gguf"
                val dest = File(modelsDir(ctx), "custom-$finalName")
                if (dest.exists()) dest.delete()
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out) }
                } ?: return@withContext Result.failure(IOException("Could not open picked file stream"))

                if (!isValidGguf(dest)) {
                    dest.delete()
                    return@withContext Result.failure(IOException("The selected file is not a valid GGUF model."))
                }

                Result.success(dest)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

