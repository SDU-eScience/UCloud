#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/.." && pwd)"
version="$(<"${script_dir}/version.txt")"
image="dreg.cloud.sdu.dk/ucloud/ucloud-cli"

if [[ -z "${version}" ]]; then
    echo "ucloud-cli/version.txt is empty" >&2
    exit 1
fi

docker buildx build \
    --file "${script_dir}/Dockerfile" \
    --build-context "shared=${repo_root}/provider-integration/shared" \
    --build-context "pgxscan=${repo_root}/provider-integration/pgxscan" \
    --build-context "gonja=${repo_root}/provider-integration/gonja" \
    --build-arg "CLI_VERSION=${version}" \
    --platform linux/amd64,linux/arm64 \
    --tag "${image}:${version}" \
    --tag "${image}:latest" \
    --push \
    "${script_dir}"
