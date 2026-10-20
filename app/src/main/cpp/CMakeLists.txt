# Offline translation: Bergamot (MPL-2.0) with its marian; build flags as in RTranslator (Apache-2.0).
# One library: libinkpeek_mt.so (translation + language identification).
cmake_minimum_required(VERSION 3.18)
project(inkpeek_native C CXX)

set(CMAKE_POLICY_VERSION_MINIMUM 3.5) # old sub-projects under CMake 4
foreach(opt COMPILE_CUDA USE_TCMALLOC BUILD_SHARED_LIBS USE_WASM_COMPATIBLE_SOURCE SPM_ENABLE_SHARED SPM_BUILD_TEST SPM_ENABLE_TCMALLOC COMPILE_TESTS)
  set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
foreach(opt USE_STATIC_LIBS THREADS_PREFER_PTHREAD_FLAG SSPLIT_USE_INTERNAL_PCRE2 USE_PATHIECPP)
  set(${opt} ON CACHE BOOL "" FORCE)
endforeach()
set(BUILD_ARCH armv8-a CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-enum-constexpr-conversion")

add_subdirectory(bergamot EXCLUDE_FROM_ALL)

# Language identification: fastText (MIT), its library sources only (no main.cc).
file(GLOB FASTTEXT_SOURCES fasttext/src/*.cc)
list(FILTER FASTTEXT_SOURCES EXCLUDE REGEX ".*/main\\.cc$")

add_library(inkpeek_mt SHARED translator_jni.cpp langid_jni.cpp ${FASTTEXT_SOURCES})
target_include_directories(inkpeek_mt PRIVATE fasttext/src)
target_link_libraries(inkpeek_mt bergamot-translator android log)
