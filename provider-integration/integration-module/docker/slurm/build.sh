#!/usr/bin/env bash
version=`cat ../../../../backend/version.txt`
docker build . -t dreg.cloud.sdu.dk/ucloud-dev/slurm:${version}
