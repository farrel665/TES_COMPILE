cmake_minimum_required(VERSION 3.22.1)

set(CMAKE_C_COMPILER_WORKS 1)
set(CMAKE_CXX_COMPILER_WORKS 1)

project(rizxbyte_engine C)

add_executable(rizxbyte_engine
    touch/worker_main.c
    touch/touch_io.c
    touch/process_watcher.c
    touch/reaper.c
)
target_include_directories(rizxbyte_engine PRIVATE touch)
target_link_libraries(rizxbyte_engine log m)

set_target_properties(rizxbyte_engine PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "rizxbyteengine"
    SUFFIX ".so"
)

set(JNILIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
file(MAKE_DIRECTORY ${JNILIBS_DIR})

add_custom_command(TARGET rizxbyte_engine POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:rizxbyte_engine>
        ${JNILIBS_DIR}/librizxbyteengine.so
)
