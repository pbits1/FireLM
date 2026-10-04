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
import com.lfmlocal.app.data.LfmModel
import com.lfmlocal.app.data.ModelCatalog
import com.lfmlocal.app.download.ModelDownloader
import com.lfmlocal.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val storageMb = remember { ModelDownloader.getAvailableStorageMb(ctx) }

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
                            contentDescription = "Back to Chat",
                            tint = TextPrimary
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            "Model Library",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "llama.cpp Native Engine · Mali GPU Acceleration",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = ElectricCyanDim
                        )
                    }

                    // Import Button
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ElectricCyan
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import .gguf", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Hardware HUD Overview Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyanContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                "DEVICE STORAGE & MEMORY",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${String.format("%,d", storageMb)} MB Free Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "RAM: ${vm.getDeviceRamInfo()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = HyperEmerald
                            )
                        }
                    }
                }
            }

            // Storage Permission Warning Banner if not granted
            if (!vm.hasStoragePermission) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.5.dp, SunsetAmber),
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = SunsetAmber.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = SunsetAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "STORAGE ACCESS REQUIRED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = SunsetAmber
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Permission Needed for /Download/FireLM",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                "Android requires 'All files access' permission so FireLM can detect and run your .gguf models from the /Download/FireLM folder with native zero-copy speed.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(14.dp))

                            Button(
                                onClick = { requestStorageAccess() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SunsetAmber,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Grant Storage Access in Settings", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Models Storage Directory & Dynamic Sync Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricCyanContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    "MODELS DIRECTORY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "/Download/FireLM",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianCanvas,
                                border = BorderStroke(1.dp, ObsidianBorderSubtle)
                            ) {
                                Text(
                                    "Zero-Copy Direct",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = HyperEmerald
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Drop .gguf models or subfolders directly into /Download/FireLM via USB, file manager, or browser. FireLM automatically detects and runs them with zero duplicate storage.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextMuted,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (!vm.hasStoragePermission) {
                                    requestStorageAccess()
                                } else {
                                    vm.refreshLocalState()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !vm.isSyncingModels,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyanContainer,
                                contentColor = ElectricCyan
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (vm.isSyncingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Scanning /Download/FireLM…", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sync / Rescan /Download/FireLM", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Custom & Local Models Header (Positioned at TOP for immediate visibility)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = AuraViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Custom & Local Models (/Download/FireLM)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Text(
                            "${vm.customModels.size} found",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (vm.customModels.isNotEmpty()) HyperEmerald else TextMuted,
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
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (!vm.hasStoragePermission)
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

            // Presets Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Optimized Liquid AI Models",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            items(ModelCatalog.models, key = { it.id }) { m ->
                ModelCard(
                    model = m,
                    vm = vm,
                    ctx = ctx
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModelCard(
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
private fun SpecBadge(label: String, tint: Color) {
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
