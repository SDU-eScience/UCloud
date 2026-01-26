#!/usr/bin/env bash

version=`cat ../../../../backend/version.txt`

mdbook build ../ --dest-dir $PWD/www

docker buildx build \
    -f Dockerfile \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud/platform-docs:${version} \
    --platform linux/arm64/v8,linux/amd64 \
    .
