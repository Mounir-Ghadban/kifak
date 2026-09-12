package com.whispercppdemo.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.whispercppdemo.accessibility.KifakAccessibilityService
import com.whispercppdemo.ui.theme.NeoAccent
import com.whispercppdemo.ui.theme.NeoAccentBright
import com.whispercppdemo.ui.theme.NeoAccentSoft
import com.whispercppdemo.ui.theme.NeoBackground
import com.whispercppdemo.ui.theme.NeoBrown
import com.whispercppdemo.ui.theme.NeoBrownDeep
import com.whispercppdemo.ui.theme.NeoInset
import com.whispercppdemo.ui.theme.NeoShadowDark
import com.whispercppdemo.ui.theme.NeoShadowLight
import com.whispercppdemo.ui.theme.NeoSurface
import com.whispercppdemo.ui.theme.NeoText
import com.whispercppdemo.ui.theme.NeoTextFaint
import com.whispercppdemo.ui.theme.NeoTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(28.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    MainScreen(
        canTranscribe = viewModel.canTranscribe,
        isRecording = viewModel.isRecording,
        transcript = viewModel.transcript,
        isTranscribing = viewModel.isTranscribing,
        status = viewModel.status,
        notes = viewModel.notes,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::deleteNote,
        onRecordTapped = viewModel::toggleRecord
    )
}

@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    transcript: String,
    isTranscribing: Boolean,
    status: String,
    notes: List<Note>,
    onSaveNote: (String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onRecordTapped: () -> Unit
) {
    val context = LocalContext.current
    var showNotes by remember { mutableStateOf(false) }
    var showA11yDialog by remember { mutableStateOf(false) }

    // Live accessibility-service status: re-check every time the app resumes
    // (the user enables it in system settings, then comes back).
    var a11yEnabled by remember { mutableStateOf(KifakAccessibilityService.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = KifakAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("transcription", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    if (showNotes) {
        NotesScreen(
            notes = notes,
            onBack = { showNotes = false },
            onCopyNote = ::copyToClipboard,
            onShareNote = { shareText(context, it) },
            onDeleteNote = onDeleteNote
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // ---- header -------------------------------------------------------
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) NeoBrown else NeoAccentBright)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isRecording) "Listening…" else "Kifak",
                style = MaterialTheme.typography.titleMedium,
                color = if (isRecording) NeoBrown else NeoText
            )
            Spacer(Modifier.weight(1f))
            // saved-notes entry point
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = NeoShadowLight, spotColor = NeoShadowDark)
                    .clip(CircleShape)
                    .background(NeoSurface)
                    .border(1.dp, NeoShadowDark.copy(alpha = 0.5f), CircleShape)
                    .clickable { showNotes = true },
                contentAlignment = Alignment.Center
            ) {
                NotesIcon(tint = NeoTextSecondary)
                if (notes.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(NeoAccent)
                    )
                }
            }
        }

        // ---- visualizer ---------------------------------------------------
        Spacer(Modifier.height(22.dp))
        VoiceVisualizer(active = isRecording, modifier = Modifier.fillMaxWidth().height(64.dp))

        // ---- transcript well ----------------------------------------------
        Spacer(Modifier.height(22.dp))
        TranscriptWell(
            text = transcript,
            onCopyTapped = { copyToClipboard(transcript) }
        )

        // ---- secondary actions --------------------------------------------
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeoPillButton(
                text = "Save",
                enabled = transcript.isNotBlank(),
                onClick = {
                    onSaveNote(transcript)
                    Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )
            NeoPillButton(
                text = "Copy",
                enabled = transcript.isNotBlank(),
                onClick = { copyToClipboard(transcript) },
                modifier = Modifier.weight(1f)
            )
            NeoPillButton(
                text = "Share",
                enabled = transcript.isNotBlank(),
                onClick = { shareText(context, transcript) },
                modifier = Modifier.weight(1f)
            )
        }

        // ---- accessibility shortcut row --------------------------------------
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(PillShape)
                .clickable { showA11yDialog = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Voice button in other apps",
                style = MaterialTheme.typography.labelMedium,
                color = NeoTextSecondary
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(PillShape)
                    .background(if (a11yEnabled) NeoAccent.copy(alpha = 0.15f) else NeoInset)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (a11yEnabled) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (a11yEnabled) NeoAccent else NeoTextFaint
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- status / progress ---------------------------------------------
        Spacer(Modifier.height(14.dp))
        if (isTranscribing) {
            TranscribingBar(modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                text = status.ifBlank { "Ready" },
                style = MaterialTheme.typography.labelSmall,
                color = NeoTextFaint,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(14.dp))

        // ---- main mic button ------------------------------------------------
        RecordButton(
            enabled = canTranscribe,
            isRecording = isRecording,
            onClick = onRecordTapped,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(30.dp))
    }

    if (showA11yDialog) {
        AccessibilitySetupDialog(
            enabled = a11yEnabled,
            onDismiss = { showA11yDialog = false }
        )
    }
}

