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
        mediaRecorder?.release()
        tts?.shutdown()
    }
}
