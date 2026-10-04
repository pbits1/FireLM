package com.lfmlocal.app.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin Kotlin wrapper over llama_jni.cpp */
object LlamaBridge {
    init {
        try { System.loadLibrary("lfm_jni") } catch (_: UnsatisfiedLinkError) { }
    }

    interface TokenCallback {
        fun onToken(piece: String)
        fun isCancelled(): Boolean
    }

    @JvmStatic external fun nativeInit()
    @JvmStatic external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    @JvmStatic external fun nativeGpuLayers(handle: Long): Int
    @JvmStatic external fun nativeResetContext(handle: Long)
    @JvmStatic external fun nativeFreeModel(handle: Long)
    @JvmStatic external fun nativeStop(handle: Long)
    @JvmStatic external fun nativeContextSize(handle: Long): Int
    @JvmStatic external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback?
    ): String

    /** LFM2 chat template:
     *  <|startoftext|><|im_start|>system\n...<|im_end|>\n<|im_start|>user\n... etc.
     */
    fun buildLfmPrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<|startoftext|>")
        if (system.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(system.trim()).append("<|im_end|>\n")
        }
        for ((role, text) in history) {
            val r = if (role == "assistant") "assistant" else "user"
            sb.append("<|im_start|>").append(r).append("\n")
                .append(sanitize(text.trim())).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(sanitize(user.trim())).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun sanitize(input: String): String =
        input.replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
            .replace("<|startoftext|>", "")

    suspend fun generateStreaming(
        handle: Long,
        prompt: String,
        maxTokens: Int = 512,
        temp: Float = 0.7f,
        topP: Float = 0.95f,
        topK: Int = 40,
        repeatPenalty: Float = 1.05f,
        onToken: (String) -> Unit,
        isCancelled: () -> Boolean
    ): String = withContext(Dispatchers.IO) {
        val cb = object : TokenCallback {
            override fun onToken(piece: String) { onToken(piece) }
            override fun isCancelled(): Boolean = isCancelled()
        }
        nativeGenerate(handle, prompt, maxTokens, temp, topP, topK, repeatPenalty, cb) ?: ""
    }
}
