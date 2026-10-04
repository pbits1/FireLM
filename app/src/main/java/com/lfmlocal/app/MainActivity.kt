package com.lfmlocal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lfmlocal.app.ui.ChatScreen
import com.lfmlocal.app.ui.ChatViewModel
import com.lfmlocal.app.ui.ModelsScreen
import com.lfmlocal.app.ui.SettingsScreen
import com.lfmlocal.app.ui.theme.LfmTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
            LaunchedEffect(vm.sustainedPerformanceMode) {
                try {
                    window.setSustainedPerformanceMode(vm.sustainedPerformanceMode)
                } catch (_: Throwable) { }
            }
            LfmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var route by remember { mutableStateOf("chat") }

                    BackHandler(enabled = route != "chat") {
                        route = "chat"
                    }

                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            if (targetState != "chat") {
                                (slideInHorizontally { width -> width / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> -width / 4 } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> width / 4 } + fadeOut())
                            }
                        },
                        label = "screen_route_transition"
                    ) { targetRoute ->
                        when (targetRoute) {
                            "chat" -> ChatScreen(
                                vm = vm,
                                onOpenModels = { route = "models" },
                                onOpenSettings = { route = "settings" }
                            )
                            "models" -> ModelsScreen(
                                vm = vm,
                                onBack = { route = "chat" }
                            )
                            "settings" -> SettingsScreen(
                                vm = vm,
                                onBack = { route = "chat" }
                            )
                            else -> ChatScreen(
                                vm = vm,
                                onOpenModels = { route = "models" },
                                onOpenSettings = { route = "settings" }
                            )
                        }
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
