package com.lfmlocal.app.data

import android.content.Context
import com.lfmlocal.app.ui.ChatMsg
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Atomic persistent storage for chat message history on disk.
 * Uses atomic file replacement (tmp -> final) to prevent corrupt files on crash or sudden shutdown.
 */
object ChatHistoryStorage {
    private const val FILE_NAME = "chat_history.json"
    private const val TMP_FILE_NAME = "chat_history.json.tmp"

    private fun getStorageFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)
    private fun getTmpFile(ctx: Context): File = File(ctx.filesDir, TMP_FILE_NAME)

    /**
     * Atomically saves chat messages list to JSON file.
     */
    fun saveMessages(ctx: Context, messages: List<ChatMsg>) {
        try {
            val jsonArray = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    if (msg.speedStats != null) {
                        put("speedStats", msg.speedStats)
                    }
                }
                jsonArray.put(obj)
            }

            val tmpFile = getTmpFile(ctx)
            val finalFile = getStorageFile(ctx)

            FileOutputStream(tmpFile).use { fos ->
                fos.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync() // Ensure physical flush to NAND flash
            }

            // Atomic rename
            if (tmpFile.exists()) {
                if (finalFile.exists()) finalFile.delete()
                tmpFile.renameTo(finalFile)
            }
        } catch (_: Throwable) {
            // Fail gracefully without crashing
        }
    }

    /**
     * Loads chat message history from JSON file. Returns empty list if missing or corrupted.
     */
    fun loadMessages(ctx: Context): List<ChatMsg> {
        val file = getStorageFile(ctx)
        if (!file.exists() || file.length() == 0L) return emptyList()

        return try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<ChatMsg>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ChatMsg(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        role = obj.getString("role"),
                        text = obj.getString("text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        speedStats = if (obj.has("speedStats")) obj.optString("speedStats") else null
                    )
                )
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Clears on-disk chat history.
     */
    fun clear(ctx: Context) {
        try {
            val file = getStorageFile(ctx)
            if (file.exists()) file.delete()
            val tmp = getTmpFile(ctx)
            if (tmp.exists()) tmp.delete()
        } catch (_: Throwable) { }
    }
}
