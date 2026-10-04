cmake_minimum_required(VERSION 3.22.1)
project(lfm_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(DEFINED ANDROID_ABI)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "" FORCE)
        set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
        set(GGML_OPENMP ON CACHE BOOL "" FORCE)
    endif()

    # ── Vulkan GPU backend ──────────────────────────────────────────────
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)

    # glslc runs on the HOST (x86_64 Linux) to compile shaders at build time.
    # Point CMake at the NDK's host-side shader-tools so FindVulkan finds glslc.
    set(Vulkan_GLSLC_EXECUTABLE
        "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc"
        CACHE FILEPATH "" FORCE)

    # Provide pristine Khronos Vulkan C++ headers (vulkan/vulkan.hpp) via isolated vulkan_include
    include_directories(SYSTEM "${CMAKE_CURRENT_SOURCE_DIR}/vulkan_include")

    # SPIRV-Headers is a header-only package installed on the host.
    # The NDK toolchain restricts find_package to the sysroot, so we
    # temporarily allow searching the host cmake prefix too.
    set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
endif()

# llama.cpp upstream. `master` tracks LFM2 arch support (old bXXXX tags retired in 2025).
include(FetchContent)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
  llama
  GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
  GIT_TAG        master
  GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama)

add_library(lfm_jni SHARED
  llama_jni.cpp
)

# Link llama, vulkan, android, and log
target_link_libraries(lfm_jni
  PRIVATE
    llama
    vulkan
    android
    log
)

target_include_directories(lfm_jni PRIVATE
  ${llama_SOURCE_DIR}/include
  ${llama_SOURCE_DIR}/common
  ${llama_SOURCE_DIR}/ggml/include
)
