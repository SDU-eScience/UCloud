# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM golang:1.26.1 AS build

ARG CLI_VERSION

WORKDIR /src
COPY . /src/ucloud-cli

WORKDIR /src/ucloud-cli
RUN --mount=type=bind,from=shared,target=/src/provider-integration/shared \
    --mount=type=bind,from=pgxscan,target=/src/provider-integration/pgxscan \
    --mount=type=bind,from=gonja,target=/src/provider-integration/gonja \
    set -eu; \
    mkdir -p /out/current; \
    for target in linux/amd64 linux/arm64 darwin/amd64 darwin/arm64; do \
        GOOS="${target%/*}"; \
        GOARCH="${target#*/}"; \
        CGO_ENABLED=0 GOOS="${GOOS}" GOARCH="${GOARCH}" go build -trimpath -ldflags="-s -w" -o /tmp/ucloud ./cmd/ucloud; \
        tar -czf "/out/current/ucloud-${CLI_VERSION}-${GOOS}-${GOARCH}.tar.gz" -C /tmp ucloud; \
    done; \
    printf '%s\n' "${CLI_VERSION}" > /out/current/version.txt; \
    sha256sum /out/current/*.tar.gz > /out/current/checksums.txt; \
    go run ./dist/manifest.go "${CLI_VERSION}" /out/current /out/current/manifest.json

FROM caddy:2.11-alpine

COPY dist/Caddyfile /etc/caddy/Caddyfile
COPY install.sh /srv/install.sh
COPY --from=build /out/current /srv/current

EXPOSE 8080
