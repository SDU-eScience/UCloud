#!/usr/bin/env bash
set -e
set -x

name='ucloud-im'
version=`cat ../../backend/version.txt`
echo "Tagging as ${name}:${version}"

rm -rf build/distributions
rm -rf build/service
gradle distTar
(mkdir -p build/service || true)
cp build/distributions/*.tar build/service.tar
cd build/service
tar xvf ../service.tar --strip-components=1 && \
cd ../../
mv build/service/bin/ucloud-integration-module build/service/bin/service 

docker build \
    --platform linux/amd64 \
    -t "${name}:${version}" \
    -f Dockerfile.alt \
    .

if hash docker-publish; then
    docker-publish "${name}:${version}"
fi
