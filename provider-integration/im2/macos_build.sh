#!/usr/bin/env bash
export DOCKER_CLI_HINTS=false
set -e

./macos_start.sh

echo "x64 compiling..."
docker exec -it ucloud-im2-macos-builder-x64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=1 go build -o bin/ucloud_x86_64 -trimpath ucloud.dk/cmd/ucloud-im
'

echo "arm64 compiling..."
docker exec -it ucloud-im2-macos-builder-arm64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=1 go build -o bin/ucloud_aarch64 -trimpath ucloud.dk/cmd/ucloud-im
'

if [ -z "$NO_DOCKER" ]; then
    version=`cat ../../backend/version.txt`
    docker buildx build \
        -f macosbuilder.Dockerfile \
        --push \
        --tag dreg.cloud.sdu.dk/ucloud/im2:${version} \
        --platform linux/arm64/v8,linux/amd64 \
        .
fi

