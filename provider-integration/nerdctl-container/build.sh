#!/usr/bin/env bash
version="2.3.5"
docker buildx build \
    --tag dreg.cloud.sdu.dk/ucloud-dev/nerdctl-support:${version} \
    --platform linux/arm64/v8,linux/amd64/v2,linux/amd64 \
    --push \
    .

