#!/usr/bin/env bash
version=`cat ../../../backend/version.txt`
docker buildx build \
    --tag dreg.cloud.sdu.dk/ucloud-dev/integration-module:${version} \
    --platform linux/arm64/v8 \
    .

    # --push \
    # --platform linux/arm64/v8,linux/amd64 \
