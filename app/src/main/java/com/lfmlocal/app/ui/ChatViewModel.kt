package com.lfmlocal.app.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lfmlocal.app.inference.LlamaBridge
import com.lfmlocal.core.download.ModelDownloadService
import com.lfmlocal.core.download.ModelDownloader
import com.lfmlocal.core.model.ChatMsg
import com.lfmlocal.core.model.LfmModel
import com.lfmlocal.core.model.ModelCatalog
import com.lfmlocal.core.storage.AppPreferences
import com.lfmlocal.core.storage.ChatHistoryStorage
import com.lfmlocal.core.storage.GgufScanner
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelDownloadProgress(
    val modelId: String,
    val modelLabel: String = "",
    val fraction: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isRunning: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        fun calculateInferenceThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return when {
                cores >= 8 -> 4
                cores >= 4 -> 3
                else -> maxOf(1, cores)
            }
        }
    }

    private val scanManager = ModelScanManager(application)
    private val inferenceCoordinator = InferenceCoordinator()

    var messages by mutableStateOf(listOf<ChatMsg>())
        private set
    var streamingText by mutableStateOf("")
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var isLoadingModel by mutableStateOf(false)
        private set
    var activeDownload by mutableStateOf<ModelDownloadProgress?>(null)
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
    private var directDownloadJob: Job? = null
    private var vmWakeLock: PowerManager.WakeLock? = null
    private var genJob: Job? = null

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

    // LM Studio System Prompt Deck
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

    // Appearance (System, Dark, Light)
    var themeMode by mutableStateOf(AppPreferences.getThemeMode(application, "dark"))
        private set

    val downloadingModelId: String? get() = activeDownload?.takeIf { it.isRunning }?.modelId
    val downloadingModelLabel: String? get() = activeDownload?.takeIf { it.isRunning }?.modelLabel
    val isDownloading: Boolean get() = activeDownload?.isRunning == true
    val downloadFraction: Float? get() = activeDownload?.takeIf { it.isRunning }?.fraction

    fun isModelDownloading(modelId: String): Boolean =
        activeDownload?.isRunning == true && activeDownload?.modelId == modelId

    fun getDownloadProgress(modelId: String): Float? =
        if (isModelDownloading(modelId)) activeDownload?.fraction else null

    fun getDownloadInfo(modelId: String): ModelDownloadProgress? =
        if (activeDownload?.modelId == modelId) activeDownload else null

    val isModelLoaded: Boolean get() = inferenceCoordinator.isLoaded
    val isModelLoading: Boolean get() = isLoadingModel
    val busy: Boolean get() = isGenerating || isLoadingModel

    init {
        messages = ChatHistoryStorage.loadMessages(application)
        checkStoragePermission()
        refreshCustomModels()
        refreshLocalState()
        observeDownloadService()
    }

    private fun observeDownloadService() {
        viewModelScope.launch {
            ModelDownloadService.downloadStatus.collect { active ->
                if (active.modelId.isNotBlank()) {
                    if (active.isRunning) {
                        activeDownload = ModelDownloadProgress(
                            modelId = active.modelId,
                            modelLabel = active.modelLabel,
                            fraction = active.state.fraction,
                            downloadedBytes = active.state.downloadedBytes,
                            totalBytes = active.state.totalBytes,
                            isRunning = true
                        )
                        val pct = (active.state.fraction * 100).toInt()
                        val mbDown = active.state.downloadedBytes / (1024 * 1024)
                        val mbTotal = active.state.totalBytes / (1024 * 1024)
                        status = "Downloading ${active.modelLabel} ($pct% · $mbDown/$mbTotal MB)…"
                    } else if (active.state.done) {
                        val finishedModelId = active.modelId
                        activeDownload = null
                        refreshLocalState()
                        val finishedModel = ModelCatalog.models.find { it.id == finishedModelId }
                        status = "Ready — ${finishedModel?.label ?: finishedModelId} · 100% offline"
                        if (selectedModel.id == finishedModelId) {
                            loadModelForSelected()
                        }
                    } else if (active.state.error != null) {
                        activeDownload = null
                        status = "Download error: ${active.state.error}"
                    }
                } else if (!active.isRunning) {
                    if (directDownloadJob == null) {
                        activeDownload = null
                    }
                }
            }
        }
    }

    fun checkStoragePermission(): Boolean {
        val granted = scanManager.checkStoragePermission()
        hasStoragePermission = granted
        return granted
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

    private var userEjectedModel = false

    fun updateThemeMode(mode: String) {
        themeMode = mode
        AppPreferences.saveThemeMode(getApplication(), mode)
    }

    fun loadCurrentModel() {
        if (busy || isModelLoaded) return
        userEjectedModel = false
        if (GgufScanner.isDownloaded(getApplication(), selectedModel)) {
            loadModelForSelected()
        }
    }

    fun ejectModel() {
        userEjectedModel = true
        if (busy) stop()
        freeModel()
        modelFile = null
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
        if (selectedModel.id == m.id && isModelLoaded) return
        userEjectedModel = false
        AppPreferences.saveSelectedModelId(getApplication(), m.id)
        stop()
        freeModel()
        selectedModel = m
        modelFile = null

        if (GgufScanner.isDownloaded(getApplication(), m)) {
            loadModelForSelected()
        } else {
            status = "Selected ${m.label}. Tap Download to save locally."
        }
    }

    fun refreshLocalState() {
        checkStoragePermission()
        refreshCustomModels()
        val ctx = getApplication<Application>()
        downloadedModelIds = scanManager.getDownloadedModelIds()

        if (GgufScanner.isDownloaded(ctx, selectedModel)) {
            if (modelFile == null && selectedModel.customFilePath != null) {
                modelFile = File(selectedModel.customFilePath!!)
            } else if (modelFile == null) {
                modelFile = GgufScanner.destFile(ctx, selectedModel)
            }
            if (!isModelLoaded && !busy && !userEjectedModel) {
                loadModelForSelected()
            }
        }
    }

    fun refreshCustomModels() {
        checkStoragePermission()
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.Main) { isSyncingModels = true }

            val detectedList = scanManager.scanCustomModels()

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

    fun startDownload(targetModel: LfmModel = selectedModel) {
        if (targetModel.isCustom) return
        if (isDownloading) {
            val current = downloadingModelLabel ?: "Another model"
            status = "$current is already downloading. Cancel or wait for it to finish."
            return
        }
        status = "Initializing download for ${targetModel.label}…"
        activeDownload = ModelDownloadProgress(
            modelId = targetModel.id,
            modelLabel = targetModel.label,
            fraction = 0f,
            totalBytes = if (targetModel.exactBytes > 0L) targetModel.exactBytes else targetModel.sizeMb * 1024L * 1024L,
            isRunning = true
        )
        val startedForeground = ModelDownloadService.startDownload(getApplication(), targetModel.id)
        if (!startedForeground) {
            runDirectDownload(targetModel)
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

        activeDownload = ModelDownloadProgress(
            modelId = model.id,
            modelLabel = model.label,
            fraction = 0f,
            totalBytes = if (model.exactBytes > 0L) model.exactBytes else model.sizeMb * 1024L * 1024L,
            isRunning = true
        )
        status = "Downloading ${model.label}…"

        directDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            val result = ModelDownloader.download(ctx, model) { st ->
                val active = com.lfmlocal.core.download.ActiveDownload(
                    modelId = model.id,
                    modelLabel = model.label,
                    state = st,
                    isRunning = !st.done && st.error == null
                )
                ModelDownloadService.updateProgress(active)
                viewModelScope.launch(Dispatchers.Main) {
                    activeDownload = ModelDownloadProgress(
                        modelId = model.id,
                        modelLabel = model.label,
                        fraction = st.fraction,
                        downloadedBytes = st.downloadedBytes,
                        totalBytes = st.totalBytes,
                        isRunning = !st.done && st.error == null
                    )
                    val pct = (st.fraction * 100).toInt()
                    val mbDown = st.downloadedBytes / (1024 * 1024)
                    val mbTotal = st.totalBytes / (1024 * 1024)
                    status = "Downloading ${model.label} ($pct% · $mbDown/$mbTotal MB)…"
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    if (vmWakeLock?.isHeld == true) vmWakeLock?.release()
                } catch (_: Exception) {}
                activeDownload = null

                result.onSuccess { file ->
                    refreshLocalState()
                    status = "Ready — ${model.label} · 100% offline"
                    if (selectedModel.id == model.id) {
                        modelFile = file
                        loadModelForSelected()
                    }
                }.onFailure { err ->
                    status = "Download error: ${err.message}"
                }
            }
        }
    }

    fun cancelDownload(modelId: String? = null) {
        directDownloadJob?.cancel()
        directDownloadJob = null
        try {
            if (vmWakeLock?.isHeld == true) vmWakeLock?.release()
        } catch (_: Exception) {}
        ModelDownloadService.cancelDownload(getApplication())
        activeDownload = null
        status = "Download cancelled."
    }

    fun importCustomGguf(uri: Uri, displayName: String) {
        if (busy) return
        status = "Registering $displayName…"
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            if (GgufScanner.isValidGguf(ctx, uri)) {
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

    private suspend fun loadModelForSelectedSuspend(): Boolean {
        val m = selectedModel
        if (isModelLoaded) return true
        isLoadingModel = true
        val requestedGpu = if (computeBackend == "GPU") (if (gpuLayers > 0) gpuLayers else 32) else 0
        status = if (requestedGpu > 0) "Loading ${m.label} (GPU default with CPU fallback)…" else "Loading ${m.label} into memory (CPU)…"

        val modelPath = withContext(Dispatchers.IO) {
            try {
                activePfd?.close()
                activePfd = null
            } catch (_: Throwable) { }

            scanManager.resolveModelPath(m) { pfd ->
                activePfd = pfd
            }
        }

        if (modelPath == null) {
            isLoadingModel = false
            status = "Model file not found. Please sync or download again."
            return false
        }

        if (modelFile == null) {
            modelFile = File(m.file)
        }

        val loadResult = inferenceCoordinator.loadModel(
            modelPath = modelPath,
            contextWindowSize = contextWindowSize,
            cpuThreads = cpuThreads,
            requestedGpuLayers = requestedGpu
        )

        isLoadingModel = false
        if (loadResult.isSuccess) {
            activeContextTokens = loadResult.contextSize
            val actualGpu = loadResult.actualGpuLayers

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
            return true
        } else {
            status = loadResult.errorMessage ?: "Could not load model."
            return false
        }
    }

    private fun loadModelForSelected() {
        if (busy) return
        viewModelScope.launch {
            loadModelForSelectedSuspend()
        }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || busy) return

        messages = messages + ChatMsg(role = "user", text = text)
        ChatHistoryStorage.saveMessages(getApplication(), messages)
        streamingText = ""
        isGenerating = true

        val activeSystemPrompt = if (systemPromptEnabled && systemPrompt.isNotBlank()) systemPrompt.trim() else ""
        val history = messages.dropLast(1).takeLast(8).map { it.role to it.text }
        val prompt = LlamaBridge.buildPrompt(
            family = selectedModel.family,
            modelFileName = selectedModel.file,
            system = activeSystemPrompt,
            history = history,
            user = text
        )

        genJob = viewModelScope.launch {
            if (!isModelLoaded) {
                userEjectedModel = false
                val loaded = loadModelForSelectedSuspend()
                if (!loaded) {
                    isGenerating = false
                    return@launch
                }
            }

            status = "Thinking on-device…"
            val startTime = System.currentTimeMillis()
            var tokenCount = 0

            val out = inferenceCoordinator.generateStreaming(
                prompt = prompt,
                maxTokens = maxTokens,
                temp = temperature,
                topP = topP,
                repeatPenalty = repeatPenalty,
                onToken = { piece ->
                    tokenCount++
                    viewModelScope.launch(Dispatchers.Main) {
                        streamingText += piece
                    }
                }
            )

            val final = out.ifBlank { streamingText }.trim()
            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
            val speedInfo = if (tokenCount > 0 && elapsedSec > 0.05) {
                val tokPerSec = tokenCount / elapsedSec
                String.format(Locale.US, "%.1f tok/s", tokPerSec)
            } else null

            if (isGenerating) {
                messages = messages + ChatMsg(
                    role = "assistant",
                    text = final.ifEmpty { "(no response)" },
                    speedStats = speedInfo
                )
                ChatHistoryStorage.saveMessages(getApplication(), messages)
                status = "Ready — 100% offline"
            }
            streamingText = ""
            isGenerating = false
        }
    }

    fun stop() {
        genJob?.cancel()
        inferenceCoordinator.requestStop()
        isGenerating = false
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
        inferenceCoordinator.resetContext()
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
                File(model.customFilePath!!).delete()
            } else {
                val target = GgufScanner.destFile(ctx, model)
                if (target.exists()) target.delete()
            }
        } catch (_: Throwable) { }
        modelFile = null
        refreshLocalState()
        status = "Model file deleted to free space."
    }

    private fun freeModel() {
        viewModelScope.launch {
            inferenceCoordinator.freeModel()
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
            val availGb = String.format(Locale.US, "%.1f", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
            val totalGb = String.format(Locale.US, "%.1f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
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
