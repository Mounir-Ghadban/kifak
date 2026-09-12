package com.whispercppdemo

object WhisperLib {
    init {
        // This must match the name of the library in your CMakeLists.txt
        System.loadLibrary("whisper_jni")
    }

    // These functions link directly to the C++ code
    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
}