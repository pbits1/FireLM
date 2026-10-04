package com.lfmlocal.app.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.ChatViewModel
import com.lfmlocal.app.ui.theme.*
import com.lfmlocal.core.model.LfmModel

@Composable
fun ModelCard(
    model: LfmModel,
    vm: ChatViewModel,
    ctx: Context
) {
    val isSelected = vm.selectedModel.id == model.id
    val isDownloaded = vm.isModelDownloaded(model)
    val isCurrentDownloading = isSelected && vm.downloadFraction != null

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) ObsidianSurfaceElevated else ObsidianSurface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) ElectricCyan else ObsidianBorderSubtle
        ),
        shadowElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title & Active status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (isSelected) {
                    val badgeColor = when {
                        vm.isModelLoaded -> HyperEmerald
                        vm.isModelLoading -> SunsetAmber
                        isDownloaded -> TextSecondary
                        else -> ElectricCyan
                    }
                    val badgeBg = when {
                        vm.isModelLoaded -> HyperEmeraldContainer
                        vm.isModelLoading -> SunsetAmber.copy(alpha = 0.2f)
                        isDownloaded -> ObsidianSurfaceHighlight
                        else -> ElectricCyanContainer
                    }
                    val badgeText = when {
                        vm.isModelLoaded -> "IN VRAM (${vm.computeBackend})"
                        vm.isModelLoading -> "LOADING…"
                        isDownloaded -> "UNLOADED"
                        else -> "ACTIVE"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Specs badge chips row (Size, RAM, Quant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (model.recommended) {
                    SpecBadge(label = "★ Recommended", tint = HyperEmerald)
                }
                SpecBadge(label = "~${model.sizeMb} MB", tint = ElectricCyanDim)
                SpecBadge(label = "RAM: ≥${model.minRamMb} MB", tint = TextSecondary)
                val quantRegex = Regex("""(?i)([qQ][0-9]_[A-Za-z0-9_]+)""")
                val match = quantRegex.find(model.file)
                val quant = match?.value?.uppercase() ?: if (model.file.endsWith(".gguf", ignoreCase = true)) "GGUF" else "MODEL"
                SpecBadge(label = quant, tint = AuraViolet)
                if (model.isCustom) {
                    SpecBadge(label = "Custom", tint = SunsetAmber)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Description
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            // Live download progress if downloading
            if (isCurrentDownloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { vm.downloadFraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ElectricCyan,
                    trackColor = ObsidianSurfaceHighlight
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Downloading: ${((vm.downloadFraction ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    TextButton(
                        onClick = { vm.cancelDownload() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Cancel", color = RadiantRose, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Interactive Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isDownloaded && !model.isCustom) {
                    // Download button
                    Button(
                        onClick = {
                            if (!isSelected) vm.selectModel(model)
                            vm.startDownload()
                        },
                        enabled = vm.downloadFraction == null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OnElectricCyan
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download (~${model.sizeMb}MB)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Model is downloaded on device
                    if (isSelected) {
                        if (vm.isModelLoaded) {
                            OutlinedButton(
                                onClick = { vm.ejectModel() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RadiantRose),
                                border = BorderStroke(1.dp, RadiantRose.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Eject from RAM", fontWeight = FontWeight.Bold)
                            }
                        } else if (vm.isModelLoading) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = ObsidianSurfaceHighlight,
                                    disabledContentColor = SunsetAmber
                                )
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SunsetAmber)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading into GPU…", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { vm.loadCurrentModel() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HyperEmerald,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Load into Memory", fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { vm.deleteModel(model) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Model", tint = RadiantRose.copy(alpha = 0.8f))
                        }
                    } else {
                        // Downloaded but not currently selected
                        Button(
                            onClick = { vm.selectModel(model) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyanContainer,
                                contentColor = ElectricCyan
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select & Load", fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { vm.deleteModel(model) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Model", tint = RadiantRose.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecBadge(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ObsidianCanvas,
        border = BorderStroke(1.dp, ObsidianBorderSubtle)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
