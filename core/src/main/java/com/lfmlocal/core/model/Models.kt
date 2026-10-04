package com.lfmlocal.core.model

import java.io.File

/**
 * Built-in and custom catalog with exact byte sizes verified against Hugging Face.
 */
data class LfmModel(
    val id: String,
    val label: String,
    val family: String = "Liquid AI",
    val repo: String = "",
    val file: String,
    val sizeMb: Int,
    val exactBytes: Long = 0L,
    val minRamMb: Int,
    val recommended: Boolean = false,
    val description: String,
    val isCustom: Boolean = false,
    val customUriString: String? = null,
    val customFilePath: String? = null
) {
    val url: String get() = if (repo.isNotBlank()) "https://huggingface.co/$repo/resolve/main/$file" else ""
    val localName: String get() = if (isCustom) file else "$id-$file"
}

object ModelCatalog {
    val models = listOf(
        // --- META LLAMA 3.2 ---
        LfmModel(
            id = "llama-3.2-1b-instruct",
            label = "Llama 3.2 1B · Fast Assistant",
            family = "Meta",
            repo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            file = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeMb = 770,
            exactBytes = 807694464L,
            minRamMb = 3000,
            recommended = true,
            description = "Meta's flagship on-device assistant (~770MB). Superb general knowledge, reasoning, and coherence."
        ),
        LfmModel(
            id = "llama-3.2-3b-instruct",
            label = "Llama 3.2 3B · Flagship Intelligence",
            family = "Meta",
            repo = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            file = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeMb = 1926,
            exactBytes = 2019377696L,
            minRamMb = 6000,
            description = "Meta's high-capacity edge model (~1.9GB). Exceptional comprehension for 6GB+ RAM phones."
        ),

        // --- ALIBABA QWEN 2.5 ---
        LfmModel(
            id = "qwen2.5-0.5b-instruct",
            label = "Qwen 2.5 0.5B · Pocket Speed",
            family = "Alibaba",
            repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeMb = 469,
            exactBytes = 491400032L,
            minRamMb = 2000,
            description = "Ultra-compact multilingual powerhouse (~469MB). High-speed coding and everyday chats on any phone."
        ),
        LfmModel(
            id = "qwen2.5-1.5b-instruct",
            label = "Qwen 2.5 1.5B · Coding & Math",
            family = "Alibaba",
            repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            file = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeMb = 1066,
            exactBytes = 1117320736L,
            minRamMb = 3500,
            description = "Benchmark-leading 1.5B model (~1.06GB). Outstanding structured reasoning, code generation, and instruction following."
        ),

        // --- DEEPSEEK REASONING ---
        LfmModel(
            id = "deepseek-r1-distill-qwen-1.5b",
            label = "DeepSeek R1 Distill 1.5B · Reasoning",
            family = "DeepSeek",
            repo = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            file = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            sizeMb = 1066,
            exactBytes = 1117320800L,
            minRamMb = 3500,
            description = "Reasoning model with thinking tokens (~1.06GB). Solves math and logic puzzles step-by-step on device."
        ),

        // --- HUGGING FACE SMOLLM2 ---
        LfmModel(
            id = "smollm2-360m-instruct",
            label = "SmolLM2 360M · Featherweight",
            family = "Hugging Face",
            repo = "bartowski/SmolLM2-360M-Instruct-GGUF",
            file = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            sizeMb = 258,
            exactBytes = 270590880L,
            minRamMb = 1800,
            description = "Hugging Face's ultra-light edge model (~258MB). Near-instant inference on budget hardware."
        ),
        LfmModel(
            id = "smollm2-1.7b-instruct",
            label = "SmolLM2 1.7B · Conversational",
            family = "Hugging Face",
            repo = "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF",
            file = "smollm2-1.7b-instruct-q4_k_m.gguf",
            sizeMb = 1007,
            exactBytes = 1055609536L,
            minRamMb = 3500,
            description = "Trained on 11 trillion tokens (~1.0GB). Superb general knowledge and natural human conversation."
        ),

        // --- GOOGLE GEMMA 2 ---
        LfmModel(
            id = "gemma-2-2b-it",
            label = "Gemma 2 2B · Creative Prose",
            family = "Google",
            repo = "bartowski/gemma-2-2b-it-GGUF",
            file = "gemma-2-2b-it-Q4_K_M.gguf",
            sizeMb = 1629,
            exactBytes = 1708582752L,
            minRamMb = 5000,
            description = "Google DeepMind's Gemma 2 (~1.6GB). State-of-the-art prose, analysis, and creative writing."
        ),

        // --- LIQUID AI LFM2 ---
        LfmModel(
            id = "lfm2-350m",
            label = "LFM2 350M · Fastest",
            family = "Liquid AI",
            repo = "LiquidAI/LFM2-350M-GGUF",
            file = "LFM2-350M-Q4_K_M.gguf",
            sizeMb = 219,
            exactBytes = 229309376L,
            minRamMb = 1500,
            description = "Ultra-compact and lightning-fast (~219MB). Ideal for older devices or quick replies."
        ),
        LfmModel(
            id = "lfm2-700m",
            label = "LFM2 700M · Balanced Mini",
            family = "Liquid AI",
            repo = "LiquidAI/LFM2-700M-GGUF",
            file = "LFM2-700M-Q4_K_M.gguf",
            sizeMb = 447,
            exactBytes = 468624320L,
            minRamMb = 2000,
            description = "Crisp, concise answers with a minimal RAM footprint (~447MB)."
        ),
        LfmModel(
            id = "lfm2-1.2b-q4km",
            label = "LFM2 1.2B Q4_K_M · Hybrid",
            family = "Liquid AI",
            repo = "LiquidAI/LFM2-1.2B-GGUF",
            file = "LFM2-1.2B-Q4_K_M.gguf",
            sizeMb = 697,
            exactBytes = 730893248L,
            minRamMb = 3000,
            description = "Sweet spot for quality, coherence, and speed (~697MB)."
        ),
        LfmModel(
            id = "lfm2-1.2b-q40",
            label = "LFM2 1.2B Q4_0 · Smallest 1.2B",
            family = "Liquid AI",
            repo = "LiquidAI/LFM2-1.2B-GGUF",
            file = "LFM2-1.2B-Q4_0.gguf",
            sizeMb = 664,
            exactBytes = 695749568L,
            minRamMb = 2800,
            description = "33MB lighter than Q4_K_M for devices with tighter internal flash storage (~664MB)."
        ),
        LfmModel(
            id = "lfm25-1.2b-instruct",
            label = "LFM2.5 1.2B Instruct · Smartest",
            family = "Liquid AI",
            repo = "LiquidAI/LFM2.5-1.2B-Instruct-GGUF",
            file = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            sizeMb = 697,
            exactBytes = 730895168L,
            minRamMb = 3000,
            description = "Liquid AI's latest revision with improved structured instruction following (~697MB)."
        ),
        LfmModel(
            id = "lfm2-2.6b",
            label = "LFM2 2.6B · Flagship Max",
            family = "Liquid AI",
            repo = "LiquidAI/LFM2-2.6B-GGUF",
            file = "LFM2-2.6B-Q4_K_M.gguf",
            sizeMb = 1491,
            exactBytes = 1563668704L,
            minRamMb = 6000,
            description = "Highest reasoning quality (~1.49GB). Demands 6GB+ free RAM flagship phones."
        )
    )

