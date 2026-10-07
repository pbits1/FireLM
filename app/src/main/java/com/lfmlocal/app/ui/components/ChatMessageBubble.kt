package com.lfmlocal.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.theme.*
import com.lfmlocal.core.model.ChatMsg
import kotlinx.coroutines.delay

sealed class ContentSegment {
    data class Text(val text: String) : ContentSegment()
    data class Code(val language: String, val code: String) : ContentSegment()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ContentSegment()
    data class Mermaid(val diagram: String) : ContentSegment()
}

data class ParsedThoughtResult(
    val thought: String?,
    val answer: String,
    val isThinkingActive: Boolean
)

fun parseThoughtAndContent(raw: String): ParsedThoughtResult {
    val openTag = when {
        raw.contains("<think>") -> "<think>"
        raw.contains("<thought>") -> "<thought>"
        raw.contains("<reasoning>") -> "<reasoning>"
        else -> null
    }
    val closeTag = when {
        raw.contains("</think>") -> "</think>"
        raw.contains("</thought>") -> "</thought>"
        raw.contains("</reasoning>") -> "</reasoning>"
        else -> null
    }

    if (openTag != null && closeTag != null) {
        val thought = raw.substringAfter(openTag).substringBefore(closeTag).trim()
        val answer = raw.substringAfter(closeTag).trim()
        return ParsedThoughtResult(
            thought = thought.ifEmpty { null },
            answer = answer,
            isThinkingActive = false
        )
    }

    if (openTag != null && closeTag == null) {
        val thought = raw.substringAfter(openTag).trim()
        return ParsedThoughtResult(
            thought = thought.ifEmpty { null },
            answer = "",
            isThinkingActive = true
        )
    }

    // Case where open tag was prefilled into prompt (e.g. DeepSeek R1) so only close tag was streamed
    if (openTag == null && closeTag != null) {
        val thought = raw.substringBefore(closeTag).trim()
        val answer = raw.substringAfter(closeTag).trim()
        return ParsedThoughtResult(
            thought = thought.ifEmpty { null },
            answer = answer,
            isThinkingActive = false
        )
    }

    return ParsedThoughtResult(thought = null, answer = raw, isThinkingActive = false)
}

fun parseMarkdownTableLines(lines: List<String>): ContentSegment.Table? {
    if (lines.size < 2) return null
    val headerLine = lines[0]
    val dividerLine = lines[1]
    if (!headerLine.contains("|") || !dividerLine.contains("|") || !dividerLine.contains("-")) return null

    fun splitRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split("|").map { it.trim() }
    }

    val headers = splitRow(headerLine)
    if (headers.isEmpty()) return null

    val rows = mutableListOf<List<String>>()
    for (i in 2 until lines.size) {
        val l = lines[i].trim()
        if (l.isNotEmpty() && l.contains("|")) {
            val row = splitRow(l)
            if (row.isNotEmpty()) {
                rows.add(row)
            }
        }
    }
    return if (rows.isNotEmpty()) ContentSegment.Table(headers, rows) else null
}

fun parseMarkdownTable(text: String): ContentSegment.Table? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return parseMarkdownTableLines(lines)
}

fun extractTextAndTableSegments(raw: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    val lines = raw.lines()
    val textLines = mutableListOf<String>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        val isTableCandidate = trimmed.contains("|") &&
            (i + 1 < lines.size) &&
            lines[i + 1].trim().contains("|") &&
            lines[i + 1].trim().contains("-")

        if (isTableCandidate) {
            // Flush preceding text lines
            if (textLines.isNotEmpty()) {
                val text = textLines.joinToString("\n").trim()
                if (text.isNotEmpty()) segments.add(ContentSegment.Text(text))
                textLines.clear()
            }

            // Gather all contiguous table lines
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().contains("|")) {
                tableLines.add(lines[i].trim())
                i++
            }

            val table = parseMarkdownTableLines(tableLines)
            if (table != null) {
                segments.add(table)
            } else {
                textLines.addAll(tableLines)
            }
        } else {
            textLines.add(line)
            i++
        }
    }

    if (textLines.isNotEmpty()) {
        val text = textLines.joinToString("\n").trim()
        if (text.isNotEmpty()) segments.add(ContentSegment.Text(text))
    }

    return segments
}

