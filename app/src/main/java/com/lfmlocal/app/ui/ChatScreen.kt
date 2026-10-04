package com.lfmlocal.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Auto-scroll to latest message
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.size - 1)
        }
    }

    Scaffold(
        containerColor = ObsidianCanvas,
        topBar = {
            Surface(
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorderSubtle),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clickable Model Pill (Opens Model Library)
                    Surface(
                        onClick = onOpenModels,
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Active status dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            vm.isModelLoading -> SunsetAmber
                                            vm.isModelLoaded -> HyperEmerald
                                            vm.downloadFraction != null -> ElectricCyan
                                            else -> TextMuted
                                        }
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    vm.selectedModel.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when {
                                        vm.isModelLoading -> {
                                            Text(
                                                "Allocating VRAM & loading…",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = SunsetAmber
                                            )
                                        }
                                        vm.isModelLoaded -> {
                                            val backendText = if (vm.computeBackend == "GPU") "⚡ GPU (${vm.gpuLayers}L)" else "⚡ CPU"
                                            Text(
                                                "$backendText · ${vm.activeContextTokens} ctx",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (vm.computeBackend == "GPU") ElectricCyan else SunsetAmber
                                            )
                                            Text(
                                                " · 🟢 In RAM",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = HyperEmerald
                                            )
                                        }
                                        vm.modelFile != null -> {
                                            Text(
                                                "Unloaded from RAM",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = TextMuted
                                            )
                                        }
                                        else -> {
                                            Text(
                                                "Not downloaded",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = SunsetAmber
                                            )
                                        }
                                    }
                                }
                            }
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Switch Model",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Dedicated Eject / Load Chip Button
                    if (vm.isModelLoaded) {
                        Surface(
                            onClick = { vm.ejectModel() },
                            shape = RoundedCornerShape(14.dp),
                            color = RadiantRose.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, RadiantRose.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = "Eject",
                                    tint = RadiantRose,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Eject",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RadiantRose
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    } else if (vm.modelFile != null && !vm.isModelLoading) {
                        Surface(
                            onClick = { vm.loadCurrentModel() },
                            shape = RoundedCornerShape(14.dp),
                            color = HyperEmeraldContainer,
                            border = BorderStroke(1.dp, HyperEmerald.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = "Load",
                                    tint = HyperEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Load",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = HyperEmerald
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    } else if (vm.isModelLoading) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SunsetAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SunsetAmber.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = SunsetAmber
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // Action buttons
                    IconButton(
                        onClick = { vm.clearChat() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // LM Studio Memory State & Ejection Prompt
                val isModelEjected = vm.modelFile != null && !vm.isModelLoaded && !vm.isModelLoading
                if (isModelEjected && vm.downloadFraction == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SunsetAmber)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Model unloaded (0 MB RAM used)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { vm.loadCurrentModel() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = HyperEmerald, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Load to RAM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = HyperEmerald)
                            }
                        }
                    }
                } else if (vm.isModelLoading) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = SunsetAmber
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Allocating VRAM & loading into GPU…",
                                style = MaterialTheme.typography.labelSmall,
                                color = SunsetAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Download progress bar if downloading
                if (vm.downloadFraction != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = ElectricCyan
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                vm.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyanDim,
                                maxLines = 1
                            )
                        }
                        TextButton(
                            onClick = { vm.cancelDownload() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall, color = RadiantRose)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { vm.downloadFraction ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ElectricCyan,
                        trackColor = ObsidianSurfaceHighlight
                    )
                }

                // Floating capsule input bar
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    when {
                                        vm.modelFile == null -> "Select or download model to start…"
                                        vm.isModelLoading -> "Allocating VRAM and initializing model…"
                                        !vm.isModelLoaded -> "Ask anything (will auto-load into memory)…"
                                        else -> "Ask anything (100% on-device)…"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            },
                            enabled = !vm.busy && vm.modelFile != null,
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (input.isNotBlank() && vm.modelFile != null && !vm.busy) {
                                        vm.send(input)
                                        input = ""
                                        focusManager.clearFocus()
                                    }
                                }
                            )
                        )

                        Spacer(Modifier.width(6.dp))

                        // Glowing Action FAB (Send / Stop)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (vm.busy) Brush.linearGradient(listOf(RadiantRose, Color(0xFFBE123C)))
                                    else if (input.isNotBlank() && vm.modelFile != null) {
                                        Brush.linearGradient(listOf(ElectricCyan, ElectricCyanDim))
                                    } else {
                                        Brush.linearGradient(listOf(ObsidianSurfaceHighlight, ObsidianSurfaceElevated))
                                    }
                                )
                                .clickable(
                                    enabled = vm.busy || (input.isNotBlank() && vm.modelFile != null)
                                ) {
                                    if (vm.busy) {
                                        vm.stop()
                                    } else if (input.isNotBlank() && vm.modelFile != null) {
                                        vm.send(input)
                                        input = ""
                                        focusManager.clearFocus()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(targetState = vm.busy, label = "send_stop_icon") { isBusy ->
                                if (isBusy) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "Stop Generation",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Prompt",
                                        tint = if (input.isNotBlank() && vm.modelFile != null) OnElectricCyan else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        if (vm.messages.isEmpty() && vm.streamingText.isEmpty()) {
            EmptyChatHeroState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                vm = vm,
                onOpenModels = onOpenModels,
                onOpenSettings = onOpenSettings,
                onSelectSuggestion = { suggestion ->
                    if (vm.modelFile != null) {
                        vm.send(suggestion)
                    } else {
                        input = suggestion
                    }
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(vm.messages, key = { it.id }) { msg ->
                    ChatBubble(msg = msg, backend = vm.computeBackend)
                }

                if (vm.streamingText.isNotEmpty()) {
                    item(key = "streaming") {
                        StreamingBubble(text = vm.streamingText, backend = vm.computeBackend)
                    }
                } else if (vm.busy) {
                    item(key = "thinking") {
                        ThinkingIndicator(backend = vm.computeBackend)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatHeroState(
    modifier: Modifier = Modifier,
    vm: ChatViewModel,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectSuggestion: (String) -> Unit
) {
    data class SuggestionItem(val title: String, val category: String, val icon: ImageVector, val tint: Color)

    val suggestions = listOf(
        SuggestionItem("Explain quantum computing simply", "Science & Systems", Icons.Default.Biotech, ElectricCyan),
        SuggestionItem("Write a Kotlin Flow retry loop", "Code Architecture", Icons.Default.Code, HyperEmerald),
        SuggestionItem("Draft a polite email declining a sync", "Professional Writing", Icons.Default.EditNote, SunsetAmber),
        SuggestionItem("Brainstorm 3 viral sci-fi concepts", "Creative Thinking", Icons.Default.Lightbulb, AuraViolet)
    )

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Hero Icon
        Surface(
            shape = CircleShape,
            color = ElectricCyanContainer,
            border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.5f)),
            modifier = Modifier.size(72.dp),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "FireLM Engine",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "100% On-Device Neural Compute • Zero Cloud Telemetry\nComplete Privacy in Airplane Mode",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        // LM Studio Telemetry Badges (Memory Residency & System Prompt Mode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // Residency Status Chip
            Surface(
                onClick = {
                    if (vm.isModelLoaded) vm.ejectModel()
                    else if (vm.modelFile != null && !vm.isModelLoading) vm.loadCurrentModel()
                    else onOpenModels()
                },
                shape = RoundedCornerShape(20.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    vm.isModelLoading -> SunsetAmber
                                    vm.isModelLoaded -> HyperEmerald
                                    vm.modelFile != null -> TextMuted
                                    else -> SunsetAmber
                                }
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            vm.isModelLoading -> "VRAM Loading…"
                            vm.isModelLoaded -> "Resident in RAM (${vm.computeBackend})"
                            vm.modelFile != null -> "Unloaded (0 MB RAM)"
                            else -> "No Model"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            vm.isModelLoaded -> HyperEmerald
                            vm.isModelLoading -> SunsetAmber
                            else -> TextSecondary
                        }
                    )
                }
            }

            // System Prompt Mode Chip
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(20.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (vm.systemPromptEnabled) Icons.Default.Tune else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (vm.systemPromptEnabled) ElectricCyan else HyperEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (vm.systemPromptEnabled) "System Prompt: ON" else "Raw SLM Mode (Optimal)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (vm.systemPromptEnabled) ElectricCyan else HyperEmerald
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 4 Suggestion Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEach { item ->
                Surface(
                    onClick = { onSelectSuggestion(item.title) },
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = item.tint.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = item.tint
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg, backend: String) {
    val isUser = msg.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val timeStr = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            // User Message: Sleek asymmetric gradient bubble
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(UserBubbleTop, UserBubbleBottom)
                        )
                    )
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        } else {
            // Assistant Message: Obsidian elevated card with glowing telemetry chip
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder),
                shadowElevation = 3.dp,
                modifier = Modifier.widthIn(max = 350.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyanContainer,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Assistant",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (backend == "GPU") HyperEmeraldContainer else ObsidianSurfaceHighlight
                        ) {
                            Text(
                                if (backend == "GPU") "Vulkan GPU" else "KleidiAI CPU",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (backend == "GPU") HyperEmerald else SunsetAmber,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextMuted
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Message text
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Telemetry & Copy action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (msg.speedStats != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianCanvas,
                                border = BorderStroke(1.dp, ObsidianBorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = HyperEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        msg.speedStats,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = HyperEmerald,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(msg.text)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                modifier = Modifier.size(15.dp),
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, backend: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = ObsidianSurfaceElevated,
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            shadowElevation = 4.dp,
            modifier = Modifier.widthIn(max = 350.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = ElectricCyanContainer,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Assistant (Streaming…)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (backend == "GPU") HyperEmeraldContainer else ObsidianSurfaceHighlight
                    ) {
                        Text(
                            if (backend == "GPU") "Vulkan GPU" else "KleidiAI CPU",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (backend == "GPU") HyperEmerald else SunsetAmber,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "$text ▊",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(backend: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurfaceElevated,
            border = BorderStroke(1.dp, ObsidianBorderSubtle),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = ElectricCyan
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (backend == "GPU") "Synthesizing on Mali GPU via Vulkan…" else "Synthesizing with CPU KleidiAI ukernels…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
