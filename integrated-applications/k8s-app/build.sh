#!/usr/bin/env bash
set -euo pipefail

# Builds and publishes the k8s-app UCX application. Intended to run inside the k8s IM container of the
# launcher environment where /opt/shared (ucloud.dk/shared) and /opt/integrated-applications are mounted.
#
# One-time setup:
#   ucloud ucx-keygen --private ucx-keys/key.priv --public ucx-keys/key.pub
#   upload app.yaml through the UCloud application studio (admin)

cd "$(dirname "$0")"

APP_NAME=kubernetes
APP_VERSION=0.1.3
PROVIDER_DOMAIN=k8s.localhost.direct

export PATH=$PATH:/usr/local/go/bin
go build -o bin/k8s-app ./cmd/k8s-app

ucloud ucx-sign \
    --binary bin/k8s-app \
    --private-key ucx-keys/key.priv \
    --provider-domain "$PROVIDER_DOMAIN" \
    --app-name "$APP_NAME" \
    --app-version "$APP_VERSION"

ucloud ucx-publish "$APP_NAME" "$APP_VERSION" "$(pwd)/bin"
