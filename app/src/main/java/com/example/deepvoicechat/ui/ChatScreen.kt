package com.example.deepvoicechat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Mic
import com.example.deepvoicechat.MainViewModel
import com.example.deepvoicechat.models.Message
import com.example.deepvoicechat.models.Model

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val providers = viewModel.providers
    val selectedProvider by viewModel.selectedProvider
    val models = viewModel.models
    val selectedModel by viewModel.selectedModel
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val speakReplies by viewModel.speakReplies

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DeepVoiceChat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Dropdowns
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Provider Dropdown
                var providerExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { providerExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedProvider)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider) },
                                onClick = {
                                    viewModel.onProviderSelected(provider)
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                // Model Dropdown
                var modelExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { modelExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = models.isNotEmpty()
                    ) {
                        Text(selectedModel?.name ?: "Loading...")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    viewModel.onModelSelected(model)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Chat Transcript
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                 if (messages.isNotEmpty()) {
                     listState.animateScrollToItem(messages.size - 1)
                 }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Speak Replies")
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = speakReplies,
                        onCheckedChange = { viewModel.onToggleSpeakReplies(it) }
                    )
                }
                
                // Toggle Button for Speak
                val isListening by viewModel.isListening
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isListening) 1.2f else 1.0f,
                    label = "scale"
                )
                // Use primary color when listening (active), container when idle
                val buttonColor = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                val iconColor = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(buttonColor, androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            if (isListening) {
                                onStopRecording()
                            } else {
                                onStartRecording()
                            }
                        }
                ) {
                   if (isLoading) {
                       CircularProgressIndicator(color = iconColor, modifier = Modifier.size(32.dp))
                   } else {
                       Icon(
                           imageVector = Icons.Filled.Mic,
                           contentDescription = if (isListening) "Tap to Stop" else "Tap to Speak",
                           tint = iconColor,
                           modifier = Modifier.size(32.dp)
                       )
                   }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            color = color,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(8.dp)
            )
        }
        Text(
            text = message.role,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
