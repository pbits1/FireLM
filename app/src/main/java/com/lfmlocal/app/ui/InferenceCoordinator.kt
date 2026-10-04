package com.lfmlocal.app.ui

import com.lfmlocal.app.inference.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ModelLoadResult(
    val handle: Long,
    val actualGpuLayers: Int,
    val contextSize: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class InferenceCoordinator {
    var handle: Long = 0L
        private set

    val isLoaded: Boolean get() = handle != 0L
    var activeContextTokens: Int = 2048
        private set

    @Volatile
    private var isCancelled = false

    suspend fun loadModel(
        modelPath: String,
        contextWindowSize: Int,
        cpuThreads: Int,
        requestedGpuLayers: Int
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        try { LlamaBridge.nativeInit() } catch (_: Throwable) { }

        val h = LlamaBridge.nativeLoadModel(modelPath, contextWindowSize, cpuThreads, requestedGpuLayers)
        if (h != 0L) {
            handle = h
            val actualGpu = try { LlamaBridge.nativeGpuLayers(h) } catch (_: Throwable) { 0 }
            val ctxSize = try { LlamaBridge.nativeContextSize(h) } catch (_: Throwable) { contextWindowSize }
            activeContextTokens = ctxSize
            ModelLoadResult(handle = h, actualGpuLayers = actualGpu, contextSize = ctxSize, isSuccess = true)
        } else {
            ModelLoadResult(
                handle = 0L,
                actualGpuLayers = 0,
                contextSize = contextWindowSize,
                isSuccess = false,
                errorMessage = "Could not load model (Out of RAM). Try a smaller 350M/700M model or reduce context."
            )
        }
    }

    suspend fun generateStreaming(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        repeatPenalty: Float,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val h = handle
        if (h == 0L) return@withContext ""
        isCancelled = false
        LlamaBridge.generateStreaming(
            handle = h,
            prompt = prompt,
            maxTokens = maxTokens,
            temp = temp,
            topP = topP,
            topK = 40,
            repeatPenalty = repeatPenalty,
            onToken = onToken,
            isCancelled = { isCancelled }
        )
    }

    fun requestStop() {
        isCancelled = true
        val h = handle
        if (h != 0L) {
            try { LlamaBridge.nativeStop(h) } catch (_: Throwable) { }
        }
    }

    fun resetContext() {
        val h = handle
        if (h != 0L) {
            try { LlamaBridge.nativeResetContext(h) } catch (_: Throwable) { }
        }
    }

    suspend fun freeModel() = withContext(Dispatchers.IO) {
        val h = handle
        handle = 0L
        if (h != 0L) {
            try { LlamaBridge.nativeFreeModel(h) } catch (_: Throwable) { }
        }
    }
}
