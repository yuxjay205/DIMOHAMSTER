cmake_minimum_required(VERSION 3.22.1)
project("dimohamster" VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# =============================================================================
# GLM Header-Only Library (OpenGL Mathematics)
# =============================================================================
# GLM is included as a header-only library via the glm subdirectory
# Download GLM and place in app/src/main/cpp/glm/ or use FetchContent
include(FetchContent)
FetchContent_Declare(
    glm
    GIT_REPOSITORY https://github.com/g-truc/glm.git
    GIT_TAG 1.0.1
)
FetchContent_MakeAvailable(glm)

# =============================================================================
# FMOD Library Configuration (Optional)
# =============================================================================
# FMOD libraries should be placed in:
#   app/src/main/cpp/fmod/lib/${ANDROID_ABI}/
#   app/src/main/cpp/fmod/inc/
set(FMOD_DIR ${CMAKE_SOURCE_DIR}/fmod)
set(FMOD_INC_DIR ${FMOD_DIR}/inc)
set(FMOD_LIB_DIR ${FMOD_DIR}/lib/${ANDROID_ABI})

# Check if FMOD libraries exist
set(FMOD_ENABLED OFF)
if(EXISTS "${FMOD_LIB_DIR}/libfmod.so" AND EXISTS "${FMOD_LIB_DIR}/libfmodstudio.so")
    set(FMOD_ENABLED ON)
    message(STATUS "FMOD libraries found, audio enabled")

    # FMOD Core Library
    add_library(fmod SHARED IMPORTED)
    set_target_properties(fmod PROPERTIES
        IMPORTED_LOCATION ${FMOD_LIB_DIR}/libfmod.so
    )

    # FMOD Studio Library
    add_library(fmodstudio SHARED IMPORTED)
    set_target_properties(fmodstudio PROPERTIES
        IMPORTED_LOCATION ${FMOD_LIB_DIR}/libfmodstudio.so
    )
else()
    message(STATUS "FMOD libraries not found, building without audio support")
endif()

# =============================================================================
# Engine Source Files
# =============================================================================
set(ENGINE_SOURCES
    engine/Renderer.cpp
    engine/Camera.cpp
    engine/ShaderLoader.cpp
)

set(ENGINE_HEADERS
    engine/Renderer.h
    engine/Camera.h
    engine/ShaderLoader.h
)

# =============================================================================
# Game Source Files
# =============================================================================
set(GAME_SOURCES
    game/PaperTossGame.cpp
)

set(GAME_HEADERS
    game/PaperTossGame.h
)

# =============================================================================
# Audio Source Files (only if FMOD is available)
# =============================================================================
if(FMOD_ENABLED)
    set(AUDIO_SOURCES
        audio/AudioSystem.cpp
    )

    set(AUDIO_HEADERS
        audio/AudioSystem.h
    )
else()
    set(AUDIO_SOURCES "")
    set(AUDIO_HEADERS "")
endif()

# =============================================================================
# Main Native Library
# =============================================================================
add_library(${PROJECT_NAME} SHARED
    jni_bridge.cpp
    ${ENGINE_SOURCES}
    ${ENGINE_HEADERS}
    ${GAME_SOURCES}
    ${GAME_HEADERS}
    ${AUDIO_SOURCES}
    ${AUDIO_HEADERS}
)

# Include directories
target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/engine
    ${CMAKE_SOURCE_DIR}/game
    ${CMAKE_SOURCE_DIR}/audio
)

# Add FMOD includes if available
if(FMOD_ENABLED)
    target_include_directories(${PROJECT_NAME} PRIVATE ${FMOD_INC_DIR})
    target_compile_definitions(${PROJECT_NAME} PRIVATE FMOD_ENABLED=1)
else()
    target_compile_definitions(${PROJECT_NAME} PRIVATE FMOD_ENABLED=0)
endif()

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
target_link_libraries(${PROJECT_NAME}
    # Android system libraries
    ${log-lib}
    ${android-lib}

    # OpenGL ES 3.2 and EGL
    GLESv3
    EGL

    # GLM (header-only, linked for include paths)
    glm::glm
)

# Link FMOD libraries if available
if(FMOD_ENABLED)
    target_link_libraries(${PROJECT_NAME}
        fmod
        fmodstudio
    )
endif()

# Compiler flags for optimization
target_compile_options(${PROJECT_NAME} PRIVATE
    -Wall
    -Wextra
    -O2
    -ffast-math
)

# Enable NEON for ARM architectures (SIMD optimization)
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(${PROJECT_NAME} PRIVATE -march=armv8-a)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(${PROJECT_NAME} PRIVATE -mfpu=neon -mfloat-abi=softfp)
endif()

# =============================================================================
# Installation Rules (for AAB packaging)
# =============================================================================
# Ensure FMOD libraries are packaged with the APK (if available)
if(FMOD_ENABLED)
    install(FILES
        ${FMOD_LIB_DIR}/libfmod.so
        ${FMOD_LIB_DIR}/libfmodstudio.so
        DESTINATION lib/${ANDROID_ABI}
    )
endif()