fun parseMessageContent(raw: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    val lines = raw.lines()
    var inCodeBlock = false
    var currentLanguage = ""
    val codeBuffer = StringBuilder()
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            val block = textBuffer.toString().trimEnd()
            textBuffer.clear()
            if (block.isNotEmpty()) {
                segments.addAll(extractTextAndTableSegments(block))
            }
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (!inCodeBlock) {
                flushText()
                inCodeBlock = true
                currentLanguage = trimmed.removePrefix("```").trim().uppercase().ifEmpty { "CODE" }
            } else {
                val code = codeBuffer.toString().trimEnd()
                codeBuffer.clear()
                inCodeBlock = false

                val table = parseMarkdownTable(code)
                when {
                    table != null -> segments.add(table)
                    currentLanguage == "MERMAID" || currentLanguage == "CHART" -> segments.add(ContentSegment.Mermaid(code))
                    else -> segments.add(ContentSegment.Code(currentLanguage, code))
                }
                currentLanguage = ""
            }
        } else {
            if (inCodeBlock) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
            } else {
                if (textBuffer.isNotEmpty()) textBuffer.append("\n")
                textBuffer.append(line)
            }
        }
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        val code = codeBuffer.toString().trimEnd()
        val table = parseMarkdownTable(code)
        if (table != null) {
            segments.add(table)
        } else {
            segments.add(ContentSegment.Code(currentLanguage, code))
        }
    } else {
        flushText()
    }

    return if (segments.isEmpty()) listOf(ContentSegment.Text(raw)) else segments
}

