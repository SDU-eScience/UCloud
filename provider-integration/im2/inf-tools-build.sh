#!/usr/bin/env bash
export DOCKER_CLI_HINTS=false
set -e

repo=dreg.cloud.sdu.dk/ucloud
version=$(cat ../../core2/version.txt)

docker buildx build \
    --file inference-tools.Dockerfile \
    --push \
    --tag "$repo/ucloud-inf-tools:$version" \
    --platform linux/arm64/v8,linux/amd64 \
    .
