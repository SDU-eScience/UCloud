FROM golang:1.23.2 AS build
WORKDIR /app
RUN --mount=type=bind,target=. ls /app
RUN --mount=type=bind,target=. cd /app && CGO_ENABLED=1 go build -o /usr/bin/ucloud-sync .

FROM ubuntu:24.04

# Exe
COPY --from=build /usr/bin/ucloud-sync /usr/bin/ucloud-sync
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Syncthing
RUN apt-get update && apt-get install -y bash wget
WORKDIR /opt
RUN if [ "$(uname -p)" = "aarch64" ]; then ARCH=arm64; else ARCH=amd64; fi \
    && wget "https://github.com/syncthing/syncthing/releases/download/v1.29.2/syncthing-linux-$ARCH-v1.29.2.tar.gz"
RUN tar xzf syncthing*.tar.gz
RUN rm syncthing*.tar.gz
RUN mv syncthing* syncthing

# User
RUN useradd --uid 11042 syncthing
RUN mkdir /home/syncthing && chown syncthing: /home/syncthing
USER 11042
WORKDIR /home/syncthing
