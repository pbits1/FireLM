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
            family.equals("Mistral", ignoreCase = true) || modelFileName.contains("mistral", ignoreCase = true) ||
                modelFileName.contains("mixtral", ignoreCase = true) || modelFileName.contains("llama-2", ignoreCase = true) ||
                modelFileName.contains("llama2", ignoreCase = true) -> {
                buildMistralPrompt(system, history, user)
            }
            family.equals("Microsoft", ignoreCase = true) || modelFileName.contains("phi", ignoreCase = true) -> {
                buildPhiPrompt(system, history, user)
            }
            family.equals("Cohere", ignoreCase = true) || modelFileName.contains("command-r", ignoreCase = true) ||
                modelFileName.contains("cohere", ignoreCase = true) || modelFileName.contains("c4ai", ignoreCase = true) -> {
                buildCoherePrompt(system, history, user)
            }
            modelFileName.contains("alpaca", ignoreCase = true) || modelFileName.contains("vicuna", ignoreCase = true) ||
                modelFileName.contains("wizard", ignoreCase = true) -> {
                buildAlpacaPrompt(system, history, user)
            }
            modelFileName.contains("chatglm", ignoreCase = true) || modelFileName.contains("glm", ignoreCase = true) ||
                modelFileName.contains("baichuan", ignoreCase = true) -> {
                buildChatGlmPrompt(system, history, user)
            }
            family.equals("DeepSeek", ignoreCase = true) || modelFileName.contains("deepseek", ignoreCase = true) ||
                modelFileName.contains("r1", ignoreCase = true) -> {
                buildDeepSeekR1Prompt(system, history, user)
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

    /** DeepSeek R1 Reasoning chat template */
    fun buildDeepSeekR1Prompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<|startoftext|>")
        if (system.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(system.trim()).append("<|im_end|>\n")
        }
        for ((role, text) in history) {
            val r = if (role == "assistant") "assistant" else "user"
            val clean = if (role == "assistant") stripThought(text.trim()) else text.trim()
            sb.append("<|im_start|>").append(r).append("\n")
                .append(sanitize(clean)).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(sanitize(user.trim())).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n<think>\n")
        return sb.toString()
    }

    /** Google Gemma 4 chat template with native thinking mode (<|think|>) */
    fun buildGemma4Prompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<bos>")
        sb.append("<|turn>system\n<|think|>")
        if (system.isNotBlank()) {
            sb.append(system.trim())
        }
        sb.append("<turn|>\n")
        for ((role, text) in history) {
            val r = if (role == "assistant") "model" else "user"
            val clean = if (role == "assistant") stripThought(text.trim()) else text.trim()
            sb.append("<|turn>").append(r).append("\n")
                .append(sanitize(clean))
                .append("<turn|>\n")
        }
        sb.append("<|turn>user\n")
            .append(sanitize(user.trim()))
            .append("<turn|>\n")
        sb.append("<|turn>model\n")
        return sb.toString()
    }

    fun stripThought(text: String): String {
        val noThink = text.replace(Regex("""<think>[\s\S]*?</think>"""), "")
            .replace(Regex("""<thought>[\s\S]*?</thought>"""), "")
            .replace(Regex("""<reasoning>[\s\S]*?</reasoning>"""), "")
        return noThink.trim()
    }

    /** Mistral / Llama 2 Classic ([INST] prompt [/INST]) */
    fun buildMistralPrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        sb.append("<s>")
        val effectiveSystem = if (system.isNotBlank()) "[INST] <<SYS>>\n${system.trim()}\n<</SYS>>\n\n" else "[INST] "
        var first = true
        for ((role, text) in history) {
            val clean = sanitize(text.trim())
            if (role == "user") {
                if (first) {
                    sb.append(effectiveSystem).append(clean).append(" [/INST]")
                    first = false
                } else {
                    sb.append("<s>[INST] ").append(clean).append(" [/INST]")
                }
            } else {
                sb.append(" ").append(clean).append(" </s>")
            }
        }
        if (first) {
            sb.append(effectiveSystem).append(sanitize(user.trim())).append(" [/INST]")
        } else {
            sb.append("<s>[INST] ").append(sanitize(user.trim())).append(" [/INST]")
        }
        return sb.toString()
    }

    /** Microsoft Phi-3 / Phi-4 (<|system|>\n...<|end|>\n<|user|>\n...<|end|>\n<|assistant|>) */
    fun buildPhiPrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("<|system|>\n").append(system.trim()).append("<|end|>\n")
        }
        for ((role, text) in history) {
            val r = if (role == "assistant") "<|assistant|>" else "<|user|>"
            sb.append(r).append("\n").append(sanitize(text.trim())).append("<|end|>\n")
        }
        sb.append("<|user|>\n").append(sanitize(user.trim())).append("<|end|>\n")
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    /** Cohere Command R (<|START_OF_TURN_TOKEN|><|USER_TOKEN|>...<|END_OF_TURN_TOKEN|><|START_OF_TURN_TOKEN|><|CHATBOT_TOKEN|>) */
    fun buildCoherePrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append("<|START_OF_TURN_TOKEN|><|SYSTEM_TOKEN|>").append(system.trim()).append("<|END_OF_TURN_TOKEN|>\n")
        }
        for ((role, text) in history) {
            val tok = if (role == "assistant") "<|CHATBOT_TOKEN|>" else "<|USER_TOKEN|>"
            sb.append("<|START_OF_TURN_TOKEN|>").append(tok).append(sanitize(text.trim())).append("<|END_OF_TURN_TOKEN|>\n")
        }
        sb.append("<|START_OF_TURN_TOKEN|><|USER_TOKEN|>").append(sanitize(user.trim())).append("<|END_OF_TURN_TOKEN|>\n")
        sb.append("<|START_OF_TURN_TOKEN|><|CHATBOT_TOKEN|>")
        return sb.toString()
    }

    /** Alpaca / Vicuna Legacy (### Instruction:\n... ### Response:) */
    fun buildAlpacaPrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        val effectiveSystem = if (system.isNotBlank()) system.trim() else "Below is an instruction that describes a task. Write a response that appropriately completes the request."
        sb.append(effectiveSystem).append("\n\n")
        for ((role, text) in history) {
            val clean = sanitize(text.trim())
            if (role == "user") {
                sb.append("### Instruction:\n").append(clean).append("\n\n")
            } else {
                sb.append("### Response:\n").append(clean).append("\n\n")
            }
        }
        sb.append("### Instruction:\n").append(sanitize(user.trim())).append("\n\n### Response:\n")
        return sb.toString()
    }

    /** ChatGLM / Baichuan ([Round 1]\n\n问：...\n\n答：) */
    fun buildChatGlmPrompt(system: String, history: List<Pair<String, String>>, user: String): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) {
            sb.append(system.trim()).append("\n\n")
        }
        var round = 1
        for ((role, text) in history) {
            val clean = sanitize(text.trim())
            if (role == "user") {
                sb.append("[Round ").append(round).append("]\n\n问：").append(clean).append("\n\n")
            } else {
                sb.append("答：").append(clean).append("\n\n")
                round++
            }
        }
        sb.append("[Round ").append(round).append("]\n\n问：").append(sanitize(user.trim())).append("\n\n答：")
        return sb.toString()
    }

    fun sanitize(input: String): String =
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
            .replace("<s>", "")
            .replace("</s>", "")
            .replace("[INST]", "")
            .replace("[/INST]", "")
            .replace("<<SYS>>", "")
            .replace("<</SYS>>", "")
            .replace("<|system|>", "")
            .replace("<|user|>", "")
            .replace("<|assistant|>", "")
            .replace("<|end|>", "")
            .replace("<|START_OF_TURN_TOKEN|>", "")
            .replace("<|END_OF_TURN_TOKEN|>", "")
            .replace("<|SYSTEM_TOKEN|>", "")
            .replace("<|USER_TOKEN|>", "")
            .replace("<|CHATBOT_TOKEN|>", "")
            .replace("### Instruction:", "")
            .replace("### Response:", "")

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
