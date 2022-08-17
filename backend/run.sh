#!/usr/bin/env bash
set -x
./start_debugger.sh
gradle :launcher:run --console=plain --args="--dev --config-dir /etc/ucloud $*"
