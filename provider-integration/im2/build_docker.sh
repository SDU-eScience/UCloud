#!/usr/bin/env bash
version=`cat ../../backend/version.txt`
docker buildx build \
    -f Dockerfile \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud/im2:${version} \
    --platform linux/arm64/v8,linux/amd64 \
    ..
