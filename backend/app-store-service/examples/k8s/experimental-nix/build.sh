#!/usr/bin/env bash
set -x
docker buildx build \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud-experimental/ubuntu-nix:202404-1 \
    --platform linux/arm64/v8,linux/amd64 \
    .
