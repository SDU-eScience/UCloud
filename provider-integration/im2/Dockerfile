FROM golang:1.23.2 AS build
WORKDIR /app
RUN  --mount=type=bind,target=. ls /app
RUN  --mount=type=bind,target=. cd /app/im2 && CGO_ENABLED=1 go build -o /usr/bin/ucloud -trimpath ucloud.dk/cmd/ucloud-im

FROM ubuntu:24.04
COPY --from=build /usr/bin/ucloud /usr/bin/ucloud
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*
