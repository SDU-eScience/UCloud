if [ ! -f "/etc/ucloud/core.json" ]; then
  cp /opt/ucloud-default-config/* /etc/ucloud
  chmod 644 /etc/ucloud/*
  chown -R ucloud: /etc/ucloud/
  chmod 600 /etc/ucloud/server.json

  chown -R munge:munge /etc/munge
  service munge start 
  chown testuser:testuser ${DATA_MOUNT} 

  rm /etc/ucloud/config_installer.sh
fi

service munge start 