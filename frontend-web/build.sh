#!/usr/bin/env bash
version=`cat ../core2/version.txt`

name="webclient"

cd webclient
npm install
npm run build
cd ../

docker build \
    --platform linux/amd64 \
    --build-arg SERVICE_NAME="${name}" \
    --build-arg APP_VERSION="${version}" \
    -f Dockerfile \
    --tag dreg.cloud.sdu.dk/ucloud/${name}:${version} \
    --push \
    .
