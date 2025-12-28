package com.example.deepvoicechat

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.deepvoicechat.models.ChatRequest
import com.example.deepvoicechat.models.ChatResponse
import com.example.deepvoicechat.models.Message
import com.example.deepvoicechat.models.Model
import com.example.deepvoicechat.models.Providers
import com.example.deepvoicechat.models.Reasoning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // PROXY URL - User to configure
    // Suggestion: 10.0.2.2 is localhost for Android Emulator
    private var baseUrl = "http://192.168.68.101:3000"

    private val client = OkHttpClient()
    private val json = Json { 
        ignoreUnknownKeys = true 
        explicitNulls = false 
        encodeDefaults = true // Force encoding defaults to see what's happening
    }

    // State
    private val _providers = Providers.list
    val providers: List<String> get() = _providers

    private val _selectedProvider = mutableStateOf(_providers.first())
    val selectedProvider: State<String> = _selectedProvider

    private val _models = mutableStateListOf<Model>()
    val models: List<Model> get() = _models

    private val _selectedModel = mutableStateOf<Model?>(null)
    val selectedModel: State<Model?> = _selectedModel

    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> get() = _messages

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _speakReplies = mutableStateOf(true)
    val speakReplies: State<Boolean> = _speakReplies

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isListening = mutableStateOf(false)
    val isListening: State<Boolean> = _isListening

    private val _speechDraft = mutableStateOf("")
    
    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (listening) {
            _speechDraft.value = ""
        }
    }
    
    fun appendToDraft(text: String) {
        val current = _speechDraft.value.trim()
        if (current.isEmpty()) {
            _speechDraft.value = text
        } else {
            _speechDraft.value = "$current $text"
        }
    }
    
    fun commitDraft() {
        val text = _speechDraft.value.trim()
        if (text.isNotEmpty()) {
            sendUserMessage(text)
        }
        _speechDraft.value = ""
    }

    // Callback for TTS - to be set by Activity
    var onSpeak: ((String) -> Unit)? = null

    init {
        fetchModels()
    }
    
    fun setBaseUrl(url: String) {
        baseUrl = url
        fetchModels()
    }

    fun onProviderSelected(provider: String) {
        _selectedProvider.value = provider
        fetchModels()
    }

    fun onModelSelected(model: Model) {
        _selectedModel.value = model
    }

    fun onToggleSpeakReplies(enabled: Boolean) {
        _speakReplies.value = enabled
    }

    private fun fetchModels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val provider = _selectedProvider.value.lowercase()
                val request = Request.Builder()
                    .url("$baseUrl/models?provider=$provider")
                    .get()
                    .build()

                Log.d("MainViewModel", "Fetching models: $baseUrl/models?provider=$provider")

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    _error.value = "Failed to load models: ${response.code}"
                    return@launch
                }

                val body = response.body?.string() ?: ""
                Log.d("MainViewModel", "Models response for $provider: ${body.take(500)}") // Log first 500 chars
                
                // Assuming response is List<Model> or List<String> converted to Model
                
                // If it's just raw strings, we manual parse or expect objects.
                // Let's assume the proxy returns `[{"id":"gpt-4", ...}, ...]`
                
                // Regex for removing date suffixes like -20240229 or -2023-05-15 or -0613
                // Strict Allowlist (Exact names after cleaning)
                val openAIStrictList = setOf(
                    "gpt-5.2", "gpt-5.1", "gpt-5", "gpt-5-mini", "gpt-5-nano",
                    "gpt-4o", "gpt-4o-mini"
                )
                
                // Other providers (Prefix based) - Anthropic only, Gemini uses strict list
                val otherAllowedPrefixes = listOf(
                    "claude-3", "claude-4", "claude-sonnet", "claude-opus"
                )
                
                // Regex for removing date suffixes
                val dateSuffixRegex = Regex("[-_](\\d{4,8}|\\d{4}-\\d{2}-\\d{2}|\\d{4})$")

                // Gemini text model prefixes (after removing "models/" prefix)
                val geminiAllowedPrefixes = listOf(
                    "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro",
                    "gemini-3-flash", "gemini-3-pro"
                )
                // Exclude these Gemini variants (non-text models)
                val geminiExcludeKeywords = listOf(
                    "image", "tts", "embedding", "exp", "preview-02", "preview-09", "preview-10"
                )

                fun processModels(rawModels: List<Model>): List<Model> {
                    return rawModels
                        .map { model ->
                            // Clean: remove date suffixes AND "models/" prefix (for Gemini)
                            var cleanName = dateSuffixRegex.replace(model.name, "")
                            if (cleanName.startsWith("models/")) {
                                cleanName = cleanName.removePrefix("models/")
                            }
                            model.copy(name = cleanName)
                        }
                        .filter { model -> 
                            val name = model.name.lowercase()
                            
                            // Check OpenAI Strict List (Exact Match)
                            if (openAIStrictList.contains(name)) return@filter true
                            
                            // Check Gemini (Prefix Match, with exclusions)
                            if (geminiAllowedPrefixes.any { prefix -> name.startsWith(prefix) }) {
                                // Exclude image/tts/embedding variants
                                if (geminiExcludeKeywords.any { kw -> name.contains(kw) }) return@filter false
                                return@filter true
                            }
                            
                            // Check Anthropic prefixes
                            if (otherAllowedPrefixes.any { prefix -> name.startsWith(prefix) }) return@filter true
                            
                            false
                        }
                        .distinctBy { it.name } // Deduplicate
                        .sortedByDescending { it.name } // Sort newer to top
                }

                try {
                     val parsedModels = json.decodeFromString<List<Model>>(body)
                     Log.d("MainViewModel", "Parsed ${parsedModels.size} models for $provider")
                     val processed = processModels(parsedModels)
                     Log.d("MainViewModel", "After filtering: ${processed.size} models: ${processed.map { it.name }}")
                     _models.clear()
                     _models.addAll(processed)
                     if (_models.isNotEmpty()) {
                         _selectedModel.value = _models.first()
                     }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error parsing models: ${e.message}")
                    // Fallback assuming list of strings just in case
                     try {
                         val stringModels = json.decodeFromString<List<String>>(body)
                         val modelsList = stringModels.map { Model(it) }
                         Log.d("MainViewModel", "Fallback: Parsed ${modelsList.size} string models")
                         val processed = processModels(modelsList)
                         _models.clear()
                         _models.addAll(processed)
                         if (_models.isNotEmpty()) {
                            _selectedModel.value = _models.first()
                         }
                     } catch(e2: Exception) {
                         Log.e("MainViewModel", "Fallback also failed: ${e2.message}")
                         _error.value = "Error parsing models: ${e.localizedMessage}"
                     }
                }
            } catch (e: IOException) {
                _error.value = "Network error: ${e.localizedMessage}. Check Proxy URL."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = Message("user", text)
        _messages.add(userMsg)
        
        viewModelScope.launch {
            val currentModel = _selectedModel.value
            if (currentModel == null) {
                _error.value = "No model selected"
                return@launch
            }
            
            _isLoading.value = true
            try {
                // Determine if model is a reasoning/newer model that needs max_completion_tokens
                val modelId = currentModel.id.lowercase()
                val isReasoningModel = modelId.contains("gpt-5") || 
                                       modelId.startsWith("o1") || 
                                       modelId.startsWith("o3") || 
                                       modelId.startsWith("o4")
                
                Log.d("MainViewModel", "ModelID: ${currentModel.id}, isReasoning: $isReasoningModel")

                val reqBody = ChatRequest(
                    provider = _selectedProvider.value.lowercase(),
                    model = currentModel.id,
                    messages = _messages.toList(),
                    stream = false,
                    // Reasoning models use max_completion_tokens, legacy models use max_tokens
                    max_tokens = if (isReasoningModel) null else 2048,
                    max_completion_tokens = if (isReasoningModel) 2048 else null,
                    reasoning = if (isReasoningModel) Reasoning(effort = "minimal") else null
                    // temperature is now handled by the proxy (omitted for GPT-5)
                )
                
                val jsonBody = json.encodeToString(reqBody)
                Log.d("MainViewModel", "Sending ChatRequest: $jsonBody")
                
                val request = Request.Builder()
                    .url("$baseUrl/chat")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                Log.d("MainViewModel", "Sending chat to $baseUrl/chat")

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    Log.e("MainViewModel", "Chat failed: ${response.code} Body: $errorBody")
                    _error.value = "Chat failed: ${response.code}\nResp: $errorBody\n\nSent: $jsonBody"
                    return@launch
                }
                
                val body = response.body?.string() ?: ""
                val chatResponse = json.decodeFromString<ChatResponse>(body)
                
                val assistantMsg = Message("assistant", chatResponse.content)
                _messages.add(assistantMsg)
                
                if (_speakReplies.value) {
                    onSpeak?.invoke(chatResponse.content)
                }
                
            } catch (e: Exception) {
                _error.value = "Chat Error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
