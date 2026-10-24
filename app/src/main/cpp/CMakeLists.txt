cmake_minimum_required(VERSION 3.22.1)
project(expenses_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp закреплён на релизе: API библиотеки часто меняется.
include(FetchContent)
FetchContent_Declare(
    llama
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/b11854.tar.gz
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
# Телефоны 2022+ (в т.ч. Galaxy S26) умеют dotprod и i8mm — с ними матричные операции в разы быстрее.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "" FORCE)

FetchContent_MakeAvailable(llama)

add_library(expenses_llm SHARED llama_jni.cpp)
target_link_libraries(expenses_llm PRIVATE llama ggml android log)
