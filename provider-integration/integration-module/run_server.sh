#!/usr/bin/env bash
mkdir -p /home/ucloud
chown -R ucloud:ucloud /home/ucloud
chown -R ucloud:ucloud /etc/ucloud
mkdir -p /var/log/ucloud/structured
chmod 777 /var/log/ucloud/structured
set -e
gradle buildDebug
set +e
sudo -u ucloud ucloud
