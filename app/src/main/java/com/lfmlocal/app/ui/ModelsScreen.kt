package com.lfmlocal.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lfmlocal.app.ui.components.DirectorySyncCard
import com.lfmlocal.app.ui.components.ModelCard
import com.lfmlocal.app.ui.components.StoragePermissionBanner
import com.lfmlocal.app.ui.theme.*
import com.lfmlocal.core.download.ModelDownloader
import com.lfmlocal.core.model.LfmModel
import com.lfmlocal.core.model.ModelCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val storageMb = remember { ModelDownloader.getAvailableStorageMb(ctx) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.checkStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        vm.checkStoragePermission()
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    ctx.startActivity(intent)
                }
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileNameFromUri(ctx, it) ?: "imported_model.gguf"
            vm.importCustomGguf(it, name)
        }
    }

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

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Model Management Console",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "llama.cpp Engine · Mali GPU Acceleration",
                            style = TelemetryMicroStyle,
                            color = PhosphorCyanDim
                        )
                    }

                    // Import Custom GGUF Button
                    Surface(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(8.dp),
                        color = InsetField,
                        border = BorderStroke(1.dp, ConsoleBorderStrong)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = PhosphorCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "IMPORT",
                                style = TelemetryMicroStyle,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorCyan
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Hardware RAM & Storage HUD Card
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = InsetField,
                    border = BorderStroke(1.dp, ConsoleBorder),
                    shadowElevation = 2.dp
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
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = PhosphorCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "SYSTEM MEMORY & STORAGE HUD",
                                    style = TelemetryMicroStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = "${String.format("%,d", storageMb)} MB Free Storage",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            // VRAM Residency readout
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (vm.isModelLoaded) MatrixEmeraldContainer else ConsoleHighlight,
                                border = BorderStroke(1.dp, if (vm.isModelLoaded) MatrixEmerald.copy(alpha = 0.4f) else ConsoleBorderSubtle)
                            ) {
                                Text(
                                    text = if (vm.isModelLoaded) "VRAM ACTIVE" else "0 MB VRAM",
                                    style = TelemetryMicroStyle.copy(fontSize = 8.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (vm.isModelLoaded) MatrixEmerald else TextMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // RAM Details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device Memory:",
                                style = TelemetryMicroStyle,
                                color = TextSecondary
                            )
                            Text(
                                text = vm.getDeviceRamInfo(),
                                style = TelemetryMetricStyle,
                                color = MatrixEmerald
                            )
                        }
                    }
                }
            }

            // Storage Permission Warning Banner if not granted
            if (!vm.hasStoragePermission) {
                item {
                    StoragePermissionBanner(
                        onRequestAccess = { requestStorageAccess() }
                    )
                }
            }

            // Direct Storage Directory & Rescan Deck
            item {
                DirectorySyncCard(
                    isSyncing = vm.isSyncingModels,
                    onSyncClick = {
                        if (!vm.hasStoragePermission) {
                            requestStorageAccess()
                        } else {
                            vm.refreshLocalState()
                        }
                    }
                )
            }

            // Filter Tabs Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL", "LOCAL / GGUF", "RECOMMENDED").forEach { filterTag ->
                        val isFilterSelected = selectedFilter == filterTag
                        Surface(
                            onClick = { selectedFilter = filterTag },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFilterSelected) InsetField else ConsoleSlate,
                            border = BorderStroke(
                                1.dp,
                                if (isFilterSelected) SolarAmber else ConsoleBorderSubtle
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = filterTag,
                                    style = TelemetryMicroStyle,
                                    fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isFilterSelected) SolarAmber else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Custom & Local Models Header
            if (selectedFilter != "RECOMMENDED") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = WorkstationViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "CUSTOM & LOCAL MODELS (/Download/FireLM)",
                            style = TelemetryMicroStyle,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = InsetField,
                            border = BorderStroke(1.dp, ConsoleBorderSubtle)
                        ) {
                            Text(
                                text = "${vm.customModels.size} FOUND",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TelemetryMicroStyle.copy(fontSize = 9.sp),
                                color = if (vm.customModels.isNotEmpty()) MatrixEmerald else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (vm.customModels.isNotEmpty()) {
                    items(vm.customModels, key = { it.id }) { custom ->
                        ModelCard(
                            model = custom,
                            vm = vm,
                            ctx = ctx
                        )
                    }
                } else {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ConsoleSlate,
                            border = BorderStroke(1.dp, ConsoleBorderSubtle)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (!vm.hasStoragePermission)
                                        "Storage permission required to scan /Download/FireLM.\nTap 'Grant Storage Access' above."
                                    else
                                        "No custom models detected in /Download/FireLM yet.\nDrop any .gguf file into Download/FireLM and tap Sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Catalog Presets Header
            if (selectedFilter != "LOCAL / GGUF") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PhosphorCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "OPTIMIZED LIQUID AI MODELS",
                            style = TelemetryMicroStyle,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                val catalogModels = if (selectedFilter == "RECOMMENDED") {
                    ModelCatalog.models.filter { it.recommended }
                } else {
                    ModelCatalog.models
                }

                items(catalogModels, key = { it.id }) { m ->
                    ModelCard(
                        model = m,
                        vm = vm,
                        ctx = ctx
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) return cursor.getString(index)
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}
