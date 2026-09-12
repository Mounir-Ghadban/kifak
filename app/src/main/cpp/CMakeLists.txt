cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

# 6 levels up from cpp/ reaches whisper.cpp-master root
set(WHISPER_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../..)

# Let whisper.cpp's own build system compile ggml + ggml-cpu + whisper
# correctly, instead of hand-listing files that go stale on reorganization
add_subdirectory(${WHISPER_ROOT} ${CMAKE_BINARY_DIR}/whisper_cpp_build)

add_library(whisper_jni SHARED
    whisper-jni.cpp
)

target_compile_definitions(whisper_jni PRIVATE
    _GNU_SOURCE
)

find_library(log-lib log)

# "whisper" is the target produced by add_subdirectory above —
# it already links ggml internally, so you don't link it separately
target_link_libraries(whisper_jni PRIVATE whisper ${log-lib})