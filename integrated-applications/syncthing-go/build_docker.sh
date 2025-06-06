#!/usr/bin/env bash
version=latest

docker buildx build \
    -f dev.Dockerfile \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud/syncthing-go-dev:${version} \
    --platform linux/arm64/v8,linux/amd64 \
    .

docker buildx build \
    -f Dockerfile \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud/syncthing-go:${version} \
    --platform linux/arm64/v8,linux/amd64 \
    .

