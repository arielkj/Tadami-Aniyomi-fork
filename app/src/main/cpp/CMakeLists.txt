# Native build for on-device Real-ESRGAN cover upscaling (v7.9.7).
#
# Links the vendored xinntao/Real-ESRGAN-ncnn-vulkan sources
# (app/src/main/cpp/realesrgan) against the official ncnn Android Vulkan
# prebuilt. The prebuilt is NOT committed: CI downloads it (pinned version +
# SHA-256, see .github/workflows/build-release.yml) into
# app/src/main/cpp/ncnn-android/<abi>/ (gitignored). A local build needs the
# same directory; see the workflow step for the exact commands.
#
# The .comp shaders are compiled at build time by glslangValidator
# (apt: glslang-tools on the CI runner), exactly like upstream.

cmake_minimum_required(VERSION 3.22.1)

project(realesrgan-jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- ncnn prebuilt -------------------------------------------------------
set(NCNN_PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn-android"
    CACHE PATH "ncnn Android Vulkan prebuilt root (per-ABI dirs)")
if(NOT EXISTS "${NCNN_PREBUILT_DIR}/${ANDROID_ABI}/lib/cmake/ncnn/ncnnConfig.cmake")
    message(FATAL_ERROR
        "ncnn prebuilt not found at ${NCNN_PREBUILT_DIR}/${ANDROID_ABI}. "
        "Download ncnn-20260526-android-vulkan.zip, verify its SHA-256 and "
        "unzip it into ${NCNN_PREBUILT_DIR} (see the CI workflow).")
endif()
list(APPEND CMAKE_PREFIX_PATH "${NCNN_PREBUILT_DIR}/${ANDROID_ABI}")
# El toolchain de Android pone CMAKE_FIND_ROOT_PATH_MODE_PACKAGE en ONLY,
# asi que find_package IGNORA CMAKE_PREFIX_PATH fuera del sysroot del NDK.
# Las variables <Package>_DIR se consultan antes y van directas: sin
# ellas, find_package(glslang) y find_package(ncnn) no ven el prebuilt.
set(glslang_DIR "${NCNN_PREBUILT_DIR}/${ANDROID_ABI}/lib/cmake/glslang")
set(ncnn_DIR "${NCNN_PREBUILT_DIR}/${ANDROID_ABI}/lib/cmake/ncnn")

find_package(Threads)
# glslang first: the ncnn config requires its targets to exist.
find_package(glslang REQUIRED)
find_package(ncnn REQUIRED)

# --- SPIR-V shaders (upstream compile_shader macro) -----------------------
find_program(GLSLANGVALIDATOR_EXECUTABLE NAMES glslangValidator)
if(NOT GLSLANGVALIDATOR_EXECUTABLE)
    message(FATAL_ERROR
        "glslangValidator not found. Install glslang-tools (apt) or the Vulkan SDK.")
endif()
message(STATUS "Found glslangValidator: ${GLSLANGVALIDATOR_EXECUTABLE}")

macro(compile_shader SHADER_SRC)
    set(SHADER_SRC_FULLPATH ${CMAKE_CURRENT_SOURCE_DIR}/${SHADER_SRC})
    get_filename_component(SHADER_SRC_NAME_WE ${SHADER_SRC} NAME_WE)
    set(SHADER_SPV_HEX_FILE ${CMAKE_CURRENT_BINARY_DIR}/${SHADER_SRC_NAME_WE}.spv.hex.h)
    add_custom_command(
        OUTPUT ${SHADER_SPV_HEX_FILE}
        COMMAND ${GLSLANGVALIDATOR_EXECUTABLE}
        ARGS -V -s -x -o ${SHADER_SPV_HEX_FILE} ${SHADER_SRC_FULLPATH}
        DEPENDS ${SHADER_SRC_FULLPATH}
        COMMENT "Building SPIR-V module ${SHADER_SRC_NAME_WE}.spv"
        VERBATIM
    )
    set_source_files_properties(${SHADER_SPV_HEX_FILE} PROPERTIES GENERATED TRUE)
    list(APPEND SHADER_SPV_HEX_FILES ${SHADER_SPV_HEX_FILE})

    # fp16 storage
    set(SHADER_fp16s_SRC_NAME_WE "${SHADER_SRC_NAME_WE}_fp16s")
    set(SHADER_fp16s_SPV_HEX_FILE ${CMAKE_CURRENT_BINARY_DIR}/${SHADER_fp16s_SRC_NAME_WE}.spv.hex.h)
    add_custom_command(
        OUTPUT ${SHADER_fp16s_SPV_HEX_FILE}
        COMMAND ${GLSLANGVALIDATOR_EXECUTABLE}
        ARGS -DNCNN_fp16_storage=1 -V -s -x -o ${SHADER_fp16s_SPV_HEX_FILE} ${SHADER_SRC_FULLPATH}
        DEPENDS ${SHADER_SRC_FULLPATH}
        COMMENT "Building SPIR-V module ${SHADER_fp16s_SRC_NAME_WE}.spv"
        VERBATIM
    )
    set_source_files_properties(${SHADER_fp16s_SPV_HEX_FILE} PROPERTIES GENERATED TRUE)
    list(APPEND SHADER_SPV_HEX_FILES ${SHADER_fp16s_SPV_HEX_FILE})

    # int8 storage
    set(SHADER_int8s_SRC_NAME_WE "${SHADER_SRC_NAME_WE}_int8s")
    set(SHADER_int8s_SPV_HEX_FILE ${CMAKE_CURRENT_BINARY_DIR}/${SHADER_int8s_SRC_NAME_WE}.spv.hex.h)
    add_custom_command(
        OUTPUT ${SHADER_int8s_SPV_HEX_FILE}
        COMMAND ${GLSLANGVALIDATOR_EXECUTABLE}
        ARGS -DNCNN_fp16_storage=1 -DNCNN_int8_storage=1 -V -s -x -o ${SHADER_int8s_SPV_HEX_FILE} ${SHADER_SRC_FULLPATH}
        DEPENDS ${SHADER_SRC_FULLPATH}
        COMMENT "Building SPIR-V module ${SHADER_int8s_SRC_NAME_WE}.spv"
        VERBATIM
    )
    set_source_files_properties(${SHADER_int8s_SPV_HEX_FILE} PROPERTIES GENERATED TRUE)
    list(APPEND SHADER_SPV_HEX_FILES ${SHADER_int8s_SPV_HEX_FILE})
endmacro()

include_directories(${CMAKE_CURRENT_BINARY_DIR})

set(SHADER_SPV_HEX_FILES)
compile_shader(realesrgan/realesrgan_preproc.comp)
compile_shader(realesrgan/realesrgan_postproc.comp)
compile_shader(realesrgan/realesrgan_preproc_tta.comp)
compile_shader(realesrgan/realesrgan_postproc_tta.comp)

add_custom_target(generate-spirv DEPENDS ${SHADER_SPV_HEX_FILES})

# --- JNI library ---------------------------------------------------------
add_library(realesrgan-jni SHARED
    realesrgan/realesrgan.cpp
    realesrgan/realesrgan_jni.cpp
)

add_dependencies(realesrgan-jni generate-spirv)

target_include_directories(realesrgan-jni PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}
    realesrgan
)

# Match the ncnn prebuilt (built with -fno-rtti -fno-exceptions).
target_compile_options(realesrgan-jni PRIVATE -fno-rtti -fno-exceptions)

target_link_libraries(realesrgan-jni
    ncnn
    vulkan
    android
    log
)
