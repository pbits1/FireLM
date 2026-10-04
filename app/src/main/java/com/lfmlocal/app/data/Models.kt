package com.lfmlocal.app.data

import java.io.File

/**
 * Built-in and custom catalog with exact byte sizes verified against Hugging Face.
 */
data class LfmModel(
    val id: String,
    val label: String,
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
        LfmModel(
            id = "lfm2-350m",
            label = "LFM2 350M · Fastest",
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
            repo = "LiquidAI/LFM2-700M-GGUF",
            file = "LFM2-700M-Q4_K_M.gguf",
            sizeMb = 447,
            exactBytes = 468624320L,
            minRamMb = 2000,
            description = "Crisp, concise answers with a minimal RAM footprint (~447MB)."
        ),
        LfmModel(
            id = "lfm2-1.2b-q4km",
            label = "LFM2 1.2B Q4_K_M · Recommended",
            repo = "LiquidAI/LFM2-1.2B-GGUF",
            file = "LFM2-1.2B-Q4_K_M.gguf",
            sizeMb = 697,
            exactBytes = 730893248L,
            minRamMb = 3000,
            recommended = true,
            description = "The gold standard sweet spot for quality, coherence, and speed (~697MB)."
        ),
        LfmModel(
            id = "lfm2-1.2b-q40",
            label = "LFM2 1.2B Q4_0 · Smallest 1.2B",
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
            id = "custom-${file.name}",
            label = cleanName.take(28),
            file = file.name,
            sizeMb = sizeMb,
            exactBytes = file.length(),
            minRamMb = ramMb,
            description = "Custom GGUF located in ${file.parentFile?.name ?: "storage"}.",
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
