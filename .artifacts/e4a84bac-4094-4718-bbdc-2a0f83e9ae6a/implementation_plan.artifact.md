# Implementation Plan - Reliability and Safety Improvements

This plan implements the JNI safety patterns, Kotlin concurrency guards, and prompt sanitization as discussed. We will adapt the proposed snippets to the actual project structure, ensuring that we maintain compatibility between the native and Kotlin layers.

## User Review Required

> [!IMPORTANT]
> The JNI signature for `fullTranscribe` will be updated to include an `initial_prompt` parameter and return the transcribed text directly as a `jstring`. This consolidates the transcription logic in the native layer for better performance and safety, but it requires updating the `LibWhisper.kt` file as well.

## Proposed Changes

### Native Layer (lib)

#### [MODIFY] [jni.c](file:///C:/Users/user/Downloads/whisper.cpp-master/examples/whisper.android/lib/src/main/jni/whisper/jni.c)
- Update logging macros to include `LOGW` and `LOGE`.
- Rewrite `fullTranscribe` to:
    - Include `jstring prompt` parameter.
    - Return `jstring` (the full transcribed text).
    - Use the single-exit `goto cleanup` pattern.
    - Implement null checks for `context` and `audio_data_arr`.
    - Implement a length check for `initial_prompt`.
    - Release all JNI resources (`ReleaseFloatArrayElements`, `ReleaseStringUTFChars`) in the cleanup block.

#### [MODIFY] [LibWhisper.kt](file:///C:/Users/user/Downloads/whisper.cpp-master/examples/whisper.android/lib/src/main/java/com/whispercpp/whisper/LibWhisper.kt)
- Update `WhisperLib.fullTranscribe` external declaration:
    - Add `prompt: String?` parameter.
    - Change return type to `String`.
- Update `WhisperContext.transcribeData`:
    - Accept an optional `prompt: String?`.
    - Call the updated `fullTranscribe`.
    - Simplify the function as it no longer needs to manually iterate over segments in Kotlin (the JNI layer will return the full string).

### Application Layer (app)

#### [MODIFY] [MainScreenViewModel.kt](file:///C:/Users/user/Downloads/whisper.cpp-master/examples/whisper.android/app/src/main/java/com/whispercppdemo/ui/main/MainScreenViewModel.kt)
- Add `TranscriptionState` enum.
- Add `transcribeMutex` and `state` (StateFlow) to `MainScreenViewModel`.
- Implement `sanitizePrompt(raw: String, maxChars: Int): String`.
- Update `transcribeAudio` to:
    - Use the `Mutex` to prevent concurrent transcriptions.
    - Update `state` to reflect the current activity (IDLE, TRANSCRIBING).
    - Call `whisperContext.transcribeData` with a sanitized prompt.
    - Update the UI log with the results or errors.

## Verification Plan

### Automated Tests
- Build the project to ensure JNI signatures match.
- Run the app and perform a transcription to verify the `goto cleanup` path works and resources are released.

### Manual Verification
- Test transcription with and without a prompt.
- Test with very long prompts to verify the sanitization logic.
- Trigger multiple transcription requests quickly to verify the `Mutex` correctly ignores overlapping calls.
