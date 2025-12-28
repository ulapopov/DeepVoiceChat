package com.example.deepvoicechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.deepvoicechat.ui.ChatScreen
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var viewModel: MainViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isRecording = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
             Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize TTS
        tts = TextToSpeech(this, this)
        viewModel.onSpeak = { text ->
            speak(text)
        }

        // Initialize Speech Recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("Speech", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("Speech", "Beginning of speech detected")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d("Speech", "End of speech")
                }

                override fun onError(error: Int) {
                    Log.e("Speech", "Error code: $error")
                    val isRunning = viewModel.isListening.value
                    if (isRunning) {
                        // If we are supposed to be listening but got an error (e.g. timeout or no match), 
                        // just restart listening silently after a small delay.
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, 
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                Log.d("Speech", "Timeout/No match - restarting after delay...")
                                // Cancel current session and restart with delay
                                speechRecognizer?.cancel()
                                android.os.Handler(mainLooper).postDelayed({
                                    if (viewModel.isListening.value) {
                                        startListeningInternal()
                                    }
                                }, 1500) // 1.5 second delay
                                return
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                Log.d("Speech", "Recognizer busy - retrying after delay")
                                android.os.Handler(mainLooper).postDelayed({
                                    if (viewModel.isListening.value) {
                                        startListeningInternal()
                                    }
                                }, 2000) // 2 second delay
                                return
                            }
                        }
                    }

                    // For other errors or if we stopped manually
                     viewModel.setListening(false)
                     val msg = when(error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No permission"
                            else -> "Error $error"
                     }
                     if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                         Log.e("Speech", msg)
                         Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                     }
                }

                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d("Speech", "onResults: $data")
                    if (!data.isNullOrEmpty()) {
                        val text = data[0]
                        Log.d("Speech", "Recognized: '$text'")
                        viewModel.appendToDraft(text)
                    }
                    
                    if (viewModel.isListening.value) {
                        // User hasn't stopped, so we restart to keep listening (continuous mode)
                        startListeningInternal()
                    } else {
                        // User toggled off, so we commit what we have
                        viewModel.commitDraft()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    Log.d("Speech", "Partial results: ${partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)}")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        checkPermission()

        setContent {
            MaterialTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onStartRecording = { 
                        tts?.stop() // Stop TTS if speaking (interruption)
                        viewModel.setListening(true)
                        startListeningInternal() 
                    },
                    onStopRecording = { 
                        stopListeningInternal() 
                    }
                )
            }
        }
    }
    
    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningInternal() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition is not available on this device", Toast.LENGTH_SHORT).show()
            viewModel.setListening(false)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            
            // We do NOT call viewModel.setListening(true) here because this function is called 
            // both on initial start AND during the loop restarts.
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                // Partial results could be useful to show the user what's being heard so far
                // putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) 
            }
            
            // Run on UI thread just in case
            runOnUiThread {
                try {
                    speechRecognizer?.startListening(intent)
                    Log.d("VoiceChat", "Started listening")
                } catch(e: Exception) {
                    Log.e("VoiceChat", "Start listening failed: ${e.message}")
                    viewModel.setListening(false)
                }
            }
        } else {
            checkPermission()
        }
    }

    private fun stopListeningInternal() {
        // We set listening to false here so that when onResults fires, it knows to commit.
        viewModel.setListening(false)
        speechRecognizer?.stopListening()
        Log.d("VoiceChat", "Stopped listening")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported")
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }
    
    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
