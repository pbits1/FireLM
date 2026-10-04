package com.lfmlocal.core.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Dedicated scanner for /Download/FireLM and device storage.
 * Handles path resolution, recursive tree discovery, and 4-byte GGUF magic header validation.
 */
object GgufScanner {

    fun getPublicDownloadsFireLMDir(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(publicDownloads, "FireLM")
    }

    fun getFireLMDirs(): List<File> {
        val dirs = LinkedHashSet<File>()
        try {
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dirs.add(File(pub, "FireLM"))
            dirs.add(File(pub, "firelm"))
        } catch (_: Throwable) { }
        try {
            val ext = Environment.getExternalStorageDirectory()
            dirs.add(File(ext, "Download/FireLM"))
            dirs.add(File(ext, "download/FireLM"))
            dirs.add(File(ext, "Download/firelm"))
        } catch (_: Throwable) { }
        dirs.add(File("/storage/emulated/0/Download/FireLM"))
        dirs.add(File("/sdcard/Download/FireLM"))
        return dirs.toList()
    }

    fun allSearchDirs(ctx: Context): List<File> {
        val dirs = LinkedHashSet<File>()
        for (d in getFireLMDirs()) {
            if (d.exists() && d.isDirectory) dirs.add(d)
        }
        val primary = getPublicDownloadsFireLMDir()
        try { if (!primary.exists()) primary.mkdirs() } catch (_: Throwable) { }
        dirs.add(primary)

        ctx.getExternalFilesDir("models")?.let { if (it.exists()) dirs.add(it) }
        val internal = File(ctx.filesDir, "models")
        if (internal.exists()) dirs.add(internal)
        return dirs.toList()
    }

    fun findGgufFilesInDir(dir: File, maxDepth: Int = 3): List<File> {
        val results = mutableListOf<File>()
        if (!dir.exists() || !dir.isDirectory) return results

        fun walk(current: File, depth: Int) {
            if (depth > maxDepth) return
            val children = current.listFiles() ?: return
            for (f in children) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".") && !f.name.startsWith("$")) {
                        walk(f, depth + 1)
                    }
                } else if (f.isFile && f.extension.equals("gguf", ignoreCase = true)) {
                    results.add(f)
                }
            }
        }

        walk(dir, 0)
        return results
    }

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

    /**
     * Checks if a content Uri has the valid 4-byte GGUF magic header without reading the full file.
     */
    fun isValidGguf(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 &&
                    header[0] == 0x47.toByte() && // 'G'
                    header[1] == 0x47.toByte() && // 'G'
                    header[2] == 0x55.toByte() && // 'U'
                    header[3] == 0x46.toByte()    // 'F'
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun modelsDir(ctx: Context): File {
        // 1. Try public Download/FireLM
        for (dir in getFireLMDirs()) {
            try {
                if (dir.exists() && dir.canWrite()) return dir
                if (dir.mkdirs() && dir.canWrite()) return dir
            } catch (_: Throwable) { }
        }

        // 2. Try app external files dir (Android/data/com.lfmlocal.app/files/models)
        try {
            val ext = ctx.getExternalFilesDir("models")
            if (ext != null && (ext.exists() || ext.mkdirs()) && ext.canWrite()) {
                return ext
            }
        } catch (_: Throwable) { }

        // 3. Fallback to private internal filesDir/models
        return File(ctx.filesDir, "models").apply { mkdirs() }
    }

    fun destFile(ctx: Context, model: com.lfmlocal.core.model.LfmModel): File {
        // If it already exists in any of our search dirs, use that
        for (dir in allSearchDirs(ctx)) {
            val candidate1 = File(dir, model.localName)
            if (candidate1.exists() && isValidGguf(candidate1)) return candidate1

            val candidate2 = File(dir, model.file)
            if (candidate2.exists() && isValidGguf(candidate2)) return candidate2

            // Also search subfolders (e.g. Download/FireLM/subfolder/model.gguf)
            val subMatch = findGgufFilesInDir(dir, maxDepth = 2).find { f ->
                (f.name.equals(model.localName, ignoreCase = true) || f.name.equals(model.file, ignoreCase = true)) && isValidGguf(f)
            }
            if (subMatch != null) return subMatch
        }
        return File(modelsDir(ctx), model.localName)
    }

    fun isDownloaded(ctx: Context, model: com.lfmlocal.core.model.LfmModel): Boolean {
        if (model.customFilePath != null) {
            val f = File(model.customFilePath)
            return f.exists() && isValidGguf(f)
        }
        if (model.customUriString != null) {
            val uri = Uri.parse(model.customUriString)
            return isValidGguf(ctx, uri)
        }

        // For catalog models, search across all directories
        for (dir in allSearchDirs(ctx)) {
            val f1 = File(dir, model.localName)
            if (f1.exists() && isValidGguf(f1)) return true

            val f2 = File(dir, model.file)
            if (f2.exists() && isValidGguf(f2)) return true

            val subMatch = findGgufFilesInDir(dir, maxDepth = 2).find { f ->
                (f.name.equals(model.localName, ignoreCase = true) || f.name.equals(model.file, ignoreCase = true)) && isValidGguf(f)
            }
            if (subMatch != null) return true
        }

        return false
    }

    fun getAvailableStorageMb(ctx: Context): Long {
        return modelsDir(ctx).usableSpace / (1024 * 1024)
    }
}