    fun byId(id: String): LfmModel =
        models.firstOrNull { it.id == id } ?: models.first { it.recommended }

    fun createCustomModel(file: File): LfmModel {
        val sizeMb = (file.length() / (1024 * 1024)).toInt().coerceAtLeast(1)
        val ramMb = (sizeMb * 1.6).toInt()
        val cleanName = if (file.name.endsWith(".gguf", ignoreCase = true)) file.name.dropLast(5) else file.name
        return LfmModel(
            id = "custom-${file.absolutePath.hashCode()}-${file.name}",
            label = cleanName,
            file = file.name,
            sizeMb = sizeMb,
            exactBytes = file.length(),
            minRamMb = ramMb,
            description = "Custom GGUF in ${file.parentFile?.name ?: "Download/FireLM"}.",
            isCustom = true,
            customFilePath = file.absolutePath
        )
    }

    fun createCustomModelFromDoc(name: String, sizeBytes: Long, uri: android.net.Uri): LfmModel {
        val sizeMb = (sizeBytes / (1024 * 1024)).toInt().coerceAtLeast(1)
        val ramMb = (sizeMb * 1.6).toInt()
        val cleanName = if (name.endsWith(".gguf", ignoreCase = true)) name.dropLast(5) else name
        return LfmModel(
            id = "saf-${name.hashCode()}-$name",
            label = cleanName.take(28),
            file = name,
            sizeMb = sizeMb,
            exactBytes = sizeBytes,
            minRamMb = ramMb,
            description = "Custom GGUF synced from Models folder.",
            isCustom = true,
            customUriString = uri.toString()
        )
    }
}
