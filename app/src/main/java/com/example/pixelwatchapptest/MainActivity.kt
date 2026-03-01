package com.example.pixelwatchapptest

import android.Manifest
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.input.RemoteInputIntentHelper
import com.example.pixelwatchapptest.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var isRecording by mutableStateOf(false)
    private var currentlyPlayingFile by mutableStateOf<File?>(null)
    private var recordings = mutableStateListOf<File>()
    
    // Maps a file to its transcription result
    private val transcriptions = mutableStateMapOf<File, String>()
    // Maps a file to a boolean indicating if it's currently being transcribed
    private val transcribingStates = mutableStateMapOf<File, Boolean>()
    
    // API Key State
    private var userApiKey by mutableStateOf("")
    private var showSettings by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(this, "録音権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Launcher for acquiring text input (from phone or watch keyboard)
    private val remoteInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val results = RemoteInput.getResultsFromIntent(result.data)
            val inputText = results?.getCharSequence("api_key_input")?.toString()
            if (!inputText.isNullOrEmpty()) {
                saveApiKey(inputText.trim())
                Toast.makeText(this, "APIキーを保存しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        loadApiKey()
        loadRecordings()

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            currentKey = userApiKey,
                            onClose = { showSettings = false },
                            onInputClick = { launchRemoteInput() }
                        )
                    } else {
                        WearApp(
                            isRecording = isRecording,
                            recordings = recordings,
                            currentlyPlayingFile = currentlyPlayingFile,
                            transcriptions = transcriptions,
                            transcribingStates = transcribingStates,
                            onStartClick = { checkPermissionAndStart() },
                            onStopClick = { stopRecording() },
                            onPlayClick = { file -> playRecording(file) },
                            onStopPlayClick = { stopPlayback() },
                            onTranscribeClick = { file -> transcribeAudio(file) },
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
    
    // --- API Key Management (BYOK) ---
    private fun loadApiKey() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        userApiKey = savedKey
    }

    private fun saveApiKey(key: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
        userApiKey = key
    }
    
    private fun launchRemoteInput() {
        // In Wear OS 3+, RemoteInputIntentHelper opens the input method picker.
        // The user must select the "Keyboard" and then the "Phone" icon to type on their phone.
        val remoteInput = RemoteInput.Builder("api_key_input")
            .setLabel("スマホから入力する場合は、⌨️を選び、次に📱アイコンを押してください")
            .build()
        
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putTitleExtra(intent, "Gemini API Key")
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        
        remoteInputLauncher.launch(intent)
    }
    
    // --- Transcription Logic ---
    private fun transcribeAudio(file: File) {
        // Use user provided key, fallback to local.properties build config key for testing
        val apiKey = if (userApiKey.isNotEmpty()) userApiKey else BuildConfig.GEMINI_API_KEY
        
        if (apiKey.isEmpty() || apiKey == "null") {
            Toast.makeText(this, "API Keyが設定されていません。設定(⚙)から入力してください。", Toast.LENGTH_LONG).show()
            return
        }
        
        transcribingStates[file] = true
        transcriptions.remove(file)
        
        // Use Coroutines launched from lifecycleScope to perform network call
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash-lite",
                    apiKey = apiKey
                )
                
                val fileByteArray = file.readBytes()
                
                val inputContent = content {
                    blob("audio/mp4", fileByteArray)
                    text("音声を日本語で正確に文字起こししてください。文字起こしのテキストだけを返してください。")
                }
                
                val response = generativeModel.generateContent(inputContent)
                
                withContext(Dispatchers.Main) {
                    transcriptions[file] = response.text ?: "文字起こしに失敗しました"
                    transcribingStates[file] = false
                }
            } catch (e: Exception) {
                Log.e("GeminiAPI", "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    transcriptions[file] = "エラー: ${e.localizedMessage}"
                    transcribingStates[file] = false
                }
            }
        }
    }
    
    private fun loadRecordings() {
        recordings.clear()
        val cacheDir = externalCacheDir ?: return
        val files = cacheDir.listFiles { _, name -> name.endsWith(".mp4") }
        if (files != null) {
            // Sort by last modified descending (newest first)
            recordings.addAll(files.sortedByDescending { it.lastModified() })
        }
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (isRecording) return
        
        // Stop playback if playing
        stopPlayback()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputFilePath = "${externalCacheDir?.absolutePath}/recording_$timeStamp.mp4"

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            try {
                prepare()
                start()
                isRecording = true
                Log.d("AudioRecord", "Recording started: $outputFilePath")
            } catch (e: IOException) {
                Log.e("AudioRecord", "prepare() failed", e)
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.d("AudioRecord", "Recording stopped")
            Toast.makeText(this, "録音完了", Toast.LENGTH_SHORT).show()
            
            // Refresh list
            loadRecordings()
        } catch (e: Exception) {
            Log.e("AudioRecord", "stop() failed", e)
        }
    }
    
    private fun playRecording(file: File) {
        if (isRecording) return
        
        stopPlayback()
        
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
                currentlyPlayingFile = file
                
                setOnCompletionListener {
                    stopPlayback()
                }
            } catch (e: IOException) {
                Log.e("AudioPlay", "prepare() failed", e)
                currentlyPlayingFile = null
            }
        }
    }
    
    private fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        currentlyPlayingFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
        
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
fun SettingsScreen(currentKey: String, onClose: () -> Unit, onInputClick: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.title3,
                color = Color.White
            )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            Text(
                text = "Gemini API Key:",
                style = MaterialTheme.typography.caption2,
                color = Color.LightGray
            )
        }
        item {
            val keyDisplay = if (currentKey.isNotEmpty()) "設定済み (...${currentKey.takeLast(4)})" else "未設定"
            Text(
                text = keyDisplay,
                style = MaterialTheme.typography.body2,
                color = if (currentKey.isNotEmpty()) Color.Green else Color.Red,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            Button(
                onClick = onInputClick,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("スマホで入力", style = MaterialTheme.typography.caption1)
            }
        }
        item {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("戻る")
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun WearApp(
    isRecording: Boolean,
    recordings: List<File>,
    currentlyPlayingFile: File?,
    transcriptions: Map<File, String>,
    transcribingStates: Map<File, Boolean>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onPlayClick: (File) -> Unit,
    onStopPlayClick: () -> Unit,
    onTranscribeClick: (File) -> Unit,
    onSettingsClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("⚙", style = MaterialTheme.typography.caption2)
                }
            }
        }
        item {
            if (isRecording) {
                Text(text = "Recording...", color = Color.Red)
            } else {
                Text(text = "Ready", color = Color.White)
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            if (!isRecording) {
                Button(onClick = onStartClick) {
                    Text("▶")
                }
            } else {
                Button(onClick = onStopClick) {
                    Text("■")
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        if (!isRecording && recordings.isNotEmpty()) {
            item {
                Text(text = "Recordings", color = Color.Gray, style = MaterialTheme.typography.caption2)
            }
            items(recordings) { file ->
                val isPlaying = currentlyPlayingFile == file
                val isTranscribing = transcribingStates[file] == true
                val transcriptionResult = transcriptions[file]
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPlaying) onStopPlayClick() else onPlayClick(file)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = file.name.replace("recording_", "").replace(".mp4", ""),
                            color = if (isPlaying) Color.Green else Color.White,
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isPlaying) "■" else "▶",
                            color = if (isPlaying) Color.Green else Color.LightGray,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Transcribe UI
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                       if (isTranscribing) {
                           CircularProgressIndicator(
                               modifier = Modifier.size(24.dp),
                               indicatorColor = MaterialTheme.colors.secondary,
                               trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
                               strokeWidth = 2.dp
                           )
                       } else {
                           Button(
                               onClick = { onTranscribeClick(file) },
                               modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                               colors = ButtonDefaults.secondaryButtonColors()
                           ) {
                              Text("T", style = MaterialTheme.typography.caption2)
                           }
                       }
                    }
                    
                    // Transcription Result
                    if (!transcriptionResult.isNullOrEmpty()) {
                        Text(
                            text = transcriptionResult,
                            color = Color.Cyan,
                            style = MaterialTheme.typography.caption3,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
