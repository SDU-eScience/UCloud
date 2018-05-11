cmake_minimum_required(VERSION 3.0)
project(native)

set(CMAKE_CXX_STANDARD 11)

add_executable(dirlisting main.cpp libacl.cpp libacl.h)
add_executable(ceph-tree tree.cpp libacl.cpp libacl.h)
add_executable(ceph-copy copy.cpp libacl.cpp libacl.h)

if (${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
    set(MACOSX TRUE)
else ()
    set(MAXOSX FALSE)
endif ()

if (NOT MACOSX)
    target_link_libraries(dirlisting acl)
endif ()
