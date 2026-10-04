package com.lfmlocal.core.model

/** One chat bubble. Lives in :core so persistence (ChatHistoryStorage) never depends on UI code. */
data class ChatMsg(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val speedStats: String? = null
)
