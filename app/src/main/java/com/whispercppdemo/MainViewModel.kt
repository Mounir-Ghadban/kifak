package com.whispercppdemo


import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var whisperContextPtr: Long = 0L

    fun testLoadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext

            try {
                // Model is downloaded once into app storage (not bundled in the APK)
                val modelFile = LevantineModel.ensureModelFile(context) { pct ->
                    Log.d("WhisperTest", "Model download: $pct%")
                } ?: run {
                    Log.e("WhisperTest", "Model download failed")
                    return@launch
                }

                // Pass the real file path to C++ JNI
                Log.d("WhisperTest", "Initializing model from: ${modelFile.absolutePath}")
                whisperContextPtr = WhisperLib.initContext(modelFile.absolutePath)

                if (whisperContextPtr != 0L) {
                    Log.d("WhisperTest", "SUCCESS: Model loaded! Context pointer: $whisperContextPtr")
                } else {
                    Log.e("WhisperTest", "FAILURE: WhisperLib.initContext returned 0.")
                }

            } catch (e: Exception) {
                Log.e("WhisperTest", "Error loading model", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (whisperContextPtr != 0L) {
            WhisperLib.freeContext(whisperContextPtr)
            whisperContextPtr = 0L
            Log.d("WhisperTest", "Whisper context freed successfully.")
        }
    }
}