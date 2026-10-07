package com.lfmlocal.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lfmlocal.app.ui.components.*
import com.lfmlocal.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Determine whether user is at the bottom of the chat list
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) true
            else {
                val lastVisibleIndex = visibleItems.last().index
                val totalItems = layoutInfo.totalItemsCount
                lastVisibleIndex >= totalItems - 1
            }
        }
    }

    // Follow stream flag: keeps tracking incoming tokens if user was at the bottom
    var autoFollowStream by remember { mutableStateOf(true) }

    // If user interacts with scroll gestures, disable auto-follow if they scrolled away
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            autoFollowStream = isAtBottom
        }
    }

    // Auto-scroll when a new message is appended or when generation begins
    LaunchedEffect(vm.messages.size, vm.isGenerating) {
        autoFollowStream = true
        val totalCount = listState.layoutInfo.totalItemsCount
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    // Smoothly follow streaming tokens as they arrive
    LaunchedEffect(vm.streamingText) {
        if (autoFollowStream && vm.streamingText.isNotEmpty()) {
            val totalCount = listState.layoutInfo.totalItemsCount
            if (totalCount > 0) {
                listState.scrollToItem(totalCount - 1)
            }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            if (vm.messages.isEmpty() && vm.streamingText.isEmpty()) {
                EmptyChatHeroState(
                    modifier = Modifier.fillMaxSize(),
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
                    } else if (vm.isGenerating) {
                        item(key = "thinking") {
                            ThinkingIndicator(backend = vm.computeBackend)
                        }
                    }
                }

                // Floating "Scroll to Bottom" button when user scrolls up
                AnimatedVisibility(
                    visible = !isAtBottom && (vm.messages.isNotEmpty() || vm.streamingText.isNotEmpty()),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                autoFollowStream = true
                                val totalCount = listState.layoutInfo.totalItemsCount
                                if (totalCount > 0) {
                                    listState.animateScrollToItem(totalCount - 1)
                                }
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll to bottom",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            if (vm.isGenerating) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
