package com.lfmlocal.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lfmlocal.app.ui.components.*
import com.lfmlocal.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                vm = vm,
                onOpenModels = onOpenModels,
                onOpenSettings = onOpenSettings
            )
        },
        bottomBar = {
            ChatInputDock(
                vm = vm,
                input = input,
                onInputChange = { input = it },
                onSend = { text ->
                    vm.send(text)
                    input = ""
                },
                onStop = { vm.stop() }
            )
        }
    ) { pad ->
        if (vm.messages.isEmpty() && vm.streamingText.isEmpty()) {
            EmptyChatHeroState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                vm = vm,
                onOpenModels = onOpenModels,
                onOpenSettings = onOpenSettings,
                onSelectSuggestion = { suggestion ->
                    if (vm.modelFile != null) {
                        vm.send(suggestion)
                    } else {
                        input = suggestion
                    }
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(vm.messages, key = { it.id }) { msg ->
                    ChatBubble(msg = msg, backend = vm.computeBackend)
                }

                if (vm.streamingText.isNotEmpty()) {
                    item(key = "streaming") {
                        StreamingBubble(text = vm.streamingText, backend = vm.computeBackend)
                    }
                } else if (vm.busy) {
                    item(key = "thinking") {
                        ThinkingIndicator(backend = vm.computeBackend)
                    }
                }
            }
        }
    }
}