/** Explains the accessibility permission and walks the user through enabling it. */
@Composable
private fun AccessibilitySetupDialog(enabled: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Voice button in any app",
                color = NeoText
            )
        },
        text = {
            Column {
                Text(
                    text = if (enabled) "The voice button is ON. In any app with a " +
                        "text field open (WhatsApp, Messages, browser): tap the " +
                        "accessibility icon in the corner — first tap records, " +
                        "second tap stops, and your Arabizi text is pasted right " +
                        "where the cursor is.\n\nMake sure the accessibility " +
                        "button is visible: Accessibility \u2192 Accessibility button " +
                        "\u2192 select Kifak."
                    else
                        "Kifak can voice-type straight into any app. With a text " +
                        "field open (e.g. a WhatsApp chat), the corner button " +
                        "records your voice, transcribes it on-device, and pastes " +
                        "the Arabizi text at the cursor — you never leave the chat.\n\n" +
                        "Android requires a one-time permission:\n\n" +
                        "1. Tap \u201COpen Settings\u201D below\n" +
                        "2. Tap \u201CInstalled apps\u201D or \u201CDownloaded apps\u201D\n" +
                        "3. Choose \u201CKifak voice typing\u201D and turn it On\n" +
                        "4. Accept the \u201Cfull control\u201D prompt — Kifak only ever " +
                        "touches the text field you are typing in, to paste your " +
                        "own words\n" +
                        "5. Show the corner button: Accessibility \u2192 Accessibility " +
                        "button \u2192 select Kifak\n\n" +
                        "Samsung: Settings \u2192 Accessibility \u2192 Installed apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoTextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                onDismiss()
            }) { Text("Open Settings", color = NeoAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NeoTextFaint) }
        },
        containerColor = NeoSurface,
        titleContentColor = NeoText,
        textContentColor = NeoTextSecondary
    )
}

/** Send text through the Android share sheet (WhatsApp, Telegram, SMS, …). */
private fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share transcription"))
}

// ---------------------------------------------------------------- components

/** Sliding brown bar shown while the model is transcribing; stops when done. */
@Composable
private fun TranscribingBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loadbar")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "loadphase"
    )
    BoxWithConstraints(
        modifier = modifier
            .height(7.dp)
            .clip(PillShape)
            .background(NeoInset)
            .border(1.dp, NeoShadowDark.copy(alpha = 0.4f), PillShape)
    ) {
        val w = maxWidth
        Box(
            Modifier
                .offset(x = w * phase)
                .width(w * 0.38f)
                .fillMaxHeight()
                .background(NeoBrown, PillShape)
        )
    }
}

@Composable
private fun NeoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = CardShape, ambientColor = NeoShadowLight, spotColor = NeoShadowDark)
            .clip(CardShape)
            .background(NeoSurface)
            .border(1.dp, Color.White.copy(alpha = 0.8f), CardShape)
    ) { content() }
}

