# Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.
# the 3d room of Audio Lab Stage: one small library with no third-party code, built by the buildStageJni task
cmake_minimum_required(VERSION 3.22.1)
project(mochystage LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(mochystage SHARED stage/splitter.cpp stage/hrtf.cpp stage/tail.cpp stage/engine.cpp stage/stage_jni.cpp)
# o3 lets the compiler vectorise the ear filters, the biggest cost of the room
target_compile_options(mochystage PRIVATE -O3 -ffp-contract=fast -Wall -fvisibility=hidden)
# 16 kb pages on android 15 phones need the segments aligned to them
target_link_options(mochystage PRIVATE "-Wl,-z,max-page-size=16384")
