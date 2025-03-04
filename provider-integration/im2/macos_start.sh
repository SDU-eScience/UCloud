#!/usr/bin/env bash
init_container() {
    platform=$1
    platform_short=$2
    CONTAINER_NAME="ucloud-im2-macos-builder-$platform_short"
    IMAGE_NAME="golang:1.23.2"

    # Check if the container is already running
    if docker ps --filter "name=$CONTAINER_NAME" --format '{{.Names}}' | grep -q "^$CONTAINER_NAME$"; then
        echo "$platform_short builder ready"
    else 
        docker run -d -v `realpath ..`/:/opt/ucloud --name "$CONTAINER_NAME" --platform $platform "$IMAGE_NAME" sleep inf
        echo "$platform_short builder starting"
    fi
}

init_container "linux/amd64" x64
init_container "linux/arm64/v8" arm64
