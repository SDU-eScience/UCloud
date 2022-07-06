#!/usr/bin/env bash
mkdir -p /home/ucloud
chown -R ucloud:ucloud /home/ucloud
chown -R ucloud:ucloud /etc/ucloud
set -e
gradle buildDebug
set +e
sudo -u ucloud ucloud
