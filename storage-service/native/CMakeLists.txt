cmake_minimum_required(VERSION 3.0)
project(native)

set(CMAKE_CXX_STANDARD 11)

add_executable(native main.cpp libacl.cpp libacl.h)
target_link_libraries(native acl)