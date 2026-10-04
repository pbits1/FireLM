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
    val isCurrentDownloading = vm.isModelDownloading(model.id)
    val downloadProgress = vm.getDownloadProgress(model.id) ?: 0f

    val cardBorderColor = when {
        isSelected && vm.isModelLoaded -> MatrixEmerald.copy(alpha = 0.6f)
        isSelected && vm.isModelLoading -> SolarAmber.copy(alpha = 0.6f)
        isSelected -> PhosphorCyan.copy(alpha = 0.6f)
        else -> ConsoleBorderSubtle
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) InsetField else ConsoleSlate,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title & VRAM residency status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = model.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (isSelected) {
                    val badgeColor = when {
                        vm.isModelLoaded -> MatrixEmerald
                        vm.isModelLoading -> SolarAmber
                        isDownloaded -> TextSecondary
                        else -> PhosphorCyan
                    }
                    val badgeBg = when {
                        vm.isModelLoaded -> MatrixEmeraldContainer
                        vm.isModelLoading -> SolarAmberContainer
                        isDownloaded -> ConsoleHighlight
                        else -> PhosphorCyanContainer
                    }
                    val badgeText = when {
                        vm.isModelLoaded -> "IN VRAM (${vm.computeBackend})"
                        vm.isModelLoading -> "LOADING…"
                        isDownloaded -> "UNLOADED"
                        else -> "SELECTED"
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = badgeText,
                                style = TelemetryMicroStyle,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Specs badges row (Architecture, Size, RAM, Quantization)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (model.recommended) {
                    SpecBadge(label = "★ RECOMMENDED", tint = MatrixEmerald)
                }
                SpecBadge(label = "~${model.sizeMb} MB", tint = PhosphorCyanDim)
                SpecBadge(label = "RAM: ≥${model.minRamMb} MB", tint = TextSecondary)
                val quantRegex = Regex("""(?i)([qQ][0-9]_[A-Za-z0-9_]+)""")
                val match = quantRegex.find(model.file)
                val quant = match?.value?.uppercase() ?: if (model.file.endsWith(".gguf", ignoreCase = true)) "GGUF" else "MODEL"
                SpecBadge(label = quant, tint = SolarAmber)
                if (model.isCustom) {
                    SpecBadge(label = "CUSTOM", tint = WorkstationViolet)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 17.sp
            )

            // Live download progress if downloading
            if (isCurrentDownloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PhosphorCyan,
                    trackColor = ConsoleHighlight
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DOWNLOADING: ${(downloadProgress * 100).toInt()}%",
                        style = TelemetryMicroStyle,
                        color = PhosphorCyan
                    )
                    TextButton(
                        onClick = { vm.cancelDownload(model.id) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            style = TelemetryMicroStyle,
                            fontWeight = FontWeight.Bold,
                            color = SignalRose
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isDownloaded && !model.isCustom) {
                    // Download Action Button
                    Button(
                        onClick = {
                            vm.startDownload(model)
                        },
                        enabled = !vm.isDownloading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SolarAmber,
                            contentColor = OnSolarAmber
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "DOWNLOAD (~${model.sizeMb} MB)",
                            style = TelemetryMetricStyle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Downloaded Model
                    if (isSelected) {
                        if (vm.isModelLoaded) {
                            OutlinedButton(
                                onClick = { vm.ejectModel() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRose),
                                border = BorderStroke(1.dp, SignalRose.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "EJECT FROM VRAM",
                                    style = TelemetryMetricStyle,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (vm.isModelLoading) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = InsetField,
                                    disabledContentColor = SolarAmber
                                )
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = SolarAmber)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "ALLOCATING VRAM…",
                                    style = TelemetryMetricStyle,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { vm.loadCurrentModel() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MatrixEmerald,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "LOAD INTO VRAM",
                                    style = TelemetryMetricStyle,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { vm.deleteModel(model) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Model Weights",
                                tint = SignalRose.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Downloaded but not active
                        Button(
                            onClick = { vm.selectModel(model) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorCyanContainer,
                                contentColor = PhosphorCyan
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "SELECT & INITIALIZE",
                                style = TelemetryMetricStyle,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { vm.deleteModel(model) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Model Weights",
                                tint = SignalRose.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
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
        shape = RoundedCornerShape(4.dp),
        color = CarbonCanvas,
        border = BorderStroke(1.dp, ConsoleBorderSubtle)
    ) {
        Text(
            text = label,
            style = TelemetryMicroStyle,
            color = tint,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
