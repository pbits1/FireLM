package com.lfmlocal.app.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = CarbonCanvas,
        topBar = {
            Surface(
                color = ConsoleSlate,
                border = BorderStroke(1.dp, ConsoleBorder),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Playground",
                            tint = TextPrimary
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Column {
                        Text(
                            text = "Diagnostics & Machine Room",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Hardware Engine & Model Alignment",
                            style = TelemetryMicroStyle,
                            color = PhosphorCyanDim
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: System Directives Deck (LM Studio Style)
            WorkstationPanel(
                icon = Icons.Default.EditNote,
                iconTint = PhosphorCyan,
                title = "System Directives & Instructions",
                subtitle = "Configure optional system directives. Small on-device models (355M–1.3B) generate fastest and adhere best when directives are direct or kept concise."
            ) {
                // Enable/Disable Toggle Surface
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CarbonCanvas,
                    border = BorderStroke(1.dp, ConsoleBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable System Directives",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (vm.systemPromptEnabled) "Custom instructions will be prepended to the context."
                                else "Disabled: Model runs unconstrained (recommended for 355M).",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (vm.systemPromptEnabled) PhosphorCyan else TextMuted
                            )
                        }
                        Switch(
                            checked = vm.systemPromptEnabled,
                            onCheckedChange = { vm.updateSystemPromptEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SolarAmber,
                                uncheckedTrackColor = ConsoleHighlight
                            )
                        )
                    }
                }

                if (vm.systemPromptEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vm.systemPrompt,
                            onValueChange = { vm.updateSystemPrompt(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("System Instructions", color = TextSecondary, style = TelemetryMicroStyle) },
                            placeholder = { Text("e.g. Be direct, factual, and concise. Avoid conversational fluff.", color = TextMuted) },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = SolarAmber,
                                unfocusedBorderColor = ConsoleBorder,
                                focusedContainerColor = CarbonCanvas,
                                unfocusedContainerColor = CarbonCanvas
                            )
                        )

                        // Quick Starter Directives
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Direct" to "Be direct, factual, and concise. No conversational fluff.",
                                "Code Only" to "Write clean, idiomatic code with best practices. Output code directly without chit-chat.",
                                "Clear" to ""
                            ).forEach { (label, text) ->
                                OutlinedButton(
                                    onClick = { vm.updateSystemPrompt(text) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ConsoleBorderSubtle),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label.uppercase(),
                                        style = TelemetryMicroStyle,
                                        fontWeight = FontWeight.Bold,
                                        color = if (label == "Clear") SignalRose else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Hardware Acceleration & Compute Matrix
            WorkstationPanel(
                icon = Icons.Default.Bolt,
                iconTint = SolarAmber,
                title = "Hardware Acceleration Matrix",
                subtitle = "Select inference compute target. GPU offloads neural layers to Mali-G68 OpenCL/Vulkan; CPU leverages ARM NEON ukernels."
            ) {
                // Compute Target Selection Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isGpu = vm.computeBackend == "GPU"
                    Surface(
                        onClick = { vm.selectComputeBackend("GPU") },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGpu) InsetField else CarbonCanvas,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isGpu) MatrixEmerald else ConsoleBorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚀", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "GPU VULKAN",
                                    style = TelemetryMetricStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGpu) MatrixEmerald else TextPrimary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Mali-G68 OpenCL / Vulkan",
                                style = TelemetryMicroStyle,
                                color = if (isGpu) MatrixEmerald else TextMuted
                            )
                        }
                    }

                    val isCpu = vm.computeBackend == "CPU"
                    Surface(
                        onClick = { vm.selectComputeBackend("CPU") },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCpu) InsetField else CarbonCanvas,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCpu) SolarAmber else ConsoleBorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "CPU KLEIDIAI",
                                    style = TelemetryMetricStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCpu) SolarAmber else TextPrimary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ARM NEON DotProd ukernels",
                                style = TelemetryMicroStyle,
                                color = if (isCpu) SolarAmber else TextMuted
                            )
                        }
                    }
                }

                // GPU Offload Slider
                if (vm.computeBackend == "GPU") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CarbonCanvas, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GPU Offloaded Layers",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${vm.gpuLayers} / 32 LAYERS",
                                style = TelemetryMetricStyle,
                                fontWeight = FontWeight.Bold,
                                color = MatrixEmerald
                            )
                        }
                        Slider(
                            value = vm.gpuLayers.toFloat(),
                            onValueChange = { vm.selectGpuOffloadLayers(it.toInt()) },
                            valueRange = 1f..32f,
                            steps = 30,
                            colors = SliderDefaults.colors(
                                thumbColor = MatrixEmerald,
                                activeTrackColor = MatrixEmerald,
                                inactiveTrackColor = ConsoleBorder
                            )
                        )
                    }
                }

                // Context Window Allocator
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Context Window Allocator",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${vm.contextWindowSize} TOKENS",
                            style = TelemetryMetricStyle,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorCyan
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2048, 4096, 8192).forEach { size ->
                            val selected = vm.contextWindowSize == size
                            Surface(
                                onClick = { vm.selectContextSize(size) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) PhosphorCyanContainer else CarbonCanvas,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) PhosphorCyan else ConsoleBorderSubtle
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = when (size) {
                                            2048 -> "2K (Fastest)"
                                            4096 -> "4K (Balanced)"
                                            else -> "8K (Full)"
                                        },
                                        style = TelemetryMicroStyle,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) PhosphorCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // CPU Inference Threads Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CarbonCanvas, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    val maxCores = Runtime.getRuntime().availableProcessors()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "CPU Inference Threads",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${vm.cpuThreads} THREADS",
                            style = TelemetryMetricStyle,
                            fontWeight = FontWeight.Bold,
                            color = SolarAmber
                        )
                    }
                    Slider(
                        value = vm.cpuThreads.toFloat(),
                        onValueChange = { vm.selectThreads(it.toInt()) },
                        valueRange = 1f..maxCores.toFloat(),
                        steps = maxOf(0, maxCores - 2),
                        colors = SliderDefaults.colors(
                            thumbColor = SolarAmber,
                            activeTrackColor = SolarAmber,
                            inactiveTrackColor = ConsoleBorder
                        )
                    )
                    Text(
                        text = "Optimal: 3-4 threads pins to prime big ARM Cortex cores without causing thermal throttling.",
                        style = TelemetryMicroStyle,
                        color = TextMuted
                    )
                }

                // Sustained Performance Mode Switch
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CarbonCanvas,
                    border = BorderStroke(1.dp, ConsoleBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MatrixEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Sustained Performance Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Locks SoC governor into sustained clock frequencies via Android Window API.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = vm.sustainedPerformanceMode,
                            onCheckedChange = { vm.updateSustainedPerformance(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MatrixEmerald,
                                uncheckedTrackColor = ConsoleHighlight
                            )
                        )
                    }
                }
            }

            // Section 3: Sampling Physics & Hyperparameters
            WorkstationPanel(
                icon = Icons.Default.Tune,
                iconTint = SolarAmber,
                title = "Inference Sampling Physics",
                subtitle = "Fine-tune generation temperature, response limits, and penalty to balance precision vs creative variance."
            ) {
                // Creativity (Temperature)
                WorkstationSlider(
                    label = "Creativity (Temperature)",
                    valueStr = String.format(java.util.Locale.US, "%.2f", vm.temperature),
                    value = vm.temperature,
                    range = 0.0f..1.5f,
                    steps = 14,
                    accentColor = SolarAmber,
                    onValueChange = { vm.updateTemperature(it) }
                )

                // Max Tokens
                WorkstationSlider(
                    label = "Max Generation Tokens",
                    valueStr = "${vm.maxTokens} TOK",
                    value = vm.maxTokens.toFloat(),
                    range = 64f..2048f,
                    steps = 15,
                    accentColor = PhosphorCyan,
                    onValueChange = { vm.updateMaxTokens(it.toInt()) }
                )

                // Top-P
                WorkstationSlider(
                    label = "Nucleus Sampling (Top-P)",
                    valueStr = String.format(java.util.Locale.US, "%.2f", vm.topP),
                    value = vm.topP,
                    range = 0.1f..1.0f,
                    steps = 9,
                    accentColor = MatrixEmerald,
                    onValueChange = { vm.updateTopP(it) }
                )

                // Repetition Penalty
                WorkstationSlider(
                    label = "Repetition Penalty",
                    valueStr = String.format(java.util.Locale.US, "%.2f", vm.repeatPenalty),
                    value = vm.repeatPenalty,
                    range = 1.0f..1.5f,
                    steps = 10,
                    accentColor = SolarAmber,
                    onValueChange = { vm.updateRepeatPenalty(it) }
                )

                // Reset to Defaults Button
                OutlinedButton(
                    onClick = {
                        vm.updateTemperature(0.7f)
                        vm.updateMaxTokens(512)
                        vm.updateTopP(0.95f)
                        vm.updateRepeatPenalty(1.05f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ConsoleBorderSubtle),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(
                        text = "RESET PARAMETERS TO OPTIMAL DEFAULTS",
                        style = TelemetryMicroStyle,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }

            // Section 4: Machine Room & Hardware Telemetry HUD
            WorkstationPanel(
                icon = Icons.Default.Memory,
                iconTint = PhosphorCyanDim,
                title = "Hardware & Runtime Telemetry HUD",
                subtitle = "Real-time hardware statistics and on-device execution telemetry."
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CarbonCanvas,
                    border = BorderStroke(1.dp, ConsoleBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryHUDItem("Device Memory", vm.getDeviceRamInfo(), MatrixEmerald)
                        TelemetryHUDItem("CPU Topology", "${Runtime.getRuntime().availableProcessors()} Cores (big.LITTLE)", TextPrimary)
                        TelemetryHUDItem(
                            "Inference Pipeline",
                            if (vm.computeBackend == "GPU") "Vulkan GPU (${vm.gpuLayers} Layers Offloaded)" else "Arm KleidiAI ukernels",
                            if (vm.computeBackend == "GPU") MatrixEmerald else SolarAmber
                        )
                        TelemetryHUDItem("Prefix Cache", "Warm Prefix KV-Cache Active", MatrixEmerald)
                        TelemetryHUDItem("Active Context", "${vm.activeContextTokens} Tokens Allocated", TextPrimary)
                        TelemetryHUDItem("SoC Governor", if (vm.sustainedPerformanceMode) "Sustained Peak Boost" else "Dynamic", MatrixEmerald)
                        TelemetryHUDItem("Target ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a", TextSecondary)
                        TelemetryHUDItem("Network Boundary", "Air-Gapped (Zero Telemetry)", PhosphorCyan)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WorkstationPanel(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = InsetField,
        border = BorderStroke(1.dp, ConsoleBorder),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 17.sp
            )

            content()
        }
    }
}

@Composable
private fun WorkstationSlider(
    label: String,
    valueStr: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonCanvas, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
            Text(
                text = valueStr,
                style = TelemetryMetricStyle,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = ConsoleBorder
            )
        )
    }
}

@Composable
private fun TelemetryHUDItem(label: String, value: String, valueTint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TelemetryMicroStyle,
            color = TextMuted
        )
        Text(
            text = value,
            style = TelemetryMetricStyle.copy(fontSize = 10.sp),
            fontWeight = FontWeight.SemiBold,
            color = valueTint
        )
    }
}
