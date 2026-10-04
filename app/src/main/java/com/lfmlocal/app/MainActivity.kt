package com.lfmlocal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lfmlocal.app.ui.ChatScreen
import com.lfmlocal.app.ui.ChatViewModel
import com.lfmlocal.app.ui.components.DiagnosticsBottomSheet
import com.lfmlocal.app.ui.components.ModelsBottomSheet
import com.lfmlocal.app.ui.theme.LfmTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setSustainedPerformanceMode(true)
        } catch (_: Throwable) { }

        // Handle external .gguf file open intent
        intent?.data?.let { uri ->
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_model.gguf"
            vm.importCustomGguf(uri, name)
        }

        setContent {
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (vm.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemInDark
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
                onDispose { }
            }

            LaunchedEffect(vm.sustainedPerformanceMode) {
                try {
                    window.setSustainedPerformanceMode(vm.sustainedPerformanceMode)
                } catch (_: Throwable) { }
            }
            LfmTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showModelsSheet by remember { mutableStateOf(false) }
                    var showDiagnosticsSheet by remember { mutableStateOf(false) }

                    // Immersive Full-Screen Chat
                    ChatScreen(
                        vm = vm,
                        onOpenModels = { showModelsSheet = true },
                        onOpenSettings = { showDiagnosticsSheet = true }
                    )

                    // Silky Apple-Style Model Selector Sheet
                    if (showModelsSheet) {
                        ModelsBottomSheet(
                            vm = vm,
                            onDismiss = { showModelsSheet = false }
                        )
                    }

                    // Silky Apple-Style Diagnostics & Settings Sheet
                    if (showDiagnosticsSheet) {
                        DiagnosticsBottomSheet(
                            vm = vm,
                            onDismiss = { showDiagnosticsSheet = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshLocalState()
    }
}
