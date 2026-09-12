package com.whispercppdemo.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.whispercppdemo.LevantineModel
import com.whispercppdemo.arabizi.Arabizi
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.sqrt

/**
 * Kifak voice typing, available over any app.
 *
 * Tap the system accessibility button once → recording starts (a toast confirms).
 * Tap it again → recording stops, the model transcribes on-device, the Arabizi
 * text is placed on the clipboard and pasted into whatever text field currently
 * has the cursor — WhatsApp, Telegram, the browser, anything. The user never
 * leaves the app they were typing in.
 *
 * The only window content this service ever touches is the focused editable
 * node, and only to paste the transcription. It reads nothing else.
 */
class KifakAccessibilityService : AccessibilityService() {

    enum class Phase { IDLE, RECORDING, TRANSCRIBING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transcribeMutex = Mutex()
    private val recorder = Recorder()
    private var recordingFile: File? = null

    @Volatile private var phase = Phase.IDLE
    private var whisperContext: WhisperContext? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
            notificationTimeout = 0
        }
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    onVoiceButtonTapped()
                }

                override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
                ) {
                }
            }
        )
        Log.i(TAG, "Kifak voice-typing service connected")
    }

    private fun onVoiceButtonTapped() {
        when (phase) {
            Phase.IDLE -> startVoiceTyping()
            Phase.RECORDING -> stopVoiceTyping()
            Phase.TRANSCRIBING -> toast("Kifak is still transcribing…")
        }
    }

    private fun startVoiceTyping() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            toast("Open Kifak once and allow the microphone, then use this button")
            return
        }
        phase = Phase.RECORDING
        recordingFile = File(cacheDir, "a11y_recording.wav")
        scope.launch {
            recorder.startRecording(recordingFile!!) { e ->
                Log.e(TAG, "recording failed", e)
                phase = Phase.IDLE
                toast("Recording failed")
            }
        }
        toast("● Recording — tap the button again to stop")
    }

    private fun stopVoiceTyping() {
        phase = Phase.TRANSCRIBING
        val file = recordingFile
        toast("Transcribing…")
        scope.launch {
            try {
                recorder.stopRecording()
                val text = file?.let { transcribe(it) }.orEmpty()
                if (text.isBlank()) {
                    toast("No speech detected")
                } else {
                    pasteIntoFocusedField(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "voice typing failed", e)
                toast("Transcription failed")
            } finally {
                file?.delete()
                phase = Phase.IDLE
            }
        }
    }

    /** Same decode config and gates as the main app. */
    private suspend fun transcribe(file: File): String = transcribeMutex.withLock {
        val ctx = whisperContext ?: WhisperContext.createContextFromFile(
            LevantineModel.modelFile(this).absolutePath
        ).also { whisperContext = it }

        val data = decodeWaveFile(file)

        // silence gate: don't invoke the model on empty audio
        var peak = 0f
        var sumSq = 0.0
        for (s in data) {
            val a = if (s < 0) -s else s
            if (a > peak) peak = a
            sumSq += s.toDouble() * s
        }
        val rms = if (data.isEmpty()) 0.0 else sqrt(sumSq / data.size)
        if (data.size < 16000 * 0.4 || peak < 0.04f || rms < 0.006) return ""

        val raw = ctx.transcribeData(data, language = "ar")?.trim().orEmpty()
        Log.d(TAG, "RAW: [$raw]")
        if (raw.isEmpty() || Arabizi.looksLikeHallucinationLoop(raw)) return ""
        return Arabizi.convert(raw)
    }

    /**
     * Copy the text and paste it into the currently focused editable field.
     * Falls back to "copied" if the cursor can't be located.
     */
    private fun pasteIntoFocusedField(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Kifak", text))

        val target = findFocusedEditable()
        val ok = target?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
        if (ok) {
            toast("✓ $text")
        } else {
            toast("Copied — long-press the text field and tap Paste")
        }
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return findFirstEditable(root)
        return if (focused.isEditable) focused else findFirstEditable(root)
            ?: focused
    }

    /** Last resort: the first editable text field in the active window. */
    private fun findFirstEditable(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 20) return null
        if (node.isEditable && node.isFocusable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: nothing on screen is monitored.
    }

    override fun onDestroy() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KifakA11y"

        /** Whether the service is currently enabled in system settings. */
        fun isEnabled(context: Context): Boolean {
            val setting = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = "${context.packageName}/com.whispercppdemo.accessibility.KifakAccessibilityService"
            return setting.split(':').any {
                it.equals(component, ignoreCase = true) ||
                    it.equals(context.packageName, ignoreCase = true)
            }
        }
    }
}
