#!/usr/bin/env bash
set -e

MAIN_PACKAGE="./cmd/ucloud"

go run "${MAIN_PACKAGE}" "$@"