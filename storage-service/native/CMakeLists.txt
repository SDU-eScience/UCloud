cmake_minimum_required(VERSION 3.0)
project(native)

set(CMAKE_CXX_STANDARD 11)

add_executable(
        ceph-interpreter

        interpreter.cpp

        utils.cpp
        utils.h

        libacl.cpp
        libacl.h

        copy.cpp
        copy.h

        tree.cpp
        tree.h

        xattr.cpp
        xattr.h

        list.cpp
        list.h

        delete.cpp
        delete.h

        stat.cpp
        stat.h
        mkdir.cpp mkdir.h move.cpp move.h file_info.cpp file_info.h)

if (${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
    set(MACOSX TRUE)
else ()
    set(MAXOSX FALSE)
endif ()

if (NOT MACOSX)
    target_link_libraries(dirlisting acl)
endif ()