fun buildMarkdownAnnotatedString(text: String, colorScheme: ColorScheme): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)""")
        var lastIdx = 0
        val matches = pattern.findAll(text)
        for (match in matches) {
            if (match.range.first > lastIdx) {
                append(text.substring(lastIdx, match.range.first))
            }
            val value = match.value
            when {
                value.startsWith("**") && value.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(value.removeSurrounding("**"))
                    }
                }
                value.startsWith("*") && value.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(value.removeSurrounding("*"))
                    }
                }
                value.startsWith("`") && value.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = colorScheme.surfaceContainerHighest,
                            color = colorScheme.primary
                        )
                    ) {
                        append(" ${value.removeSurrounding("`")} ")
                    }
                }
                else -> append(value)
            }
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}

@Composable
fun MarkdownBlock(rawText: String) {
    val colorScheme = MaterialTheme.colorScheme
    val lines = rawText.lines()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = buildMarkdownAnnotatedString(trimmed.removePrefix("### "), colorScheme),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = buildMarkdownAnnotatedString(trimmed.removePrefix("## "), colorScheme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = buildMarkdownAnnotatedString(trimmed.removePrefix("# "), colorScheme),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 6.dp, top = 1.dp, bottom = 1.dp)) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Text(
                            text = buildMarkdownAnnotatedString(trimmed.substring(2), colorScheme),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurface
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Surface(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = buildMarkdownAnnotatedString(trimmed.removePrefix("> "), colorScheme),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = buildMarkdownAnnotatedString(line, colorScheme),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun formatTableAsMarkdown(table: ContentSegment.Table): String {
    val sb = StringBuilder()
    sb.append("| ").append(table.headers.joinToString(" | ")).append(" |\n")
    sb.append("| ").append(table.headers.map { "---" }.joinToString(" | ")).append(" |\n")
    for (row in table.rows) {
        val paddedRow = table.headers.indices.map { row.getOrNull(it) ?: "" }
        sb.append("| ").append(paddedRow.joinToString(" | ")).append(" |\n")
    }
    return sb.toString().trimEnd()
}

@Composable
fun MarkdownTableView(table: ContentSegment.Table) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table Card Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Table · ${table.headers.size} cols",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .clickable {
                            clipboardManager.setText(AnnotatedString(formatTableAsMarkdown(table)))
                            copied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy table",
                        tint = colorScheme.tertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.tertiary
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)

            // Table Content Grid with responsive column widths
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val totalCols = table.headers.size.coerceAtLeast(1)

                // Calculate intelligent column widths
                val columnWidths = remember(table, availableWidth) {
                    when (totalCols) {
                        2 -> {
                            val w = (availableWidth - 1.dp) / 2
                            listOf(w, w)
                        }
                        3 -> {
                            val w0 = ((availableWidth - 2.dp) * 0.28f).coerceAtLeast(90.dp)
                            val remaining = (availableWidth - 2.dp - w0)
                            val w12 = (remaining / 2).coerceAtLeast(110.dp)
                            listOf(w0, w12, w12)
                        }
                        else -> {
                            table.headers.indices.map { colIdx ->
                                val headerLen = table.headers.getOrNull(colIdx)?.length ?: 0
                                val maxCellLen = table.rows.maxOfOrNull { row ->
                                    row.getOrNull(colIdx)?.length ?: 0
                                } ?: 0
                                val maxLen = maxOf(headerLen, maxCellLen)
                                when {
                                    maxLen <= 6 -> 85.dp
                                    maxLen <= 14 -> 120.dp
                                    else -> 160.dp
                                }
                            }
                        }
                    }
                }

                val totalCalculatedWidth = columnWidths.fold(0.dp) { acc, w -> acc + w } + (totalCols - 1).dp
                val needsScroll = totalCalculatedWidth > availableWidth
                val scrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (needsScroll) Modifier.horizontalScroll(scrollState) else Modifier)
                ) {
                    Column(modifier = Modifier.width(if (needsScroll) totalCalculatedWidth else availableWidth)) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.surfaceContainerHighest)
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            table.headers.forEachIndexed { colIdx, header ->
                                if (colIdx > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(colorScheme.outlineVariant)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(columnWidths[colIdx])
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)

                        // Data Rows
                        table.rows.forEachIndexed { rowIdx, row ->
                            val rowBg = if (rowIdx % 2 == 1) {
                                colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            } else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                table.headers.indices.forEach { colIdx ->
                                    if (colIdx > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(1.dp)
                                                .background(colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        )
                                    }
                                    val cell = row.getOrNull(colIdx) ?: ""
                                    Box(
                                        modifier = Modifier
                                            .width(columnWidths[colIdx])
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = buildMarkdownAnnotatedString(cell, colorScheme),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (rowIdx < table.rows.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MermaidDiagramBlock(diagram: String) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Diagram / Chart",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .clickable {
                            clipboardManager.setText(AnnotatedString(diagram))
                            copied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = colorScheme.tertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.tertiary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            ) {
                Text(
                    text = diagram,
                    style = CodeSnippetStyle,
                    color = colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun CollapsibleThoughtBlock(
    thought: String,
    isLive: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Surface(
            onClick = onToggleExpand,
            shape = RoundedCornerShape(12.dp),
            color = if (isLive) colorScheme.primary.copy(alpha = 0.08f) else colorScheme.surfaceContainerHighest,
            border = BorderStroke(
                1.dp,
                if (isLive) colorScheme.primary.copy(alpha = 0.35f) else colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = if (isLive) colorScheme.primary else colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isLive) "Thinking live…" else if (expanded) "Thought Process" else "Thought Process (Tap to expand)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isLive) colorScheme.primary else colorScheme.tertiary
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = thought,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        lineHeight = 18.sp
                    ),
                    color = colorScheme.tertiary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
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
            val parsed = remember(msg.text) { parseThoughtAndContent(msg.text) }
            var thoughtExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, top = 2.dp, bottom = 6.dp)
            ) {
                // If thought exists, render collapsible block
                if (!parsed.thought.isNullOrBlank()) {
                    CollapsibleThoughtBlock(
                        thought = parsed.thought,
                        isLive = false,
                        expanded = thoughtExpanded,
                        onToggleExpand = { thoughtExpanded = !thoughtExpanded }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val segments = remember(parsed.answer) { parseMessageContent(parsed.answer) }

                // Content Segments
                segments.forEach { segment ->
                    when (segment) {
                        is ContentSegment.Text -> {
                            MarkdownBlock(rawText = segment.text)
                        }
                        is ContentSegment.Code -> {
                            Spacer(Modifier.height(8.dp))
                            AppleCodeBlock(language = segment.language, code = segment.code)
                            Spacer(Modifier.height(8.dp))
                        }
                        is ContentSegment.Table -> {
                            Spacer(Modifier.height(8.dp))
                            MarkdownTableView(table = segment)
                            Spacer(Modifier.height(8.dp))
                        }
                        is ContentSegment.Mermaid -> {
                            Spacer(Modifier.height(8.dp))
                            MermaidDiagramBlock(diagram = segment.diagram)
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
    val parsed = remember(text) { parseThoughtAndContent(text) }
    var thoughtExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp, top = 2.dp, bottom = 6.dp)
    ) {
        if (!parsed.thought.isNullOrBlank()) {
            CollapsibleThoughtBlock(
                thought = parsed.thought,
                isLive = parsed.isThinkingActive,
                expanded = thoughtExpanded,
                onToggleExpand = { thoughtExpanded = !thoughtExpanded }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (parsed.answer.isNotEmpty()) {
            val segments = remember(parsed.answer) { parseMessageContent(parsed.answer) }
            segments.forEachIndexed { index, segment ->
                val isLast = index == segments.lastIndex
                when (segment) {
                    is ContentSegment.Text -> {
                        MarkdownBlock(rawText = if (isLast) "${segment.text} ▋" else segment.text)
                    }
                    is ContentSegment.Code -> {
                        Spacer(Modifier.height(8.dp))
                        AppleCodeBlock(language = segment.language, code = segment.code)
                        Spacer(Modifier.height(8.dp))
                    }
                    is ContentSegment.Table -> {
                        Spacer(Modifier.height(8.dp))
                        MarkdownTableView(table = segment)
                        Spacer(Modifier.height(8.dp))
                    }
                    is ContentSegment.Mermaid -> {
                        Spacer(Modifier.height(8.dp))
                        MermaidDiagramBlock(diagram = segment.diagram)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else if (parsed.thought.isNullOrBlank()) {
            Text(
                text = "▋",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ThinkingIndicator(backend: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingPulse")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Thinking",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .graphicsLayer { alpha = dot1 }
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Box(
                    Modifier
                        .size(5.dp)
                        .graphicsLayer { alpha = dot2 }
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Box(
                    Modifier
                        .size(5.dp)
                        .graphicsLayer { alpha = dot3 }
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
