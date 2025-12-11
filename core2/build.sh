#!/usr/bin/env bash
export DOCKER_CLI_HINTS=false
set -e

./builder_start.sh

echo "x64 compiling..."
docker exec -it ucloud-core2-builder-x64 bash -c '
    cd /opt/ucloud;
    CGO_ENABLED=0 go build -o bin/ucloud_x86_64 -trimpath ucloud.dk/core/cmd/ucloud
'

echo "arm64 compiling..."
docker exec -it ucloud-core2-builder-arm64 bash -c '
    cd /opt/ucloud;
    CGO_ENABLED=0 go build -o bin/ucloud_aarch64 -trimpath ucloud.dk/core/cmd/ucloud
'

if [ -z "$NO_DOCKER" ]; then
    version=`cat ../backend/version.txt`
    docker buildx build \
        -f Dockerfile \
        --push \
        --tag dreg.cloud.sdu.dk/ucloud/core2:${version} \
        --platform linux/arm64/v8,linux/amd64 \
        .
fi

