package com.lfmlocal.app.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
                        val f = ModelDownloader.destFile(getApplication(), selectedModel)
                        if (f.exists() && ModelDownloader.isValidGguf(f)) {
                            modelFile = f
                            loadModel(f)
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
        val f = modelFile ?: run {
            val dest = if (selectedModel.isCustom) {
                File(ModelDownloader.modelsDir(getApplication()), selectedModel.file)
            } else {
                ModelDownloader.destFile(getApplication(), selectedModel)
            }
            if (dest.exists() && ModelDownloader.isValidGguf(dest)) dest else null
        } ?: return
        modelFile = f
        if (busy || handle != 0L) return
        loadModel(f)
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

        val f = if (m.isCustom) {
            File(ModelDownloader.modelsDir(getApplication()), m.file)
        } else {
            ModelDownloader.destFile(getApplication(), m)
        }

        if (f.exists() && ModelDownloader.isValidGguf(f)) {
            modelFile = f
            loadModel(f)
        } else {
            status = "Selected ${m.label}. Tap Download to save locally."
        }
    }

    fun refreshLocalState() {
        refreshCustomModels()
        val downloaded = ModelCatalog.models.filter {
            ModelDownloader.isDownloaded(getApplication(), it)
        }.map { it.id }.toSet()
        downloadedModelIds = downloaded

        val f = if (selectedModel.isCustom) {
            File(ModelDownloader.modelsDir(getApplication()), selectedModel.file)
        } else {
            ModelDownloader.destFile(getApplication(), selectedModel)
        }

        if (f.exists() && ModelDownloader.isValidGguf(f)) {
            if (modelFile?.absolutePath != f.absolutePath) {
                modelFile = f
                if (handle == 0L) loadModel(f)
            }
        }
    }

    fun refreshCustomModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = ModelDownloader.modelsDir(getApplication())
            val customFiles = dir.listFiles { file ->
                file.isFile && file.extension.equals("gguf", ignoreCase = true) &&
                    ModelCatalog.models.none { it.localName == file.name }
            } ?: emptyArray()

            val list = customFiles.map { ModelCatalog.createCustomModel(it) }
            withContext(Dispatchers.Main) {
                customModels = list
                val savedId = AppPreferences.getSelectedModelId(getApplication())
                if (savedId != null && selectedModel.id != savedId) {
                    val customMatch = list.find { it.id == savedId }
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
                    loadModel(file)
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
        busy = true
        status = "Importing $displayName…"
        viewModelScope.launch {
            val result = ModelDownloader.importUri(getApplication(), uri, displayName)
            busy = false
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
        val f = modelFile ?: return
        if (busy) return
        stop()
        freeModel()
        loadModel(f)
    }

    private fun loadModel(f: File) {
        if (busy) return
        busy = true
        val requestedGpu = if (computeBackend == "GPU") (if (gpuLayers > 0) gpuLayers else 32) else 0
        status = if (requestedGpu > 0) "Loading ${f.name} (GPU default with CPU fallback)…" else "Loading ${f.name} into memory (CPU)…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { LlamaBridge.nativeInit() } catch (_: Throwable) { }
                val h = LlamaBridge.nativeLoadModel(f.absolutePath, contextWindowSize, cpuThreads, requestedGpu)
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
            val target = if (model.isCustom) {
                File(ModelDownloader.modelsDir(ctx), model.file)
            } else {
                ModelDownloader.destFile(ctx, model)
            }
            if (target.exists()) target.delete()
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
            }
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
