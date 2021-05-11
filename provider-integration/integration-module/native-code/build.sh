#!/usr/bin/env bash
rm -f *.o *.a
gcc -D_GNU_SOURCE -O3 -c -o ucloud.o ucloud.c
ar rsc libucloud.a ucloud.o
rm *.o
