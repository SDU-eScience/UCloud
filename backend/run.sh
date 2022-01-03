#!/usr/bin/env bash
set -x
gradle :launcher:run --console=plain --args="--dev --config-dir /etc/ucloud $*"
