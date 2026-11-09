# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM golang:1.26.1 AS build

ARG CLI_VERSION

WORKDIR /src/ucloud-cli
COPY go.mod go.sum ./

RUN --mount=type=bind,from=shared,target=/src/provider-integration/shared \
    --mount=type=bind,from=pgxscan,target=/src/provider-integration/pgxscan \
    --mount=type=bind,from=gonja,target=/src/provider-integration/gonja \
    --mount=type=cache,id=ucloud-cli-go-mod,target=/go/pkg/mod \
    --mount=type=cache,id=ucloud-cli-go-build,target=/root/.cache/go-build \
    go mod download

COPY . /src/ucloud-cli

RUN --mount=type=bind,from=shared,target=/src/provider-integration/shared \
    --mount=type=bind,from=pgxscan,target=/src/provider-integration/pgxscan \
    --mount=type=bind,from=gonja,target=/src/provider-integration/gonja \
    --mount=type=cache,id=ucloud-cli-go-mod,target=/go/pkg/mod \
    --mount=type=cache,id=ucloud-cli-go-build,target=/root/.cache/go-build \
    set -eu; \
    mkdir -p /out/current; \
    for target in linux/amd64 linux/arm64 darwin/amd64 darwin/arm64; do \
        GOOS="${target%/*}"; \
        GOARCH="${target#*/}"; \
        CGO_ENABLED=0 GOOS="${GOOS}" GOARCH="${GOARCH}" go build -trimpath -ldflags="-s -w -X ucloud.dk/ucloud_cli/pkg/version.Version=${CLI_VERSION}" -o /tmp/ucloud ./cmd/ucloud; \
        tar -czf "/out/current/ucloud-${CLI_VERSION}-${GOOS}-${GOARCH}.tar.gz" -C /tmp ucloud; \
    done; \
    printf '%s\n' "${CLI_VERSION}" > /out/current/version.txt; \
    (cd /out/current && sha256sum *.tar.gz > checksums.txt); \
    go run ./dist/manifest.go "${CLI_VERSION}" /out/current /out/current/manifest.json

FROM caddy:2.11-alpine

COPY dist/Caddyfile /etc/caddy/Caddyfile
COPY install.sh /srv/install.sh
COPY --from=build /out/current /srv/current

EXPOSE 8080
