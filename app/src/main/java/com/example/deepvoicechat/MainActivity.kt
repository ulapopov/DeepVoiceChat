package com.example.deepvoicechat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
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
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var viewModel: MainViewModel
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var tts: TextToSpeech? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
                            tts?.stop()
                            viewModel.setIsTtsSpeaking(false)
                        } else {
                            startRecording()
                        }
                    },
                    onStopRecording = { 
                        stopRecording() 
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        try {
            audioFile = File(cacheDir, "audio_record.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            viewModel.setListening(true)
            // Simulating "Ready" immediately for MediaRecorder
            viewModel.setMicReady(true)
            Log.d("Audio", "Recording started: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("Audio", "startRecording failed", e)
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            viewModel.setListening(false)
            viewModel.setMicReady(false)
            Log.d("Audio", "Recording stopped")
            
            audioFile?.let {
                if (it.exists()) {
                    viewModel.transcribeAudio(it)
                }
            }
        } catch (e: Exception) {
            Log.e("Audio", "stopRecording failed", e)
            viewModel.setListening(false)
            viewModel.setMicReady(false)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.getDefault())
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                private var lastUtteranceId: String? = null

                override fun onStart(utteranceId: String?) {
                    mainHandler.post { viewModel.setIsTtsSpeaking(true) }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("ai_msg_final") == true || utteranceId == "ai_msg") {
                        mainHandler.post { viewModel.setIsTtsSpeaking(false) }
                    }
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
        if (text.isBlank()) return
        
        // Android TTS has a limit of ~4000 chars. We'll use 2000 for safety and split by sentences.
        val maxLength = 2000
        if (text.length <= maxLength) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ai_msg")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ai_msg")
        } else {
            // Split into chunks by sentences
            val chunks = splitTextIntoChunks(text, maxLength)
            chunks.forEachIndexed { index, chunk ->
                val utteranceId = if (index == chunks.size - 1) "ai_msg_final_$index" else "ai_msg_chunk_$index"
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(chunk, queueMode, params, utteranceId)
            }
        }
    }

    private fun splitTextIntoChunks(text: String, maxLength: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = text
        
        while (remaining.length > maxLength) {
            // Find the last sentence boundary within maxLength
            val sub = remaining.substring(0, maxLength)
            var splitIndex = sub.lastIndexOfAny(listOf(".", "!", "?", "\n"))
            
            // If no sentence boundary found, just split at last space
            if (splitIndex == -1) {
                splitIndex = sub.lastIndexOf(" ")
            }
            
            // Fallback to absolute split if no space found
            if (splitIndex == -1) {
                splitIndex = maxLength
            } else {
                splitIndex += 1 // Include the punctuation/space
            }
            
            result.add(remaining.substring(0, splitIndex).trim())
            remaining = remaining.substring(splitIndex).trim()
        }
        
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        tts?.shutdown()
    }
}