@Composable
private fun TranscriptWell(text: String, onCopyTapped: () -> Unit) {
    NeoCard(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NeoInset)
                .border(1.dp, NeoShadowDark.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        ) {
            if (text.isBlank()) {
                Text(
                    "Your words appear here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeoTextFaint,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeoText,
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        )
                    }
                    // tap-to-copy affordance inside the well
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(PillShape)
                            .clickable(onClick = onCopyTapped)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CopyIcon(tint = NeoTextFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("tap to copy", style = MaterialTheme.typography.labelSmall, color = NeoTextFaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(
    notes: List<Note>,
    onBack: () -> Unit,
    onCopyNote: (String) -> Unit,
    onShareNote: (String) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // back button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = NeoShadowLight, spotColor = NeoShadowDark)
                    .clip(CircleShape)
                    .background(NeoSurface)
                    .border(1.dp, NeoShadowDark.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                BackIcon(tint = NeoTextSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Saved Notes",
                style = MaterialTheme.typography.titleMedium,
                color = NeoText
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${notes.size}",
                style = MaterialTheme.typography.labelLarge,
                color = NeoTextFaint
            )
        }
        Spacer(Modifier.height(20.dp))

        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Notes you save appear here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeoTextFaint
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onCopy = { onCopyNote(note.text) },
                        onShare = { onShareNote(note.text) },
                        onDelete = { onDeleteNote(note.id) }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoteCard(note: Note, onCopy: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(note.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = NeoTextFaint
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyLarge,
                color = NeoText,
                maxLines = 6
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniAction(label = "Copy", onClick = onCopy)
                MiniAction(label = "Share", onClick = onShare)
                MiniAction(label = "Delete", onClick = onDelete, danger = true)
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(NeoInset)
            .border(1.dp, NeoShadowDark.copy(alpha = 0.55f), PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (danger) NeoBrownDeep else NeoTextSecondary
        )
    }
}

@Composable
private fun NeoPillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .alpha(alpha)
            .shadow(elevation = 8.dp, shape = PillShape, ambientColor = NeoShadowLight, spotColor = NeoShadowDark)
            .clip(PillShape)
            .background(NeoSurface)
            .border(1.dp, NeoShadowDark.copy(alpha = 0.45f), PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = NeoTextSecondary)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(
    enabled: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted -> if (granted) onClick() }
    )

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(modifier = modifier.size(96.dp), contentAlignment = Alignment.Center) {
        if (isRecording) {
            // soft outer glow ring while recording
            Box(
                Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(NeoAccent.copy(alpha = 0.18f))
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(if (isRecording) pulseScale else 1f)
                .shadow(elevation = 14.dp, shape = CircleShape, ambientColor = NeoShadowLight, spotColor = NeoShadowDark)
                .clip(CircleShape)
                .background(if (isRecording) NeoAccent else NeoSurface)
                .border(
                    1.dp,
                    if (isRecording) NeoAccentSoft else NeoShadowDark.copy(alpha = 0.5f),
                    CircleShape
                )
                .clickable(enabled = enabled) {
                    if (micPermissionState.status.isGranted) onClick() else micPermissionState.launchPermissionRequest()
                },
            contentAlignment = Alignment.Center
        ) {
            MicIcon(tint = if (isRecording) Color.White else NeoAccent)
        }
    }
}

/** Small notebook icon: rounded page with a spiral binding and text lines. */
@Composable
private fun NotesIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        // page
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.2f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.82f),
            cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
            style = stroke
        )
        // spiral rings
        for (i in 0..2) {
            val y = h * (0.26f + i * 0.24f)
            drawLine(
                color = tint,
                start = Offset(w * 0.08f, y),
                end = Offset(w * 0.3f, y),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
        }
        // text lines
        for (i in 0..1) {
            val y = h * (0.4f + i * 0.2f)
            drawLine(
                color = tint,
                start = Offset(w * 0.42f, y),
                end = Offset(w * 0.72f, y),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CopyIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        // back sheet
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.3f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke
        )
        // front sheet
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke
        )
    }
}

@Composable
private fun BackIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.14f, cap = StrokeCap.Round)
        drawLine(
            color = tint,
            start = Offset(w * 0.62f, h * 0.15f),
            end = Offset(w * 0.28f, h * 0.5f),
            strokeWidth = w * 0.14f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.28f, h * 0.5f),
            end = Offset(w * 0.62f, h * 0.85f),
            strokeWidth = w * 0.14f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MicIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        // mic capsule
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.36f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )
        // cradle arc
        drawArc(
            color = tint,
            startAngle = -20f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
            style = stroke
        )
        // stem
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.92f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )
        // base
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.92f),
            end = Offset(w * 0.68f, h * 0.92f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun VoiceVisualizer(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "viz")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase"
    )
    val barHeights = remember { List(28) { 0.25f + 0.75f * kotlin.math.abs(Math.sin(it * 0.7)).toFloat() } }

    Canvas(modifier = modifier) {
        val n = barHeights.size
        val gap = 5.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        val midY = size.height / 2f
        for (i in 0 until n) {
            val amp = if (active) {
                // travelling wave while recording
                0.35f + 0.65f * kotlin.math.abs(Math.sin(phase + i * 0.55)).toFloat()
            } else {
                // calm baseline
                0.12f + 0.05f * kotlin.math.abs(Math.sin(phase * 0.5 + i * 0.4)).toFloat()
            }
            val target = barHeights[i] * amp * size.height
            val x = i * (barW + gap)
            drawRoundRect(
                color = if (active) NeoBrownDeep.copy(alpha = 0.9f) else NeoBrown.copy(alpha = 0.55f),
                topLeft = Offset(x, midY - target / 2f),
                size = androidx.compose.ui.geometry.Size(barW, target),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}
