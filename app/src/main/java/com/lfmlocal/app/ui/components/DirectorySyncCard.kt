package com.lfmlocal.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfmlocal.app.ui.theme.*

@Composable
fun DirectorySyncCard(
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = InsetField,
        border = BorderStroke(1.dp, ConsoleBorder),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PhosphorCyanContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = PhosphorCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "NEURAL STORAGE DIRECTORY",
                        style = TelemetryMicroStyle,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "/Download/FireLM",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MatrixEmeraldContainer,
                    border = BorderStroke(1.dp, MatrixEmerald.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "ZERO-COPY MMAP",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = TelemetryMicroStyle.copy(fontSize = 8.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = MatrixEmerald
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Drop any .gguf models or subfolders directly into /Download/FireLM via USB, file manager, or browser. FireLM automatically detects and memory-maps them with zero duplicate storage.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSyncClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConsoleHighlight,
                    contentColor = PhosphorCyan
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = PhosphorCyan
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "SCANNING /Download/FireLM…",
                        style = TelemetryMetricStyle,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "SYNC & RESCAN DIRECTORY",
                        style = TelemetryMetricStyle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
