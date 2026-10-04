package com.lfmlocal.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.ChatViewModel
import com.lfmlocal.app.ui.theme.*

@Composable
fun ChatTopBar(
    vm: ChatViewModel,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
