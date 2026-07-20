#!/usr/bin/env bash
set -euo pipefail

version=1.29.2
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

docker buildx build \
    -f "$script_dir/dev.Dockerfile" \
    --build-arg "SYNCTHING_VERSION=$version" \
    --push \
    --tag "dreg.cloud.sdu.dk/ucloud/syncthing-go-dev:${version}" \
    --platform linux/arm64/v8,linux/amd64 \
    "$script_dir"

docker buildx build \
    -f "$script_dir/Dockerfile" \
    --build-arg "SYNCTHING_VERSION=$version" \
    --push \
    --tag "dreg.cloud.sdu.dk/ucloud/syncthing-go:${version}" \
    --platform linux/arm64/v8,linux/amd64 \
    "$script_dir"
