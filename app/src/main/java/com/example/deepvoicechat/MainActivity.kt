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
        setupSpeechRecognizer()

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

    private fun destroySpeechRecognizer() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        
        destroySpeechRecognizer()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
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
                    if (viewModel.isListening.value) {
                        // If we are supposed to be listening but got an error, 
                        // restart after a small delay.
                        val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 1000L
                        
                        // For certain errors, a full recreation is better
                        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_AUDIO) {
                            Log.d("Speech", "Critical error, recreating recognizer")
                            destroySpeechRecognizer()
                        }

                        android.os.Handler(mainLooper).postDelayed({
                            if (viewModel.isListening.value) {
                                startListeningInternal()
                            }
                        }, delay)
                    } else {
                        viewModel.setListening(false)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d("Speech", "onResults: $data")
                    if (!data.isNullOrEmpty()) {
                        val text = data[0]
                        viewModel.appendToDraft(text)
                    }
                    
                    if (viewModel.isListening.value) {
                        // Slight delay before restarting to avoid BUSY error
                        android.os.Handler(mainLooper).postDelayed({
                            if (viewModel.isListening.value) startListeningInternal()
                        }, 500)
                    } else {
                        viewModel.commitDraft()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Log.d("Speech", "Partial: ${partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)}")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningInternal() {
        if (speechRecognizer == null) {
            setupSpeechRecognizer()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            setupSpeechRecognizer()
        }
    }

    private fun stopListeningInternal() {
        viewModel.setListening(false)
        speechRecognizer?.stopListening()
        // Ensure draft is committed even if onResults is slow
        android.os.Handler(mainLooper).postDelayed({
            if (!viewModel.isListening.value) viewModel.commitDraft()
        }, 500)
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
