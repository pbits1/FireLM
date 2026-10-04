package com.lfmlocal.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.theme.*
import com.lfmlocal.core.model.ChatMsg
import kotlinx.coroutines.delay

sealed class ContentSegment {
    data class Text(val text: String) : ContentSegment()
    data class Code(val language: String, val code: String) : ContentSegment()
}

fun parseMessageContent(raw: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    val lines = raw.lines()
    var inCodeBlock = false
    var currentLanguage = ""
    val currentBuffer = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (!inCodeBlock) {
                if (currentBuffer.isNotEmpty()) {
                    segments.add(ContentSegment.Text(currentBuffer.toString().trimEnd()))
                    currentBuffer.clear()
                }
                inCodeBlock = true
                currentLanguage = trimmed.removePrefix("```").trim().uppercase().ifEmpty { "CODE" }
            } else {
                segments.add(ContentSegment.Code(currentLanguage, currentBuffer.toString().trimEnd()))
                currentBuffer.clear()
                inCodeBlock = false
                currentLanguage = ""
            }
        } else {
            if (currentBuffer.isNotEmpty()) {
                currentBuffer.append("\n")
            }
            currentBuffer.append(line)
        }
    }

    if (currentBuffer.isNotEmpty()) {
        if (inCodeBlock) {
            segments.add(ContentSegment.Code(currentLanguage, currentBuffer.toString().trimEnd()))
        } else {
            segments.add(ContentSegment.Text(currentBuffer.toString().trimEnd()))
        }
    }

    return if (segments.isEmpty()) listOf(ContentSegment.Text(raw)) else segments
}

@Composable
fun ChatBubble(msg: ChatMsg, backend: String) {
    val isUser = msg.role == "user"
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            // User Prompt Bubble: Soft rounded pill
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            // Assistant Message: Borderless flowing typography (Claude / ChatGPT style)
            val segments = remember(msg.text) { parseMessageContent(msg.text) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, top = 2.dp, bottom = 6.dp)
            ) {
                // Content Segments
                segments.forEach { segment ->
                    when (segment) {
                        is ContentSegment.Text -> {
                            Text(
                                text = segment.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is ContentSegment.Code -> {
                            Spacer(Modifier.height(8.dp))
                            AppleCodeBlock(language = segment.language, code = segment.code)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Whisper-Quiet Action Row (Copy + Discreet Speed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                            copied = true
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            modifier = Modifier.size(15.dp),
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                    }

                    val stats = msg.speedStats
                    if (stats != null) {
                        // Extract tok/s compactly, e.g. "29.2 tok/s"
                        val tokSec = Regex("""([0-9.]+\s*tok/s)""").find(stats)?.value ?: stats
                        Text(
                            text = tokSec,
                            style = TelemetryMetricStyle,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppleCodeBlock(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )

                Row(
                    modifier = Modifier
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            copied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Scrollable Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 2.dp)
            ) {
                Text(
                    text = code,
                    style = CodeSnippetStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun StreamingBubble(text: String, backend: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp, top = 2.dp, bottom = 6.dp)
    ) {
        Text(
            text = "$text ▋",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ThinkingIndicator(backend: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Thinking…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}
