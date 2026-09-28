cmake_minimum_required(VERSION 3.22.1)

set(CMAKE_C_COMPILER_WORKS 1)
set(CMAKE_CXX_COMPILER_WORKS 1)

project(input_ancore C)

add_executable(input_ancore
    touch/worker_main.c
    touch/touch_io.c
    touch/process_watcher.c
    touch/reaper.c
)
target_include_directories(input_ancore PRIVATE touch)
target_link_libraries(input_ancore log m)

set_target_properties(input_ancore PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "input_ancore"
    SUFFIX ".so"
)

set(JNILIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
file(MAKE_DIRECTORY ${JNILIBS_DIR})

add_custom_command(TARGET input_ancore POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:input_ancore>
        ${JNILIBS_DIR}/libinput_ancore.so
)
