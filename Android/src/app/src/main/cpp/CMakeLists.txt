cmake_minimum_required(VERSION 3.22.1)

project(recovery_embedding_bridge LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/sentencepiece
  ${CMAKE_CURRENT_BINARY_DIR}/sentencepiece)

add_library(recovery_embedding_bridge SHARED native_embedding_bridge.cpp)

target_include_directories(
  recovery_embedding_bridge
  PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/sentencepiece/src
)

target_link_libraries(
  recovery_embedding_bridge
  sentencepiece-static
  log
)
