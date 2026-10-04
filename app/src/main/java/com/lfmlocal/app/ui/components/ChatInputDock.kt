package com.lfmlocal.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lfmlocal.app.ui.ChatViewModel
import com.lfmlocal.app.ui.theme.*

@Composable
fun ChatInputDock(
    vm: ChatViewModel,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
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
                    onValueChange = onInputChange,
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
                                onSend(input)
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
                                onStop()
                            } else if (input.isNotBlank() && vm.modelFile != null) {
                                onSend(input)
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
