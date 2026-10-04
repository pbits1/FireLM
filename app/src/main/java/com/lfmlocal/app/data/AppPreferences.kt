package com.lfmlocal.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Robust persistent storage for user settings, hardware inference configuration,
 * selected models, and system persona prompts using Android SharedPreferences.
 */
object AppPreferences {
    private const val PREFS_NAME = "lfm_app_preferences"

    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    private const val KEY_COMPUTE_BACKEND = "compute_backend"
    private const val KEY_GPU_LAYERS = "gpu_layers"
    private const val KEY_CONTEXT_WINDOW_SIZE = "context_window_size"
    private const val KEY_CPU_THREADS = "cpu_threads"
    private const val KEY_SUSTAINED_PERF = "sustained_performance_mode"

    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_SYSTEM_PROMPT_ENABLED = "system_prompt_enabled"

    private const val KEY_TEMPERATURE = "temperature"
    private const val KEY_TOP_P = "top_p"
    private const val KEY_REPEAT_PENALTY = "repeat_penalty"
    private const val KEY_MAX_TOKENS = "max_tokens"
    private const val KEY_MODELS_FOLDER_URI = "models_folder_uri"
    private const val KEY_MODELS_FOLDER_NAME = "models_folder_name"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Models Storage Directory & SAF Tree
    fun saveCustomModelsFolderUri(ctx: Context, uri: String?) {
        prefs(ctx).edit().putString(KEY_MODELS_FOLDER_URI, uri).apply()
    }

    fun getCustomModelsFolderUri(ctx: Context): String? {
        return prefs(ctx).getString(KEY_MODELS_FOLDER_URI, null)
    }

    fun saveCustomModelsFolderName(ctx: Context, name: String?) {
        prefs(ctx).edit().putString(KEY_MODELS_FOLDER_NAME, name).apply()
    }

    fun getCustomModelsFolderName(ctx: Context, default: String = "Download/FireLM"): String {
        return prefs(ctx).getString(KEY_MODELS_FOLDER_NAME, default) ?: default
    }

    // Model selection
    fun saveSelectedModelId(ctx: Context, modelId: String) {
        prefs(ctx).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }

    fun getSelectedModelId(ctx: Context): String? {
        return prefs(ctx).getString(KEY_SELECTED_MODEL_ID, null)
    }

    // Hardware & Compute Engine
    fun saveComputeBackend(ctx: Context, backend: String) {
        prefs(ctx).edit().putString(KEY_COMPUTE_BACKEND, backend).apply()
    }

    fun getComputeBackend(ctx: Context, default: String = "GPU"): String {
        return prefs(ctx).getString(KEY_COMPUTE_BACKEND, default) ?: default
    }

    fun saveGpuLayers(ctx: Context, layers: Int) {
        prefs(ctx).edit().putInt(KEY_GPU_LAYERS, layers).apply()
    }

    fun getGpuLayers(ctx: Context, default: Int = 32): Int {
        return prefs(ctx).getInt(KEY_GPU_LAYERS, default)
    }

    fun saveContextWindowSize(ctx: Context, size: Int) {
        prefs(ctx).edit().putInt(KEY_CONTEXT_WINDOW_SIZE, size).apply()
    }

    fun getContextWindowSize(ctx: Context, default: Int = 2048): Int {
        return prefs(ctx).getInt(KEY_CONTEXT_WINDOW_SIZE, default)
    }

    fun saveCpuThreads(ctx: Context, threads: Int) {
        prefs(ctx).edit().putInt(KEY_CPU_THREADS, threads).apply()
    }

    fun getCpuThreads(ctx: Context, default: Int): Int {
        return prefs(ctx).getInt(KEY_CPU_THREADS, default)
    }

    fun saveSustainedPerformance(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SUSTAINED_PERF, enabled).apply()
    }

    fun getSustainedPerformance(ctx: Context, default: Boolean = true): Boolean {
        return prefs(ctx).getBoolean(KEY_SUSTAINED_PERF, default)
    }

    // System Prompt & Alignment Configuration
    fun saveSystemPromptEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SYSTEM_PROMPT_ENABLED, enabled).apply()
    }

    fun isSystemPromptEnabled(ctx: Context, default: Boolean = false): Boolean {
        return prefs(ctx).getBoolean(KEY_SYSTEM_PROMPT_ENABLED, default)
    }

    fun saveSystemPrompt(ctx: Context, prompt: String) {
        prefs(ctx).edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
    }

    fun getSystemPrompt(ctx: Context, default: String = ""): String {
        return prefs(ctx).getString(KEY_SYSTEM_PROMPT, default) ?: default
    }

    // Inference Hyperparameters
    fun saveTemperature(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat(KEY_TEMPERATURE, value).apply()
    }

    fun getTemperature(ctx: Context, default: Float = 0.7f): Float {
        return prefs(ctx).getFloat(KEY_TEMPERATURE, default)
    }

    fun saveTopP(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat(KEY_TOP_P, value).apply()
    }

    fun getTopP(ctx: Context, default: Float = 0.95f): Float {
        return prefs(ctx).getFloat(KEY_TOP_P, default)
    }

    fun saveRepeatPenalty(ctx: Context, value: Float) {
        prefs(ctx).edit().putFloat(KEY_REPEAT_PENALTY, value).apply()
    }

    fun getRepeatPenalty(ctx: Context, default: Float = 1.05f): Float {
        return prefs(ctx).getFloat(KEY_REPEAT_PENALTY, default)
    }

    fun saveMaxTokens(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_MAX_TOKENS, value).apply()
    }

    fun getMaxTokens(ctx: Context, default: Int = 512): Int {
        return prefs(ctx).getInt(KEY_MAX_TOKENS, default)
    }
}
