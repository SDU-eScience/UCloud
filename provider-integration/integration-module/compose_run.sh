#!/usr/bin/env bash
chmod 755 /opt/ucloud
chmod 755 /opt/ucloud/build
chmod 755 /opt/ucloud/build/install
chmod 755 /opt/ucloud/build/install/ucloud-integration-module
chmod 755 /opt/ucloud/build/install/ucloud-integration-module/bin
chmod 755 /opt/ucloud/build/install/ucloud-integration-module/bin/ucloud-integration-module
chmod -R 755 /opt/ucloud/build/install/ucloud-integration-module/lib

mkdir -p /home/ucloud
chown -R ucloud:ucloud /home/ucloud
chown -R ucloud:ucloud /etc/ucloud
mkdir -p /var/log/ucloud/structured
chmod 777 /var/log/ucloud/structured
sudo -u ucloud ucloud