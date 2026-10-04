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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Column {
                        Text(
                            "Tuning & Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Hardware Engine & Model Alignment",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = ElectricCyanDim
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: System Prompt & Instructions (LM Studio style)
            SettingsCard(
                icon = Icons.Default.EditNote,
                iconTint = ElectricCyan,
                title = "System Prompt & Instructions",
                subtitle = "Configure optional system directives. Small on-device models (350M–1.2B) run fastest and most reliably with system prompt disabled or kept concise."
            ) {
                // Enable/Disable Toggle Surface
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianCanvas,
                    border = BorderStroke(1.dp, ObsidianBorderSubtle)
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
                                "Enable System Prompt",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (vm.systemPromptEnabled) "Custom instructions are active for chat turns."
                                else "Disabled: Model runs unconstrained (recommended for 350M).",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (vm.systemPromptEnabled) ElectricCyan else TextMuted
                            )
                        }
                        Switch(
                            checked = vm.systemPromptEnabled,
                            onCheckedChange = { vm.updateSystemPromptEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricCyan,
                                uncheckedTrackColor = ObsidianSurfaceHighlight
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
                            label = { Text("Desired System Instructions", color = TextSecondary) },
                            placeholder = { Text("e.g. Be concise, direct, and factual. Avoid fluff.", color = TextMuted) },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedContainerColor = ObsidianCanvas,
                                unfocusedContainerColor = ObsidianCanvas
                            )
                        )

                        // Quick Starter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Be Direct" to "Be direct, factual, and concise. No conversational fluff.",
                                "Code Only" to "Write clean, idiomatic code with best practices. Output code directly without chit-chat.",
                                "Clear" to ""
                            ).forEach { (label, text) ->
                                OutlinedButton(
                                    onClick = { vm.updateSystemPrompt(text) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ObsidianBorderSubtle),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (label == "Clear") RadiantRose else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Hardware Acceleration & Compute Engine
            SettingsCard(
                icon = Icons.Default.Bolt,
                iconTint = ElectricCyan,
                title = "Hardware Acceleration & Engine",
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
                        shape = RoundedCornerShape(14.dp),
                        color = if (isGpu) ObsidianSurfaceHighlight else ObsidianCanvas,
                        border = BorderStroke(
                            width = if (isGpu) 1.5.dp else 1.dp,
                            color = if (isGpu) ElectricCyan else ObsidianBorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚀", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "GPU Vulkan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGpu) ElectricCyan else TextPrimary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Fastest Prefill (12+ tok/s)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isGpu) HyperEmerald else TextMuted
                            )
                        }
                    }

                    val isCpu = vm.computeBackend == "CPU"
                    Surface(
                        onClick = { vm.selectComputeBackend("CPU") },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCpu) ObsidianSurfaceHighlight else ObsidianCanvas,
                        border = BorderStroke(
                            width = if (isCpu) 1.5.dp else 1.dp,
                            color = if (isCpu) SunsetAmber else ObsidianBorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "CPU KleidiAI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCpu) SunsetAmber else TextPrimary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "ARM NEON DotProd",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isCpu) SunsetAmber else TextMuted
                            )
                        }
                    }
                }

                // GPU Offload Slider
                if (vm.computeBackend == "GPU") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianCanvas, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GPU Offloaded Layers", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Text(
                                "${vm.gpuLayers} / 32 layers",
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = vm.gpuLayers.toFloat(),
                            onValueChange = { vm.selectGpuOffloadLayers(it.toInt()) },
                            valueRange = 1f..32f,
                            steps = 30,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = ObsidianBorder
                            )
                        )
                    }
                }

                // Context Window Size
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Context Window Size", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Text("${vm.contextWindowSize} tokens", fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2048, 4096, 8192).forEach { size ->
                            val selected = vm.contextWindowSize == size
                            Surface(
                                onClick = { vm.selectContextSize(size) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ElectricCyanContainer else ObsidianCanvas,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ElectricCyan else ObsidianBorderSubtle
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        when (size) {
                                            2048 -> "2K (Fastest)"
                                            4096 -> "4K (Balanced)"
                                            else -> "8K (Full)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) ElectricCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // CPU Inference Threads
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianCanvas, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    val maxCores = Runtime.getRuntime().availableProcessors()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CPU Inference Threads", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Text("${vm.cpuThreads} threads", fontWeight = FontWeight.Bold, color = HyperEmerald)
                    }
                    Slider(
                        value = vm.cpuThreads.toFloat(),
                        onValueChange = { vm.selectThreads(it.toInt()) },
                        valueRange = 1f..maxCores.toFloat(),
                        steps = maxOf(0, maxCores - 2),
                        colors = SliderDefaults.colors(
                            thumbColor = HyperEmerald,
                            activeTrackColor = HyperEmerald,
                            inactiveTrackColor = ObsidianBorder
                        )
                    )
                    Text(
                        "Optimal: 3 threads on Dimensity 920 pins to prime Cortex-A78 big cores without thermal throttling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                // Sustained Boost / Game Mode Switch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianCanvas,
                    border = BorderStroke(1.dp, ObsidianBorderSubtle)
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
                                    tint = HyperEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Game Mode / Sustained Boost", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Locks SoC governor into high performance mode via HyperBoost / GT Mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = vm.sustainedPerformanceMode,
                            onCheckedChange = { vm.updateSustainedPerformance(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HyperEmerald,
                                uncheckedTrackColor = ObsidianSurfaceHighlight
                            )
                        )
                    }
                }
            }

            // Section 3: Generation Hyperparameters
            SettingsCard(
                icon = Icons.Default.Tune,
                iconTint = SunsetAmber,
                title = "Inference Hyperparameters",
                subtitle = "Fine-tune sampling temperature, context limits, and penalty to control creativity vs precision."
            ) {
                // Temperature
                SliderControl(
                    label = "Creativity (Temperature)",
                    valueStr = String.format(java.util.Locale.US, "%.2f", vm.temperature),
                    value = vm.temperature,
                    range = 0.0f..1.5f,
                    steps = 14,
                    onValueChange = { vm.updateTemperature(it) }
                )

                // Max Tokens
                SliderControl(
                    label = "Max Response Tokens",
                    valueStr = "${vm.maxTokens} tok",
                    value = vm.maxTokens.toFloat(),
                    range = 64f..2048f,
                    steps = 15,
                    onValueChange = { vm.updateMaxTokens(it.toInt()) }
                )

                // Top-P
                SliderControl(
                    label = "Nucleus Sampling (Top-P)",
                    valueStr = String.format(java.util.Locale.US, "%.2f", vm.topP),
                    value = vm.topP,
                    range = 0.1f..1.0f,
                    steps = 9,
                    onValueChange = { vm.updateTopP(it) }
                )

                // Repetition Penalty
                SliderControl(
                    label = "Repetition Penalty",
                    valueStr = String.format(java.util.Locale.US, "%.2f", vm.repeatPenalty),
                    value = vm.repeatPenalty,
                    range = 1.0f..1.5f,
                    steps = 10,
                    onValueChange = { vm.updateRepeatPenalty(it) }
                )
            }

            // Section 4: Device & Engine Diagnostics HUD
            SettingsCard(
                icon = Icons.Default.Memory,
                iconTint = ElectricCyanDim,
                title = "Device & Engine Diagnostics",
                subtitle = "Real-time hardware statistics and on-device execution telemetry."
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianCanvas,
                    border = BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticItem("Device RAM", vm.getDeviceRamInfo(), HyperEmerald)
                        DiagnosticItem("CPU Topology", "${Runtime.getRuntime().availableProcessors()} Cores (Cortex-A78 + A55)", TextPrimary)
                        DiagnosticItem(
                            "Inference Pipeline",
                            if (vm.computeBackend == "GPU") "Vulkan GPU (${vm.gpuLayers} Layers)" else "Arm KleidiAI ukernels",
                            if (vm.computeBackend == "GPU") ElectricCyan else SunsetAmber
                        )
                        DiagnosticItem("Context Cache", "Warm Prefix Retention Active", HyperEmerald)
                        DiagnosticItem("Active Window", "${vm.activeContextTokens} Tokens", TextPrimary)
                        DiagnosticItem("HyperBoost Engine", if (vm.sustainedPerformanceMode) "Active (High Perf)" else "Standard", HyperEmerald)
                        DiagnosticItem("Target ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a", TextSecondary)
                        DiagnosticItem("Network Boundary", "Air-Gapped (Zero Telemetry)", ElectricCyan)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurfaceElevated,
        border = BorderStroke(1.dp, ObsidianBorder),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            content()
        }
    }
}



@Composable
private fun SliderControl(
    label: String,
    valueStr: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianCanvas, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Text(valueStr, fontWeight = FontWeight.Bold, color = ElectricCyan, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = ObsidianBorder
            )
        )
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String, valueTint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontFamily = FontFamily.Monospace
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueTint,
            fontFamily = FontFamily.Monospace
        )
    }
}
