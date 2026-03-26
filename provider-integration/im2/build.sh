#!/usr/bin/env bash
export DOCKER_CLI_HINTS=false
set -e

./builder_start.sh

echo "x64 compiling..."
docker exec -it ucloud-im2-builder-x64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=0 go build -o bin/ucloud_x86_64 -trimpath ucloud.dk/cmd/ucloud-im
'
docker exec -it ucloud-im2-builder-x64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=0 go build -o bin/ucviz_x86_64 -trimpath ucloud.dk/cmd/ucviz
'
docker exec -it ucloud-im2-builder-x64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=0 go build -o bin/ucmetrics_x86_64 -trimpath ucloud.dk/cmd/ucmetrics
'
docker exec -it ucloud-im2-builder-x64 bash -c '
    cd /opt/ucloud/im2 ;
    CGO_ENABLED=0 go build -o bin/vmagent_x86_64 -trimpath ucloud.dk/cmd/vmagent
'

echo "arm64 compiling..."
docker exec -it ucloud-im2-builder-arm64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=0 go build -o bin/ucloud_aarch64 -trimpath ucloud.dk/cmd/ucloud-im
'
docker exec -it ucloud-im2-builder-arm64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=0 go build -o bin/ucmetrics_aarch64 -trimpath ucloud.dk/cmd/ucmetrics
'
docker exec -it ucloud-im2-builder-arm64 bash -c '
    cd /opt/ucloud/im2 ; 
    CGO_ENABLED=0 go build -o bin/ucviz_aarch64 -trimpath ucloud.dk/cmd/ucviz
'
docker exec -it ucloud-im2-builder-arm64 bash -c '
    cd /opt/ucloud/im2 ;
    CGO_ENABLED=0 go build -o bin/vmagent_aarch64 -trimpath ucloud.dk/cmd/vmagent
'


if [ -z "$NO_DOCKER" ]; then
    version=`cat ../../core2/version.txt`
    docker buildx build \
        -f Dockerfile \
        --push \
        --tag dreg.cloud.sdu.dk/ucloud/im2:${version} \
        --platform linux/arm64/v8,linux/amd64 \
        .
fi

