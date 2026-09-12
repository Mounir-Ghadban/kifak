#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

static void whisper_android_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void) user_data;
    android_LogPriority prio = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN) prio = ANDROID_LOG_WARN;
    __android_log_print(prio, "whisper.cpp", "%s", text);
}

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jlong JNICALL
Java_com_whispercppdemo_WhisperLib_initContext(JNIEnv *env, jobject /* this */, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGI("Failed to initialize whisper context");
        return 0;
    }

    LOGI("Successfully loaded whisper model!");
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_whispercppdemo_WhisperLib_freeContext(JNIEnv *env, jobject /* this */, jlong context_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper context freed.");
    }
}