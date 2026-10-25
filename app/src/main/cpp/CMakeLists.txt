# Offline translation: Bergamot (MPL-2.0) with its marian; build flags as in RTranslator (Apache-2.0).
# Two libraries: libinkpeek_mt.so (translation + language identification), libinkpeek_llm.so (llama.cpp, the AI model).
cmake_minimum_required(VERSION 3.18)
project(inkpeek_native C CXX)

set(CMAKE_POLICY_VERSION_MINIMUM 3.5) # old sub-projects under CMake 4
foreach(opt COMPILE_CUDA USE_TCMALLOC BUILD_SHARED_LIBS USE_WASM_COMPATIBLE_SOURCE SPM_ENABLE_SHARED SPM_BUILD_TEST SPM_ENABLE_TCMALLOC COMPILE_TESTS)
  set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
foreach(opt USE_STATIC_LIBS THREADS_PREFER_PTHREAD_FLAG USE_PATHIECPP)
  set(${opt} ON CACHE BOOL "" FORCE)
endforeach()

# PCRE2 (BSD) for ssplit's sentence splitting, from our submodule (pinned to the version ssplit downloads itself):
# nothing is fetched while building. ssplit takes it as a found library.
foreach(opt PCRE2_BUILD_PCRE2GREP PCRE2_BUILD_TESTS SSPLIT_USE_INTERNAL_PCRE2)
  set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
foreach(opt PCRE2_SUPPORT_JIT PCRE2_STATIC_PIC)
  set(${opt} ON CACHE BOOL "" FORCE)
endforeach()
add_subdirectory(pcre2 EXCLUDE_FROM_ALL)
set(PCRE2_LIBRARIES pcre2-8-static CACHE STRING "" FORCE)
set(PCRE2_INCLUDE_DIRS ${CMAKE_CURRENT_BINARY_DIR}/pcre2 CACHE PATH "" FORCE)
set(BUILD_ARCH armv8-a CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-enum-constexpr-conversion")

add_subdirectory(bergamot EXCLUDE_FROM_ALL)

# Language identification: fastText (MIT), its library sources only (no main.cc).
file(GLOB FASTTEXT_SOURCES fasttext/src/*.cc)
list(FILTER FASTTEXT_SOURCES EXCLUDE REGEX ".*/main\\.cc$")

add_library(inkpeek_mt SHARED translator_jni.cpp langid_jni.cpp ${FASTTEXT_SOURCES})
target_include_directories(inkpeek_mt PRIVATE fasttext/src)
target_link_libraries(inkpeek_mt bergamot-translator android log)

# AI translation (an optional pack): llama.cpp (MIT) in its own library. Its kernels use int8 dot product, so the app
# loads it only on a CPU that has it (Device: asimddp); the weights are mapped as they are, not repacked.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)
foreach(opt GGML_OPENMP GGML_LLAMAFILE GGML_BACKEND_DL GGML_CPU_KLEIDIAI LLAMA_CURL LLAMA_OPENSSL LLAMA_BUILD_TESTS LLAMA_BUILD_EXAMPLES LLAMA_BUILD_TOOLS LLAMA_BUILD_SERVER LLAMA_BUILD_COMMON)
  set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory(llama EXCLUDE_FROM_ALL)
add_library(inkpeek_llm SHARED llm_jni.cpp)
target_link_libraries(inkpeek_llm llama log)
