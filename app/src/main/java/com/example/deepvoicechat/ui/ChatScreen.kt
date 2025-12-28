package com.example.deepvoicechat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.deepvoicechat.MainViewModel
import com.example.deepvoicechat.models.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
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
                        viewModel.providers.forEach { provider ->
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
                    // Feature toggles
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Search", style = MaterialTheme.typography.labelLarge)
                            val searchEnabled by viewModel.searchEnabled
                            Switch(
                                checked = searchEnabled,
                                onCheckedChange = { viewModel.onToggleSearch(it) },
                                modifier = Modifier.scale(0.8f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(Modifier.width(8.dp))
                            Text("Voice", style = MaterialTheme.typography.labelLarge)
                            Switch(
                                checked = speakReplies,
                                onCheckedChange = { viewModel.onToggleSpeakReplies(it) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
                
                // Mic Button
                val isListening by viewModel.isListening
                val isTtsSpeaking by viewModel.isTtsSpeaking
                val micScale by animateFloatAsState(
                    targetValue = if (isListening) 1.2f else 1.0f,
                    label = "scale"
                )
                
                // Color logic: Red if listening, Secondary if AI is talking (Silence mode), otherwise PrimaryContainer
                val buttonColor = when {
                    isListening -> MaterialTheme.colorScheme.error
                    isTtsSpeaking -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                
                val iconColor = when {
                    isListening -> MaterialTheme.colorScheme.onError
                    isTtsSpeaking -> MaterialTheme.colorScheme.onSecondary
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Permanent Listening Indicator (No more Toast!)
                    if (isListening) {
                        Text(
                            "Listening...",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else if (isTtsSpeaking) {
                        Text(
                            "AI Speaking",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Spacer(Modifier.height(28.dp)) // Maintain layout height
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                scaleX = micScale
                                scaleY = micScale
                                alpha = if (isLoading) 0.5f else 1.0f
                            }
                            .background(buttonColor, CircleShape)
                            .clickable(enabled = !isLoading) {
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
                           val icon = when {
                               isListening -> Icons.Filled.Stop
                               isTtsSpeaking -> Icons.Filled.MicOff
                               else -> Icons.Filled.Mic
                           }
                           Icon(
                               imageVector = icon,
                               contentDescription = if (isListening) "Stop" else if (isTtsSpeaking) "Silence" else "Speak",
                               tint = iconColor,
                               modifier = Modifier.size(32.dp)
                           )
                       }
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
