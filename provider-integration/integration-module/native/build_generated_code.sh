#!/usr/bin/env bash
target_file=../src/main/kotlin/libc/SharedLibraryData.kt
cat > $target_file << EOF
// AUTO-GENERATED FILE SEE native/build.sh
// AUTO-GENERATED FILE SEE native/build.sh
// AUTO-GENERATED FILE SEE native/build.sh
package libc

val libcSharedData = """
EOF
cat libc_wrapper.so | base64 | fold -w 118 >> $target_file
cat >> $target_file << EOF
"""
EOF
