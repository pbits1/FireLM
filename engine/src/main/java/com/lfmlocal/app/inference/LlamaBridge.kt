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

    /** Multi-family prompt builder with native chat templates */
    fun buildPrompt(
        family: String = "",
        modelFileName: String = "",
        system: String,
        history: List<Pair<String, String>>,
        user: String
    ): String {
        return when {
            family.equals("Meta", ignoreCase = true) || modelFileName.contains("llama-3", ignoreCase = true) -> {
                buildLlama3Prompt(system, history, user)
            }
            modelFileName.contains("gemma-4", ignoreCase = true) -> {
                buildGemma4Prompt(system, history, user)
            }
            family.equals("Google", ignoreCase = true) || modelFileName.contains("gemma", ignoreCase = true) -> {
                buildGemmaPrompt(system, history, user)
            }
            else -> {
                buildLfmPrompt(system, history, user)
            }
        }
    }

    /** Meta Llama 3 / 3.2 chat template */
    fun buildLlama3Prompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        if (system.isNotBlank()) {
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                .append(system.trim())
                .append("<|eot_id|>")
        }
        for ((role, text) in history) {
            val r = if (role == "assistant") "assistant" else "user"
            sb.append("<|start_header_id|>").append(r).append("<|end_header_id|>\n\n")
                .append(sanitize(text.trim()))
                .append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
            .append(sanitize(user.trim()))
            .append("<|eot_id|>")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    /** Google Gemma 2 chat template */
    fun buildGemmaPrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<bos>")
        val effectiveSystem = if (system.isNotBlank()) "${system.trim()}\n\n" else ""
        if (history.isEmpty()) {
            sb.append("<start_of_turn>user\n")
                .append(effectiveSystem)
                .append(sanitize(user.trim()))
                .append("<end_of_turn>\n<start_of_turn>model\n")
        } else {
            var isFirstUser = true
            for ((role, text) in history) {
                val r = if (role == "assistant") "model" else "user"
                sb.append("<start_of_turn>").append(r).append("\n")
                if (r == "user" && isFirstUser) {
                    sb.append(effectiveSystem)
                    isFirstUser = false
                }
                sb.append(sanitize(text.trim())).append("<end_of_turn>\n")
            }
            sb.append("<start_of_turn>user\n")
            if (isFirstUser) sb.append(effectiveSystem)
            sb.append(sanitize(user.trim())).append("<end_of_turn>\n<start_of_turn>model\n")
        }
        return sb.toString()
    }

    /** ChatML template (Qwen 2.5, DeepSeek R1, SmolLM2, Liquid AI LFM2):
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

    /** Google Gemma 4 chat template (<bos><|turn>system\n...<turn|>\n<|turn>user\n...<turn|>\n<|turn>model\n) */
    fun buildGemma4Prompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<bos>")
        if (system.isNotBlank()) {
            sb.append("<|turn>system\n").append(system.trim()).append("<turn|>\n")
        }
        for ((role, text) in history) {
            val r = if (role == "assistant") "model" else "user"
            sb.append("<|turn>").append(r).append("\n")
                .append(sanitize(text.trim()))
                .append("<turn|>\n")
        }
        sb.append("<|turn>user\n")
            .append(sanitize(user.trim()))
            .append("<turn|>\n")
        sb.append("<|turn>model\n")
        return sb.toString()
    }

    private fun sanitize(input: String): String =
        input.replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
            .replace("<|startoftext|>", "")
            .replace("<|begin_of_text|>", "")
            .replace("<|start_header_id|>", "")
            .replace("<|end_header_id|>", "")
            .replace("<|eot_id|>", "")
            .replace("<start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .replace("<|turn>", "")
            .replace("<turn|>", "")
            .replace("<bos>", "")

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
