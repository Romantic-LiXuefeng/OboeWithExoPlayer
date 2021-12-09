#
# Copyright 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.4.1)

### INCLUDE OBOE LIBRARY ###

# Set the path to the Oboe library directory
set (OBOE_DIR ../../../../../)

# Add the Oboe library as a subproject. Since Oboe is an out-of-tree source library we must also
# specify a binary directory
add_subdirectory(${OBOE_DIR} ./oboe-bin)
# Include the SDK headers
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/common
        ${CMAKE_CURRENT_SOURCE_DIR}/player
        ${CMAKE_CURRENT_SOURCE_DIR}/recorder)

# Include the Oboe headers and shared sample code
include_directories(${OBOE_DIR}/include ${OBOE_DIR}/samples/shared)

# Debug utilities
set (DEBUG_UTILS_PATH "${OBOE_DIR}/samples/debug-utils")
set (DEBUG_UTILS_SOURCES ${DEBUG_UTILS_PATH}/trace.cpp)
include_directories(${DEBUG_UTILS_PATH})


### END OBOE INCLUDE SECTION ###

# App specific sources
set (APP_SOURCES
        common/OboeStream.cpp
        common/StreamBase.cpp
        player/BlipAudioSource.cpp
        player/JavaSourceProxy.cpp
        player/NativeAudioSource.cpp
        player/NoiseAudioSource.cpp
        player/OboePlayer.cpp
        player/SinAudioSource.cpp
        player/SilenceAudioSource.cpp
        player/WaveTableSource.cpp
        recorder/AppCallbackAudioSink.cpp
        recorder/DefaultAudioSink.cpp
        recorder/NativeAudioSink.cpp
        recorder/OboeRecorder.cpp
)

# Build the libmegaaudio_jni library
add_library(megaaudio_jni SHARED
            ${DEBUG_UTILS_SOURCES}
            ${APP_SOURCES}
            )

# Specify the libraries needed for megaaudio_jni
target_link_libraries(megaaudio_jni android log oboe)

# Enable optimization flags: if having problems with source level debugging,
# disable -Ofast ( and debug ), re-enable after done debugging.
target_compile_options(
        megaaudio_jni
        PRIVATE -Wall -Werror -Wno-unused-parameter -Wno-unused-variable
        "$<$<CONFIG:RELEASE>:-Ofast>")
