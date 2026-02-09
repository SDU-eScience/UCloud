#!/usr/bin/env bash
version=`cat ../../../core2/version.txt`
docker buildx build \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud-dev/slurm:${version} \
    --platform linux/arm64/v8,linux/amd64/v2,linux/amd64 \
    .
