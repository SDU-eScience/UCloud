#!/usr/bin/env bash
g++ -c -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -I${JAVA_HOME}/include/darwin libc_wrapper.cpp -o libc_wrapper.o
g++ -shared -fPIC -o libc_wrapper.so *.o -lc
rm *.o
./build_generated_code.sh
