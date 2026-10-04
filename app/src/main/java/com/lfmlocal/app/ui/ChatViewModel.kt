package com.lfmlocal.app.ui

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lfmlocal.app.data.AppPreferences
import com.lfmlocal.app.data.ChatHistoryStorage
import com.lfmlocal.app.data.LfmModel
import com.lfmlocal.app.data.ModelCatalog
import com.lfmlocal.app.download.ModelDownloadService
import com.lfmlocal.app.download.ModelDownloader
import com.lfmlocal.app.inference.LlamaBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMsg(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val speedStats: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        fun calculateInferenceThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return when {
                cores >= 8 -> 4 // 2x big cores + 2x LITTLE for throughput on modern 8-core SoCs
                cores >= 4 -> 3
                else -> maxOf(1, cores)
            }
        }
    }

    var messages by mutableStateOf(listOf<ChatMsg>())
        private set
    var streamingText by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Ready to chat offline.")
        private set
    var selectedModel by mutableStateOf<LfmModel>(
        AppPreferences.getSelectedModelId(application)?.let { savedId ->
            ModelCatalog.models.find { it.id == savedId }
        } ?: ModelCatalog.models.first { it.recommended }
    )
        private set
    var modelFile: File? by mutableStateOf(null)
        private set
    var downloadFraction by mutableStateOf<Float?>(null)
        private set
    var customModels by mutableStateOf(listOf<LfmModel>())
        private set
    var downloadedModelIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var activeContextTokens by mutableStateOf(AppPreferences.getContextWindowSize(application, 2048))
        private set
    var modelsFolderName by mutableStateOf("Download/FireLM")
        private set
    var hasStoragePermission by mutableStateOf(false)
        private set
    var isSyncingModels by mutableStateOf(false)
        private set
    private var activePfd: ParcelFileDescriptor? = null

    fun checkStoragePermission(): Boolean {
        val ctx = getApplication<Application>()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        hasStoragePermission = granted
        return granted
    }

    // Hardware & Inference tuning (Persisted)
    var computeBackend by mutableStateOf(AppPreferences.getComputeBackend(application, "GPU"))
        private set
    var gpuLayers by mutableStateOf(AppPreferences.getGpuLayers(application, 32))
        private set
    var contextWindowSize by mutableStateOf(AppPreferences.getContextWindowSize(application, 2048))
        private set
    var cpuThreads by mutableStateOf(AppPreferences.getCpuThreads(application, calculateInferenceThreads()))
        private set
    var sustainedPerformanceMode by mutableStateOf(AppPreferences.getSustainedPerformance(application, true))
        private set

    // LM Studio System Prompt Deck (Direct user instructions; optional for SLMs)
    var systemPromptEnabled by mutableStateOf(AppPreferences.isSystemPromptEnabled(application, false))
        private set
    var systemPrompt by mutableStateOf(AppPreferences.getSystemPrompt(application, "Be direct, factual, and concise."))
        private set

    // Generation knobs (Persisted)
    var temperature by mutableStateOf(AppPreferences.getTemperature(application, 0.7f))
        private set
    var topP by mutableStateOf(AppPreferences.getTopP(application, 0.95f))
        private set
    var repeatPenalty by mutableStateOf(AppPreferences.getRepeatPenalty(application, 1.05f))
        private set
    var maxTokens by mutableStateOf(AppPreferences.getMaxTokens(application, 512))
        private set

    private var handle by mutableLongStateOf(0L)
    private var genJob: Job? = null
    private var cancelled = false

    // Explicit LM Studio Residency & Loading Indicators
    val isModelLoaded: Boolean get() = handle != 0L
    val isModelLoading: Boolean get() = busy && downloadFraction == null && handle == 0L

    init {
        // Restore persistent chat history from flash storage
        messages = ChatHistoryStorage.loadMessages(application)
        checkStoragePermission()
        refreshCustomModels()
        refreshLocalState()
        observeDownloadService()
    }

    private var directDownloadJob: Job? = null
    private var vmWakeLock: PowerManager.WakeLock? = null

    private fun observeDownloadService() {
        viewModelScope.launch {
            ModelDownloadService.downloadStatus.collect { active ->
                if (active.modelId.isNotBlank() && active.modelId == selectedModel.id) {
                    if (active.isRunning) {
                        downloadFraction = active.state.fraction
                        val pct = (active.state.fraction * 100).toInt()
                        status = "Downloading ${active.modelLabel} ($pct%)…"
                        busy = true
                    } else if (active.state.done) {
                        downloadFraction = null
                        busy = false
                        refreshLocalState()
                        if (ModelDownloader.isDownloaded(getApplication(), selectedModel)) {
                            loadModelForSelected()
                        }
                    } else if (active.state.error != null) {
                        downloadFraction = null
                        busy = false
                        status = "Download error: ${active.state.error}"
                    }
                }
            }
        }
    }

    fun isModelDownloaded(model: LfmModel): Boolean {
        if (model.isCustom) return true
        return model.id in downloadedModelIds
    }

    fun updateSystemPrompt(prompt: String) {
        systemPrompt = prompt
        AppPreferences.saveSystemPrompt(getApplication(), prompt)
    }

    fun updateSystemPromptEnabled(enabled: Boolean) {
        systemPromptEnabled = enabled
        AppPreferences.saveSystemPromptEnabled(getApplication(), enabled)
    }

    fun loadCurrentModel() {
        if (busy || handle != 0L) return
        if (ModelDownloader.isDownloaded(getApplication(), selectedModel)) {
            loadModelForSelected()
        }
    }

    fun ejectModel() {
        if (busy) stop()
        freeModel()
        status = "Model ejected from RAM. Ready to load."
    }

    fun updateTemperature(value: Float) {
        temperature = value
        AppPreferences.saveTemperature(getApplication(), value)
    }

    fun updateTopP(value: Float) {
        topP = value
        AppPreferences.saveTopP(getApplication(), value)
    }

    fun updateRepeatPenalty(value: Float) {
        repeatPenalty = value
        AppPreferences.saveRepeatPenalty(getApplication(), value)
    }

    fun updateMaxTokens(value: Int) {
        maxTokens = value
        AppPreferences.saveMaxTokens(getApplication(), value)
    }

    fun updateSustainedPerformance(enabled: Boolean) {
        sustainedPerformanceMode = enabled
        AppPreferences.saveSustainedPerformance(getApplication(), enabled)
    }

    fun selectModel(m: LfmModel) {
        if (selectedModel.id == m.id && handle != 0L) return
        AppPreferences.saveSelectedModelId(getApplication(), m.id)
        stop()
        freeModel()
        selectedModel = m
        modelFile = null

        if (ModelDownloader.isDownloaded(getApplication(), m)) {
            loadModelForSelected()
        } else {
            status = "Selected ${m.label}. Tap Download to save locally."
        }
    }

    fun refreshLocalState() {
        checkStoragePermission()
        refreshCustomModels()
        val ctx = getApplication<Application>()
        val downloaded = ModelCatalog.models.filter {
            ModelDownloader.isDownloaded(ctx, it)
        }.map { it.id }.toSet()
        downloadedModelIds = downloaded

        if (ModelDownloader.isDownloaded(ctx, selectedModel)) {
            if (modelFile == null && selectedModel.customFilePath != null) {
                modelFile = File(selectedModel.customFilePath!!)
            } else if (modelFile == null) {
                modelFile = ModelDownloader.destFile(ctx, selectedModel)
            }
            if (handle == 0L && !busy) {
                loadModelForSelected()
            }
        }
    }

    fun refreshCustomModels() {
        checkStoragePermission()
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.Main) { isSyncingModels = true }

            val detectedList = mutableListOf<LfmModel>()
            val seenKeys = mutableSetOf<String>()

            // Scan physical directories (/Download/FireLM, external app storage, internal storage)
            // recursively up to depth 3 so user can drop files or subfolders
            for (dir in ModelDownloader.allSearchDirs(ctx)) {
                try {
                    if (dir.exists() && dir.isDirectory) {
                        val files = ModelDownloader.findGgufFilesInDir(dir, maxDepth = 3)
                        for (f in files) {
                            val key = f.name.lowercase()
                            val isCatalog = ModelCatalog.models.any {
                                it.localName.equals(f.name, ignoreCase = true) || it.file.equals(f.name, ignoreCase = true)
                            }
                            if (!isCatalog && !seenKeys.contains(key) && ModelDownloader.isValidGguf(f)) {
                                detectedList.add(ModelCatalog.createCustomModel(f))
                                seenKeys.add(key)
                            }
                        }
                    }
                } catch (_: Throwable) { }
            }

            withContext(Dispatchers.Main) {
                customModels = detectedList
                isSyncingModels = false
                val savedId = AppPreferences.getSelectedModelId(ctx)
                if (savedId != null && selectedModel.id != savedId) {
                    val customMatch = detectedList.find { it.id == savedId }
                    if (customMatch != null) {
                        selectModel(customMatch)
                    }
                }
            }
        }
    }

    fun startDownload() {
        if (busy || selectedModel.isCustom) return
        status = "Initializing download for ${selectedModel.label}…"
        val startedForeground = ModelDownloadService.startDownload(getApplication(), selectedModel.id)
        if (!startedForeground) {
            // Direct in-ViewModel fallback if foreground service was restricted by OS
            runDirectDownload(selectedModel)
        }
    }

    private fun runDirectDownload(model: LfmModel) {
        directDownloadJob?.cancel()
        val ctx = getApplication<Application>()
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            vmWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LfmLocal:DirectDownloadWakeLock")?.apply {
                acquire(30 * 60 * 1000L)
            }
        } catch (_: Exception) {}

        busy = true
        downloadFraction = 0f
        status = "Downloading ${model.label}…"

        directDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            val result = ModelDownloader.download(ctx, model) { st ->
                val active = com.lfmlocal.app.download.ActiveDownload(
                    modelId = model.id,
                    modelLabel = model.label,
                    state = st,
                    isRunning = !st.done && st.error == null
                )
                ModelDownloadService.updateProgress(active)
                viewModelScope.launch(Dispatchers.Main) {
                    downloadFraction = st.fraction
                    val pct = (st.fraction * 100).toInt()
                    status = "Downloading ${model.label} ($pct%)…"
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    if (vmWakeLock?.isHeld == true) vmWakeLock?.release()
                } catch (_: Exception) {}
                busy = false
                downloadFraction = null

                result.onSuccess { file ->
                    refreshLocalState()
                    modelFile = file
                    loadModelForSelected()
                    status = "Ready — ${model.label} · 100% offline"
                }.onFailure { err ->
                    status = "Download error: ${err.message}"
                }
            }
        }
    }

    fun cancelDownload() {
        directDownloadJob?.cancel()
        directDownloadJob = null
        try {
            if (vmWakeLock?.isHeld == true) vmWakeLock?.release()
        } catch (_: Exception) {}
        ModelDownloadService.cancelDownload(getApplication())
        downloadFraction = null
        busy = false
        status = "Download cancelled."
    }

    fun importCustomGguf(uri: Uri, displayName: String) {
        if (busy) return
        status = "Registering $displayName…"
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            if (ModelDownloader.isValidGguf(ctx, uri)) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    ctx.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (_: Throwable) { }

                val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "r") } catch (_: Throwable) { null }
                val sizeBytes = pfd?.statSize ?: 0L
                try { pfd?.close() } catch (_: Throwable) { }

                val custom = ModelCatalog.createCustomModelFromDoc(displayName, sizeBytes, uri)
                withContext(Dispatchers.Main) {
                    refreshCustomModels()
                    selectModel(custom)
                    status = "Imported $displayName (zero-copy). Ready to load."
                }
            } else {
                // Fallback to local copy if direct stream validation fails
                val result = ModelDownloader.importUri(ctx, uri, displayName)
                withContext(Dispatchers.Main) {
                    result.onSuccess { importedFile ->
                        refreshCustomModels()
                        val custom = ModelCatalog.createCustomModel(importedFile)
                        selectModel(custom)
                        status = "Imported ${importedFile.name}. Ready to load."
                    }.onFailure { err ->
                        status = "Failed to import: ${err.message}"
                    }
                }
            }
        }
    }

    fun selectComputeBackend(backend: String) {
        if (computeBackend == backend) return
        computeBackend = backend
        gpuLayers = if (backend == "GPU") 32 else 0
        AppPreferences.saveComputeBackend(getApplication(), backend)
        AppPreferences.saveGpuLayers(getApplication(), gpuLayers)
        reloadModelWithNewSettings()
    }

    fun selectContextSize(size: Int) {
        if (contextWindowSize == size) return
        contextWindowSize = size
        AppPreferences.saveContextWindowSize(getApplication(), size)
        reloadModelWithNewSettings()
    }

    fun selectThreads(threads: Int) {
        if (cpuThreads == threads) return
        cpuThreads = threads
        AppPreferences.saveCpuThreads(getApplication(), threads)
        reloadModelWithNewSettings()
    }

    fun selectGpuOffloadLayers(layers: Int) {
        if (gpuLayers == layers) return
        gpuLayers = layers
        AppPreferences.saveGpuLayers(getApplication(), layers)
        if (computeBackend == "GPU") {
            reloadModelWithNewSettings()
        }
    }

    fun reloadModelWithNewSettings() {
        if (busy) return
        stop()
        freeModel()
        loadModelForSelected()
    }

    private fun loadModelForSelected() {
        val m = selectedModel
        if (busy) return
        busy = true
        val requestedGpu = if (computeBackend == "GPU") (if (gpuLayers > 0) gpuLayers else 32) else 0
        status = if (requestedGpu > 0) "Loading ${m.label} (GPU default with CPU fallback)…" else "Loading ${m.label} into memory (CPU)…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { LlamaBridge.nativeInit() } catch (_: Throwable) { }

                try {
                    activePfd?.close()
                    activePfd = null
                } catch (_: Throwable) { }

                val ctx = getApplication<Application>()
                val modelPath: String? = when {
                    m.customUriString != null -> {
                        try {
                            val uri = Uri.parse(m.customUriString)
                            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                            activePfd = pfd
                            if (pfd != null) "/proc/self/fd/${pfd.fd}" else null
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    m.customFilePath != null -> {
                        val f = File(m.customFilePath)
                        if (f.exists()) f.absolutePath else null
                    }
                    else -> {
                        val f = ModelDownloader.destFile(ctx, m)
                        if (f.exists()) f.absolutePath else null
                    }
                }

                if (modelPath == null) {
                    withContext(Dispatchers.Main) {
                        busy = false
                        status = "Model file not found. Please sync or download again."
                    }
                    return@withContext
                }

                withContext(Dispatchers.Main) {
                    if (modelFile == null) {
                        modelFile = File(m.file)
                    }
                }

                val h = LlamaBridge.nativeLoadModel(modelPath, contextWindowSize, cpuThreads, requestedGpu)
                withContext(Dispatchers.Main) {
                    busy = false
                    if (h != 0L) {
                        handle = h
                        val actualGpu = try { LlamaBridge.nativeGpuLayers(h) } catch (_: Throwable) { 0 }
                        val ctxSize = try { LlamaBridge.nativeContextSize(h) } catch (_: Throwable) { contextWindowSize }
                        activeContextTokens = ctxSize

                        val backendDesc = when {
                            actualGpu > 0 -> {
                                computeBackend = "GPU"
                                gpuLayers = actualGpu
                                "GPU Accelerated ($actualGpu layers)"
                            }
                            requestedGpu > 0 -> {
                                computeBackend = "CPU"
                                gpuLayers = 0
                                "CPU (KleidiAI NEON · Auto-offloaded from GPU)"
                            }
                            else -> {
                                computeBackend = "CPU"
                                gpuLayers = 0
                                "CPU (KleidiAI NEON Optimized)"
                            }
                        }
                        status = "Ready — ${selectedModel.label} · $backendDesc"
                        if (messages.isEmpty()) {
                            messages = listOf(
                                ChatMsg(
                                    role = "assistant",
                                    text = "Ready! Running ${selectedModel.label} offline on your device ($backendDesc). Ask me anything."
                                )
                            )
                            ChatHistoryStorage.saveMessages(getApplication(), messages)
                        }
                    } else {
                        status = "Could not load model (Out of RAM). Try a smaller 350M/700M model or reduce context."
                    }
                }
            }
        }
    }

    private fun loadModel(f: File) {
        modelFile = f
        loadModelForSelected()
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || busy || handle == 0L) return
        messages = messages + ChatMsg(role = "user", text = text)
        ChatHistoryStorage.saveMessages(getApplication(), messages)
        streamingText = ""
        busy = true
        cancelled = false
        status = "Thinking on-device…"

        // Context-window preservation: keep last 8 turns
        val activeSystemPrompt = if (systemPromptEnabled && systemPrompt.isNotBlank()) systemPrompt.trim() else ""
        val history = messages.dropLast(1).takeLast(8).map { it.role to it.text }
        val prompt = LlamaBridge.buildLfmPrompt(activeSystemPrompt, history, text)

        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        genJob = viewModelScope.launch {
            val out = LlamaBridge.generateStreaming(
                handle = handle,
                prompt = prompt,
                maxTokens = maxTokens,
                temp = temperature,
                topP = topP,
                topK = 40,
                repeatPenalty = repeatPenalty,
                onToken = { piece ->
                    tokenCount++
                    viewModelScope.launch(Dispatchers.Main) {
                        streamingText += piece
                    }
                },
                isCancelled = { cancelled }
            )
            val final = out.ifBlank { streamingText }.trim()
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedInfo = if (tokenCount > 0 && elapsedSec > 0.05) {
                val tokPerSec = tokenCount / elapsedSec
                String.format(java.util.Locale.US, "%.1f tok/s", tokPerSec)
            } else null

            if (!cancelled) {
                messages = messages + ChatMsg(
                    role = "assistant",
                    text = final.ifEmpty { "(no response)" },
                    speedStats = speedInfo
                )
                ChatHistoryStorage.saveMessages(getApplication(), messages)
                status = "Ready — 100% offline"
            }
            streamingText = ""
            busy = false
        }
    }

    fun stop() {
        cancelled = true
        genJob?.cancel()
        val h = handle
        if (h != 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                try { LlamaBridge.nativeStop(h) } catch (_: Throwable) { }
            }
        }
        busy = false
        if (streamingText.isNotBlank()) {
            messages = messages + ChatMsg(role = "assistant", text = streamingText.trim() + " (stopped)")
            ChatHistoryStorage.saveMessages(getApplication(), messages)
        }
        streamingText = ""
        status = "Generation stopped."
    }

    fun clearChat() {
        stop()
        messages = emptyList()
        ChatHistoryStorage.clear(getApplication())
        val h = handle
        if (h != 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                try { LlamaBridge.nativeResetContext(h) } catch (_: Throwable) { }
            }
        }
        status = "Conversation cleared & context cache reset."
    }

    fun deleteModel(model: LfmModel) {
        stop()
        freeModel()
        val ctx = getApplication<Application>()
        try {
            if (model.customUriString != null) {
                val uri = Uri.parse(model.customUriString)
                DocumentFile.fromSingleUri(ctx, uri)?.delete()
            } else if (model.customFilePath != null) {
                File(model.customFilePath).delete()
            } else {
                val target = ModelDownloader.destFile(ctx, model)
                if (target.exists()) target.delete()
            }
        } catch (_: Throwable) { }
        modelFile = null
        refreshLocalState()
        status = "Model file deleted to free space."
    }

    private fun freeModel() {
        val h = handle
        handle = 0L
        if (h != 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                try { LlamaBridge.nativeFreeModel(h) } catch (_: Throwable) { }
                try {
                    activePfd?.close()
                    activePfd = null
                } catch (_: Throwable) { }
            }
        } else {
            try {
                activePfd?.close()
                activePfd = null
            } catch (_: Throwable) { }
        }
    }

    fun getDeviceRamInfo(): String {
        return try {
            val actManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val availGb = String.format("%.1f", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
            val totalGb = String.format("%.1f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
            "$availGb GB free / $totalGb GB total"
        } catch (_: Throwable) {
            "Unknown"
        }
    }

    override fun onCleared() {
        freeModel()
        super.onCleared()
    }
}
