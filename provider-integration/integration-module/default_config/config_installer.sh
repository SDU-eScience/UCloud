#!/bin/bash

if [ ! -f "/etc/ucloud/core.json" ]; then
  cp /opt/ucloud-default-config/* /etc/ucloud
  chmod 644 /etc/ucloud/*
  chown -R ucloud: /etc/ucloud/
  chmod 600 /etc/ucloud/server.json

  chown -R munge:munge /etc/munge
  service munge start 
  chown testuser:testuser ${DATA_MOUNT} 

  rm /etc/ucloud/config_installer.sh

  echo -e   '{ 
	"remoteHost": "localhost", 
	"remotePort": 8889, 
	"remoteScheme": "http", 
	"sharedSecret": "somesharedsecret" 
  } ' > /etc/ucloud/frontend_proxy.json

  chown ucloud:ucloud /etc/ucloud/frontend_proxy.json
  chmod 640 /etc/ucloud/frontend_proxy.json
  usermod -a -G ucloud testuser
  chmod 600 /etc/ucloud/frontend_proxy.json

fi

service munge start 