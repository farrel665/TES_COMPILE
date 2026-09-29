cmake_minimum_required(VERSION 3.22.1)

set(CMAKE_C_COMPILER_WORKS 1)
set(CMAKE_CXX_COMPILER_WORKS 1)

project(ancore_engine C)

add_executable(ancore_engine
    touch/worker_main.c
    touch/touch_io.c
    touch/process_watcher.c
    touch/reaper.c
)
target_include_directories(ancore_engine PRIVATE touch)
target_link_libraries(ancore_engine log m)

set_target_properties(ancore_engine PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "ancoreengine"
    SUFFIX ".so"
)

set(JNILIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
file(MAKE_DIRECTORY ${JNILIBS_DIR})

add_custom_command(TARGET ancore_engine POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:ancore_engine>
        ${JNILIBS_DIR}/libancoreengine.so
)
