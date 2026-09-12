package com.whispercppdemo.ui.main

import android.R
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercppdemo.LevantineModel
import com.whispercppdemo.arabizi.Arabizi
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

enum class TranscriptionState { IDLE, RECORDING, TRANSCRIBING }

data class Note(val id: Long, val text: String, val createdAt: Long)

class MainScreenViewModel(private val application: Application) : AndroidViewModel(application) {


    private val transcribeMutex = Mutex()
    private val _state = MutableStateFlow(TranscriptionState.IDLE)
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set
    var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    var transcript by mutableStateOf("")
        private set
    var isTranscribing by mutableStateOf(false)
        private set
    var status by mutableStateOf("Starting…")
        private set

    private val modelsPath = LevantineModel.modelFile(application).parentFile!!
    private val samplesPath = File(application.filesDir, "samples")
    private var recorder: Recorder = Recorder()
    private var whisperContext: WhisperContext? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

    init {
        viewModelScope.launch {
            printSystemInfo()
            loadData()
            loadNotes()
        }
    }

    // ---- saved notes -------------------------------------------------------

    private val notesFile get() = File(application.filesDir, "notes.json")

    fun saveNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        notes = listOf(Note(System.currentTimeMillis(), trimmed, System.currentTimeMillis())) + notes
        persistNotes()
    }

    fun deleteNote(id: Long) {
        notes = notes.filterNot { it.id == id }
        persistNotes()
    }

    private fun persistNotes() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            notes.forEach { n ->
                arr.put(
                    JSONObject()
                        .put("id", n.id)
                        .put("text", n.text)
                        .put("createdAt", n.createdAt)
                )
            }
            val tmp = File(application.filesDir, "notes.json.part")
            tmp.writeText(arr.toString())
            if (notesFile.exists()) notesFile.delete()
            if (!tmp.renameTo(notesFile)) {
                Log.w(LOG_TAG, "persistNotes: rename failed")
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "persistNotes failed", e)
        }
    }

    private suspend fun loadNotes() = withContext(Dispatchers.IO) {
        try {
            if (!notesFile.isFile) return@withContext
            val arr = JSONArray(notesFile.readText())
            val loaded = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(o.getLong("id"), o.getString("text"), o.getLong("createdAt"))
            }
            withContext(Dispatchers.Main) { notes = loaded }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "loadNotes failed", e)
        }
    }

    private suspend fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun loadData() {
        printMessage("Loading data...\n")
        try {
            copyAssets()
            loadBaseModel()
            canTranscribe = true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath.mkdirs()
        samplesPath.mkdirs()
        application.copyData("samples", samplesPath, ::printMessage)
        printMessage("All data copied to working directory.\n")
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        printMessage("Loading model...\n")

        // The model is downloaded once instead of being bundled, keeping the APK
        // small; afterwards it is reused straight from app storage.
        val existing = LevantineModel.modelFile(application)
        val model = if (existing.isFile && existing.length() > 0) {
            existing
        } else {
            printMessage("Downloading model (one time, ~260 MB)...\n")
            LevantineModel.ensureModelFile(application)
        }

        if (model == null) {
            printMessage("Model download failed. Check your connection and try again.\n")
            status = "Model download failed."
            return@withContext
        }

        try {
            whisperContext = WhisperContext.createContextFromFile(model.absolutePath)
            printMessage("Loaded model ${model.name}.\n")
            status = "Model loaded."
        } catch (e: Exception) {
            // A truncated download would fail to load: drop it so the next run retries
            printMessage("Failed to load ${model.name}: ${e.localizedMessage}\n")
            model.delete()
        }
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        startPlayback(file)
        return@withContext decodeWaveFile(file)
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private fun sanitizePrompt(raw: String, maxChars: Int = 200): String {
        val clean = String(raw.toByteArray(Charsets.UTF_8), Charsets.UTF_8)
        return clean.take(maxChars)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "whisper_transcription_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Transcription Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(1, notification)
        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Missing notification permissions", e)
        }
    }

    private suspend fun transcribeAudio(file: File) {
        if (!transcribeMutex.tryLock()) {
            Log.w(LOG_TAG, "transcribeAudio: ignored, already transcribing")
            return
        }

        try {
            _state.value = TranscriptionState.TRANSCRIBING
            isTranscribing = true
            status = ""
            canTranscribe = false

            val data = readAudioSamples(file)

            // ---- silence gate ------------------------------------------------
            // Whisper invents fluent nonsense when fed silence (observed: looping
            // "نهاية الدرس المترجم..." on an empty room). Skip the model entirely
            // when the clip is too short or too quiet to contain speech.
            var peak = 0.0f
            var sumSquares = 0.0
            for (s in data) {
                val a = if (s < 0) -s else s
                if (a > peak) peak = a
                sumSquares += s.toDouble() * s.toDouble()
            }
            val rms = if (data.isEmpty()) 0.0 else sqrt(sumSquares / data.size)
            val seconds = data.size / 16000.0
            Log.d("WhisperApp", "AUDIO: seconds=$seconds peak=$peak rms=$rms")
            if (seconds < 0.4 || peak < 0.04f || rms < 0.006) {
                printMessage("No speech detected.\n")
                status = "No speech detected."
                transcript = ""
                return
            }
            
            val start = System.currentTimeMillis()
            val rawText = withContext(Dispatchers.Default) {
                whisperContext?.transcribeData(data, language = "ar")
            } ?: ""

            Log.d("WhisperApp", "RAW OUTPUT: [$rawText]")
            printMessage("Raw: $rawText\n")

            // ---- hallucination-loop gate --------------------------------------
            // Faint noise can slip past the energy gate; its signature is the same
            // phrase repeated on loop. No real utterance repeats a 3-word phrase
            // three times, so treat that as silence.
            if (Arabizi.looksLikeHallucinationLoop(rawText)) {
                Log.d("WhisperApp", "HALLUCINATION: [$rawText]")
                printMessage("No speech detected.\n")
                status = "No speech detected."
                transcript = ""
                return
            }

            // Only Arabic-script output goes through the Arabizi dictionary;
            // English/French transcripts pass through untouched.
            val text = Arabizi.convert(rawText)

            val timeTaken = System.currentTimeMillis() - start
            printMessage("($timeTaken ms): \n$text\n")

            // showNotification(application, "Transcription Ready", text)
            
            val elapsed = System.currentTimeMillis() - start
            printMessage("Done ($elapsed ms): \n$text\n")
            transcript = text
            status = "Ready"
        } catch (e: Exception) {
            Log.e(LOG_TAG, "transcribeAudio failed", e)
            printMessage("${e.localizedMessage}\n")
            status = e.localizedMessage ?: "Error"
        } finally {
            canTranscribe = true
            isTranscribing = false
            _state.value = TranscriptionState.IDLE
            transcribeMutex.unlock()
        }
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                _state.value = TranscriptionState.IDLE
                recordedFile?.let { transcribeAudio(it) }
            } else {
                stopPlayback()
                val file = getTempFileForRecording()
                recorder.startRecording(file) { e ->
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            printMessage("${e.localizedMessage}\n")
                            isRecording = false
                            _state.value = TranscriptionState.IDLE
                        }
                    }
                }
                isRecording = true
                _state.value = TranscriptionState.RECORDING
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
            _state.value = TranscriptionState.IDLE
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav")
    }

    override fun onCleared() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
            stopPlayback()
        }
    }

    private suspend fun Context.copyData(
        assetDirName: String,
        destDir: File,
        printMessage: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        assets.list(assetDirName)?.forEach { name ->
            val assetPath = "$assetDirName/$name"
            Log.v(LOG_TAG, "Processing $assetPath...")
            val destination = File(destDir, name)
            Log.v(LOG_TAG, "Copying $assetPath to $destination...")
            printMessage("Copying $name...\n")
            assets.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.v(LOG_TAG, "Copied $assetPath to $destination")
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}
