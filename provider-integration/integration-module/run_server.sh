#!/usr/bin/env bash
mkdir -p /home/ucloud
chown -R ucloud:ucloud /home/ucloud
gradle buildDebug
sudo -u ucloud ucloud
