#!/usr/bin/env bash
version=`cat ../../../../backend/version.txt`
docker buildx build \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud-dev/slurm:${version} \
    --platform linux/arm64/v8,linux/amd64 \
    .
