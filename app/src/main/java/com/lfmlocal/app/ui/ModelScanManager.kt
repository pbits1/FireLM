package com.lfmlocal.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.lfmlocal.core.model.LfmModel
import com.lfmlocal.core.model.ModelCatalog
import com.lfmlocal.core.storage.GgufScanner
import java.io.File

class ModelScanManager(private val context: Context) {

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDownloadedModelIds(): Set<String> {
        return ModelCatalog.models.filter {
            GgufScanner.isDownloaded(context, it)
        }.map { it.id }.toSet()
    }

    fun scanCustomModels(): List<LfmModel> {
        val detectedList = mutableListOf<LfmModel>()
        val seenKeys = mutableSetOf<String>()

        for (dir in GgufScanner.allSearchDirs(context)) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    val files = GgufScanner.findGgufFilesInDir(dir, maxDepth = 3)
                    for (f in files) {
                        val key = f.name.lowercase()
                        val isCatalog = ModelCatalog.models.any {
                            it.localName.equals(f.name, ignoreCase = true) || it.file.equals(f.name, ignoreCase = true)
                        }
                        if (!isCatalog && !seenKeys.contains(key) && GgufScanner.isValidGguf(f)) {
                            detectedList.add(ModelCatalog.createCustomModel(f))
                            seenKeys.add(key)
                        }
                    }
                }
            } catch (_: Throwable) { }
        }
        return detectedList
    }

    fun resolveModelPath(
        model: LfmModel,
        onPfdCreated: (ParcelFileDescriptor?) -> Unit
    ): String? {
        return when {
            model.customUriString != null -> {
                try {
                    val uri = Uri.parse(model.customUriString)
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    onPfdCreated(pfd)
                    if (pfd != null) "/proc/self/fd/${pfd.fd}" else null
                } catch (_: Throwable) {
                    null
                }
            }
            model.customFilePath != null -> {
                val f = File(model.customFilePath!!)
                if (f.exists()) f.absolutePath else null
            }
            else -> {
                val f = GgufScanner.destFile(context, model)
                if (f.exists()) f.absolutePath else null
            }
        }
    }
}
