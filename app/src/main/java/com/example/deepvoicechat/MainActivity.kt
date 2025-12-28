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
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Safety timer to commit draft if onResults is slow or missing
    private val safetyCommitRunnable = Runnable {
        if (!viewModel.isListening.value) {
            Log.d("Speech", "Safety commit firing")
            viewModel.commitDraft()
        }
    }

    private val restartRunnable = Runnable {
        if (viewModel.isListening.value) {
            startListeningInternal(isRestart = true)
        }
    }

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

        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onStartRecording = { 
                        if (viewModel.isTtsSpeaking.value) {
                            // First tap: Silence AI
                            tts?.stop()
                            viewModel.setIsTtsSpeaking(false)
                            Log.d("Speech", "AI Silenced by user")
                        } else {
                            // Normal tap: Start recording
                            viewModel.setListening(true)
                            // Wait for audio hardware to clear
                            mainHandler.postDelayed({
                                if (viewModel.isListening.value) startListeningInternal(isRestart = false) 
                            }, 800)
                        }
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
        
        Log.d("Speech", "setupSpeechRecognizer - RECREATING")
        destroySpeechRecognizer()
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("Speech", "onReadyForSpeech - Mic Active")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("Speech", "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d("Speech", "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic Busy"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        else -> "Mic Error: $error"
                    }
                    Log.e("Speech", "onError: $msg ($error)")
                    
                    if (!viewModel.isListening.value) {
                         mainHandler.removeCallbacks(safetyCommitRunnable)
                         viewModel.commitDraft() 
                         return
                    }

                    // For continuous mode, retry after delay
                    val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 1000L
                    mainHandler.postDelayed(restartRunnable, delay)
                }

                override fun onResults(results: Bundle?) {
                    mainHandler.removeCallbacks(safetyCommitRunnable)
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d("Speech", "onResults: $data")
                    if (!data.isNullOrEmpty()) {
                        viewModel.appendToDraft(data[0])
                    }
                    
                    if (viewModel.isListening.value) {
                        // Keep listening
                        mainHandler.removeCallbacks(restartRunnable)
                        mainHandler.postDelayed(restartRunnable, 600)
                    } else {
                        // User stopped, commit final
                        viewModel.commitDraft()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningInternal(isRestart: Boolean) {
        Log.d("Speech", "startListeningInternal(isRestart=$isRestart)")
        
        mainHandler.post {
            // Recreate ONLY on fresh turns or if null
            if (!isRestart || speechRecognizer == null) {
                setupSpeechRecognizer()
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            try {
                speechRecognizer?.cancel() // Force reset
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("Speech", "startListening failed: ${e.message}")
                setupSpeechRecognizer()
            }
        }
    }

    private fun stopListeningInternal() {
        Log.d("Speech", "stopListeningInternal")
        viewModel.setListening(false)
        speechRecognizer?.stopListening()
        
        mainHandler.removeCallbacks(safetyCommitRunnable)
        mainHandler.postDelayed(safetyCommitRunnable, 2000) 
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.getDefault())
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post { viewModel.setIsTtsSpeaking(true) }
                }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { viewModel.setIsTtsSpeaking(false) }
                }
                override fun onError(utteranceId: String?) {
                    mainHandler.post { viewModel.setIsTtsSpeaking(false) }
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    mainHandler.post { viewModel.setIsTtsSpeaking(false) }
                }
            })
        }
    }
    
    private fun speak(text: String) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ai_msg")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ai_msg")
    }

    override fun onDestroy() {
        super.onDestroy()
        destroySpeechRecognizer()
        tts?.shutdown()
    }
}
