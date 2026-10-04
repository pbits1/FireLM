package com.lfmlocal.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.ChatViewModel
import com.lfmlocal.app.ui.theme.*

@Composable
fun EmptyChatHeroState(
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
            textAlign = TextAlign.Center,
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
