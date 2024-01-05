#!/usr/bin/env bash
set -x
./start_debugger.sh
./gradlew :launcher:run --console=plain --args="--dev --config-dir /etc/ucloud $*"
