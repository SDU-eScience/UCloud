#!/usr/bin/env bash
set -e

BINARY_NAME="ucloud"
MAIN_PACKAGE="./cmd/ucloud"

echo "Building ${BINARY_NAME} cli tool..."
go build -o "${BINARY_NAME}" "${MAIN_PACKAGE}"

echo "Built ./${BINARY_NAME}"