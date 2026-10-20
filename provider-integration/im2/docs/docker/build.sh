#!/usr/bin/env bash

version=`cat ../../../../core2/version.txt`

mdbook build ../ --dest-dir $PWD/www

if ! grep -rq "@VERSION@" --include='*.html' $PWD/www; then
    echo "No @VERSION@ token found in the built documentation" >&2
    exit 1
fi

find $PWD/www -name '*.html' -exec sed -i.bak "s/@VERSION@/${version}/g" {} +
find $PWD/www -name '*.bak' -delete

docker buildx build \
    -f Dockerfile \
    --push \
    --tag dreg.cloud.sdu.dk/ucloud/platform-docs:${version} \
    --platform linux/arm64/v8,linux/amd64 \
    .
