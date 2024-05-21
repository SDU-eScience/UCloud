#!/usr/bin/env bash
arch=`uname -m`
var_name=libcSharedData
target_file=../src/main/kotlin/libc/SharedLibraryData.kt
if [[ $arch = "aarch64" ]]; then
	target_file=../src/main/kotlin/libc/SharedLibraryDataArm64.kt
	var_name=libcSharedDataArm64
fi
cat > $target_file << EOF
// AUTO-GENERATED FILE SEE native/build.sh
// AUTO-GENERATED FILE SEE native/build.sh
// AUTO-GENERATED FILE SEE native/build.sh
package libc

val $var_name = """
EOF
cat libc_wrapper.so | base64 | fold -w 118 >> $target_file
cat >> $target_file << EOF
"""
EOF
